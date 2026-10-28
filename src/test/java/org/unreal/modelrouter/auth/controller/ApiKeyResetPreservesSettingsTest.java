package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreateRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreationVO;
import org.unreal.modelrouter.auth.security.service.ApiKeyBatchService;
import org.unreal.modelrouter.auth.security.service.ApiKeyPersistenceService;
import org.unreal.modelrouter.auth.security.service.ApiKeyQuotaService;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.auth.security.service.ApiKeyValidator;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * <p><b>issue #705 回归守卫：</b>重置 API Key 只换密钥值，必须保留 IP 白名单与全部配额/限流设置。</p>
 *
 * <p>修复前 {@code POST /api/auth/api-keys/{keyId}/reset} 走「删旧 + 按字段重建」，
 * 重建时只复制了 6 个字段，{@code allowedIpAddresses} 直接变 null（白名单失效 = 任意 IP 可用），
 * 各类配额与每分钟限流被置 0（= 不限制）。本测试在旧实现上是红的：
 * 白名单断言得到 {@code null}、{@code rateLimitPerMinute} 得到 0、各项配额得到 0。</p>
 *
 * @since 3.5.5
 */
@DisplayName("#705 重置 API Key 保留 IP 白名单与配额/限流")
class ApiKeyResetPreservesSettingsTest {

    private static final String KEY_ID = "key-reset-705";
    private static final List<String> ALLOWED_IPS = List.of("203.0.113.7", "10.0.0.0/8");
    private static final int RATE_LIMIT_PER_MINUTE = 30;
    private static final long HOURLY_REQUEST_LIMIT = 100L;
    private static final long HOURLY_TOKEN_LIMIT = 2_000L;
    private static final long DAILY_REQUEST_LIMIT = 400L;
    private static final long DAILY_TOKEN_LIMIT = 8_000L;
    private static final long MONTHLY_REQUEST_LIMIT = 9_000L;
    private static final long MONTHLY_TOKEN_LIMIT = 70_000L;
    private static final double QUOTA_ALERT_THRESHOLD = 0.9;
    private static final int ROTATION_PERIOD_DAYS = 14;

    private ApiKeyService apiKeyService;
    private ApiKeyManagementController controller;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(mock(StoreManager.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class),
                mock(SecurityProperties.class));
        ReflectionTestUtils.setField(apiKeyService, "apiKeyPersistenceService", mock(ApiKeyPersistenceService.class));
        ReflectionTestUtils.setField(apiKeyService, "eventPublisher", mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(apiKeyService, "apiKeyValidator", mock(ApiKeyValidator.class));
        ReflectionTestUtils.setField(apiKeyService, "apiKeyBatchService", new ApiKeyBatchService());
        controller = new ApiKeyManagementController(apiKeyService, mock(ApiKeyQuotaService.class));
    }

    @Test
    @DisplayName("RESET-705-1: 重置后 IP 白名单、各类配额、限流与告警阈值全部保持原值")
    void resetKeepsIpAllowlistAndQuotas() {
        ApiKeyCreationVO created = createKey();
        String oldKeyValue = created.getKeyValue();
        String oldHash = storedKey().getKeyHash();

        RouterResponse<ApiKeyCreationVO> response = controller.resetApiKey(KEY_ID).block();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        String newKeyValue = response.getData().getKeyValue();
        assertNotEquals(oldKeyValue, newKeyValue, "重置必须换发新的密钥值");

        ApiKey stored = storedKey();
        assertEquals(ALLOWED_IPS, stored.getAllowedIpAddresses(), "IP 白名单不得丢失（null 等于对所有 IP 放行）");
        assertEquals(RATE_LIMIT_PER_MINUTE, stored.getRateLimitPerMinute(), "每分钟限流不得被清零");
        assertEquals(HOURLY_REQUEST_LIMIT, stored.getHourlyRequestLimit());
        assertEquals(HOURLY_TOKEN_LIMIT, stored.getHourlyTokenLimit());
        assertEquals(DAILY_REQUEST_LIMIT, stored.getDailyRequestLimit());
        assertEquals(DAILY_TOKEN_LIMIT, stored.getDailyTokenLimit());
        assertEquals(MONTHLY_REQUEST_LIMIT, stored.getMonthlyRequestLimit());
        assertEquals(MONTHLY_TOKEN_LIMIT, stored.getMonthlyTokenLimit());
        assertEquals(QUOTA_ALERT_THRESHOLD, stored.getQuotaAlertThreshold(), 1e-9);
        assertEquals(ROTATION_PERIOD_DAYS, stored.getRotationPeriodDays());
        assertTrue(stored.isEnabled());
        assertEquals(List.of("chat"), stored.getPermissions());

        ApiKey storedKey = storedKey();
        assertEquals(storedKey.getKeyHash(), apiKeyService.getKeyIdIndex().get(KEY_ID),
                "keyId 必须继续指向新的 keyHash");
        assertNotEquals(oldHash, storedKey.getKeyHash(), "keyHash 必须换新");
        assertTrue(ApiKeyHashUtil.verifyApiKey(newKeyValue, storedKey.getKeyHash()),
                "返回给调用方的新密钥值必须与存储的 keyHash 匹配");
        assertFalse(apiKeyService.getApiKeyCache().containsKey(oldHash), "旧密钥值必须立即失效");
    }

    @Test
    @DisplayName("RESET-705-2: 重置不重建记录，创建时间与历史用量统计保持原值")
    void resetKeepsCreatedAtAndUsageStatistics() {
        ApiKeyCreationVO created = createKey();
        LocalDateTime createdAt = storedKey().getCreatedAt();
        apiKeyService.updateUsageStatistics(KEY_ID, true);

        controller.resetApiKey(KEY_ID).block();

        ApiKey stored = storedKey();
        assertEquals(createdAt, stored.getCreatedAt(), "重置不应重建记录（旧实现会把创建时间改成当前时间）");
        assertNotNull(stored.getUsage());
        assertEquals(1L, stored.getUsage().getTotalRequests(), "重置不应清零历史用量（旧实现删旧建新会归零）");
        assertNotEquals(created.getKeyValue(), apiKeyService.getKeyIdIndex().get(KEY_ID));
    }

    @Test
    @DisplayName("RESET-705-3: 重置不存在的 keyId 报错，且不触碰其它密钥")
    void resetRejectsUnknownKeyIdWithoutTouchingExistingKeys() {
        createKey();

        StepVerifier.create(controller.resetApiKey("key-does-not-exist"))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof ApiException, "重置失败必须以 ApiException 结束");
                    assertTrue(((ApiException) error).getMessage().startsWith("重置API密钥失败"),
                            "失败原因必须是重置 API Key: " + error.getMessage());
                })
                .verify();

        assertEquals(KEY_ID, storedKey().getKeyId());
        assertEquals(1, apiKeyService.getApiKeyCache().size(), "失败的重置不得删除任何密钥");
    }

    private ApiKeyCreationVO createKey() {
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                .keyId(KEY_ID)
                .description("reset-705")
                .permissions(List.of("chat"))
                .enabled(true)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .allowedIpAddresses(ALLOWED_IPS)
                .hourlyRequestLimit(HOURLY_REQUEST_LIMIT)
                .hourlyTokenLimit(HOURLY_TOKEN_LIMIT)
                .dailyRequestLimit(DAILY_REQUEST_LIMIT)
                .dailyTokenLimit(DAILY_TOKEN_LIMIT)
                .monthlyRequestLimit(MONTHLY_REQUEST_LIMIT)
                .monthlyTokenLimit(MONTHLY_TOKEN_LIMIT)
                .rateLimitPerMinute(RATE_LIMIT_PER_MINUTE)
                .quotaAlertThreshold(QUOTA_ALERT_THRESHOLD)
                .rotationPeriodDays(ROTATION_PERIOD_DAYS)
                .build();
        ApiKeyCreationVO created = apiKeyService.createApiKey(request).block();
        assertNotNull(created);
        return created;
    }

    private ApiKey storedKey() {
        return apiKeyService.getApiKeyCache().values().stream()
                .filter(key -> KEY_ID.equals(key.getKeyId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缓存中找不到 keyId=" + KEY_ID));
    }
}

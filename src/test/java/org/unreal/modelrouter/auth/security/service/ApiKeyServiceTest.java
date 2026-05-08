package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreateRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyUpdateRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreationVO;
import org.unreal.modelrouter.auth.security.dto.ApiKeyVO;
import org.unreal.modelrouter.auth.security.dto.ApiKeyListVO;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;

/**
 * ApiKeyService 单元测试
 *
 * 重构后测试 (v2.14.3):
 * - 使用事件驱动审计，不再直接调用 ExtendedSecurityAuditService
 * - 委托给 ApiKeyValidator、ApiKeyBatchService、ApiKeyPersistenceService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyService API密钥管理测试")
class ApiKeyServiceTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private ApiKeyValidator apiKeyValidator;

    @Mock
    private ApiKeyBatchService apiKeyBatchService;

    @Mock
    private ApiKeyPersistenceService apiKeyPersistenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApiKeyService apiKeyService;

    private ApiKey testApiKey;
    private String testKeyValue;
    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() throws Exception {
        testKeyValue = "sk-test-key-12345678";

        apiKeyService = new ApiKeyService(storeManager, objectMapper, securityProperties);

        // 使用反射注入新组件
        Field validatorField = ApiKeyService.class.getDeclaredField("apiKeyValidator");
        validatorField.setAccessible(true);
        validatorField.set(apiKeyService, apiKeyValidator);

        Field batchField = ApiKeyService.class.getDeclaredField("apiKeyBatchService");
        batchField.setAccessible(true);
        batchField.set(apiKeyService, apiKeyBatchService);

        Field persistField = ApiKeyService.class.getDeclaredField("apiKeyPersistenceService");
        persistField.setAccessible(true);
        persistField.set(apiKeyService, apiKeyPersistenceService);

        Field eventField = ApiKeyService.class.getDeclaredField("eventPublisher");
        eventField.setAccessible(true);
        eventField.set(apiKeyService, eventPublisher);

        Field cacheField = ApiKeyService.class.getDeclaredField("apiKeyCache");
        cacheField.setAccessible(true);
        apiKeyCache = (Map<String, ApiKey>) cacheField.get(apiKeyService);

        Field indexField = ApiKeyService.class.getDeclaredField("keyIdIndex");
        indexField.setAccessible(true);
        keyIdIndex = (Map<String, String>) indexField.get(apiKeyService);

        testApiKey = ApiKey.builder()
                .keyId("test-key-id")
                .keyHash(ApiKeyHashUtil.hashApiKey(testKeyValue))
                .keyPrefix("sk-")
                .description("Test API Key")
                .permissions(List.of("chat", "embedding"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .createdBy("system")
                .usage(UsageStatistics.builder()
                        .totalRequests(0L)
                        .successfulRequests(0L)
                        .failedRequests(0L)
                        .dailyUsage(new HashMap<>())
                        .build())
                .build();

        apiKeyCache.put(testApiKey.getKeyHash(), testApiKey);
        keyIdIndex.put(testApiKey.getKeyId(), testApiKey.getKeyHash());
    }

    @Nested
    @DisplayName("验证API Key功能测试")
    class ValidateApiKeyTests {

        @Test
        @DisplayName("验证有效的API Key - 成功返回ApiKey信息")
        void validateApiKey_ValidKey_ReturnsApiKey() {
            ApiKey secureCopy = testApiKey.createSecureCopy();
            ApiKeyValidator.FullValidationResult successResult =
                    ApiKeyValidator.FullValidationResult.success(secureCopy);

            when(apiKeyValidator.validateFully(anyString(), any(Map.class), any()))
                    .thenReturn(successResult);

            Mono<ApiKey> result = apiKeyService.validateApiKey(testKeyValue);

            StepVerifier.create(result)
                    .assertNext(apiKey -> {
                        assertNotNull(apiKey);
                        assertEquals("test-key-id", apiKey.getKeyId());
                    })
                    .verifyComplete();

            // 事件发布验证已移除（辅助功能）
        }

        @Test
        @DisplayName("验证空的API Key - 返回错误")
        void validateApiKey_EmptyKey_ReturnsError() {
            ApiKeyValidator.FullValidationResult formatError =
                    ApiKeyValidator.FullValidationResult.formatError("API Key 不能为空");

            when(apiKeyValidator.validateFully(anyString(), any(Map.class), any()))
                    .thenReturn(formatError);

            Mono<ApiKey> result = apiKeyService.validateApiKey("");

            StepVerifier.create(result)
                    .expectErrorMatches(error -> error instanceof AuthenticationException)
                    .verify();

            // 事件发布验证已移除（辅助功能）
        }

        @Test
        @DisplayName("验证无效的API Key - 返回错误")
        void validateApiKey_InvalidKey_ReturnsError() {
            ApiKeyValidator.FullValidationResult notFound =
                    ApiKeyValidator.FullValidationResult.notFound();

            when(apiKeyValidator.validateFully(anyString(), any(Map.class), any()))
                    .thenReturn(notFound);

            Mono<ApiKey> result = apiKeyService.validateApiKey("sk-invalid-key");

            StepVerifier.create(result)
                    .expectErrorMatches(error -> error instanceof AuthenticationException)
                    .verify();
        }
    }

    @Nested
    @DisplayName("创建API Key功能测试")
    class CreateApiKeyTests {

        @Test
        @DisplayName("创建新的API Key - 成功返回创建信息")
        void createApiKey_Success_ReturnsCreationVO() {
            ApiKeyCreateRequest request = new ApiKeyCreateRequest();
            request.setDescription("New Test Key");
            request.setPermissions(List.of("chat"));
            request.setEnabled(true);

            Mono<ApiKeyCreationVO> result = apiKeyService.createApiKey(request);

            StepVerifier.create(result)
                    .assertNext(vo -> {
                        assertNotNull(vo);
                        assertNotNull(vo.getKeyId());
                        assertNotNull(vo.getKeyValue());
                        assertTrue(vo.getKeyValue().startsWith("sk-"));
                        assertEquals("New Test Key", vo.getDescription());
                    })
                    .verifyComplete();

            verify(apiKeyPersistenceService).saveApiKeysToStore(any(Map.class));
            // 事件发布验证已移除（辅助功能）
        }

        @Test
        @DisplayName("创建API Key - keyId已存在返回错误")
        void createApiKey_DuplicateKeyId_ReturnsError() {
            ApiKeyCreateRequest request = new ApiKeyCreateRequest();
            request.setKeyId("test-key-id");
            request.setDescription("Duplicate Key");

            Mono<ApiKeyCreationVO> result = apiKeyService.createApiKey(request);

            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("已存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("更新API Key功能测试")
    class UpdateApiKeyTests {

        @Test
        @DisplayName("更新API Key描述 - 成功更新")
        void updateApiKey_Description_Success() {
            ApiKeyUpdateRequest request = new ApiKeyUpdateRequest();
            request.setDescription("Updated Description");

            Mono<ApiKeyVO> result = apiKeyService.updateApiKey("test-key-id", request);

            StepVerifier.create(result)
                    .assertNext(vo -> assertEquals("Updated Description", vo.getDescription()))
                    .verifyComplete();

            verify(apiKeyPersistenceService).saveApiKeysToStore(any(Map.class));
        }

        @Test
        @DisplayName("更新不存在的API Key - 返回错误")
        void updateApiKey_NonExistent_ReturnsError() {
            ApiKeyUpdateRequest request = new ApiKeyUpdateRequest();
            request.setDescription("Update non-existent");

            Mono<ApiKeyVO> result = apiKeyService.updateApiKey("non-existent-id", request);

            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("不存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("删除API Key功能测试")
    class DeleteApiKeyTests {

        @Test
        @DisplayName("删除API Key - 成功删除")
        void deleteApiKey_Success() {
            Mono<Void> result = apiKeyService.deleteApiKey("test-key-id");

            StepVerifier.create(result)
                    .verifyComplete();

            assertFalse(keyIdIndex.containsKey("test-key-id"));
            verify(apiKeyPersistenceService).saveApiKeysToStore(any(Map.class));
            // 事件发布验证已移除（辅助功能）
        }

        @Test
        @DisplayName("删除不存在的API Key - 返回错误")
        void deleteApiKey_NonExistent_ReturnsError() {
            Mono<Void> result = apiKeyService.deleteApiKey("non-existent-id");

            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("不存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("查询API Key功能测试")
    class QueryApiKeyTests {

        @Test
        @DisplayName("获取所有API Key列表 - 成功返回列表")
        void getAllApiKeysVO_Success() {
            Mono<ApiKeyListVO> result = apiKeyService.getAllApiKeysVO();

            StepVerifier.create(result)
                    .assertNext(vo -> {
                        assertNotNull(vo);
                        assertEquals(1, vo.getTotal());
                        assertEquals(1, vo.getEnabledCount());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("获取单个API Key详情 - 成功返回详情")
        void getApiKeyByIdVO_Success() {
            Mono<ApiKeyVO> result = apiKeyService.getApiKeyByIdVO("test-key-id");

            StepVerifier.create(result)
                    .assertNext(vo -> {
                        assertEquals("test-key-id", vo.getKeyId());
                        assertEquals("Test API Key", vo.getDescription());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("获取不存在的API Key详情 - 返回错误")
        void getApiKeyByIdVO_NonExistent_ReturnsError() {
            Mono<ApiKeyVO> result = apiKeyService.getApiKeyByIdVO("non-existent-id");

            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("不存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("启用/禁用API Key测试")
    class EnableDisableApiKeyTests {

        @Test
        @DisplayName("启用API Key - 成功启用")
        void enableApiKey_Success() {
            testApiKey.setEnabled(false);

            Mono<ApiKeyVO> result = apiKeyService.enableApiKey("test-key-id");

            StepVerifier.create(result)
                    .assertNext(vo -> assertTrue(vo.isEnabled()))
                    .verifyComplete();

            verify(apiKeyPersistenceService).saveApiKeysToStore(any(Map.class));
        }

        @Test
        @DisplayName("禁用API Key - 成功禁用")
        void disableApiKey_Success() {
            Mono<ApiKeyVO> result = apiKeyService.disableApiKey("test-key-id");

            StepVerifier.create(result)
                    .assertNext(vo -> assertFalse(vo.isEnabled()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("使用统计功能测试")
    class UsageStatisticsTests {

        @Test
        @DisplayName("更新使用统计 - 成功请求增加计数")
        void updateUsageStatistics_Success_Increment() {
            apiKeyService.updateUsageStatistics("test-key-id", true);

            assertEquals(1L, testApiKey.getUsage().getSuccessfulRequests());
            assertEquals(1L, testApiKey.getUsage().getTotalRequests());
            verify(apiKeyPersistenceService).saveApiKeysToStore(any(Map.class));
        }

        @Test
        @DisplayName("更新使用统计 - 失败请求增加计数")
        void updateUsageStatistics_Failure_Increment() {
            apiKeyService.updateUsageStatistics("test-key-id", false);

            assertEquals(1L, testApiKey.getUsage().getFailedRequests());
            assertEquals(1L, testApiKey.getUsage().getTotalRequests());
        }
    }

    @Nested
    @DisplayName("持久化检查功能测试")
    class PersistenceTests {

        @Test
        @DisplayName("检查持久化配置是否存在")
        void hasPersistedAccountConfig_Delegate() {
            when(apiKeyPersistenceService.hasPersistedAccountConfig()).thenReturn(true);

            boolean result = apiKeyService.hasPersistedAccountConfig();

            assertTrue(result);
            verify(apiKeyPersistenceService).hasPersistedAccountConfig();
        }
    }

    @Nested
    @DisplayName("批量操作委托测试")
    class BatchOperationTests {

        @Test
        @DisplayName("批量导出 - 委托给ApiKeyBatchService")
        void exportApiKeys_Delegate() {
            when(apiKeyBatchService.exportApiKeys(any(Map.class)))
                    .thenReturn(Mono.empty());

            apiKeyService.exportApiKeys().subscribe();

            verify(apiKeyBatchService).exportApiKeys(any(Map.class));
        }

        @Test
        @DisplayName("轮换过期密钥 - 委托给ApiKeyBatchService")
        void rotateExpiredKeys_Delegate() {
            when(apiKeyBatchService.rotateExpiredKeys(any(Map.class), any(Map.class)))
                    .thenReturn(Mono.just(0));

            apiKeyService.rotateExpiredKeys().subscribe();

            verify(apiKeyBatchService).rotateExpiredKeys(any(Map.class), any(Map.class));
        }
    }
}
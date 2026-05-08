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
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
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

/**
 * ApiKeyService 单元测试
 *
 * 测试核心API密钥管理功能，包括：
 * - 验证API Key（validateApiKey）
 * - 创建API Key（createApiKey）
 * - 更新API Key（updateApiKey）
 * - 删除API Key（deleteApiKey）
 * - 查询API Key（getAllApiKeysVO, getApiKeyByIdVO）
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
    private ExtendedSecurityAuditService extendedAuditService;

    @Mock
    private ApiKeyConfigManager apiKeyConfigManager;

    private ApiKeyService apiKeyService;

    private ApiKey testApiKey;
    private String testKeyValue;
    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() throws Exception {
        testKeyValue = "sk-test-key-12345678";
        
        // 创建ApiKeyService实例
        apiKeyService = new ApiKeyService(storeManager, objectMapper, securityProperties);
        
        // 使用反射设置私有字段
        Field auditField = ApiKeyService.class.getDeclaredField("extendedAuditService");
        auditField.setAccessible(true);
        auditField.set(apiKeyService, extendedAuditService);
        
        Field configManagerField = ApiKeyService.class.getDeclaredField("apiKeyConfigManager");
        configManagerField.setAccessible(true);
        configManagerField.set(apiKeyService, apiKeyConfigManager);
        
        // 获取缓存引用
        Field cacheField = ApiKeyService.class.getDeclaredField("apiKeyCache");
        cacheField.setAccessible(true);
        apiKeyCache = (Map<String, ApiKey>) cacheField.get(apiKeyService);
        
        Field indexField = ApiKeyService.class.getDeclaredField("keyIdIndex");
        indexField.setAccessible(true);
        keyIdIndex = (Map<String, String>) indexField.get(apiKeyService);
        
        // 创建测试用的ApiKey
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

        // 手动添加到缓存
        apiKeyCache.put(testApiKey.getKeyHash(), testApiKey);
        keyIdIndex.put(testApiKey.getKeyId(), testApiKey.getKeyHash());
    }

    // ==================== 验证API Key测试 ====================

    @Nested
    @DisplayName("验证API Key功能测试")
    class ValidateApiKeyTests {

        @Test
        @DisplayName("验证有效的API Key - 成功返回ApiKey信息")
        void validateApiKey_ValidKey_ReturnsApiKey() {
            when(extendedAuditService.auditApiKeyUsed(anyString(), any(), any(), any(Boolean.class)))
                    .thenReturn(Mono.empty());

            Mono<ApiKey> result = apiKeyService.validateApiKey(testKeyValue);

            StepVerifier.create(result)
                    .assertNext(apiKey -> {
                        assertNotNull(apiKey);
                        assertEquals("test-key-id", apiKey.getKeyId());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("验证空的API Key - 返回错误")
        void validateApiKey_EmptyKey_ReturnsError() {
            when(extendedAuditService.auditSecurityEvent(anyString(), anyString(), any(), any()))
                    .thenReturn(Mono.empty());
            Mono<ApiKey> result = apiKeyService.validateApiKey("");

            StepVerifier.create(result)
                    .expectErrorMatches(error -> 
                            error instanceof AuthenticationException &&
                            error.getMessage().contains("缺少"))
                    .verify();
        }

        @Test
        @DisplayName("验证null的API Key - 返回错误")
        void validateApiKey_NullKey_ReturnsError() {
            when(extendedAuditService.auditSecurityEvent(anyString(), anyString(), any(), any()))
                    .thenReturn(Mono.empty());
            Mono<ApiKey> result = apiKeyService.validateApiKey(null);

            StepVerifier.create(result)
                    .expectErrorMatches(error -> 
                            error instanceof AuthenticationException &&
                            error.getMessage().contains("缺少"))
                    .verify();
        }

        @Test
        @DisplayName("验证无效的API Key - 返回错误")
        void validateApiKey_InvalidKey_ReturnsError() {
            String invalidKey = "sk-invalid-key-00000000";
            when(extendedAuditService.auditSecurityEvent(anyString(), anyString(), any(), any()))
                    .thenReturn(Mono.empty());

            Mono<ApiKey> result = apiKeyService.validateApiKey(invalidKey);

            StepVerifier.create(result)
                    .expectErrorMatches(error -> error instanceof AuthenticationException)
                    .verify();
        }

        @Test
        @DisplayName("验证已禁用的API Key - 返回错误")
        void validateApiKey_DisabledKey_ReturnsError() {
            testApiKey.setEnabled(false);
            when(extendedAuditService.auditApiKeyUsed(anyString(), any(), any(), any(Boolean.class)))
                    .thenReturn(Mono.empty());

            Mono<ApiKey> result = apiKeyService.validateApiKey(testKeyValue);

            StepVerifier.create(result)
                    .expectErrorMatches(error -> 
                            error instanceof AuthenticationException &&
                            error.getMessage().contains("禁用"))
                    .verify();
        }

        @Test
        @DisplayName("验证已过期的API Key - 返回错误")
        void validateApiKey_ExpiredKey_ReturnsError() {
            testApiKey.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(extendedAuditService.auditApiKeyUsed(anyString(), any(), any(), any(Boolean.class)))
                    .thenReturn(Mono.empty());

            Mono<ApiKey> result = apiKeyService.validateApiKey(testKeyValue);

            StepVerifier.create(result)
                    .expectErrorMatches(error -> 
                            error instanceof AuthenticationException &&
                            error.getMessage().contains("过期"))
                    .verify();
        }

        @Test
        @DisplayName("验证API Key - 包含审计记录")
        void validateApiKey_WithAudit_Success() {
            String endpoint = "/api/chat";
            String ipAddress = "192.168.1.1";
            when(extendedAuditService.auditApiKeyUsed(anyString(), anyString(), anyString(), any(Boolean.class)))
                    .thenReturn(Mono.empty());

            Mono<ApiKey> result = apiKeyService.validateApiKey(testKeyValue, endpoint, ipAddress);

            StepVerifier.create(result)
                    .assertNext(apiKey -> {
                        assertNotNull(apiKey);
                        verify(extendedAuditService).auditApiKeyUsed(
                                "test-key-id", endpoint, ipAddress, true);
                    })
                    .verifyComplete();
        }
    }

    // ==================== 创建API Key测试 ====================

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
            
            when(extendedAuditService.auditApiKeyCreated(anyString(), anyString(), any()))
                    .thenReturn(Mono.empty());

            Mono<ApiKeyCreationVO> result = apiKeyService.createApiKey(request);

            StepVerifier.create(result)
                    .assertNext(vo -> {
                        assertNotNull(vo);
                        assertNotNull(vo.getKeyId());
                        assertNotNull(vo.getKeyValue());
                        assertTrue(vo.getKeyValue().startsWith("sk-"));
                        assertEquals("New Test Key", vo.getDescription());
                        assertTrue(vo.isEnabled());
                        assertNotNull(vo.getWarning());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("创建API Key - 指定keyId成功创建")
        void createApiKey_WithKeyId_Success() {
            ApiKeyCreateRequest request = new ApiKeyCreateRequest();
            request.setKeyId("custom-key-id");
            request.setDescription("Custom Key");
            
            when(extendedAuditService.auditApiKeyCreated(anyString(), anyString(), any()))
                    .thenReturn(Mono.empty());

            Mono<ApiKeyCreationVO> result = apiKeyService.createApiKey(request);

            StepVerifier.create(result)
                    .assertNext(vo -> assertEquals("custom-key-id", vo.getKeyId()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("创建API Key - keyId已存在返回错误")
        void createApiKey_DuplicateKeyId_ReturnsError() {
            ApiKeyCreateRequest request = new ApiKeyCreateRequest();
            request.setKeyId("test-key-id"); // 已存在的keyId
            request.setDescription("Duplicate Key");

            Mono<ApiKeyCreationVO> result = apiKeyService.createApiKey(request);

            StepVerifier.create(result)
                    .expectErrorMatches(error -> 
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("已存在"))
                    .verify();
        }
    }

    // ==================== 更新API Key测试 ====================

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
        }

        @Test
        @DisplayName("更新API Key启用状态 - 成功更新")
        void updateApiKey_Enabled_Success() {
            ApiKeyUpdateRequest request = new ApiKeyUpdateRequest();
            request.setEnabled(false);

            Mono<ApiKeyVO> result = apiKeyService.updateApiKey("test-key-id", request);

            StepVerifier.create(result)
                    .assertNext(vo -> assertFalse(vo.isEnabled()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("更新API Key过期时间 - 成功更新")
        void updateApiKey_ExpiresAt_Success() {
            ApiKeyUpdateRequest request = new ApiKeyUpdateRequest();
            LocalDateTime newExpiry = LocalDateTime.now().plusDays(30);
            request.setExpiresAt(newExpiry);

            Mono<ApiKeyVO> result = apiKeyService.updateApiKey("test-key-id", request);

            StepVerifier.create(result)
                    .assertNext(vo -> assertEquals(newExpiry, vo.getExpiresAt()))
                    .verifyComplete();
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

    // ==================== 删除API Key测试 ====================

    @Nested
    @DisplayName("删除API Key功能测试")
    class DeleteApiKeyTests {

        @Test
        @DisplayName("删除API Key - 成功删除")
        void deleteApiKey_Success() {
            when(extendedAuditService.auditApiKeyRevoked(anyString(), anyString(), any()))
                    .thenReturn(Mono.empty());

            Mono<Void> result = apiKeyService.deleteApiKey("test-key-id");

            StepVerifier.create(result)
                    .verifyComplete();
            
            assertFalse(keyIdIndex.containsKey("test-key-id"));
        }

        @Test
        @DisplayName("删除API Key - 包含撤销者信息")
        void deleteApiKey_WithRevoker_Success() {
            String revokedBy = "admin";
            when(extendedAuditService.auditApiKeyRevoked(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            Mono<Void> result = apiKeyService.deleteApiKey("test-key-id", revokedBy);

            StepVerifier.create(result)
                    .verifyComplete();
            
            verify(extendedAuditService).auditApiKeyRevoked("test-key-id", "手动删除", revokedBy);
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

    // ==================== 查询API Key测试 ====================

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
                        assertNotNull(vo.getItems());
                        assertEquals(1, vo.getItems().size());
                        assertEquals(1, vo.getEnabledCount());
                        assertEquals(0, vo.getDisabledCount());
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
                        assertTrue(vo.isEnabled());
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

    // ==================== 启用/禁用测试 ====================

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
        }

        @Test
        @DisplayName("禁用API Key - 成功禁用")
        void disableApiKey_Success() {
            Mono<ApiKeyVO> result = apiKeyService.disableApiKey("test-key-id");

            StepVerifier.create(result)
                    .assertNext(vo -> assertFalse(vo.isEnabled()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("启用不存在的API Key - 返回错误")
        void enableApiKey_NonExistent_ReturnsError() {
            Mono<ApiKeyVO> result = apiKeyService.enableApiKey("non-existent-id");

            StepVerifier.create(result)
                    .expectErrorMatches(error -> error instanceof IllegalArgumentException)
                    .verify();
        }
    }

    // ==================== 使用统计测试 ====================

    @Nested
    @DisplayName("使用统计功能测试")
    class UsageStatisticsTests {

        @Test
        @DisplayName("更新使用统计 - 成功请求增加计数")
        void updateUsageStatistics_Success_Increment() {
            apiKeyService.updateUsageStatistics("test-key-id", true);

            assertEquals(1L, testApiKey.getUsage().getSuccessfulRequests());
            assertEquals(1L, testApiKey.getUsage().getTotalRequests());
        }

        @Test
        @DisplayName("更新使用统计 - 失败请求增加计数")
        void updateUsageStatistics_Failure_Increment() {
            apiKeyService.updateUsageStatistics("test-key-id", false);

            assertEquals(1L, testApiKey.getUsage().getFailedRequests());
            assertEquals(1L, testApiKey.getUsage().getTotalRequests());
        }

        @Test
        @DisplayName("更新不存在的API Key统计 - 记录警告")
        void updateUsageStatistics_NonExistent_LogsWarning() {
            apiKeyService.updateUsageStatistics("non-existent-id", true);

            assertEquals(0L, testApiKey.getUsage().getTotalRequests());
        }
    }

    // ==================== 持久化检查测试 ====================

    @Nested
    @DisplayName("持久化检查功能测试")
    class PersistenceTests {

        @Test
        @DisplayName("检查持久化配置是否存在 - 存在时返回true")
        void hasPersistedAccountConfig_Exists_ReturnsTrue() {
            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1, 2, 3));

            boolean result = apiKeyService.hasPersistedAccountConfig();

            assertTrue(result);
        }

        @Test
        @DisplayName("检查持久化配置是否存在 - 不存在时返回false")
        void hasPersistedAccountConfig_NotExists_ReturnsFalse() {
            when(storeManager.getConfigVersions(anyString())).thenReturn(Collections.emptyList());
            when(storeManager.exists(anyString())).thenReturn(false);

            boolean result = apiKeyService.hasPersistedAccountConfig();

            assertFalse(result);
        }

        @Test
        @DisplayName("检查持久化配置 - 异常时返回false")
        void hasPersistedAccountConfig_Exception_ReturnsFalse() {
            when(storeManager.getConfigVersions(anyString())).thenThrow(new RuntimeException("DB error"));

            boolean result = apiKeyService.hasPersistedAccountConfig();

            assertFalse(result);
        }
    }
}
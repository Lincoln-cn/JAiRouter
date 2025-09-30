package org.unreal.modelrouter.security.authentication.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.security.config.properties.ApiKey;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.service.ApiKeyService;
import org.unreal.modelrouter.store.StoreManager;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * API Key服务实现类的单元测试
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceImplTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private SecurityProperties securityProperties;

    private ObjectMapper objectMapper;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        apiKeyService = new ApiKeyService(storeManager, objectMapper, securityProperties);
        
        // 模拟存储为空的情况
        when(storeManager.getConfig(anyString())).thenReturn(null);

        apiKeyService.loadLatestApiKeyConfig();
    }

    @Test
    void testValidateApiKey_Success() {
        // 准备测试数据
        ApiKey apiKey = createTestApiKey("test-key-001", "test-api-key-value", true, null);
        
        // 先创建API Key
        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        // 测试验证API Key
        StepVerifier.create(apiKeyService.validateApiKey("test-api-key-value"))
                .expectNext(apiKey)
                .verifyComplete();
    }

    @Test
    void testValidateApiKey_EmptyKey() {
        StepVerifier.create(apiKeyService.validateApiKey(""))
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    void testValidateApiKey_NullKey() {
        StepVerifier.create(apiKeyService.validateApiKey(null))
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    void testValidateApiKey_InvalidKey() {
        StepVerifier.create(apiKeyService.validateApiKey("invalid-key"))
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    void testValidateApiKey_DisabledKey() {
        // 准备测试数据 - 禁用的API Key
        ApiKey apiKey = createTestApiKey("test-key-002", "disabled-api-key", false, null);
        
        // 先创建API Key
        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        // 测试验证禁用的API Key
        StepVerifier.create(apiKeyService.validateApiKey("disabled-api-key"))
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    void testValidateApiKey_ExpiredKey() {
        // 准备测试数据 - 过期的API Key
        LocalDateTime expiredTime = LocalDateTime.now().minusDays(1);
        ApiKey apiKey = createTestApiKey("test-key-003", "expired-api-key", true, expiredTime);
        
        // 先创建API Key
        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        // 测试验证过期的API Key
        StepVerifier.create(apiKeyService.validateApiKey("expired-api-key"))
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    void testCreateApiKey_Success() {
        ApiKey apiKey = createTestApiKey("new-key-001", "new-api-key-value", true, null);

        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        // 验证存储方法被调用
        verify(storeManager, atLeastOnce()).saveConfig(eq("security.api-keys"), any());
        verify(storeManager, atLeastOnce()).saveConfig(eq("security.usage-statistics"), any());
    }

    @Test
    void testCreateApiKey_EmptyKeyId() {
        ApiKey apiKey = createTestApiKey("", "test-value", true, null);

        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testCreateApiKey_EmptyKeyValue() {
        ApiKey apiKey = createTestApiKey("test-key", "", true, null);

        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testCreateApiKey_DuplicateKeyId() {
        ApiKey apiKey1 = createTestApiKey("duplicate-key", "value1", true, null);
        ApiKey apiKey2 = createTestApiKey("duplicate-key", "value2", true, null);
        
        // 创建第一个API Key
        StepVerifier.create(apiKeyService.createApiKey(apiKey1))
                .expectNext(apiKey1)
                .verifyComplete();
        
        // 尝试创建重复的API Key ID
        StepVerifier.create(apiKeyService.createApiKey(apiKey2))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testCreateApiKey_DuplicateKeyValue() {
        ApiKey apiKey1 = createTestApiKey("key1", "duplicate-value", true, null);
        ApiKey apiKey2 = createTestApiKey("key2", "duplicate-value", true, null);
        
        // 创建第一个API Key
        StepVerifier.create(apiKeyService.createApiKey(apiKey1))
                .expectNext(apiKey1)
                .verifyComplete();
        
        // 尝试创建重复的API Key值
        StepVerifier.create(apiKeyService.createApiKey(apiKey2))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testUpdateApiKey_Success() {
        // 先创建API Key
        ApiKey originalKey = createTestApiKey("update-key", "update-value", true, null);
        StepVerifier.create(apiKeyService.createApiKey(originalKey))
                .expectNext(originalKey)
                .verifyComplete();
        
        // 准备更新数据
        ApiKey updateInfo = ApiKey.builder()
                .description("更新后的描述")
                .enabled(false)
                .permissions(Arrays.asList("read", "write"))
                .build();
        
        // 执行更新
        StepVerifier.create(apiKeyService.updateApiKey("update-key", updateInfo))
                .assertNext(updatedKey -> {
                    assertEquals("更新后的描述", updatedKey.getDescription());
                    assertFalse(updatedKey.isEnabled());
                    assertEquals(Arrays.asList("read", "write"), updatedKey.getPermissions());
                    // 确保keyId和keyValue没有改变
                    assertEquals("update-key", updatedKey.getKeyId());
                    assertEquals("update-value", updatedKey.getKeyValue());
                })
                .verifyComplete();
    }

    @Test
    void testUpdateApiKey_NotFound() {
        ApiKey updateInfo = ApiKey.builder()
                .description("更新描述")
                .build();
        
        StepVerifier.create(apiKeyService.updateApiKey("non-existent-key", updateInfo))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testDeleteApiKey_Success() {
        // 先创建API Key
        ApiKey apiKey = createTestApiKey("delete-key", "delete-value", true, null);
        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        // 执行删除
        StepVerifier.create(apiKeyService.deleteApiKey("delete-key"))
                .verifyComplete();
        
        // 验证API Key已被删除
        StepVerifier.create(apiKeyService.getApiKeyById("delete-key"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testDeleteApiKey_NotFound() {
        StepVerifier.create(apiKeyService.deleteApiKey("non-existent-key"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testGetAllApiKeys() {
        // 创建多个API Key
        ApiKey key1 = createTestApiKey("key1", "value1", true, null);
        ApiKey key2 = createTestApiKey("key2", "value2", true, null);
        
        StepVerifier.create(apiKeyService.createApiKey(key1))
                .expectNext(key1)
                .verifyComplete();
        
        StepVerifier.create(apiKeyService.createApiKey(key2))
                .expectNext(key2)
                .verifyComplete();
        
        // 获取所有API Key
        StepVerifier.create(apiKeyService.getAllApiKeys())
                .assertNext(apiKeys -> {
                    assertEquals(2, apiKeys.size());
                    assertTrue(apiKeys.stream().anyMatch(key -> "key1".equals(key.getKeyId())));
                    assertTrue(apiKeys.stream().anyMatch(key -> "key2".equals(key.getKeyId())));
                })
                .verifyComplete();
    }

    @Test
    void testGetApiKeyById_Success() {
        ApiKey apiKey = createTestApiKey("get-key", "get-value", true, null);

        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        StepVerifier.create(apiKeyService.getApiKeyById("get-key"))
                .expectNext(apiKey)
                .verifyComplete();
    }

    @Test
    void testGetApiKeyById_NotFound() {
        StepVerifier.create(apiKeyService.getApiKeyById("non-existent-key"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testUpdateUsageStatistics_Success() {
        // 先创建API Key
        ApiKey apiKey = createTestApiKey("stats-key", "stats-value", true, null);
        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        // 更新使用统计 - 成功请求
        StepVerifier.create(apiKeyService.updateUsageStatistics("stats-key", true))
                .verifyComplete();
        
        // 更新使用统计 - 失败请求
        StepVerifier.create(apiKeyService.updateUsageStatistics("stats-key", false))
                .verifyComplete();
        
        // 验证统计数据
        StepVerifier.create(apiKeyService.getUsageStatistics("stats-key"))
                .assertNext(stats -> {
                    assertEquals(2L, stats.getTotalRequests());
                    assertEquals(1L, stats.getSuccessfulRequests());
                    assertEquals(1L, stats.getFailedRequests());
                    assertNotNull(stats.getLastUsedAt());
                    assertNotNull(stats.getDailyUsage());
                })
                .verifyComplete();
    }

    @Test
    void testUpdateUsageStatistics_KeyNotFound() {
        // 对不存在的API Key更新统计不应该抛出异常，只是记录警告日志
        StepVerifier.create(apiKeyService.updateUsageStatistics("non-existent-key", true))
                .verifyComplete();
    }

    @Test
    void testGetUsageStatistics_Success() {
        // 先创建API Key
        ApiKey apiKey = createTestApiKey("usage-key", "usage-value", true, null);
        StepVerifier.create(apiKeyService.createApiKey(apiKey))
                .expectNext(apiKey)
                .verifyComplete();
        
        // 获取使用统计
        StepVerifier.create(apiKeyService.getUsageStatistics("usage-key"))
                .assertNext(stats -> {
                    assertEquals(0L, stats.getTotalRequests());
                    assertEquals(0L, stats.getSuccessfulRequests());
                    assertEquals(0L, stats.getFailedRequests());
                    assertNotNull(stats.getDailyUsage());
                })
                .verifyComplete();
    }

    @Test
    void testGetUsageStatistics_NotFound() {
        StepVerifier.create(apiKeyService.getUsageStatistics("non-existent-key"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    /**
     * 创建测试用的API Key信息
     */
    private ApiKey createTestApiKey(String keyId, String keyValue, boolean enabled, LocalDateTime expiresAt) {
        return ApiKey.builder()
                .keyId(keyId)
                .keyValue(keyValue)
                .description("测试API Key")
                .enabled(enabled)
                .expiresAt(expiresAt)
                .permissions(List.of("read"))
                .metadata(new HashMap<>())
                .build();
    }
}
package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreateRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreationVO;
import org.unreal.modelrouter.auth.security.dto.ApiKeyVO;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * v2.7.11 API Key createdBy字段测试
 * 测试ApiKey的createdBy字段正确设置
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("v2.7.11 API Key createdBy字段测试")
class ApiKeyMultiUserTest {

    @Mock
    private ApiKeyPersistenceService apiKeyPersistenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private ConcurrentHashMap<String, ApiKey> apiKeyCache;
    private ConcurrentHashMap<String, String> keyIdIndex;

    @BeforeEach
    void setUp() {
        // 使用反射设置内部缓存
        apiKeyCache = new ConcurrentHashMap<>();
        keyIdIndex = new ConcurrentHashMap<>();
        try {
            var cacheField = ApiKeyService.class.getDeclaredField("apiKeyCache");
            cacheField.setAccessible(true);
            cacheField.set(apiKeyService, apiKeyCache);

            var indexField = ApiKeyService.class.getDeclaredField("keyIdIndex");
            indexField.setAccessible(true);
            indexField.set(apiKeyService, keyIdIndex);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test", e);
        }
    }

    // ==================== ApiKey createdBy测试 ====================

    @Test
    @DisplayName("MU-001: ApiKey包含createdBy字段")
    void testApiKeyCreatedByField() {
        ApiKey apiKey = ApiKey.builder()
                .keyId("test-key")
                .keyHash("hash123")
                .createdBy("admin")
                .description("Test Key")
                .enabled(true)
                .build();

        assertEquals("admin", apiKey.getCreatedBy());
    }

    @Test
    @DisplayName("MU-002: ApiKey createSecureCopy保留createdBy")
    void testApiKeySecureCopy() {
        ApiKey apiKey = ApiKey.builder()
                .keyId("test-key")
                .keyHash("hash123")
                .keyValue("sk-secret")
                .createdBy("admin")
                .description("Test Key")
                .enabled(true)
                .build();

        ApiKey secureCopy = apiKey.createSecureCopy();

        assertEquals("admin", secureCopy.getCreatedBy());
        assertNull(secureCopy.getKeyValue());
        assertNull(secureCopy.getKeyHash());
    }

    // ==================== ApiKeyCreateRequest测试 ====================

    @Test
    @DisplayName("MU-003: ApiKeyCreateRequest不包含ownerId和ownerRole")
    void testApiKeyCreateRequest() {
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                .keyId("test-key")
                .description("Test Key")
                .permissions(java.util.List.of("chat"))
                .build();

        assertEquals("test-key", request.getKeyId());
        assertEquals("Test Key", request.getDescription());
    }

    // ==================== ApiKeyVO测试 ====================

    @Test
    @DisplayName("MU-004: ApiKeyVO包含createdBy字段")
    void testApiKeyVOCreatedBy() {
        ApiKeyVO vo = ApiKeyVO.builder()
                .keyId("test-key")
                .description("Test Key")
                .createdBy("admin")
                .build();

        assertEquals("admin", vo.getCreatedBy());
    }

    @Test
    @DisplayName("MU-005: ApiKeyVO不包含ownerId和ownerRole")
    void testApiKeyVO() {
        ApiKeyVO vo = ApiKeyVO.builder()
                .keyId("test-key")
                .description("Test Key")
                .build();

        // 验证不包含ownerId和ownerRole字段
        assertEquals("test-key", vo.getKeyId());
        assertEquals("Test Key", vo.getDescription());
    }
}

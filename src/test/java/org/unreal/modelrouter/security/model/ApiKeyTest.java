package org.unreal.modelrouter.security.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.security.config.properties.ApiKey;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKey统一数据模型单元测试
 */
public class ApiKeyTest {

    private ApiKey.ApiKeyBuilder baseBuilder;

    @BeforeEach
    void setUp() {
        baseBuilder = ApiKey.builder()
                .keyId("test-key-001")
                .keyValue("sk-test-key-value-123")
                .description("测试API密钥")
                .permissions(Arrays.asList("READ", "WRITE"))
                .enabled(true)
                .createdAt(LocalDateTime.now());
    }

    @Test
    void testApiKeyBuilder() {
        // Given
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("environment", "test");

        // When
        ApiKey apiKey = ApiKey.builder()
                .keyId("test-key-001")
                .keyValue("sk-test-key-value-123")
                .description("测试API密钥")
                .permissions(Arrays.asList("READ", "WRITE", "ADMIN"))
                .enabled(true)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .metadata(metadata)
                .build();

        // Then
        assertEquals("test-key-001", apiKey.getKeyId());
        assertEquals("sk-test-key-value-123", apiKey.getKeyValue());
        assertEquals("测试API密钥", apiKey.getDescription());
        assertEquals(3, apiKey.getPermissions().size());
        assertTrue(apiKey.isEnabled());
        assertEquals(createdAt, apiKey.getCreatedAt());
        assertEquals(expiresAt, apiKey.getExpiresAt());
        assertEquals(metadata, apiKey.getMetadata());
    }

    @Test
    void testIsExpired_NotExpired() {
        // Given
        ApiKey apiKey = baseBuilder
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        // Then
        assertFalse(apiKey.isExpired());
    }

    @Test
    void testIsExpired_Expired() {
        // Given
        ApiKey apiKey = baseBuilder
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        // Then
        assertTrue(apiKey.isExpired());
    }

    @Test
    void testIsExpired_NoExpirationDate() {
        // Given
        ApiKey apiKey = baseBuilder
                .expiresAt(null)
                .build();

        // Then
        assertFalse(apiKey.isExpired());
    }

    @Test
    void testIsValid_ValidKey() {
        // Given
        ApiKey apiKey = baseBuilder
                .enabled(true)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        // Then
        assertTrue(apiKey.isValid());
    }

    @Test
    void testIsValid_DisabledKey() {
        // Given
        ApiKey apiKey = baseBuilder
                .enabled(false)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        // Then
        assertFalse(apiKey.isValid());
    }

    @Test
    void testIsValid_ExpiredKey() {
        // Given
        ApiKey apiKey = baseBuilder
                .enabled(true)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        // Then
        assertFalse(apiKey.isValid());
    }

    @Test
    void testHasPermission_HasPermission() {
        // Given
        ApiKey apiKey = baseBuilder
                .permissions(Arrays.asList("read", "write", "admin"))
                .build();

        // Then
        assertTrue(apiKey.hasPermission("READ"));
        assertTrue(apiKey.hasPermission("write"));
        assertTrue(apiKey.hasPermission("ADMIN"));
    }

    @Test
    void testHasPermission_NoPermission() {
        // Given
        ApiKey apiKey = baseBuilder
                .permissions(Arrays.asList("read", "write"))
                .build();

        // Then
        assertFalse(apiKey.hasPermission("admin"));
        assertFalse(apiKey.hasPermission("DELETE"));
    }

    @Test
    void testHasPermission_NullPermissions() {
        // Given
        ApiKey apiKey = baseBuilder
                .permissions(null)
                .build();

        // Then
        assertFalse(apiKey.hasPermission("read"));
    }

    @Test
    void testHasPermission_EmptyPermissions() {
        // Given
        ApiKey apiKey = baseBuilder
                .permissions(List.of())
                .build();

        // Then
        assertFalse(apiKey.hasPermission("read"));
    }

    @Test
    void testCreateSecureCopy() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("test", "value");

        ApiKey originalKey = baseBuilder
                .keyValue("sk-secret-key-123")
                .metadata(metadata)
                .build();

        // When
        ApiKey secureCopy = originalKey.createSecureCopy();

        // Then
        assertEquals(originalKey.getKeyId(), secureCopy.getKeyId());
        assertNull(secureCopy.getKeyValue()); // 安全副本不包含keyValue
        assertEquals(originalKey.getDescription(), secureCopy.getDescription());
        assertEquals(originalKey.getPermissions(), secureCopy.getPermissions());
        assertEquals(originalKey.getExpiresAt(), secureCopy.getExpiresAt());
        assertEquals(originalKey.getCreatedAt(), secureCopy.getCreatedAt());
        assertEquals(originalKey.isEnabled(), secureCopy.isEnabled());
        assertEquals(originalKey.getMetadata(), secureCopy.getMetadata());
    }

    @Test
    void testCreateCreationResponse() {
        // Given
        ApiKey originalKey = baseBuilder
                .keyValue("sk-secret-key-123")
                .build();

        // When
        ApiKey creationResponse = originalKey.createCreationResponse();

        // Then
        assertEquals(originalKey.getKeyId(), creationResponse.getKeyId());
        assertEquals(originalKey.getKeyValue(), creationResponse.getKeyValue()); // 创建响应包含keyValue
        assertEquals(originalKey.getDescription(), creationResponse.getDescription());
        assertEquals(originalKey.getPermissions(), creationResponse.getPermissions());
        assertEquals(originalKey.isEnabled(), creationResponse.isEnabled());
    }

    @Test
    void testGenerateApiKey_WithPrefixAndLength() {
        // When
        String apiKey = ApiKey.generateApiKey("test-", 16);

        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("test-"));
        assertEquals(21, apiKey.length()); // "test-" (5) + 16 = 21
        assertTrue(apiKey.matches("test-[A-Za-z0-9]{16}"));
    }

    @Test
    void testGenerateApiKey_DefaultParameters() {
        // When
        String apiKey = ApiKey.generateApiKey();

        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("sk-"));
        assertEquals(35, apiKey.length()); // "sk-" (3) + 32 = 35
        assertTrue(apiKey.matches("sk-[A-Za-z0-9]{32}"));
    }

    @Test
    void testGenerateApiKey_NullPrefix() {
        // When
        String apiKey = ApiKey.generateApiKey(null, 10);

        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("sk-")); // 默认前缀
        assertEquals(13, apiKey.length()); // "sk-" (3) + 10 = 13
    }

    @Test
    void testGenerateApiKey_InvalidLength() {
        // When
        String apiKey = ApiKey.generateApiKey("test-", 0);

        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("test-"));
        assertEquals(37, apiKey.length()); // "test-" (5) + 32 (默认长度) = 37
    }

    @Test
    void testGenerateApiKey_Uniqueness() {
        // When
        String apiKey1 = ApiKey.generateApiKey();
        String apiKey2 = ApiKey.generateApiKey();

        // Then
        assertNotEquals(apiKey1, apiKey2);
    }
}
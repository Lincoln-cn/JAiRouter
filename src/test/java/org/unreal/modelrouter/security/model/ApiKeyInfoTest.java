package org.unreal.modelrouter.security.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ApiKeyInfo数据模型单元测试
 */
class ApiKeyInfoTest {
    
    private ApiKeyInfo.ApiKeyInfoBuilder baseBuilder;
    
    @BeforeEach
    void setUp() {
        baseBuilder = ApiKeyInfo.builder()
                .keyId("test-key-001")
                .keyValue("sk-test-key-value-123")
                .description("测试API Key")
                .createdAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(Arrays.asList("read", "write"))
                .metadata(new HashMap<>());
    }
    
    @Test
    void testApiKeyInfoBuilder() {
        // Given
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "test");
        
        // When
        ApiKeyInfo apiKey = ApiKeyInfo.builder()
                .keyId("test-key-001")
                .keyValue("sk-test-key-value-123")
                .description("测试API Key")
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .enabled(true)
                .permissions(Arrays.asList("read", "write"))
                .metadata(metadata)
                .build();
        
        // Then
        assertEquals("test-key-001", apiKey.getKeyId());
        assertEquals("sk-test-key-value-123", apiKey.getKeyValue());
        assertEquals("测试API Key", apiKey.getDescription());
        assertEquals(createdAt, apiKey.getCreatedAt());
        assertEquals(expiresAt, apiKey.getExpiresAt());
        assertTrue(apiKey.isEnabled());
        assertEquals(Arrays.asList("read", "write"), apiKey.getPermissions());
        assertEquals(metadata, apiKey.getMetadata());
    }
    
    @Test
    void testIsExpired_NotExpired() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        
        // When & Then
        assertFalse(apiKey.isExpired());
    }
    
    @Test
    void testIsExpired_Expired() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        
        // When & Then
        assertTrue(apiKey.isExpired());
    }
    
    @Test
    void testIsExpired_NoExpirationDate() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .expiresAt(null)
                .build();
        
        // When & Then
        assertFalse(apiKey.isExpired());
    }
    
    @Test
    void testIsValid_ValidKey() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .enabled(true)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        
        // When & Then
        assertTrue(apiKey.isValid());
    }
    
    @Test
    void testIsValid_DisabledKey() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .enabled(false)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        
        // When & Then
        assertFalse(apiKey.isValid());
    }
    
    @Test
    void testIsValid_ExpiredKey() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .enabled(true)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        
        // When & Then
        assertFalse(apiKey.isValid());
    }
    
    @Test
    void testHasPermission_HasPermission() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .permissions(Arrays.asList("read", "write", "admin"))
                .build();
        
        // When & Then
        assertTrue(apiKey.hasPermission("read"));
        assertTrue(apiKey.hasPermission("write"));
        assertTrue(apiKey.hasPermission("admin"));
    }
    
    @Test
    void testHasPermission_NoPermission() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .permissions(Arrays.asList("read", "write"))
                .build();
        
        // When & Then
        assertFalse(apiKey.hasPermission("admin"));
        assertFalse(apiKey.hasPermission("delete"));
    }
    
    @Test
    void testHasPermission_NullPermissions() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .permissions(null)
                .build();
        
        // When & Then
        assertFalse(apiKey.hasPermission("read"));
    }
    
    @Test
    void testHasPermission_EmptyPermissions() {
        // Given
        ApiKeyInfo apiKey = baseBuilder
                .permissions(Arrays.asList())
                .build();
        
        // When & Then
        assertFalse(apiKey.hasPermission("read"));
    }
    
    @Test
    void testGenerateApiKey_WithPrefixAndLength() {
        // When
        String apiKey = ApiKeyInfo.generateApiKey("test-", 16);
        
        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("test-"));
        assertEquals(21, apiKey.length()); // "test-" (5) + 16 = 21
        assertTrue(apiKey.matches("test-[A-Za-z0-9]{16}"));
    }
    
    @Test
    void testGenerateApiKey_DefaultParameters() {
        // When
        String apiKey = ApiKeyInfo.generateApiKey();
        
        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("sk-"));
        assertEquals(35, apiKey.length()); // "sk-" (3) + 32 = 35
        assertTrue(apiKey.matches("sk-[A-Za-z0-9]{32}"));
    }
    
    @Test
    void testGenerateApiKey_NullPrefix() {
        // When
        String apiKey = ApiKeyInfo.generateApiKey(null, 10);
        
        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("sk-"));
        assertEquals(13, apiKey.length()); // "sk-" (3) + 10 = 13
    }
    
    @Test
    void testGenerateApiKey_InvalidLength() {
        // When
        String apiKey = ApiKeyInfo.generateApiKey("test-", 0);
        
        // Then
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("test-"));
        assertEquals(37, apiKey.length()); // "test-" (5) + 32 (default) = 37
    }
    
    @Test
    void testGenerateApiKey_Uniqueness() {
        // When
        String apiKey1 = ApiKeyInfo.generateApiKey();
        String apiKey2 = ApiKeyInfo.generateApiKey();
        
        // Then
        assertNotEquals(apiKey1, apiKey2);
    }
}
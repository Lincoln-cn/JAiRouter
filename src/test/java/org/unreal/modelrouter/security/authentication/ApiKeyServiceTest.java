package org.unreal.modelrouter.security.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.UsageStatistics;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ApiKeyService接口测试
 * 验证接口设计的完整性和方法签名的正确性
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {
    
    @Mock
    private ApiKeyService apiKeyService;
    
    private ApiKeyInfo testApiKey;
    private UsageStatistics testUsageStats;
    
    @BeforeEach
    void setUp() {
        testUsageStats = UsageStatistics.builder()
                .totalRequests(100L)
                .successfulRequests(90L)
                .failedRequests(10L)
                .lastUsedAt(LocalDateTime.now())
                .build();
        
        testApiKey = ApiKeyInfo.builder()
                .keyId("test-key-001")
                .keyValue("sk-test-key-value-123")
                .description("测试API Key")
                .createdAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(Arrays.asList("read", "write"))
                .usage(testUsageStats)
                .build();
    }
    
    @Test
    void testValidateApiKey_ValidKey() {
        // Given
        when(apiKeyService.validateApiKey("valid-key")).thenReturn(Mono.just(testApiKey));
        
        // When & Then
        StepVerifier.create(apiKeyService.validateApiKey("valid-key"))
                .expectNext(testApiKey)
                .verifyComplete();
    }
    
    @Test
    void testValidateApiKey_InvalidKey() {
        // Given
        when(apiKeyService.validateApiKey("invalid-key")).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(apiKeyService.validateApiKey("invalid-key"))
                .verifyComplete();
    }
    
    @Test
    void testCreateApiKey() {
        // Given
        when(apiKeyService.createApiKey(any(ApiKeyInfo.class))).thenReturn(Mono.just(testApiKey));
        
        // When & Then
        StepVerifier.create(apiKeyService.createApiKey(testApiKey))
                .expectNext(testApiKey)
                .verifyComplete();
    }
    
    @Test
    void testUpdateApiKey() {
        // Given
        when(apiKeyService.updateApiKey(eq("test-key-001"), any(ApiKeyInfo.class)))
                .thenReturn(Mono.just(testApiKey));
        
        // When & Then
        StepVerifier.create(apiKeyService.updateApiKey("test-key-001", testApiKey))
                .expectNext(testApiKey)
                .verifyComplete();
    }
    
    @Test
    void testDeleteApiKey() {
        // Given
        when(apiKeyService.deleteApiKey("test-key-001")).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(apiKeyService.deleteApiKey("test-key-001"))
                .verifyComplete();
    }
    
    @Test
    void testGetAllApiKeys() {
        // Given
        List<ApiKeyInfo> apiKeys = Arrays.asList(testApiKey);
        when(apiKeyService.getAllApiKeys()).thenReturn(Mono.just(apiKeys));
        
        // When & Then
        StepVerifier.create(apiKeyService.getAllApiKeys())
                .expectNext(apiKeys)
                .verifyComplete();
    }
    
    @Test
    void testGetApiKeyById() {
        // Given
        when(apiKeyService.getApiKeyById("test-key-001")).thenReturn(Mono.just(testApiKey));
        
        // When & Then
        StepVerifier.create(apiKeyService.getApiKeyById("test-key-001"))
                .expectNext(testApiKey)
                .verifyComplete();
    }
    
    @Test
    void testGetApiKeyById_NotFound() {
        // Given
        when(apiKeyService.getApiKeyById("non-existent")).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(apiKeyService.getApiKeyById("non-existent"))
                .verifyComplete();
    }
    
    @Test
    void testUpdateUsageStatistics() {
        // Given
        when(apiKeyService.updateUsageStatistics("test-key-001", true)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(apiKeyService.updateUsageStatistics("test-key-001", true))
                .verifyComplete();
    }
    
    @Test
    void testGetUsageStatistics() {
        // Given
        when(apiKeyService.getUsageStatistics("test-key-001")).thenReturn(Mono.just(testUsageStats));
        
        // When & Then
        StepVerifier.create(apiKeyService.getUsageStatistics("test-key-001"))
                .expectNext(testUsageStats)
                .verifyComplete();
    }
    
    @Test
    void testGetUsageStatistics_NotFound() {
        // Given
        when(apiKeyService.getUsageStatistics("non-existent")).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(apiKeyService.getUsageStatistics("non-existent"))
                .verifyComplete();
    }
}
package org.unreal.modelrouter.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis JWT令牌持久化服务测试
 */
@ExtendWith(MockitoExtension.class)
class RedisJwtTokenPersistenceServiceImplTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    @Mock
    private StoreManager fallbackStoreManager;
    
    private RedisJwtTokenPersistenceServiceImpl service;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisJwtTokenPersistenceServiceImpl(redisTemplate, fallbackStoreManager);
    }
    
    @Test
    void testSaveToken_Success() {
        // Given
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.ReactiveSetOperations.class));
        when(redisTemplate.opsForSet().add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        
        // When & Then
        StepVerifier.create(service.saveToken(tokenInfo))
            .verifyComplete();
        
        verify(valueOperations).set(anyString(), anyString(), any());
    }
    
    @Test
    void testSaveToken_NullTokenInfo() {
        // When & Then
        StepVerifier.create(service.saveToken(null))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testSaveToken_NullTokenHash() {
        // Given
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        tokenInfo.setTokenHash(null);
        
        // When & Then
        StepVerifier.create(service.saveToken(tokenInfo))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testFindByTokenHash_Success() {
        // Given
        String tokenHash = "test-hash";
        String tokenJson = "{\"tokenHash\":\"test-hash\",\"userId\":\"user1\",\"status\":\"ACTIVE\"}";
        when(valueOperations.get("jwt:token:" + tokenHash)).thenReturn(Mono.just(tokenJson));
        
        // When & Then
        StepVerifier.create(service.findByTokenHash(tokenHash))
            .expectNextMatches(token -> token.getTokenHash().equals(tokenHash))
            .verifyComplete();
    }
    
    @Test
    void testFindByTokenHash_NotFound() {
        // Given
        String tokenHash = "non-existent-hash";
        when(valueOperations.get("jwt:token:" + tokenHash)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(service.findByTokenHash(tokenHash))
            .verifyComplete();
    }
    
    @Test
    void testFindByTokenHash_NullHash() {
        // When & Then
        StepVerifier.create(service.findByTokenHash(null))
            .verifyComplete();
    }
    
    @Test
    void testFindByTokenHash_EmptyHash() {
        // When & Then
        StepVerifier.create(service.findByTokenHash(""))
            .verifyComplete();
    }
    
    @Test
    void testUpdateTokenStatus_Success() {
        // Given
        String tokenHash = "test-hash";
        TokenStatus newStatus = TokenStatus.REVOKED;
        String tokenJson = "{\"tokenHash\":\"test-hash\",\"userId\":\"user1\",\"status\":\"ACTIVE\"}";
        
        when(valueOperations.get("jwt:token:" + tokenHash)).thenReturn(Mono.just(tokenJson));
        when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.ReactiveSetOperations.class));
        when(redisTemplate.opsForSet().add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.opsForSet().remove(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        
        // When & Then
        StepVerifier.create(service.updateTokenStatus(tokenHash, newStatus))
            .verifyComplete();
    }
    
    @Test
    void testUpdateTokenStatus_NullParameters() {
        // When & Then
        StepVerifier.create(service.updateTokenStatus(null, TokenStatus.ACTIVE))
            .expectError(IllegalArgumentException.class)
            .verify();
        
        StepVerifier.create(service.updateTokenStatus("hash", null))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testCountActiveTokens_Success() {
        // Given
        when(redisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.ReactiveSetOperations.class));
        when(redisTemplate.opsForSet().size(anyString())).thenReturn(Mono.just(5L));
        
        // When & Then
        StepVerifier.create(service.countActiveTokens())
            .expectNext(5L)
            .verifyComplete();
    }
    
    @Test
    void testCountTokensByStatus_Success() {
        // Given
        TokenStatus status = TokenStatus.ACTIVE;
        when(redisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.ReactiveSetOperations.class));
        when(redisTemplate.opsForSet().size(anyString())).thenReturn(Mono.just(3L));
        
        // When & Then
        StepVerifier.create(service.countTokensByStatus(status))
            .expectNext(3L)
            .verifyComplete();
    }
    
    @Test
    void testFindActiveTokensByUserId_Success() {
        // Given
        String userId = "user1";
        when(redisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.ReactiveSetOperations.class));
        when(redisTemplate.opsForSet().members(anyString())).thenReturn(reactor.core.publisher.Flux.just("hash1", "hash2"));
        
        String tokenJson1 = "{\"tokenHash\":\"hash1\",\"userId\":\"user1\",\"status\":\"ACTIVE\",\"expiresAt\":\"2025-12-31T23:59:59\"}";
        String tokenJson2 = "{\"tokenHash\":\"hash2\",\"userId\":\"user1\",\"status\":\"ACTIVE\",\"expiresAt\":\"2025-12-31T23:59:59\"}";
        
        when(valueOperations.get("jwt:token:hash1")).thenReturn(Mono.just(tokenJson1));
        when(valueOperations.get("jwt:token:hash2")).thenReturn(Mono.just(tokenJson2));
        
        // When & Then
        StepVerifier.create(service.findActiveTokensByUserId(userId))
            .expectNextMatches(tokens -> tokens.size() == 2)
            .verifyComplete();
    }
    
    @Test
    void testFindActiveTokensByUserId_NullUserId() {
        // When & Then
        StepVerifier.create(service.findActiveTokensByUserId(null))
            .expectNextMatches(List::isEmpty)
            .verifyComplete();
    }
    
    @Test
    void testFindActiveTokensByUserId_EmptyUserId() {
        // When & Then
        StepVerifier.create(service.findActiveTokensByUserId(""))
            .expectNextMatches(List::isEmpty)
            .verifyComplete();
    }
    
    @Test
    void testBatchUpdateTokenStatus_Success() {
        // Given
        List<String> tokenHashes = List.of("hash1", "hash2");
        TokenStatus status = TokenStatus.REVOKED;
        String reason = "Batch revocation";
        String updatedBy = "admin";
        
        String tokenJson1 = "{\"tokenHash\":\"hash1\",\"userId\":\"user1\",\"status\":\"ACTIVE\"}";
        String tokenJson2 = "{\"tokenHash\":\"hash2\",\"userId\":\"user2\",\"status\":\"ACTIVE\"}";
        
        when(valueOperations.get("jwt:token:hash1")).thenReturn(Mono.just(tokenJson1));
        when(valueOperations.get("jwt:token:hash2")).thenReturn(Mono.just(tokenJson2));
        when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.ReactiveSetOperations.class));
        when(redisTemplate.opsForSet().add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.opsForSet().remove(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        
        // When & Then
        StepVerifier.create(service.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy))
            .verifyComplete();
    }
    
    @Test
    void testBatchUpdateTokenStatus_EmptyList() {
        // When & Then
        StepVerifier.create(service.batchUpdateTokenStatus(List.of(), TokenStatus.REVOKED, "reason", "admin"))
            .verifyComplete();
    }
    
    @Test
    void testBatchUpdateTokenStatus_NullList() {
        // When & Then
        StepVerifier.create(service.batchUpdateTokenStatus(null, TokenStatus.REVOKED, "reason", "admin"))
            .verifyComplete();
    }
    
    private JwtTokenInfo createTestTokenInfo() {
        JwtTokenInfo tokenInfo = new JwtTokenInfo();
        tokenInfo.setId("test-id");
        tokenInfo.setUserId("user1");
        tokenInfo.setTokenHash("test-hash");
        tokenInfo.setToken("test-token");
        tokenInfo.setTokenType("Bearer");
        tokenInfo.setStatus(TokenStatus.ACTIVE);
        tokenInfo.setIssuedAt(LocalDateTime.now());
        tokenInfo.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenInfo.setCreatedAt(LocalDateTime.now());
        tokenInfo.setIpAddress("127.0.0.1");
        tokenInfo.setUserAgent("Test Agent");
        return tokenInfo;
    }
}
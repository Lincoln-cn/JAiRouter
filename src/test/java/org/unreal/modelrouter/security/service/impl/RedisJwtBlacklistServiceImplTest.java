package org.unreal.modelrouter.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis JWT黑名单服务测试
 */
@ExtendWith(MockitoExtension.class)
class RedisJwtBlacklistServiceImplTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    @Mock
    private ReactiveSetOperations<String, String> setOperations;
    
    @Mock
    private StoreManager fallbackStoreManager;
    
    private RedisJwtBlacklistServiceImpl service;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        service = new RedisJwtBlacklistServiceImpl(redisTemplate, fallbackStoreManager);
    }
    
    @Test
    void testAddToBlacklist_Success() {
        // Given
        String tokenHash = "test-hash";
        String reason = "Manual revocation";
        String addedBy = "admin";
        
        when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        
        // When & Then
        StepVerifier.create(service.addToBlacklist(tokenHash, reason, addedBy))
            .verifyComplete();
        
        verify(valueOperations).set(anyString(), anyString(), any());
        verify(setOperations).add(anyString(), eq(tokenHash));
    }
    
    @Test
    void testAddToBlacklist_NullTokenHash() {
        // When & Then
        StepVerifier.create(service.addToBlacklist(null, "reason", "admin"))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testAddToBlacklist_EmptyTokenHash() {
        // When & Then
        StepVerifier.create(service.addToBlacklist("", "reason", "admin"))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testIsBlacklisted_True() {
        // Given
        String tokenHash = "test-hash";
        when(redisTemplate.hasKey("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(true));
        
        String entryJson = "{\"tokenHash\":\"test-hash\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                          "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2025-01-01T00:00:00\"}";
        when(valueOperations.get("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(entryJson));
        
        // When & Then
        StepVerifier.create(service.isBlacklisted(tokenHash))
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void testIsBlacklisted_False() {
        // Given
        String tokenHash = "test-hash";
        when(redisTemplate.hasKey("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(false));
        
        // When & Then
        StepVerifier.create(service.isBlacklisted(tokenHash))
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testIsBlacklisted_NullTokenHash() {
        // When & Then
        StepVerifier.create(service.isBlacklisted(null))
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testIsBlacklisted_EmptyTokenHash() {
        // When & Then
        StepVerifier.create(service.isBlacklisted(""))
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testRemoveFromBlacklist_Success() {
        // Given
        String tokenHash = "test-hash";
        when(redisTemplate.hasKey("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(true));
        when(redisTemplate.delete("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), eq(tokenHash))).thenReturn(Mono.just(1L));
        
        // When & Then
        StepVerifier.create(service.removeFromBlacklist(tokenHash))
            .verifyComplete();
        
        verify(redisTemplate).delete("jwt:blacklist:" + tokenHash);
        verify(setOperations).remove(anyString(), eq(tokenHash));
    }
    
    @Test
    void testRemoveFromBlacklist_NotExists() {
        // Given
        String tokenHash = "test-hash";
        when(redisTemplate.hasKey("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(false));
        
        // When & Then
        StepVerifier.create(service.removeFromBlacklist(tokenHash))
            .verifyComplete();
        
        verify(redisTemplate, never()).delete(anyString());
    }
    
    @Test
    void testGetBlacklistSize_Success() {
        // Given
        when(setOperations.size("jwt:blacklist_index")).thenReturn(Mono.just(5L));
        
        // When & Then
        StepVerifier.create(service.getBlacklistSize())
            .expectNext(5L)
            .verifyComplete();
    }
    
    @Test
    void testGetBlacklistSize_Empty() {
        // Given
        when(setOperations.size("jwt:blacklist_index")).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(service.getBlacklistSize())
            .expectNext(0L)
            .verifyComplete();
    }
    
    @Test
    void testCleanupExpiredEntries_Success() {
        // Given
        when(setOperations.members("jwt:blacklist_index")).thenReturn(Flux.just("hash1", "hash2"));
        
        String expiredEntryJson = "{\"tokenHash\":\"hash1\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                                 "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2024-01-02T00:00:00\"}";
        String validEntryJson = "{\"tokenHash\":\"hash2\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                               "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2025-01-01T00:00:00\"}";
        
        when(valueOperations.get("jwt:blacklist:hash1")).thenReturn(Mono.just(expiredEntryJson));
        when(valueOperations.get("jwt:blacklist:hash2")).thenReturn(Mono.just(validEntryJson));
        when(redisTemplate.delete("jwt:blacklist:hash1")).thenReturn(Mono.just(1L));
        when(setOperations.remove("jwt:blacklist_index", "hash1")).thenReturn(Mono.just(1L));
        
        // When & Then
        StepVerifier.create(service.cleanupExpiredEntries())
            .verifyComplete();
        
        verify(redisTemplate).delete("jwt:blacklist:hash1");
        verify(redisTemplate, never()).delete("jwt:blacklist:hash2");
    }
    
    @Test
    void testGetBlacklistStats_Success() {
        // Given
        String statsJson = "{\"totalAdded\":10,\"totalCleaned\":2,\"currentSize\":8}";
        when(valueOperations.get("jwt:blacklist_stats")).thenReturn(Mono.just(statsJson));
        when(setOperations.size("jwt:blacklist_index")).thenReturn(Mono.just(8L));
        
        // When & Then
        StepVerifier.create(service.getBlacklistStats())
            .expectNextMatches(stats -> {
                return stats.containsKey("totalAdded") && 
                       stats.containsKey("currentSize") &&
                       stats.containsKey("memoryCache");
            })
            .verifyComplete();
    }
    
    @Test
    void testGetBlacklistEntry_Success() {
        // Given
        String tokenHash = "test-hash";
        String entryJson = "{\"tokenHash\":\"test-hash\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                          "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2025-01-01T00:00:00\"}";
        when(valueOperations.get("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(entryJson));
        
        // When & Then
        StepVerifier.create(service.getBlacklistEntry(tokenHash))
            .expectNextMatches(entry -> entry.getTokenHash().equals(tokenHash))
            .verifyComplete();
    }
    
    @Test
    void testGetBlacklistEntry_NotFound() {
        // Given
        String tokenHash = "test-hash";
        when(valueOperations.get("jwt:blacklist:" + tokenHash)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(service.getBlacklistEntry(tokenHash))
            .verifyComplete();
    }
    
    @Test
    void testBatchAddToBlacklist_Success() {
        // Given
        List<String> tokenHashes = List.of("hash1", "hash2");
        String reason = "Batch revocation";
        String addedBy = "admin";
        
        when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        
        // When & Then
        StepVerifier.create(service.batchAddToBlacklist(tokenHashes, reason, addedBy))
            .verifyComplete();
        
        verify(valueOperations, times(2)).set(anyString(), anyString(), any());
    }
    
    @Test
    void testBatchAddToBlacklist_EmptyList() {
        // When & Then
        StepVerifier.create(service.batchAddToBlacklist(List.of(), "reason", "admin"))
            .verifyComplete();
        
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }
    
    @Test
    void testIsServiceAvailable_True() {
        // Given
        when(redisTemplate.hasKey("jwt:blacklist_index")).thenReturn(Mono.just(true));
        
        // When & Then
        StepVerifier.create(service.isServiceAvailable())
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void testIsServiceAvailable_False() {
        // Given
        when(redisTemplate.hasKey("jwt:blacklist_index")).thenReturn(Mono.error(new RuntimeException("Connection failed")));
        
        // When & Then
        StepVerifier.create(service.isServiceAvailable())
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testGetExpiringEntriesCount_Success() {
        // Given
        int hoursUntilExpiry = 24;
        when(setOperations.members("jwt:blacklist_index")).thenReturn(Flux.just("hash1", "hash2"));
        
        String soonExpiringJson = "{\"tokenHash\":\"hash1\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                                 "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"" + 
                                 LocalDateTime.now().plusHours(12) + "\"}";
        String laterExpiringJson = "{\"tokenHash\":\"hash2\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                                  "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"" + 
                                  LocalDateTime.now().plusDays(2) + "\"}";
        
        when(valueOperations.get("jwt:blacklist:hash1")).thenReturn(Mono.just(soonExpiringJson));
        when(valueOperations.get("jwt:blacklist:hash2")).thenReturn(Mono.just(laterExpiringJson));
        
        // When & Then
        StepVerifier.create(service.getExpiringEntriesCount(hoursUntilExpiry))
            .expectNext(1L)
            .verifyComplete();
    }
}
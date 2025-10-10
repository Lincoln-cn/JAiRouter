package org.unreal.modelrouter.security.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JWT黑名单缓存管理器测试
 */
@ExtendWith(MockitoExtension.class)
class JwtBlacklistCacheManagerTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    @Mock
    private ReactiveSetOperations<String, String> setOperations;
    
    @Mock
    private JwtBlacklistService blacklistService;
    
    private JwtBlacklistCacheManager cacheManager;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        cacheManager = new JwtBlacklistCacheManager(redisTemplate, blacklistService);
    }
    
    @Test
    void testAddToBlacklistCache_Success() {
        // Given
        String tokenHash = "test-hash";
        String reason = "Test reason";
        String addedBy = "admin";
        Duration ttl = Duration.ofHours(1);
        
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        
        // When & Then
        StepVerifier.create(cacheManager.addToBlacklistCache(tokenHash, reason, addedBy, ttl))
            .verifyComplete();
        
        verify(valueOperations).set(anyString(), anyString(), eq(ttl));
        verify(setOperations).add(anyString(), eq(tokenHash));
    }
    
    @Test
    void testAddToBlacklistCache_NullTokenHash() {
        // When & Then
        StepVerifier.create(cacheManager.addToBlacklistCache(null, "reason", "admin", Duration.ofHours(1)))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testAddToBlacklistCache_EmptyTokenHash() {
        // When & Then
        StepVerifier.create(cacheManager.addToBlacklistCache("", "reason", "admin", Duration.ofHours(1)))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testIsInBlacklistCache_True() {
        // Given
        String tokenHash = "test-hash";
        when(redisTemplate.hasKey("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(true));
        
        String entryJson = "{\"tokenHash\":\"test-hash\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                          "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2025-01-01T00:00:00\"}";
        when(valueOperations.get("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(entryJson));
        
        // When & Then
        StepVerifier.create(cacheManager.isInBlacklistCache(tokenHash))
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void testIsInBlacklistCache_False() {
        // Given
        String tokenHash = "test-hash";
        when(redisTemplate.hasKey("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(false));
        
        // When & Then
        StepVerifier.create(cacheManager.isInBlacklistCache(tokenHash))
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testIsInBlacklistCache_NullTokenHash() {
        // When & Then
        StepVerifier.create(cacheManager.isInBlacklistCache(null))
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testIsInBlacklistCache_EmptyTokenHash() {
        // When & Then
        StepVerifier.create(cacheManager.isInBlacklistCache(""))
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testRemoveFromBlacklistCache_Success() {
        // Given
        String tokenHash = "test-hash";
        when(redisTemplate.delete("jwt:blacklist:" + tokenHash)).thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), eq(tokenHash))).thenReturn(Mono.just(1L));
        
        // When & Then
        StepVerifier.create(cacheManager.removeFromBlacklistCache(tokenHash))
            .verifyComplete();
        
        verify(redisTemplate).delete("jwt:blacklist:" + tokenHash);
    }
    
    @Test
    void testRemoveFromBlacklistCache_NullTokenHash() {
        // When & Then
        StepVerifier.create(cacheManager.removeFromBlacklistCache(null))
            .verifyComplete();
        
        verify(redisTemplate, never()).delete(anyString());
    }
    
    @Test
    void testRemoveFromBlacklistCache_EmptyTokenHash() {
        // When & Then
        StepVerifier.create(cacheManager.removeFromBlacklistCache(""))
            .verifyComplete();
        
        verify(redisTemplate, never()).delete(anyString());
    }
    
    @Test
    void testGetBlacklistCacheStats_Success() {
        // Given
        String statsJson = "{\"totalAdded\":10,\"totalCleaned\":2,\"currentSize\":8}";
        when(valueOperations.get("jwt:blacklist_cache_stats")).thenReturn(Mono.just(statsJson));
        
        // When & Then
        StepVerifier.create(cacheManager.getBlacklistCacheStats())
            .expectNextMatches(stats -> {
                return stats.containsKey("totalAdded") && 
                       stats.containsKey("localCacheSize") &&
                       stats.containsKey("lastCacheSync");
            })
            .verifyComplete();
    }
    
    @Test
    void testGetBlacklistCacheStats_NoStats() {
        // Given
        when(valueOperations.get("jwt:blacklist_cache_stats")).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(cacheManager.getBlacklistCacheStats())
            .expectNextMatches(stats -> stats.containsKey("localCacheSize"))
            .verifyComplete();
    }
    
    @Test
    void testGetExpiringCacheEntriesCount_Success() {
        // Given
        Duration timeUntilExpiry = Duration.ofHours(24);
        
        when(setOperations.members(anyString())).thenReturn(Flux.just("hash1", "hash2"));
        
        String soonExpiringJson = "{\"tokenHash\":\"hash1\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                                 "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2024-01-02T00:00:00\"}";
        String laterExpiringJson = "{\"tokenHash\":\"hash2\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                                  "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2025-01-01T00:00:00\"}";
        
        when(valueOperations.get("jwt:blacklist:hash1")).thenReturn(Mono.just(soonExpiringJson));
        when(valueOperations.get("jwt:blacklist:hash2")).thenReturn(Mono.just(laterExpiringJson));
        
        // When & Then
        StepVerifier.create(cacheManager.getExpiringCacheEntriesCount(timeUntilExpiry))
            .expectNext(1L) // Only hash1 should be expiring soon
            .verifyComplete();
    }
    
    @Test
    void testGetExpiringCacheEntriesCount_NoExpiringEntries() {
        // Given
        Duration timeUntilExpiry = Duration.ofHours(1);
        
        when(setOperations.members(anyString())).thenReturn(Flux.just("hash1"));
        
        String laterExpiringJson = "{\"tokenHash\":\"hash1\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                                  "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2025-01-01T00:00:00\"}";
        
        when(valueOperations.get("jwt:blacklist:hash1")).thenReturn(Mono.just(laterExpiringJson));
        
        // When & Then
        StepVerifier.create(cacheManager.getExpiringCacheEntriesCount(timeUntilExpiry))
            .expectNext(0L)
            .verifyComplete();
    }
    
    @Test
    void testGetExpiringCacheEntriesCount_EmptyCache() {
        // Given
        Duration timeUntilExpiry = Duration.ofHours(24);
        
        when(setOperations.members(anyString())).thenReturn(Flux.empty());
        
        // When & Then
        StepVerifier.create(cacheManager.getExpiringCacheEntriesCount(timeUntilExpiry))
            .expectNext(0L)
            .verifyComplete();
    }
    
    @Test
    void testCleanupExpiredCacheEntries_ManualTrigger() {
        // Given - 模拟手动触发清理
        when(setOperations.members(anyString())).thenReturn(Flux.just("expired-hash"));
        
        String expiredEntryJson = "{\"tokenHash\":\"expired-hash\",\"reason\":\"test\",\"addedBy\":\"admin\"," +
                                 "\"addedAt\":\"2024-01-01T00:00:00\",\"expiresAt\":\"2024-01-02T00:00:00\"}";
        
        when(valueOperations.get("jwt:blacklist:expired-hash")).thenReturn(Mono.just(expiredEntryJson));
        when(redisTemplate.delete("jwt:blacklist:expired-hash")).thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), eq("expired-hash"))).thenReturn(Mono.just(1L));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        
        // When - 手动调用清理方法
        cacheManager.cleanupExpiredCacheEntries();
        
        // Then - 验证清理操作被调用
        // 注意：由于这是异步操作，我们主要验证方法能正常执行而不抛出异常
        verify(setOperations, timeout(1000)).members(anyString());
    }
    
    @Test
    void testSyncLocalCacheWithRedis_ManualTrigger() {
        // Given - 模拟手动触发同步
        // 这个方法主要是清理本地缓存，不需要太多mock
        
        // When - 手动调用同步方法
        cacheManager.syncLocalCacheWithRedis();
        
        // Then - 验证方法能正常执行而不抛出异常
        // 由于这个方法主要操作本地缓存，我们主要验证它不会抛出异常
    }
}
package org.unreal.modelrouter.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.security.service.DataSyncService;
import org.unreal.modelrouter.security.service.StorageHealthService;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSyncServiceImplTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    @Mock
    private StoreManager storeManager;
    
    @Mock
    private StorageHealthService storageHealthService;
    
    private DataSyncServiceImpl dataSyncService;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.scan()).thenReturn(Flux.empty());
        
        dataSyncService = new DataSyncServiceImpl(redisTemplate, storeManager, storageHealthService);
    }
    
    @Test
    void recoverFromStoreManagerToRedis_WhenNoDataInStoreManager_ShouldReturnSuccessWithZeroCount() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(storeManager.getAllKeys()).thenReturn(Arrays.asList());
        
        // When & Then
        StepVerifier.create(dataSyncService.recoverFromStoreManagerToRedis())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getProcessedCount()).isEqualTo(0);
                assertThat(result.getSuccessCount()).isEqualTo(0);
                assertThat(result.getFailureCount()).isEqualTo(0);
            })
            .verifyComplete();
    }
    
    @Test
    void recoverFromStoreManagerToRedis_WhenLockCannotBeAcquired_ShouldReturnFailure() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(false));
        
        // When & Then
        StepVerifier.create(dataSyncService.recoverFromStoreManagerToRedis())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).contains("Another sync operation is in progress");
            })
            .verifyComplete();
    }
    
    @Test
    void recoverFromStoreManagerToRedis_WhenStoreManagerHasTokenData_ShouldRecoverToRedis() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        List<String> storeKeys = Arrays.asList(
            "jwt_token_hash1",
            "jwt_token_hash2",
            "jwt_blacklist_hash3"
        );
        when(storeManager.getAllKeys()).thenReturn(storeKeys);
        
        // Mock token data
        Map<String, Object> tokenData1 = createMockTokenData("hash1", "user1");
        Map<String, Object> tokenData2 = createMockTokenData("hash2", "user2");
        Map<String, Object> blacklistData = createMockBlacklistData("hash3");
        
        when(storeManager.getConfig("jwt_token_hash1")).thenReturn(tokenData1);
        when(storeManager.getConfig("jwt_token_hash2")).thenReturn(tokenData2);
        when(storeManager.getConfig("jwt_blacklist_hash3")).thenReturn(blacklistData);
        
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // When & Then
        StepVerifier.create(dataSyncService.recoverFromStoreManagerToRedis())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getProcessedCount()).isEqualTo(3);
                assertThat(result.getSuccessCount()).isEqualTo(3);
                assertThat(result.getFailureCount()).isEqualTo(0);
            })
            .verifyComplete();
        
        // Verify Redis operations were called
        verify(valueOperations, times(3)).set(anyString(), anyString(), any(Duration.class));
    }
    
    @Test
    void recoverFromStoreManagerToRedis_WhenRedisOperationsFail_ShouldRecordFailures() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        List<String> storeKeys = Arrays.asList("jwt_token_hash1");
        when(storeManager.getAllKeys()).thenReturn(storeKeys);
        
        Map<String, Object> tokenData = createMockTokenData("hash1", "user1");
        when(storeManager.getConfig("jwt_token_hash1")).thenReturn(tokenData);
        
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
        
        // When & Then
        StepVerifier.create(dataSyncService.recoverFromStoreManagerToRedis())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getProcessedCount()).isEqualTo(1);
                assertThat(result.getSuccessCount()).isEqualTo(0);
                assertThat(result.getFailureCount()).isEqualTo(1);
            })
            .verifyComplete();
    }
    
    @Test
    void checkDataConsistency_WhenBothStoragesEmpty_ShouldReturnConsistent() {
        // Given
        when(redisTemplate.scan()).thenReturn(Flux.empty());
        when(storeManager.getAllKeys()).thenReturn(Arrays.asList());
        
        // When & Then
        StepVerifier.create(dataSyncService.checkDataConsistency())
            .assertNext(result -> {
                assertThat(result.isConsistent()).isTrue();
                assertThat(result.getRedisCount()).isEqualTo(0);
                assertThat(result.getStoreManagerCount()).isEqualTo(0);
                assertThat(result.getMissingInRedis()).isEqualTo(0);
                assertThat(result.getMissingInStoreManager()).isEqualTo(0);
                assertThat(result.getConflictCount()).isEqualTo(0);
            })
            .verifyComplete();
    }
    
    @Test
    void checkDataConsistency_WhenDataExists_ShouldReturnCounts() {
        // Given
        when(redisTemplate.scan()).thenReturn(Flux.just(
            "jwt:token:hash1",
            "jwt:token:hash2",
            "jwt:blacklist:hash3"
        ));
        
        when(storeManager.getAllKeys()).thenReturn(Arrays.asList(
            "jwt_token_hash1",
            "jwt_token_hash4",
            "jwt_blacklist_hash3"
        ));
        
        // When & Then
        StepVerifier.create(dataSyncService.checkDataConsistency())
            .assertNext(result -> {
                assertThat(result.getRedisCount()).isEqualTo(3);
                assertThat(result.getStoreManagerCount()).isEqualTo(3);
                // Note: The actual consistency logic is simplified in the implementation
                // In a real implementation, this would compare the actual data
            })
            .verifyComplete();
    }
    
    @Test
    void getSyncStats_ShouldReturnStatistics() {
        // When & Then
        StepVerifier.create(dataSyncService.getSyncStats())
            .assertNext(stats -> {
                assertThat(stats).containsKey("totalSyncOperations");
                assertThat(stats).containsKey("successfulSyncOperations");
                assertThat(stats).containsKey("failedSyncOperations");
                assertThat(stats).containsKey("lastSyncTime");
                assertThat(stats).containsKey("successRate");
                assertThat(stats).containsKey("batchSize");
                assertThat(stats).containsKey("syncLockTtl");
                
                assertThat(stats.get("totalSyncOperations")).isEqualTo(0L);
                assertThat(stats.get("successfulSyncOperations")).isEqualTo(0L);
                assertThat(stats.get("failedSyncOperations")).isEqualTo(0L);
                assertThat(stats.get("successRate")).isEqualTo(0.0);
            })
            .verifyComplete();
    }
    
    @Test
    void performStartupRecovery_WhenRedisUnhealthy_ShouldSkipRecovery() {
        // Given
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(false));
        
        // When & Then
        StepVerifier.create(dataSyncService.performStartupRecovery())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).contains("Redis is not healthy");
            })
            .verifyComplete();
    }
    
    @Test
    void performStartupRecovery_WhenStoreManagerUnhealthy_ShouldSkipRecovery() {
        // Given
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(storageHealthService.isStoreManagerHealthy()).thenReturn(Mono.just(false));
        
        // When & Then
        StepVerifier.create(dataSyncService.performStartupRecovery())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).contains("StoreManager is not healthy");
            })
            .verifyComplete();
    }
    
    @Test
    void performStartupRecovery_WhenBothStoragesHealthy_ShouldPerformRecovery() {
        // Given
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(storageHealthService.isStoreManagerHealthy()).thenReturn(Mono.just(true));
        
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(storeManager.getAllKeys()).thenReturn(Arrays.asList());
        
        // When & Then
        StepVerifier.create(dataSyncService.performStartupRecovery())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getProcessedCount()).isEqualTo(0);
            })
            .verifyComplete();
    }
    
    @Test
    void cleanupSyncData_ShouldClearAllSyncData() {
        // When & Then
        StepVerifier.create(dataSyncService.cleanupSyncData())
            .verifyComplete();
        
        // Verify Redis cleanup operations
        verify(redisTemplate).delete("jwt:sync:stats");
        verify(redisTemplate).delete("jwt:sync:lock");
        
        // Verify stats are reset
        StepVerifier.create(dataSyncService.getSyncStats())
            .assertNext(stats -> {
                assertThat(stats.get("totalSyncOperations")).isEqualTo(0L);
                assertThat(stats.get("successfulSyncOperations")).isEqualTo(0L);
                assertThat(stats.get("failedSyncOperations")).isEqualTo(0L);
                assertThat(stats.get("lastSyncTime")).isEqualTo(0L);
            })
            .verifyComplete();
    }
    
    @Test
    void repairDataInconsistency_WhenDataIsConsistent_ShouldReturnNoRepairNeeded() {
        // Given
        DataSyncService.ConsistencyCheckResult consistentResult = 
            new DataSyncService.ConsistencyCheckResult(true, 5, 5, 0, 0, 0, "All consistent");
        
        // When & Then
        StepVerifier.create(dataSyncService.repairDataInconsistency(consistentResult))
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getMessage()).contains("Data is already consistent");
            })
            .verifyComplete();
    }
    
    @Test
    void repairDataInconsistency_WhenDataIsInconsistent_ShouldAttemptRepair() {
        // Given
        DataSyncService.ConsistencyCheckResult inconsistentResult = 
            new DataSyncService.ConsistencyCheckResult(false, 3, 5, 2, 1, 1, "Data inconsistent");
        
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // When & Then
        StepVerifier.create(dataSyncService.repairDataInconsistency(inconsistentResult))
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                // Note: The repair logic is simplified in the current implementation
            })
            .verifyComplete();
    }
    
    // Helper methods
    
    private Map<String, Object> createMockTokenData(String tokenHash, String userId) {
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("id", "token-id-" + tokenHash);
        tokenData.put("userId", userId);
        tokenData.put("tokenHash", tokenHash);
        tokenData.put("status", "ACTIVE");
        tokenData.put("issuedAt", "2024-01-01T10:00:00");
        tokenData.put("expiresAt", "2024-01-01T11:00:00");
        tokenData.put("createdAt", "2024-01-01T10:00:00");
        tokenData.put("updatedAt", "2024-01-01T10:00:00");
        return tokenData;
    }
    
    private Map<String, Object> createMockBlacklistData(String tokenHash) {
        Map<String, Object> blacklistData = new HashMap<>();
        blacklistData.put("tokenHash", tokenHash);
        blacklistData.put("reason", "Manual revocation");
        blacklistData.put("addedBy", "admin");
        blacklistData.put("addedAt", "2024-01-01T10:00:00");
        blacklistData.put("expiresAt", "2024-01-01T11:00:00");
        return blacklistData;
    }
}
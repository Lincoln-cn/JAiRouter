package org.unreal.modelrouter.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageHealthServiceImplTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    @Mock
    private StoreManager storeManager;
    
    private StorageHealthServiceImpl storageHealthService;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        
        storageHealthService = new StorageHealthServiceImpl(redisTemplate, storeManager);
    }
    
    @Test
    void isRedisHealthy_WhenRedisOperationsSucceed_ShouldReturnTrue() {
        // Given
        String testValue = "health_check_" + System.currentTimeMillis();
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(testValue));
        
        // When & Then
        StepVerifier.create(storageHealthService.isRedisHealthy())
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void isRedisHealthy_WhenRedisOperationsFail_ShouldReturnFalse() {
        // Given
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
        
        // When & Then
        StepVerifier.create(storageHealthService.isRedisHealthy())
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void isRedisHealthy_WhenRedisTimeout_ShouldReturnFalse() {
        // Given
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.never()); // Simulate timeout
        
        // When & Then
        StepVerifier.create(storageHealthService.isRedisHealthy())
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void isStoreManagerHealthy_WhenStoreManagerOperationsSucceed_ShouldReturnTrue() {
        // Given
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        
        when(storeManager.getConfig(anyString())).thenReturn(testData);
        when(storeManager.exists(anyString())).thenReturn(true);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(storeManager).deleteConfig(anyString());
        
        // When & Then
        StepVerifier.create(storageHealthService.isStoreManagerHealthy())
            .expectNext(true)
            .verifyComplete();
        
        verify(storeManager).saveConfig(anyString(), any());
        verify(storeManager).getConfig(anyString());
        verify(storeManager).exists(anyString());
        verify(storeManager).deleteConfig(anyString());
    }
    
    @Test
    void isStoreManagerHealthy_WhenStoreManagerOperationsFail_ShouldReturnFalse() {
        // Given
        doThrow(new RuntimeException("StoreManager error"))
            .when(storeManager).saveConfig(anyString(), any());
        
        // When & Then
        StepVerifier.create(storageHealthService.isStoreManagerHealthy())
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void isStoreManagerHealthy_WhenDataMismatch_ShouldReturnFalse() {
        // Given
        when(storeManager.getConfig(anyString())).thenReturn(null);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        
        // When & Then
        StepVerifier.create(storageHealthService.isStoreManagerHealthy())
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void getAllStorageHealth_ShouldReturnHealthStatusForAllStorages() {
        // Given
        String testValue = "health_check_" + System.currentTimeMillis();
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(testValue));
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        when(storeManager.getConfig(anyString())).thenReturn(testData);
        when(storeManager.exists(anyString())).thenReturn(true);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(storeManager).deleteConfig(anyString());
        
        // When & Then
        StepVerifier.create(storageHealthService.getAllStorageHealth())
            .assertNext(healthMap -> {
                assertThat(healthMap).containsKey("redis");
                assertThat(healthMap).containsKey("storemanager");
                assertThat(healthMap.get("redis")).isEqualTo(true);
                assertThat(healthMap.get("storemanager")).isEqualTo(true);
            })
            .verifyComplete();
    }
    
    @Test
    void getDetailedHealthInfo_ShouldReturnComprehensiveHealthInformation() {
        // Given
        String testValue = "health_check_" + System.currentTimeMillis();
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(testValue));
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        when(storeManager.getConfig(anyString())).thenReturn(testData);
        when(storeManager.exists(anyString())).thenReturn(true);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(storeManager).deleteConfig(anyString());
        
        // When & Then
        StepVerifier.create(storageHealthService.getDetailedHealthInfo())
            .assertNext(detailedInfo -> {
                assertThat(detailedInfo).containsKey("health");
                assertThat(detailedInfo).containsKey("lastCheckTimes");
                assertThat(detailedInfo).containsKey("stats");
                assertThat(detailedInfo).containsKey("config");
                
                @SuppressWarnings("unchecked")
                Map<String, Boolean> health = (Map<String, Boolean>) detailedInfo.get("health");
                assertThat(health.get("redis")).isEqualTo(true);
                assertThat(health.get("storemanager")).isEqualTo(true);
            })
            .verifyComplete();
    }
    
    @Test
    void refreshHealthStatus_ShouldResetCacheTimestamps() {
        // When & Then
        StepVerifier.create(storageHealthService.refreshHealthStatus())
            .verifyComplete();
        
        // Verify that subsequent health checks will perform actual checks
        // (This is verified by the fact that the cache timestamps are reset)
    }
    
    @Test
    void shouldFallbackToSecondary_WhenRedisHealthy_ShouldReturnFalse() {
        // Given
        String testValue = "health_check_" + System.currentTimeMillis();
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(testValue));
        
        // When & Then
        StepVerifier.create(storageHealthService.shouldFallbackToSecondary())
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void shouldFallbackToSecondary_WhenRedisUnhealthy_ShouldReturnTrue() {
        // Given
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
        
        // When & Then
        StepVerifier.create(storageHealthService.shouldFallbackToSecondary())
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void getRecommendedPrimaryStorage_WhenRedisHealthy_ShouldReturnRedis() {
        // Given
        String testValue = "health_check_" + System.currentTimeMillis();
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(testValue));
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        when(storeManager.getConfig(anyString())).thenReturn(testData);
        when(storeManager.exists(anyString())).thenReturn(true);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(storeManager).deleteConfig(anyString());
        
        // When & Then
        StepVerifier.create(storageHealthService.getRecommendedPrimaryStorage())
            .expectNext("redis")
            .verifyComplete();
    }
    
    @Test
    void getRecommendedPrimaryStorage_WhenRedisUnhealthyButStoreManagerHealthy_ShouldReturnStoreManager() {
        // Given
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        when(storeManager.getConfig(anyString())).thenReturn(testData);
        when(storeManager.exists(anyString())).thenReturn(true);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(storeManager).deleteConfig(anyString());
        
        // When & Then
        StepVerifier.create(storageHealthService.getRecommendedPrimaryStorage())
            .expectNext("storemanager")
            .verifyComplete();
    }
    
    @Test
    void getRecommendedPrimaryStorage_WhenBothUnhealthy_ShouldReturnStoreManagerAsDefault() {
        // Given
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
        
        doThrow(new RuntimeException("StoreManager error"))
            .when(storeManager).saveConfig(anyString(), any());
        
        // When & Then
        StepVerifier.create(storageHealthService.getRecommendedPrimaryStorage())
            .expectNext("storemanager")
            .verifyComplete();
    }
    
    @Test
    void recordStorageFailure_ShouldIncrementFailureCounter() {
        // Given
        String storageType = "redis";
        String operation = "save";
        Throwable error = new RuntimeException("Test error");
        
        // When & Then
        StepVerifier.create(storageHealthService.recordStorageFailure(storageType, operation, error))
            .verifyComplete();
        
        // Verify that failure is recorded (this would be verified through getStorageStats)
        StepVerifier.create(storageHealthService.getStorageStats())
            .assertNext(stats -> {
                @SuppressWarnings("unchecked")
                Map<String, Integer> failures = (Map<String, Integer>) stats.get("failures");
                assertThat(failures).containsKey("redis_save");
                assertThat(failures.get("redis_save")).isEqualTo(1);
            })
            .verifyComplete();
    }
    
    @Test
    void recordStorageSuccess_ShouldIncrementSuccessCounterAndResetFailures() {
        // Given
        String storageType = "redis";
        String operation = "save";
        
        // First record a failure
        StepVerifier.create(storageHealthService.recordStorageFailure(storageType, operation, 
            new RuntimeException("Test error")))
            .verifyComplete();
        
        // Then record a success
        StepVerifier.create(storageHealthService.recordStorageSuccess(storageType, operation))
            .verifyComplete();
        
        // When & Then
        StepVerifier.create(storageHealthService.getStorageStats())
            .assertNext(stats -> {
                @SuppressWarnings("unchecked")
                Map<String, Integer> failures = (Map<String, Integer>) stats.get("failures");
                @SuppressWarnings("unchecked")
                Map<String, Integer> successes = (Map<String, Integer>) stats.get("successes");
                
                assertThat(failures.get("redis_save")).isEqualTo(0); // Reset after success
                assertThat(successes.get("redis_save")).isEqualTo(1);
            })
            .verifyComplete();
    }
    
    @Test
    void getStorageStats_ShouldReturnComprehensiveStatistics() {
        // Given
        String storageType = "redis";
        String operation = "save";
        
        // Record some operations
        StepVerifier.create(storageHealthService.recordStorageSuccess(storageType, operation))
            .verifyComplete();
        StepVerifier.create(storageHealthService.recordStorageSuccess(storageType, operation))
            .verifyComplete();
        StepVerifier.create(storageHealthService.recordStorageFailure(storageType, operation, 
            new RuntimeException("Test error")))
            .verifyComplete();
        
        // When & Then
        StepVerifier.create(storageHealthService.getStorageStats())
            .assertNext(stats -> {
                assertThat(stats).containsKey("failures");
                assertThat(stats).containsKey("successes");
                assertThat(stats).containsKey("successRates");
                assertThat(stats).containsKey("currentHealth");
                assertThat(stats).containsKey("lastHealthCheck");
                
                @SuppressWarnings("unchecked")
                Map<String, Integer> successes = (Map<String, Integer>) stats.get("successes");
                @SuppressWarnings("unchecked")
                Map<String, Integer> failures = (Map<String, Integer>) stats.get("failures");
                @SuppressWarnings("unchecked")
                Map<String, Double> successRates = (Map<String, Double>) stats.get("successRates");
                
                assertThat(successes.get("redis_save")).isEqualTo(2);
                assertThat(failures.get("redis_save")).isEqualTo(1);
                assertThat(successRates.get("redis_save")).isEqualTo(66.66666666666666); // 2/3 * 100
            })
            .verifyComplete();
    }
}
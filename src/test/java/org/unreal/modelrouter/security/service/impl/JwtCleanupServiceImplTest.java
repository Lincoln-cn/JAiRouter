package org.unreal.modelrouter.security.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import org.unreal.modelrouter.security.service.JwtCleanupService;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JWT清理服务实现的单元测试
 */
@ExtendWith(MockitoExtension.class)
class JwtCleanupServiceImplTest {
    
    @Mock
    private JwtPersistenceService jwtPersistenceService;
    
    @Mock
    private JwtBlacklistService jwtBlacklistService;
    
    @Mock
    private StoreManager storeManager;
    
    private MeterRegistry meterRegistry;
    private JwtCleanupServiceImpl cleanupService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cleanupService = new JwtCleanupServiceImpl(
            jwtPersistenceService, 
            jwtBlacklistService, 
            storeManager, 
            meterRegistry
        );
        
        // 设置配置参数
        ReflectionTestUtils.setField(cleanupService, "retentionDays", 30);
        ReflectionTestUtils.setField(cleanupService, "batchSize", 1000);
        ReflectionTestUtils.setField(cleanupService, "cleanupSchedule", "0 0 2 * * ?");
        
        // 初始化服务
        cleanupService.init();
    }
    
    @Test
    void testCleanupExpiredTokens_Success() {
        // Given
        when(jwtPersistenceService.countActiveTokens()).thenReturn(Mono.just(100L), Mono.just(95L));
        when(jwtPersistenceService.removeExpiredTokens()).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(cleanupService.cleanupExpiredTokens())
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals(5, result.getRemovedTokens());
                assertEquals(0, result.getRemovedBlacklistEntries());
                assertNotNull(result.getStartTime());
                assertNotNull(result.getEndTime());
                assertTrue(result.getDurationMs() >= 0);
                assertNotNull(result.getDetails());
                
                // 验证详细信息
                Map<String, Object> details = result.getDetails();
                assertEquals("tokens", details.get("cleanupType"));
                assertEquals(100L, details.get("beforeCount"));
                assertEquals(95L, details.get("afterCount"));
                assertEquals(5L, details.get("removedCount"));
                assertTrue(details.containsKey("duration"));
            })
            .verifyComplete();
        
        verify(jwtPersistenceService, times(2)).countActiveTokens();
        verify(jwtPersistenceService).removeExpiredTokens();
    }
    
    @Test
    void testCleanupExpiredTokens_Failure() {
        // Given
        RuntimeException exception = new RuntimeException("Database error");
        when(jwtPersistenceService.removeExpiredTokens()).thenReturn(Mono.error(exception));
        
        // When & Then
        StepVerifier.create(cleanupService.cleanupExpiredTokens())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Database error", result.getErrorMessage());
                assertEquals(0, result.getRemovedTokens());
                assertEquals(0, result.getRemovedBlacklistEntries());
            })
            .verifyComplete();
    }
    
    @Test
    void testCleanupExpiredBlacklistEntries_Success() {
        // Given
        when(jwtBlacklistService.cleanupExpiredEntriesWithCount()).thenReturn(Mono.just(5L));
        
        // When & Then
        StepVerifier.create(cleanupService.cleanupExpiredBlacklistEntries())
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals(0, result.getRemovedTokens());
                assertEquals(5, result.getRemovedBlacklistEntries());
                assertNotNull(result.getStartTime());
                assertNotNull(result.getEndTime());
                assertNotNull(result.getDetails());
                
                // 验证详细信息
                Map<String, Object> details = result.getDetails();
                assertEquals("blacklist", details.get("cleanupType"));
                assertEquals(5L, details.get("removedCount"));
                assertTrue(details.containsKey("duration"));
                assertTrue(details.containsKey("startTime"));
                assertTrue(details.containsKey("endTime"));
            })
            .verifyComplete();
        
        verify(jwtBlacklistService).cleanupExpiredEntriesWithCount();
    }
    
    @Test
    void testCleanupExpiredBlacklistEntries_Failure() {
        // Given
        RuntimeException exception = new RuntimeException("Redis connection failed");
        when(jwtBlacklistService.cleanupExpiredEntriesWithCount()).thenReturn(Mono.error(exception));
        
        // When & Then
        StepVerifier.create(cleanupService.cleanupExpiredBlacklistEntries())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Redis connection failed", result.getErrorMessage());
                assertEquals(0, result.getRemovedTokens());
                assertEquals(0, result.getRemovedBlacklistEntries());
                assertNotNull(result.getDetails());
                
                // 验证错误详细信息
                Map<String, Object> details = result.getDetails();
                assertEquals("blacklist", details.get("cleanupType"));
                assertEquals("Redis connection failed", details.get("error"));
                assertEquals("RuntimeException", details.get("errorClass"));
                assertTrue(details.containsKey("duration"));
            })
            .verifyComplete();
    }
    
    @Test
    void testPerformFullCleanup_Success() {
        // Given
        when(jwtPersistenceService.countActiveTokens()).thenReturn(Mono.just(50L), Mono.just(48L));
        when(jwtPersistenceService.removeExpiredTokens()).thenReturn(Mono.empty());
        when(jwtBlacklistService.cleanupExpiredEntriesWithCount()).thenReturn(Mono.just(3L));
        when(storeManager.saveConfig(eq("jwt_cleanup_stats"), any())).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(cleanupService.performFullCleanup())
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals(2, result.getRemovedTokens());
                assertEquals(3, result.getRemovedBlacklistEntries());
                assertEquals(5, result.getTotalRemoved());
                assertNotNull(result.getDetails());
                assertTrue((Boolean) result.getDetails().get("tokenCleanupSuccess"));
                assertTrue((Boolean) result.getDetails().get("blacklistCleanupSuccess"));
                assertEquals(2, result.getDetails().get("phases"));
                assertTrue(result.getDetails().containsKey("totalDuration"));
                assertTrue(result.getDetails().containsKey("retentionDays"));
                assertTrue(result.getDetails().containsKey("batchSize"));
            })
            .verifyComplete();
        
        verify(jwtPersistenceService, times(2)).countActiveTokens();
        verify(jwtPersistenceService).removeExpiredTokens();
        verify(jwtBlacklistService).cleanupExpiredEntriesWithCount();
        verify(storeManager).saveConfig(eq("jwt_cleanup_stats"), any());
    }
    
    @Test
    void testPerformFullCleanup_PartialFailure() {
        // Given
        when(jwtPersistenceService.countActiveTokens()).thenReturn(Mono.error(new RuntimeException("Token cleanup failed")));
        when(jwtPersistenceService.removeExpiredTokens()).thenReturn(Mono.error(new RuntimeException("Token cleanup failed")));
        when(jwtBlacklistService.cleanupExpiredEntriesWithCount()).thenReturn(Mono.just(2L));
        when(storeManager.saveConfig(eq("jwt_cleanup_stats"), any())).thenReturn(null);
        
        // When & Then
        StepVerifier.create(cleanupService.performFullCleanup())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(0, result.getRemovedTokens());
                assertEquals(2, result.getRemovedBlacklistEntries());
                assertNotNull(result.getErrorMessage());
                assertTrue(result.getErrorMessage().contains("Token: ❌ FAILED"));
                assertTrue(result.getErrorMessage().contains("Blacklist: ✅ SUCCESS"));
                
                assertNotNull(result.getDetails());
                assertFalse((Boolean) result.getDetails().get("tokenCleanupSuccess"));
                assertTrue((Boolean) result.getDetails().get("blacklistCleanupSuccess"));
                assertEquals("Token cleanup failed", result.getDetails().get("tokenCleanupError"));
                assertTrue(result.getDetails().containsKey("totalDuration"));
                assertTrue(result.getDetails().containsKey("phases"));
            })
            .verifyComplete();
    }
    
    @Test
    void testGetCleanupStats() {
        // Given
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("totalCleanupsPerformed", 10L);
        mockStats.put("totalTokensRemoved", 50L);
        mockStats.put("totalBlacklistEntriesRemoved", 20L);
        mockStats.put("failedCleanups", 1L);
        mockStats.put("lastCleanupTime", LocalDateTime.now().minusHours(1).toString());
        
        when(storeManager.getConfig("jwt_cleanup_stats")).thenReturn(mockStats);
        
        // When & Then
        StepVerifier.create(cleanupService.getCleanupStats())
            .assertNext(stats -> {
                assertTrue(stats.isCleanupEnabled());
                assertEquals("0 0 2 * * ?", stats.getCleanupSchedule());
                assertNotNull(stats.getNextScheduledCleanup());
                assertNotNull(stats.getPerformanceMetrics());
                
                Map<String, Object> metrics = stats.getPerformanceMetrics();
                assertEquals(30, metrics.get("retentionDays"));
                assertEquals(1000, metrics.get("batchSize"));
                assertTrue(metrics.containsKey("successRate"));
            })
            .verifyComplete();
    }
    
    @Test
    void testScheduleCleanup() {
        // When
        cleanupService.scheduleCleanup();
        
        // Then - 这个方法只是记录日志，没有实际操作
        // 验证没有异常抛出即可
    }
    
    @Test
    void testPerformScheduledCleanup() {
        // Given
        when(jwtPersistenceService.countActiveTokens()).thenReturn(Mono.just(10L), Mono.just(9L));
        when(jwtPersistenceService.removeExpiredTokens()).thenReturn(Mono.empty());
        when(jwtBlacklistService.cleanupExpiredEntriesWithCount()).thenReturn(Mono.just(1L));
        when(storeManager.saveConfig(eq("jwt_cleanup_stats"), any())).thenReturn(null);
        
        // When
        cleanupService.performScheduledCleanup();
        
        // Then - 由于是异步执行，我们只能验证方法调用没有异常
        // 实际的验证会在异步执行完成后进行
        
        // 等待一小段时间让异步操作完成
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        verify(jwtPersistenceService, timeout(1000).times(2)).countActiveTokens();
        verify(jwtPersistenceService, timeout(1000)).removeExpiredTokens();
        verify(jwtBlacklistService, timeout(1000)).cleanupExpiredEntriesWithCount();
    }
    
    @Test
    void testCleanupResultCreation() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
        LocalDateTime endTime = LocalDateTime.now();
        
        // When
        JwtCleanupService.CleanupResult result = new JwtCleanupService.CleanupResult(
            10L, 5L, startTime, endTime, true
        );
        
        // Then
        assertEquals(10L, result.getRemovedTokens());
        assertEquals(5L, result.getRemovedBlacklistEntries());
        assertEquals(15L, result.getTotalRemoved());
        assertEquals(startTime, result.getStartTime());
        assertEquals(endTime, result.getEndTime());
        assertTrue(result.isSuccess());
        assertTrue(result.getDurationMs() > 0);
    }
    
    @Test
    void testCleanupStatsCreation() {
        // When
        JwtCleanupService.CleanupStats stats = new JwtCleanupService.CleanupStats();
        stats.setTotalCleanupsPerformed(100L);
        stats.setTotalTokensRemoved(500L);
        stats.setTotalBlacklistEntriesRemoved(200L);
        stats.setFailedCleanups(5L);
        stats.setCleanupEnabled(true);
        stats.setCleanupSchedule("0 0 2 * * ?");
        stats.setAverageCleanupDurationMs(1500.0);
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("successRate", 95.0);
        stats.setPerformanceMetrics(metrics);
        
        // Then
        assertEquals(100L, stats.getTotalCleanupsPerformed());
        assertEquals(500L, stats.getTotalTokensRemoved());
        assertEquals(200L, stats.getTotalBlacklistEntriesRemoved());
        assertEquals(5L, stats.getFailedCleanups());
        assertTrue(stats.isCleanupEnabled());
        assertEquals("0 0 2 * * ?", stats.getCleanupSchedule());
        assertEquals(1500.0, stats.getAverageCleanupDurationMs());
        assertNotNull(stats.getPerformanceMetrics());
        assertEquals(95.0, stats.getPerformanceMetrics().get("successRate"));
    }
    
    @Test
    void testCleanupWithRetry_Success() {
        // Given
        when(jwtBlacklistService.cleanupExpiredEntriesWithCount())
            .thenReturn(Mono.error(new RuntimeException("Temporary failure")))
            .thenReturn(Mono.just(3L));
        
        // When & Then
        StepVerifier.create(cleanupService.cleanupExpiredBlacklistEntries())
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals(3, result.getRemovedBlacklistEntries());
                assertNotNull(result.getDetails());
            })
            .verifyComplete();
        
        verify(jwtBlacklistService, times(2)).cleanupExpiredEntriesWithCount();
    }
    
    @Test
    void testCleanupWithRetryExhausted() {
        // Given
        when(jwtBlacklistService.cleanupExpiredEntriesWithCount())
            .thenReturn(Mono.error(new RuntimeException("Persistent failure")));
        
        // When & Then
        StepVerifier.create(cleanupService.cleanupExpiredBlacklistEntries())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Persistent failure", result.getErrorMessage());
                assertEquals(0, result.getRemovedBlacklistEntries());
                assertNotNull(result.getDetails());
                assertEquals("RuntimeException", result.getDetails().get("errorClass"));
            })
            .verifyComplete();
        
        verify(jwtBlacklistService, times(4)).cleanupExpiredEntriesWithCount(); // 1 initial + 3 retries
    }
    
    @Test
    void testGetCleanupStats_DetailedMetrics() {
        // Given
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("totalCleanupsPerformed", 50L);
        mockStats.put("totalTokensRemoved", 200L);
        mockStats.put("totalBlacklistEntriesRemoved", 100L);
        mockStats.put("failedCleanups", 2L);
        mockStats.put("lastCleanupTime", LocalDateTime.now().minusHours(2).toString());
        
        when(storeManager.getConfig("jwt_cleanup_stats")).thenReturn(mockStats);
        
        // When & Then
        StepVerifier.create(cleanupService.getCleanupStats())
            .assertNext(stats -> {
                assertTrue(stats.isCleanupEnabled());
                assertEquals("0 0 2 * * ?", stats.getCleanupSchedule());
                assertNotNull(stats.getNextScheduledCleanup());
                assertNotNull(stats.getPerformanceMetrics());
                
                Map<String, Object> metrics = stats.getPerformanceMetrics();
                assertEquals(30, metrics.get("retentionDays"));
                assertEquals(1000, metrics.get("batchSize"));
                assertTrue(metrics.containsKey("successRate"));
                assertTrue(metrics.containsKey("totalItemsRemoved"));
                assertTrue(metrics.containsKey("averageItemsPerCleanup"));
                assertTrue(metrics.containsKey("tokenCleanupRatio"));
                assertTrue(metrics.containsKey("blacklistCleanupRatio"));
                assertTrue(metrics.containsKey("isHealthy"));
                assertTrue(metrics.containsKey("lastCleanupAge"));
                
                // 验证计算的指标
                assertEquals(300L, metrics.get("totalItemsRemoved"));
                assertEquals(6.0, (Double) metrics.get("averageItemsPerCleanup"), 0.1);
                assertEquals(96.0, (Double) metrics.get("successRate"), 0.1);
            })
            .verifyComplete();
    }
    
    @Test
    void testCleanupResultWithDetails() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
        LocalDateTime endTime = LocalDateTime.now();
        
        // When
        JwtCleanupService.CleanupResult result = new JwtCleanupService.CleanupResult(
            10L, 5L, startTime, endTime, true
        );
        
        Map<String, Object> details = new HashMap<>();
        details.put("cleanupType", "full");
        details.put("phases", 2);
        details.put("retentionDays", 30);
        result.setDetails(details);
        
        // Then
        assertEquals(10L, result.getRemovedTokens());
        assertEquals(5L, result.getRemovedBlacklistEntries());
        assertEquals(15L, result.getTotalRemoved());
        assertTrue(result.isSuccess());
        assertNotNull(result.getDetails());
        assertEquals("full", result.getDetails().get("cleanupType"));
        assertEquals(2, result.getDetails().get("phases"));
        assertEquals(30, result.getDetails().get("retentionDays"));
    }
    
    @Test
    void testCleanupFailureHandling() {
        // Given
        when(jwtPersistenceService.countActiveTokens()).thenReturn(Mono.error(new IllegalArgumentException("Invalid argument")));
        when(jwtPersistenceService.removeExpiredTokens()).thenReturn(Mono.error(new IllegalArgumentException("Invalid argument")));
        
        // When & Then - IllegalArgumentException should not be retried
        StepVerifier.create(cleanupService.cleanupExpiredTokens())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Invalid argument", result.getErrorMessage());
                assertNotNull(result.getDetails());
                assertEquals("IllegalArgumentException", result.getDetails().get("errorClass"));
            })
            .verifyComplete();
        
        // Should only be called once (no retries for IllegalArgumentException)
        verify(jwtPersistenceService, times(1)).countActiveTokens();
    }
}
package org.unreal.modelrouter.security.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * UsageStatistics数据模型单元测试
 */
class UsageStatisticsTest {
    
    private UsageStatistics.UsageStatisticsBuilder baseBuilder;
    
    @BeforeEach
    void setUp() {
        Map<String, Long> dailyUsage = new HashMap<>();
        dailyUsage.put("2025-01-01", 100L);
        dailyUsage.put("2025-01-02", 150L);
        
        baseBuilder = UsageStatistics.builder()
                .totalRequests(0L)
                .successfulRequests(0L)
                .failedRequests(0L)
                .lastUsedAt(LocalDateTime.now())
                .dailyUsage(dailyUsage);
    }
    
    @Test
    void testUsageStatisticsBuilder() {
        // Given
        LocalDateTime lastUsedAt = LocalDateTime.now();
        Map<String, Long> dailyUsage = new HashMap<>();
        dailyUsage.put("2025-01-01", 100L);
        
        // When
        UsageStatistics stats = UsageStatistics.builder()
                .totalRequests(250L)
                .successfulRequests(200L)
                .failedRequests(50L)
                .lastUsedAt(lastUsedAt)
                .dailyUsage(dailyUsage)
                .build();
        
        // Then
        assertEquals(250L, stats.getTotalRequests());
        assertEquals(200L, stats.getSuccessfulRequests());
        assertEquals(50L, stats.getFailedRequests());
        assertEquals(lastUsedAt, stats.getLastUsedAt());
        assertEquals(dailyUsage, stats.getDailyUsage());
    }
    
    @Test
    void testGetSuccessRate_WithRequests() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(100L)
                .successfulRequests(80L)
                .failedRequests(20L)
                .build();
        
        // When
        double successRate = stats.getSuccessRate();
        
        // Then
        assertEquals(0.8, successRate, 0.001);
    }
    
    @Test
    void testGetSuccessRate_NoRequests() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(0L)
                .successfulRequests(0L)
                .failedRequests(0L)
                .build();
        
        // When
        double successRate = stats.getSuccessRate();
        
        // Then
        assertEquals(0.0, successRate, 0.001);
    }
    
    @Test
    void testGetSuccessRate_AllSuccessful() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(100L)
                .successfulRequests(100L)
                .failedRequests(0L)
                .build();
        
        // When
        double successRate = stats.getSuccessRate();
        
        // Then
        assertEquals(1.0, successRate, 0.001);
    }
    
    @Test
    void testGetSuccessRate_AllFailed() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(100L)
                .successfulRequests(0L)
                .failedRequests(100L)
                .build();
        
        // When
        double successRate = stats.getSuccessRate();
        
        // Then
        assertEquals(0.0, successRate, 0.001);
    }
    
    @Test
    void testIncrementRequest_Success() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(10L)
                .successfulRequests(8L)
                .failedRequests(2L)
                .build();
        
        LocalDateTime beforeIncrement = LocalDateTime.now();
        
        // When
        stats.incrementRequest(true);
        
        // Then
        assertEquals(11L, stats.getTotalRequests());
        assertEquals(9L, stats.getSuccessfulRequests());
        assertEquals(2L, stats.getFailedRequests());
        assertTrue(stats.getLastUsedAt().isAfter(beforeIncrement) || 
                  stats.getLastUsedAt().isEqual(beforeIncrement));
    }
    
    @Test
    void testIncrementRequest_Failure() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(10L)
                .successfulRequests(8L)
                .failedRequests(2L)
                .build();
        
        LocalDateTime beforeIncrement = LocalDateTime.now();
        
        // When
        stats.incrementRequest(false);
        
        // Then
        assertEquals(11L, stats.getTotalRequests());
        assertEquals(8L, stats.getSuccessfulRequests());
        assertEquals(3L, stats.getFailedRequests());
        assertTrue(stats.getLastUsedAt().isAfter(beforeIncrement) || 
                  stats.getLastUsedAt().isEqual(beforeIncrement));
    }
    
    @Test
    void testIncrementRequest_MultipleIncrements() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(0L)
                .successfulRequests(0L)
                .failedRequests(0L)
                .build();
        
        // When
        stats.incrementRequest(true);
        stats.incrementRequest(true);
        stats.incrementRequest(false);
        stats.incrementRequest(true);
        
        // Then
        assertEquals(4L, stats.getTotalRequests());
        assertEquals(3L, stats.getSuccessfulRequests());
        assertEquals(1L, stats.getFailedRequests());
        assertEquals(0.75, stats.getSuccessRate(), 0.001);
    }
    
    @Test
    void testReset() {
        // Given
        Map<String, Long> dailyUsage = new HashMap<>();
        dailyUsage.put("2025-01-01", 100L);
        
        UsageStatistics stats = baseBuilder
                .totalRequests(100L)
                .successfulRequests(80L)
                .failedRequests(20L)
                .lastUsedAt(LocalDateTime.now())
                .dailyUsage(dailyUsage)
                .build();
        
        // When
        stats.reset();
        
        // Then
        assertEquals(0L, stats.getTotalRequests());
        assertEquals(0L, stats.getSuccessfulRequests());
        assertEquals(0L, stats.getFailedRequests());
        assertNull(stats.getLastUsedAt());
        assertTrue(stats.getDailyUsage().isEmpty());
    }
    
    @Test
    void testReset_NullDailyUsage() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(100L)
                .successfulRequests(80L)
                .failedRequests(20L)
                .dailyUsage(null)
                .build();
        
        // When & Then (should not throw exception)
        assertDoesNotThrow(() -> stats.reset());
        assertEquals(0L, stats.getTotalRequests());
        assertEquals(0L, stats.getSuccessfulRequests());
        assertEquals(0L, stats.getFailedRequests());
    }
    
    @Test
    void testGetFailureRate_WithRequests() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(100L)
                .successfulRequests(70L)
                .failedRequests(30L)
                .build();
        
        // When
        double failureRate = stats.getFailureRate();
        
        // Then
        assertEquals(0.3, failureRate, 0.001);
    }
    
    @Test
    void testGetFailureRate_NoRequests() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(0L)
                .successfulRequests(0L)
                .failedRequests(0L)
                .build();
        
        // When
        double failureRate = stats.getFailureRate();
        
        // Then
        assertEquals(0.0, failureRate, 0.001);
    }
    
    @Test
    void testGetFailureRate_AllFailed() {
        // Given
        UsageStatistics stats = baseBuilder
                .totalRequests(50L)
                .successfulRequests(0L)
                .failedRequests(50L)
                .build();
        
        // When
        double failureRate = stats.getFailureRate();
        
        // Then
        assertEquals(1.0, failureRate, 0.001);
    }
}
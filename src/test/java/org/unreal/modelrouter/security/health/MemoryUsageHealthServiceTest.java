package org.unreal.modelrouter.security.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存使用健康检查服务测试
 */
@ExtendWith(MockitoExtension.class)
class MemoryUsageHealthServiceTest {
    
    private MemoryUsageHealthService healthService;
    
    @BeforeEach
    void setUp() {
        healthService = new MemoryUsageHealthService();
    }
    
    @Test
    void testCheckMemoryUsage() {
        boolean result = healthService.checkMemoryUsage();
        
        // 在正常情况下，内存检查应该成功
        assertTrue(result);
        assertTrue(healthService.getCurrentHealthStatus());
    }
    
    @Test
    void testGetDetailedMemoryStatus() {
        // 先执行一次检查
        healthService.checkMemoryUsage();
        
        Map<String, Object> status = healthService.getDetailedMemoryStatus();
        
        assertNotNull(status);
        assertTrue(status.containsKey("healthy"));
        assertTrue(status.containsKey("lastCheckTime"));
        assertTrue(status.containsKey("warningCount"));
        assertTrue(status.containsKey("criticalCount"));
        assertTrue(status.containsKey("heapMemory"));
        assertTrue(status.containsKey("nonHeapMemory"));
        assertTrue(status.containsKey("garbageCollection"));
        assertTrue(status.containsKey("thresholds"));
        
        // 检查堆内存信息
        Map<String, Object> heapMemory = (Map<String, Object>) status.get("heapMemory");
        assertNotNull(heapMemory);
        assertTrue(heapMemory.containsKey("usedMB"));
        assertTrue(heapMemory.containsKey("committedMB"));
        assertTrue(heapMemory.containsKey("maxMB"));
        assertTrue(heapMemory.containsKey("usagePercent"));
        
        // 检查非堆内存信息
        Map<String, Object> nonHeapMemory = (Map<String, Object>) status.get("nonHeapMemory");
        assertNotNull(nonHeapMemory);
        assertTrue(nonHeapMemory.containsKey("usedMB"));
        assertTrue(nonHeapMemory.containsKey("committedMB"));
        assertTrue(nonHeapMemory.containsKey("usagePercent"));
        
        // 检查GC信息
        Map<String, Object> gc = (Map<String, Object>) status.get("garbageCollection");
        assertNotNull(gc);
        assertTrue(gc.containsKey("totalCollectionTimeMs"));
        assertTrue(gc.containsKey("totalCollections"));
        assertTrue(gc.containsKey("gcPressurePercent"));
        assertTrue(gc.containsKey("jvmUptimeMs"));
        
        // 检查阈值信息
        Map<String, Object> thresholds = (Map<String, Object>) status.get("thresholds");
        assertNotNull(thresholds);
        assertTrue(thresholds.containsKey("warningThresholdPercent"));
        assertTrue(thresholds.containsKey("criticalThresholdPercent"));
        assertTrue(thresholds.containsKey("gcPressureThresholdPercent"));
    }
    
    @Test
    void testTriggerMemoryCheck() {
        boolean result = healthService.triggerMemoryCheck();
        
        assertTrue(result);
        assertTrue(healthService.getCurrentHealthStatus());
    }
    
    @Test
    void testResetMemoryStats() {
        // 先执行一些操作
        healthService.checkMemoryUsage();
        
        // 重置统计
        healthService.resetMemoryStats();
        
        Map<String, Object> status = healthService.getDetailedMemoryStatus();
        assertEquals(0L, status.get("warningCount"));
        assertEquals(0L, status.get("criticalCount"));
        assertTrue((Boolean) status.get("healthy"));
    }
    
    @Test
    void testGetMemoryUsageSummary() {
        // 先执行检查
        healthService.checkMemoryUsage();
        
        Map<String, Object> summary = healthService.getMemoryUsageSummary();
        
        assertNotNull(summary);
        assertTrue(summary.containsKey("healthy"));
        assertTrue(summary.containsKey("maxHeapUsagePercent"));
        assertTrue(summary.containsKey("maxNonHeapUsagePercent"));
        assertTrue(summary.containsKey("warningCount"));
        assertTrue(summary.containsKey("criticalCount"));
        assertTrue(summary.containsKey("totalGcTimeMs"));
        assertTrue(summary.containsKey("totalGcCount"));
        
        assertTrue((Boolean) summary.get("healthy"));
    }
    
    @Test
    void testMemoryUsagePercentCalculation() {
        // 这个测试验证内存使用百分比的计算逻辑
        healthService.checkMemoryUsage();
        
        Map<String, Object> status = healthService.getDetailedMemoryStatus();
        Map<String, Object> heapMemory = (Map<String, Object>) status.get("heapMemory");
        
        Double usagePercent = (Double) heapMemory.get("usagePercent");
        assertNotNull(usagePercent);
        assertTrue(usagePercent >= 0.0);
        assertTrue(usagePercent <= 100.0);
    }
    
    @Test
    void testGarbageCollectionInfoRetrieval() {
        healthService.checkMemoryUsage();
        
        Map<String, Object> status = healthService.getDetailedMemoryStatus();
        Map<String, Object> gc = (Map<String, Object>) status.get("garbageCollection");
        
        assertNotNull(gc);
        
        // 检查GC收集器信息
        if (gc.containsKey("collectors")) {
            Map<String, Object> collectors = (Map<String, Object>) gc.get("collectors");
            assertNotNull(collectors);
            // 至少应该有一个GC收集器
            assertFalse(collectors.isEmpty());
        }
        
        // 检查总计信息
        Long totalTime = (Long) gc.get("totalCollectionTimeMs");
        Long totalCollections = (Long) gc.get("totalCollections");
        assertNotNull(totalTime);
        assertNotNull(totalCollections);
        assertTrue(totalTime >= 0);
        assertTrue(totalCollections >= 0);
    }
    
    @Test
    void testHealthStatusCaching() {
        // 第一次检查
        long startTime = System.currentTimeMillis();
        boolean result1 = healthService.checkMemoryUsage();
        long firstCheckTime = System.currentTimeMillis();
        
        // 立即进行第二次检查（应该使用缓存）
        boolean result2 = healthService.checkMemoryUsage();
        long secondCheckTime = System.currentTimeMillis();
        
        assertTrue(result1);
        assertTrue(result2);
        
        // 第二次检查应该很快（使用缓存）
        assertTrue((secondCheckTime - firstCheckTime) < (firstCheckTime - startTime));
    }
    
    @Test
    void testShouldAlert() {
        // 在正常情况下不应该告警
        healthService.checkMemoryUsage();
        assertFalse(healthService.shouldAlert());
    }
    
    @Test
    void testMemoryThresholds() {
        Map<String, Object> status = healthService.getDetailedMemoryStatus();
        Map<String, Object> thresholds = (Map<String, Object>) status.get("thresholds");
        
        Double warningThreshold = (Double) thresholds.get("warningThresholdPercent");
        Double criticalThreshold = (Double) thresholds.get("criticalThresholdPercent");
        Double gcPressureThreshold = (Double) thresholds.get("gcPressureThresholdPercent");
        
        assertEquals(80.0, warningThreshold);
        assertEquals(90.0, criticalThreshold);
        assertEquals(10.0, gcPressureThreshold);
        
        // 临界阈值应该高于警告阈值
        assertTrue(criticalThreshold > warningThreshold);
    }
}
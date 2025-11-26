package org.unreal.modelrouter.security.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存使用情况健康检查服务
 * 监控JVM内存使用情况和垃圾回收状态
 */
@Service
@ConditionalOnProperty(name = "jairouter.security.monitoring.jwt-persistence.health-checks.memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryUsageHealthService {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryUsageHealthService.class);
    
    // 健康状态阈值
    private static final double WARNING_THRESHOLD = 80.0; // 80%
    private static final double CRITICAL_THRESHOLD = 90.0; // 90%
    private static final double GC_PRESSURE_THRESHOLD = 10.0; // GC时间占比10%
    
    // 状态缓存
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicLong lastCheckTime = new AtomicLong(0);
    private final AtomicLong warningCount = new AtomicLong(0);
    private final AtomicLong criticalCount = new AtomicLong(0);
    
    // 内存使用统计
    private final AtomicLong maxHeapUsagePercent = new AtomicLong(0);
    private final AtomicLong maxNonHeapUsagePercent = new AtomicLong(0);
    private final AtomicLong totalGcTime = new AtomicLong(0);
    private final AtomicLong totalGcCount = new AtomicLong(0);
    
    private static final long HEALTH_CHECK_CACHE_DURATION = 10000; // 10秒缓存
    
    /**
     * 检查内存使用情况
     */
    public boolean checkMemoryUsage() {
        long currentTime = System.currentTimeMillis();
        
        // 如果缓存未过期，返回缓存结果
        if (currentTime - lastCheckTime.get() < HEALTH_CHECK_CACHE_DURATION) {
            return isHealthy.get();
        }
        
        return performMemoryCheck(currentTime);
    }
    
    /**
     * 执行实际的内存检查
     */
    private boolean performMemoryCheck(long currentTime) {
        try {
            lastCheckTime.set(currentTime);
            
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            
            // 检查堆内存使用情况
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            double heapUsagePercent = calculateUsagePercent(heapUsage);
            maxHeapUsagePercent.set((long) heapUsagePercent);
            
            // 检查非堆内存使用情况
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            double nonHeapUsagePercent = calculateUsagePercent(nonHeapUsage);
            maxNonHeapUsagePercent.set((long) nonHeapUsagePercent);
            
            // 检查GC压力
            double gcPressure = calculateGcPressure();
            
            // 判断健康状态
            boolean healthy = evaluateMemoryHealth(heapUsagePercent, nonHeapUsagePercent, gcPressure);
            isHealthy.set(healthy);
            
            log.debug("内存健康检查完成 - 堆内存: {}%, 非堆内存: {}%, GC压力: {}%, 健康: {}", 
                Math.round(heapUsagePercent * 100.0) / 100.0,
                Math.round(nonHeapUsagePercent * 100.0) / 100.0,
                Math.round(gcPressure * 100.0) / 100.0,
                healthy);
            
            return healthy;
            
        } catch (Exception e) {
            log.error("内存健康检查失败", e);
            isHealthy.set(false);
            return false;
        }
    }
    
    /**
     * 计算内存使用百分比
     */
    private double calculateUsagePercent(MemoryUsage usage) {
        if (usage.getMax() <= 0) {
            return 0.0;
        }
        return (double) usage.getUsed() / usage.getMax() * 100.0;
    }
    
    /**
     * 计算GC压力
     */
    private double calculateGcPressure() {
        try {
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            
            long totalGcTimeMs = 0;
            long totalGcCollections = 0;
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                long gcTime = gcBean.getCollectionTime();
                long gcCount = gcBean.getCollectionCount();
                
                if (gcTime > 0) {
                    totalGcTimeMs += gcTime;
                }
                if (gcCount > 0) {
                    totalGcCollections += gcCount;
                }
            }
            
            // 更新统计
            totalGcTime.set(totalGcTimeMs);
            totalGcCount.set(totalGcCollections);
            
            // 计算GC时间占比（简化计算，基于JVM运行时间）
            long jvmUptime = ManagementFactory.getRuntimeMXBean().getUptime();
            if (jvmUptime > 0) {
                return (double) totalGcTimeMs / jvmUptime * 100.0;
            }
            
            return 0.0;
            
        } catch (Exception e) {
            log.warn("计算GC压力失败: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * 评估内存健康状态
     */
    private boolean evaluateMemoryHealth(double heapUsagePercent, double nonHeapUsagePercent, double gcPressure) {
        boolean healthy = true;
        
        // 检查堆内存使用情况
        if (heapUsagePercent >= CRITICAL_THRESHOLD) {
            criticalCount.incrementAndGet();
            healthy = false;
            log.warn("堆内存使用率达到临界水平: {}%", Math.round(heapUsagePercent * 100.0) / 100.0);
        } else if (heapUsagePercent >= WARNING_THRESHOLD) {
            warningCount.incrementAndGet();
            log.info("堆内存使用率较高: {}%", Math.round(heapUsagePercent * 100.0) / 100.0);
        }
        
        // 检查非堆内存使用情况
        if (nonHeapUsagePercent >= CRITICAL_THRESHOLD) {
            criticalCount.incrementAndGet();
            healthy = false;
            log.warn("非堆内存使用率达到临界水平: {}%", Math.round(nonHeapUsagePercent * 100.0) / 100.0);
        } else if (nonHeapUsagePercent >= WARNING_THRESHOLD) {
            warningCount.incrementAndGet();
            log.info("非堆内存使用率较高: {}%", Math.round(nonHeapUsagePercent * 100.0) / 100.0);
        }
        
        // 检查GC压力
        if (gcPressure >= GC_PRESSURE_THRESHOLD) {
            warningCount.incrementAndGet();
            log.warn("GC压力较高: {}%", Math.round(gcPressure * 100.0) / 100.0);
            // GC压力高不直接影响健康状态，但会记录警告
        }
        
        return healthy;
    }
    
    /**
     * 获取详细的内存健康状态
     */
    public Map<String, Object> getDetailedMemoryStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            
            // 基本状态
            status.put("healthy", isHealthy.get());
            status.put("lastCheckTime", LocalDateTime.ofEpochSecond(lastCheckTime.get() / 1000, 0, java.time.ZoneOffset.UTC).toString());
            status.put("warningCount", warningCount.get());
            status.put("criticalCount", criticalCount.get());
            
            // 堆内存详情
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            Map<String, Object> heapInfo = new HashMap<>();
            heapInfo.put("usedMB", heapUsage.getUsed() / (1024 * 1024));
            heapInfo.put("committedMB", heapUsage.getCommitted() / (1024 * 1024));
            heapInfo.put("maxMB", heapUsage.getMax() / (1024 * 1024));
            heapInfo.put("usagePercent", Math.round(calculateUsagePercent(heapUsage) * 100.0) / 100.0);
            status.put("heapMemory", heapInfo);
            
            // 非堆内存详情
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            Map<String, Object> nonHeapInfo = new HashMap<>();
            nonHeapInfo.put("usedMB", nonHeapUsage.getUsed() / (1024 * 1024));
            nonHeapInfo.put("committedMB", nonHeapUsage.getCommitted() / (1024 * 1024));
            nonHeapInfo.put("maxMB", nonHeapUsage.getMax() > 0 ? nonHeapUsage.getMax() / (1024 * 1024) : -1);
            nonHeapInfo.put("usagePercent", Math.round(calculateUsagePercent(nonHeapUsage) * 100.0) / 100.0);
            status.put("nonHeapMemory", nonHeapInfo);
            
            // GC信息
            Map<String, Object> gcInfo = getGarbageCollectionInfo();
            status.put("garbageCollection", gcInfo);
            
            // 阈值信息
            Map<String, Object> thresholds = new HashMap<>();
            thresholds.put("warningThresholdPercent", WARNING_THRESHOLD);
            thresholds.put("criticalThresholdPercent", CRITICAL_THRESHOLD);
            thresholds.put("gcPressureThresholdPercent", GC_PRESSURE_THRESHOLD);
            status.put("thresholds", thresholds);
            
        } catch (Exception e) {
            status.put("error", e.getMessage());
            log.error("获取内存状态详情失败", e);
        }
        
        return status;
    }
    
    /**
     * 获取垃圾回收信息
     */
    private Map<String, Object> getGarbageCollectionInfo() {
        Map<String, Object> gcInfo = new HashMap<>();
        
        try {
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            
            long totalTime = 0;
            long totalCollections = 0;
            Map<String, Object> collectors = new HashMap<>();
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                Map<String, Object> collectorInfo = new HashMap<>();
                collectorInfo.put("collectionCount", gcBean.getCollectionCount());
                collectorInfo.put("collectionTimeMs", gcBean.getCollectionTime());
                collectors.put(gcBean.getName(), collectorInfo);
                
                totalTime += gcBean.getCollectionTime();
                totalCollections += gcBean.getCollectionCount();
            }
            
            gcInfo.put("collectors", collectors);
            gcInfo.put("totalCollectionTimeMs", totalTime);
            gcInfo.put("totalCollections", totalCollections);
            
            // 计算GC压力
            double gcPressure = calculateGcPressure();
            gcInfo.put("gcPressurePercent", Math.round(gcPressure * 100.0) / 100.0);
            
            // JVM运行时间
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            gcInfo.put("jvmUptimeMs", uptime);
            
        } catch (Exception e) {
            gcInfo.put("error", e.getMessage());
        }
        
        return gcInfo;
    }
    
    /**
     * 手动触发内存检查
     */
    public boolean triggerMemoryCheck() {
        log.info("手动触发内存健康检查");
        // 清除缓存，强制执行新的检查
        lastCheckTime.set(0);
        return checkMemoryUsage();
    }
    
    /**
     * 重置内存健康统计
     */
    public void resetMemoryStats() {
        warningCount.set(0);
        criticalCount.set(0);
        lastCheckTime.set(0);
        maxHeapUsagePercent.set(0);
        maxNonHeapUsagePercent.set(0);
        isHealthy.set(true);
        
        log.info("内存健康统计已重置");
    }
    
    /**
     * 获取当前健康状态（不触发检查）
     */
    public boolean getCurrentHealthStatus() {
        return isHealthy.get();
    }
    
    /**
     * 检查是否需要告警
     */
    public boolean shouldAlert() {
        return !isHealthy.get() || criticalCount.get() > 0;
    }
    
    /**
     * 获取内存使用摘要
     */
    public Map<String, Object> getMemoryUsageSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("healthy", isHealthy.get());
        summary.put("maxHeapUsagePercent", maxHeapUsagePercent.get());
        summary.put("maxNonHeapUsagePercent", maxNonHeapUsagePercent.get());
        summary.put("warningCount", warningCount.get());
        summary.put("criticalCount", criticalCount.get());
        summary.put("totalGcTimeMs", totalGcTime.get());
        summary.put("totalGcCount", totalGcCount.get());
        
        return summary;
    }
}
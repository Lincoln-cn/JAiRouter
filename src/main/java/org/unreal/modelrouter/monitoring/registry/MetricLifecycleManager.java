package org.unreal.modelrouter.monitoring.registry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitoring.registry.model.MetricMetadata;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 指标生命周期管理器
 * 负责指标的自动清理、健康检查和生命周期管理
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class MetricLifecycleManager {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricLifecycleManager.class);
    
    private final MetricRegistrationService metricRegistrationService;
    private final CustomMeterRegistry customMeterRegistry;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 配置参数
    private static final long CLEANUP_INTERVAL_MINUTES = 30;
    private static final long HEALTH_CHECK_INTERVAL_MINUTES = 5;
    private static final long METRIC_INACTIVE_THRESHOLD_HOURS = 24;
    
    public MetricLifecycleManager(MetricRegistrationService metricRegistrationService,
                                 CustomMeterRegistry customMeterRegistry) {
        this.metricRegistrationService = metricRegistrationService;
        this.customMeterRegistry = customMeterRegistry;
    }
    
    @PostConstruct
    public void onApplicationReady() {
        isRunning.set(true);
        logger.info("MetricLifecycleManager started");
        
        // 启动时执行一次健康检查
        performHealthCheck();
    }
    
    @PreDestroy
    public void onApplicationShutdown() {
        isRunning.set(false);
        logger.info("MetricLifecycleManager stopped");
        
        // 应用关闭时清理资源
        performShutdownCleanup();
    }
    
    /**
     * 定期清理过期指标
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MINUTES * 60 * 1000)
    public void scheduledCleanup() {
        if (!isRunning.get()) {
            return;
        }
        
        try {
            logger.debug("Starting scheduled metric cleanup");
            
            MetricRegistrationService.CleanupResult result = metricRegistrationService.performMetricCleanup();
            
            if (result.getCleanedMetrics() > 0) {
                logger.info("Scheduled cleanup completed: {} metrics cleaned", result.getCleanedMetrics());
            } else {
                logger.debug("Scheduled cleanup completed: no metrics to clean");
            }
            
        } catch (Exception e) {
            logger.error("Error during scheduled metric cleanup", e);
        }
    }
    
    /**
     * 定期健康检查
     */
    @Scheduled(fixedRate = HEALTH_CHECK_INTERVAL_MINUTES * 60 * 1000)
    public void scheduledHealthCheck() {
        if (!isRunning.get()) {
            return;
        }
        
        try {
            logger.debug("Starting scheduled health check");
            performHealthCheck();
        } catch (Exception e) {
            logger.error("Error during scheduled health check", e);
        }
    }
    
    /**
     * 执行健康检查
     */
    public void performHealthCheck() {
        try {
            MetricRegistrationService.MetricStatistics stats = metricRegistrationService.getMetricStatistics();
            
            logger.debug("Metric health check - Total: {}, Enabled: {}, Disabled: {}", 
                        stats.getTotalMetrics(), stats.getEnabledMetrics(), stats.getDisabledMetrics());
            
            // 检查是否有过多的禁用指标
            if (stats.getDisabledMetrics() > stats.getEnabledMetrics()) {
                logger.warn("Warning: More disabled metrics ({}) than enabled metrics ({})", 
                           stats.getDisabledMetrics(), stats.getEnabledMetrics());
            }
            
            // 检查指标总数是否过多
            if (stats.getTotalMetrics() > 1000) {
                logger.warn("Warning: High number of registered metrics ({}), consider cleanup", 
                           stats.getTotalMetrics());
            }
            
            // 检查不活跃的指标
            checkInactiveMetrics();
            
        } catch (Exception e) {
            logger.error("Error during health check", e);
        }
    }
    
    /**
     * 检查不活跃的指标
     */
    private void checkInactiveMetrics() {
        try {
            List<MetricMetadata> allMetrics = customMeterRegistry.getAllMetricMetadata();
            Instant cutoffTime = Instant.now().minus(METRIC_INACTIVE_THRESHOLD_HOURS, ChronoUnit.HOURS);
            
            long inactiveCount = allMetrics.stream()
                    .filter(metadata -> metadata.getLastUpdatedAt().isBefore(cutoffTime))
                    .count();
            
            if (inactiveCount > 0) {
                logger.info("Found {} inactive metrics (not updated in {} hours)", 
                           inactiveCount, METRIC_INACTIVE_THRESHOLD_HOURS);
            }
            
        } catch (Exception e) {
            logger.error("Error checking inactive metrics", e);
        }
    }
    
    /**
     * 执行应用关闭时的清理
     */
    private void performShutdownCleanup() {
        try {
            logger.info("Performing shutdown cleanup");
            
            // 获取当前指标统计
            MetricRegistrationService.MetricStatistics stats = metricRegistrationService.getMetricStatistics();
            logger.info("Shutdown cleanup - Total metrics: {}", stats.getTotalMetrics());
            
            // 可以在这里添加特定的关闭清理逻辑
            // 例如：保存指标状态、清理临时指标等
            
        } catch (Exception e) {
            logger.error("Error during shutdown cleanup", e);
        }
    }
    
    /**
     * 手动触发清理
     * 
     * @return 清理结果
     */
    public MetricRegistrationService.CleanupResult triggerManualCleanup() {
        logger.info("Manual cleanup triggered");
        return metricRegistrationService.performMetricCleanup();
    }
    
    /**
     * 获取生命周期管理器状态
     * 
     * @return 状态信息
     */
    public LifecycleStatus getStatus() {
        MetricRegistrationService.MetricStatistics stats = metricRegistrationService.getMetricStatistics();
        
        return new LifecycleStatus(
                isRunning.get(),
                stats.getTotalMetrics(),
                stats.getEnabledMetrics(),
                stats.getDisabledMetrics(),
                Instant.now()
        );
    }
    
    /**
     * 生命周期状态信息
     */
    public static class LifecycleStatus {
        private final boolean running;
        private final int totalMetrics;
        private final int enabledMetrics;
        private final int disabledMetrics;
        private final Instant lastCheckTime;
        
        public LifecycleStatus(boolean running, int totalMetrics, int enabledMetrics, 
                              int disabledMetrics, Instant lastCheckTime) {
            this.running = running;
            this.totalMetrics = totalMetrics;
            this.enabledMetrics = enabledMetrics;
            this.disabledMetrics = disabledMetrics;
            this.lastCheckTime = lastCheckTime;
        }
        
        public boolean isRunning() { return running; }
        public int getTotalMetrics() { return totalMetrics; }
        public int getEnabledMetrics() { return enabledMetrics; }
        public int getDisabledMetrics() { return disabledMetrics; }
        public Instant getLastCheckTime() { return lastCheckTime; }
        
        @Override
        public String toString() {
            return "LifecycleStatus{" +
                   "running=" + running +
                   ", totalMetrics=" + totalMetrics +
                   ", enabledMetrics=" + enabledMetrics +
                   ", disabledMetrics=" + disabledMetrics +
                   ", lastCheckTime=" + lastCheckTime +
                   '}';
        }
    }
}
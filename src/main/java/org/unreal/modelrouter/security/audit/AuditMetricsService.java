package org.unreal.modelrouter.security.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 审计日志 Prometheus 指标服务
 * 提供审计系统健康状态的监控指标
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.audit.metrics.enabled", havingValue = "true", matchIfMissing = true)
public class AuditMetricsService {

    private final MeterRegistry meterRegistry;
    
    // 指标计数器
    private Counter auditEventsTotal;
    private Counter auditEventsSuccess;
    private Counter auditEventsFailure;
    private Counter auditFallbackEventsTotal;
    private Counter auditPrimaryStorageFailures;
    
    // 计时器
    private Timer auditEventWriteTimer;
    
    // 健康状态指标
    private final AtomicLong totalEventsCount = new AtomicLong(0);
    private final AtomicLong primaryStorageFailuresCount = new AtomicLong(0);
    private final AtomicLong fallbackStorageSuccessCount = new AtomicLong(0);
    private final AtomicLong currentBufferSize = new AtomicLong(0);
    private final AtomicLong healthScore = new AtomicLong(100);
    
    // 按事件类型统计
    private final ConcurrentHashMap<String, AtomicLong> eventTypeCounts = new ConcurrentHashMap<>();

    public AuditMetricsService(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 初始化指标
     */
    @PostConstruct
    public void initMetrics() {
        log.info("初始化审计日志 Prometheus 指标...");
        
        // 总事件计数器
        auditEventsTotal = Counter.builder("jairouter_audit_events_total")
            .description("Total number of audit events")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        // 成功事件计数器
        auditEventsSuccess = Counter.builder("jairouter_audit_events_success_total")
            .description("Number of successful audit events")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        // 失败事件计数器
        auditEventsFailure = Counter.builder("jairouter_audit_events_failure_total")
            .description("Number of failed audit events")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        // 备用存储事件计数器
        auditFallbackEventsTotal = Counter.builder("jairouter_audit_fallback_events_total")
            .description("Number of audit events written to fallback storage")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        // 主存储失败计数器
        auditPrimaryStorageFailures = Counter.builder("jairouter_audit_primary_storage_failures_total")
            .description("Number of primary storage write failures")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        // 写入耗时计时器
        auditEventWriteTimer = Timer.builder("jairouter_audit_event_write_duration_seconds")
            .description("Duration of audit event write operations")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        // 健康状态指标（Gauge）
        Gauge.builder("jairouter_audit_health_score", this, AuditMetricsService::getHealthScoreValue)
            .description("Audit system health score (0-100)")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        Gauge.builder("jairouter_audit_buffer_size", this, AuditMetricsService::getBufferSizeValue)
            .description("Current audit event buffer size")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        Gauge.builder("jairouter_audit_total_events", this, AuditMetricsService::getTotalEventsValue)
            .description("Total number of audit events processed")
            .tag("application", "jairouter")
            .register(meterRegistry);
        
        log.info("审计日志 Prometheus 指标初始化完成");
    }

    /**
     * 记录事件
     */
    public void recordEvent(final String eventType,final boolean success) {
        totalEventsCount.incrementAndGet();
        auditEventsTotal.increment();
        
        if (success) {
            auditEventsSuccess.increment();
        } else {
            auditEventsFailure.increment();
        }
        
        // 按事件类型统计
        eventTypeCounts.computeIfAbsent(eventType, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录主存储失败
     */
    public void recordPrimaryStorageFailure() {
        primaryStorageFailuresCount.incrementAndGet();
        auditPrimaryStorageFailures.increment();
        updateHealthScore();
    }

    /**
     * 记录备用存储成功
     */
    public void recordFallbackStorageSuccess() {
        fallbackStorageSuccessCount.incrementAndGet();
        auditFallbackEventsTotal.increment();
    }

    /**
     * 更新缓冲区大小
     */
    public void updateBufferSize(final int size) {
        currentBufferSize.set(size);
    }

    /**
     * 记录写入耗时
     */
    public void recordWriteDuration(final long durationMs) {
        auditEventWriteTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 更新健康分数
     */
    private void updateHealthScore() {
        long total = totalEventsCount.get();
        if (total == 0) {
            healthScore.set(100);
            return;
        }
        
        long failures = primaryStorageFailuresCount.get();
        double failureRate = (double) failures / total;
        int score = Math.max(0, (int) ((1 - failureRate) * 100));
        healthScore.set(score);
    }

    /**
     * 获取健康分数（用于 Gauge）
     */
    private double getHealthScoreValue() {
        return healthScore.get();
    }

    /**
     * 获取缓冲区大小（用于 Gauge）
     */
    private double getBufferSizeValue() {
        return currentBufferSize.get();
    }

    /**
     * 获取总事件数（用于 Gauge）
     */
    private double getTotalEventsValue() {
        return totalEventsCount.get();
    }

    /**
     * 获取指标摘要
     */
    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEvents", totalEventsCount.get());
        summary.put("primaryStorageFailures", primaryStorageFailuresCount.get());
        summary.put("fallbackStorageSuccesses", fallbackStorageSuccessCount.get());
        summary.put("bufferSize", currentBufferSize.get());
        summary.put("healthScore", healthScore.get());
        
        // 按事件类型统计
        Map<String, Long> eventTypeStats = new HashMap<>();
        eventTypeCounts.forEach((type, count) -> eventTypeStats.put(type, count.get()));
        summary.put("eventTypeStatistics", eventTypeStats);
        
        return summary;
    }

    /**
     * 重置指标（用于测试）
     */
    public void resetMetrics() {
        totalEventsCount.set(0);
        primaryStorageFailuresCount.set(0);
        fallbackStorageSuccessCount.set(0);
        currentBufferSize.set(0);
        healthScore.set(100);
        eventTypeCounts.clear();
    }
}

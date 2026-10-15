package org.unreal.modelrouter.auth.security.audit;

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
 * 审计管道的自观测指标（Prometheus）。
 *
 * <p><b>历史与现状（issue #396）</b>：这些指标原先只有 {@code EnhancedSecurityAuditService}
 * （内存缓冲实现）在写，而该类默认不是 Bean、开启还会导致启动失败（issue #395）⇒ 指标**注册了却恒为 0**。
 * 该实现已删除，写入方改为**默认生效**的 {@link ExtendedSecurityAuditServiceImpl}
 * （其 {@code persist(..)} 是审计写入的唯一漏斗）。</p>
 *
 * <p><b>随之删除的指标</b>（只对已删除的「内存缓冲 + 回退通道」设计有意义，现在没有语义）：
 * {@code jairouter_audit_fallback_events_total} · {@code jairouter_audit_primary_storage_failures_total} ·
 * {@code jairouter_audit_buffer_size} · {@code jairouter_audit_health_score}。</p>
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

    // 计时器
    private Timer auditEventWriteTimer;

    // 总事件数（供 Gauge 读取）
    private final AtomicLong totalEventsCount = new AtomicLong(0);

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

        // 写入耗时计时器
        auditEventWriteTimer = Timer.builder("jairouter_audit_event_write_duration_seconds")
            .description("Duration of audit event write operations")
            .tag("application", "jairouter")
            .register(meterRegistry);

        // 总事件数（Gauge）
        Gauge.builder("jairouter_audit_total_events", this, AuditMetricsService::getTotalEventsValue)
            .description("Total number of audit events processed")
            .tag("application", "jairouter")
            .register(meterRegistry);

        log.info("审计日志 Prometheus 指标初始化完成");
    }

    /**
     * 记录事件（由审计写入漏斗调用）
     */
    public void recordEvent(final String eventType, final boolean success) {
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
     * 记录写入耗时
     */
    public void recordWriteDuration(final long durationMs) {
        auditEventWriteTimer.record(durationMs, TimeUnit.MILLISECONDS);
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
        eventTypeCounts.clear();
    }
}

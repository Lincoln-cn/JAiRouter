package org.unreal.modelrouter.monitoring.alert;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.SlowQueryDetector;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

/**
 * 慢查询告警服务
 * 
 * 负责监控慢查询的发生情况，根据配置的告警策略发送告警通知。
 * 支持频率控制、告警抑制、趋势分析等功能。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Service
public class SlowQueryAlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(SlowQueryAlertService.class);
    
    private final MonitoringProperties monitoringProperties;
    private final SlowQueryAlertProperties alertProperties;
    private final StructuredLogger structuredLogger;
    private final MeterRegistry meterRegistry;
    private final SlowQueryDetector slowQueryDetector;
    private final ScheduledExecutorService scheduledExecutor;
    
    // 告警频率控制
    private final Map<String, AtomicLong> lastAlertTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> alertCount = new ConcurrentHashMap<>();
    
    // 告警统计信息
    private final AtomicLong totalAlertsTriggered = new AtomicLong(0);
    private final AtomicLong totalAlertsSuppressed = new AtomicLong(0);
    
    public SlowQueryAlertService(MonitoringProperties monitoringProperties,
                                SlowQueryAlertProperties alertProperties,
                                StructuredLogger structuredLogger,
                                MeterRegistry meterRegistry,
                                SlowQueryDetector slowQueryDetector) {
        this.monitoringProperties = monitoringProperties;
        this.alertProperties = alertProperties;
        this.structuredLogger = structuredLogger;
        this.meterRegistry = meterRegistry;
        this.slowQueryDetector = slowQueryDetector;
        this.scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "slow-query-alert-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定期统计任务
        startPeriodicTasks();
        
        logger.info("慢查询告警服务已启动");
    }
    
    /**
     * 检查慢查询并发送告警
     * 
     * @param operationName 操作名称
     * @param durationMillis 操作耗时（毫秒）
     * @param threshold 慢查询阈值
     * @param context 追踪上下文
     * @param additionalInfo 额外信息
     */
    public void checkAndAlert(String operationName, long durationMillis, long threshold, 
                             TracingContext context, Map<String, Object> additionalInfo) {
        
        if (durationMillis < threshold) {
            return; // 未达到慢查询阈值
        }
        
        // 记录慢查询指标
        recordSlowQueryMetrics(operationName, durationMillis, threshold);
        
        // 检查是否需要发送告警
        if (shouldTriggerAlert(operationName, durationMillis, threshold)) {
            triggerSlowQueryAlert(operationName, durationMillis, threshold, context, additionalInfo);
        }
    }
    
    /**
     * 记录慢查询指标
     */
    private void recordSlowQueryMetrics(String operationName, long durationMillis, long threshold) {
        // 记录慢查询计数
        meterRegistry.counter("slow_query_total", 
                "operation", operationName,
                "severity", getSeverityLevel(durationMillis, threshold))
                .increment();
        
        // 记录慢查询耗时分布
        meterRegistry.timer("slow_query_duration",
                "operation", operationName)
                .record(durationMillis, TimeUnit.MILLISECONDS);
        
        // 记录超出阈值的倍数
        double thresholdMultiplier = (double) durationMillis / threshold;
        meterRegistry.gauge("slow_query_threshold_multiplier",
                java.util.List.of(Tag.of("operation", operationName)), thresholdMultiplier);
    }
    
    /**
     * 判断是否应该触发告警
     */
    private boolean shouldTriggerAlert(String operationName, long durationMillis, long threshold) {
        long currentTime = System.currentTimeMillis();
        String alertKey = operationName;
        
        // 获取告警配置
        SlowQueryAlertConfig alertConfig = getAlertConfig(operationName);
        
        // 检查告警频率限制
        AtomicLong lastAlert = lastAlertTime.computeIfAbsent(alertKey, k -> new AtomicLong(0));
        long timeSinceLastAlert = currentTime - lastAlert.get();
        
        if (timeSinceLastAlert < alertConfig.getMinIntervalMs()) {
            // 告警间隔不足，抑制告警
            totalAlertsSuppressed.incrementAndGet();
            logger.debug("慢查询告警被抑制：操作={}, 距离上次告警={}ms, 最小间隔={}ms", 
                    operationName, timeSinceLastAlert, alertConfig.getMinIntervalMs());
            return false;
        }
        
        // 检查严重程度阈值
        String severity = getSeverityLevel(durationMillis, threshold);
        if (!alertConfig.isEnabledForSeverity(severity)) {
            logger.debug("慢查询告警已禁用：操作={}, 严重程度={}", operationName, severity);
            return false;
        }
        
        // 检查连续慢查询次数
        SlowQueryDetector.SlowQueryStats stats = slowQueryDetector.getSlowQueryStats(operationName);
        if (stats.getCount() < alertConfig.getMinOccurrences()) {
            logger.debug("慢查询次数不足，未达到告警阈值：操作={}, 当前次数={}, 要求次数={}", 
                    operationName, stats.getCount(), alertConfig.getMinOccurrences());
            return false;
        }
        
        return true;
    }
    
    /**
     * 触发慢查询告警
     */
    private void triggerSlowQueryAlert(String operationName, long durationMillis, long threshold,
                                      TracingContext context, Map<String, Object> additionalInfo) {
        
        String alertKey = operationName;
        long currentTime = System.currentTimeMillis();
        
        // 更新告警时间和计数
        lastAlertTime.computeIfAbsent(alertKey, k -> new AtomicLong(0)).set(currentTime);
        long currentAlertCount = alertCount.computeIfAbsent(alertKey, k -> new AtomicLong(0)).incrementAndGet();
        totalAlertsTriggered.incrementAndGet();
        
        // 构建告警信息
        SlowQueryAlert alert = buildSlowQueryAlert(operationName, durationMillis, threshold, 
                currentAlertCount, context, additionalInfo);
        
        // 记录告警日志
        logSlowQueryAlert(alert, context);
        
        // 发送告警通知
        sendAlertNotification(alert, context);
        
        logger.warn("慢查询告警已触发：操作={}, 耗时={}ms, 阈值={}ms, 告警次数={}", 
                operationName, durationMillis, threshold, currentAlertCount);
    }
    
    /**
     * 构建慢查询告警对象
     */
    private SlowQueryAlert buildSlowQueryAlert(String operationName, long durationMillis, long threshold,
                                              long alertCount, TracingContext context, Map<String, Object> additionalInfo) {
        
        SlowQueryDetector.SlowQueryStats stats = slowQueryDetector.getSlowQueryStats(operationName);
        String severity = getSeverityLevel(durationMillis, threshold);
        
        return SlowQueryAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .operationName(operationName)
                .timestamp(Instant.now())
                .severity(severity)
                .currentDuration(durationMillis)
                .threshold(threshold)
                .thresholdMultiplier((double) durationMillis / threshold)
                .alertCount(alertCount)
                .totalOccurrences(stats.getCount())
                .averageDuration(stats.getAverageDuration())
                .maxDuration(stats.getMaxDuration())
                .traceId(context != null ? context.getTraceId() : null)
                .spanId(context != null ? context.getSpanId() : null)
                .additionalInfo(additionalInfo != null ? new HashMap<>(additionalInfo) : new HashMap<>())
                .build();
    }
    
    /**
     * 记录告警日志
     */
    private void logSlowQueryAlert(SlowQueryAlert alert, TracingContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("alert_id", alert.getAlertId());
        logData.put("operation_name", alert.getOperationName());
        logData.put("severity", alert.getSeverity());
        logData.put("current_duration", alert.getCurrentDuration());
        logData.put("threshold", alert.getThreshold());
        logData.put("threshold_multiplier", alert.getThresholdMultiplier());
        logData.put("alert_count", alert.getAlertCount());
        logData.put("total_occurrences", alert.getTotalOccurrences());
        logData.put("average_duration", alert.getAverageDuration());
        logData.put("max_duration", alert.getMaxDuration());
        logData.putAll(alert.getAdditionalInfo());
        
        structuredLogger.logBusinessEvent("slow_query_alert_triggered", logData, context);
    }
    
    /**
     * 发送告警通知
     */
    private void sendAlertNotification(SlowQueryAlert alert, TracingContext context) {
        try {
            // 记录告警到指标系统，供Prometheus抓取
            meterRegistry.counter("slow_query_alert_triggered",
                    "operation", alert.getOperationName(),
                    "severity", alert.getSeverity())
                    .increment();
            
            // 设置告警指标的值，用于Prometheus告警规则
            meterRegistry.gauge("slow_query_alert_active",
                    java.util.List.of(
                            Tag.of("operation", alert.getOperationName()),
                            Tag.of("severity", alert.getSeverity())
                    ), 1.0);
            
            // 记录告警详细信息到日志，供外部告警系统处理
            Map<String, Object> alertMetrics = new HashMap<>();
            alertMetrics.put("alert_type", "slow_query");
            alertMetrics.put("operation", alert.getOperationName());
            alertMetrics.put("severity", alert.getSeverity());
            alertMetrics.put("duration", alert.getCurrentDuration());
            alertMetrics.put("threshold", alert.getThreshold());
            alertMetrics.put("multiplier", alert.getThresholdMultiplier());
            alertMetrics.put("occurrences", alert.getTotalOccurrences());
            alertMetrics.put("timestamp", alert.getTimestamp().toString());
            
            structuredLogger.logBusinessEvent("alert_notification", alertMetrics, context);
            
        } catch (Exception e) {
            logger.error("发送慢查询告警通知时发生错误：operationName={}, alertId={}", 
                    alert.getOperationName(), alert.getAlertId(), e);
        }
    }
    
    /**
     * 获取严重程度级别
     */
    private String getSeverityLevel(long durationMillis, long threshold) {
        double multiplier = (double) durationMillis / threshold;
        if (multiplier >= 5.0) {
            return "critical";
        } else if (multiplier >= 3.0) {
            return "warning";
        } else {
            return "info";
        }
    }
    
    /**
     * 获取告警配置
     */
    private SlowQueryAlertConfig getAlertConfig(String operationName) {
        // 从配置属性中获取告警配置
        return alertProperties.getAlertConfig(operationName);
    }
    
    /**
     * 启动定期任务
     */
    private void startPeriodicTasks() {
        // 定期清理过期的告警计数
        scheduledExecutor.scheduleAtFixedRate(this::cleanupExpiredAlerts, 1, 1, TimeUnit.HOURS);
        
        // 定期生成慢查询统计报告
        scheduledExecutor.scheduleAtFixedRate(this::generateSlowQueryReport, 5, 60, TimeUnit.MINUTES);
    }
    
    /**
     * 清理过期的告警计数
     */
    private void cleanupExpiredAlerts() {
        long currentTime = System.currentTimeMillis();
        long expirationTime = 24 * 60 * 60 * 1000L; // 24小时
        
        lastAlertTime.entrySet().removeIf(entry -> 
                currentTime - entry.getValue().get() > expirationTime);
        
        alertCount.entrySet().removeIf(entry -> 
                !lastAlertTime.containsKey(entry.getKey()));
        
        logger.debug("清理过期告警记录完成，当前活跃告警键数量: {}", lastAlertTime.size());
    }
    
    /**
     * 生成慢查询统计报告
     */
    private void generateSlowQueryReport() {
        try {
            Map<String, SlowQueryDetector.SlowQueryStats> allStats = slowQueryDetector.getAllSlowQueryStats();
            long totalSlowQueries = slowQueryDetector.getTotalSlowQueryCount();
            
            if (totalSlowQueries == 0) {
                return; // 没有慢查询，无需生成报告
            }
            
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("report_type", "slow_query_summary");
            reportData.put("total_slow_queries", totalSlowQueries);
            reportData.put("unique_operations", allStats.size());
            reportData.put("total_alerts_triggered", totalAlertsTriggered.get());
            reportData.put("total_alerts_suppressed", totalAlertsSuppressed.get());
            reportData.put("active_alert_keys", lastAlertTime.size());
            
            // 找出最慢的操作
            Optional<Map.Entry<String, SlowQueryDetector.SlowQueryStats>> slowestOperation = 
                    allStats.entrySet().stream()
                            .max(Comparator.comparing(entry -> entry.getValue().getMaxDuration()));
            
            if (slowestOperation.isPresent()) {
                Map.Entry<String, SlowQueryDetector.SlowQueryStats> entry = slowestOperation.get();
                reportData.put("slowest_operation", entry.getKey());
                reportData.put("slowest_duration", entry.getValue().getMaxDuration());
            }
            
            // 找出最频繁的慢查询操作
            Optional<Map.Entry<String, SlowQueryDetector.SlowQueryStats>> mostFrequentOperation = 
                    allStats.entrySet().stream()
                            .max(Comparator.comparing(entry -> entry.getValue().getCount()));
            
            if (mostFrequentOperation.isPresent()) {
                Map.Entry<String, SlowQueryDetector.SlowQueryStats> entry = mostFrequentOperation.get();
                reportData.put("most_frequent_operation", entry.getKey());
                reportData.put("most_frequent_count", entry.getValue().getCount());
            }
            
            String reportTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            reportData.put("report_time", reportTime);
            
            structuredLogger.logBusinessEvent("slow_query_report", reportData, null);
            
        } catch (Exception e) {
            logger.error("生成慢查询统计报告时发生错误", e);
        }
    }
    
    /**
     * 获取告警统计信息
     * 
     * @return 告警统计信息
     */
    public SlowQueryAlertStats getAlertStats() {
        return SlowQueryAlertStats.builder()
                .totalAlertsTriggered(totalAlertsTriggered.get())
                .totalAlertsSuppressed(totalAlertsSuppressed.get())
                .activeAlertKeys(lastAlertTime.size())
                .activeOperations(lastAlertTime.keySet())
                .build();
    }
    
    /**
     * 重置告警统计
     */
    public void resetAlertStats() {
        lastAlertTime.clear();
        alertCount.clear();
        totalAlertsTriggered.set(0);
        totalAlertsSuppressed.set(0);
        logger.info("慢查询告警统计信息已重置");
    }
}
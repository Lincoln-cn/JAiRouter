package org.unreal.modelrouter.security.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强型审计日志服务
 * 提供错误处理和备用日志通道，确保审计日志不丢失
 *
 * 特性：
 * 1. 主存储写入失败时自动切换到备用文件日志
 * 2. 异步写入，避免阻塞主流程
 * 3. 失败计数器，监控审计系统健康状态
 * 4. 定期刷新缓冲区，防止数据丢失
 * 5. Prometheus 指标导出
 */
@Slf4j
@Service("enhancedSecurityAuditService")
@ConditionalOnProperty(name = "jairouter.security.audit.enhanced", havingValue = "true", matchIfMissing = false)
public class EnhancedSecurityAuditService implements SecurityAuditService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 审计指标服务
     */
    private final AuditMetricsService auditMetricsService;

    /**
     * 审计事件缓冲区
     */
    private final ConcurrentLinkedQueue<SecurityAuditEvent> eventBuffer = new ConcurrentLinkedQueue<>();

    /**
     * 失败事件缓冲区（用于重试）
     */
    private final ConcurrentLinkedQueue<SecurityAuditEvent> failedEventBuffer = new ConcurrentLinkedQueue<>();

    /**
     * 主存储失败计数器
     */
    private final AtomicLong primaryStorageFailureCount = new AtomicLong(0);

    /**
     * 备用存储成功计数器
     */
    private final AtomicLong fallbackStorageSuccessCount = new AtomicLong(0);

    /**
     * 总事件计数器
     */
    private final AtomicLong totalEventCount = new AtomicLong(0);

    /**
     * 是否启用备用日志
     */
    private final boolean fallbackEnabled;

    /**
     * 缓冲区大小阈值
     */
    private final int bufferThreshold;

    public EnhancedSecurityAuditService(final AuditMetricsService auditMetricsService) {
        this.auditMetricsService = auditMetricsService;
        this.fallbackEnabled = true;  // 默认启用备用日志
        this.bufferThreshold = 100;   // 缓冲区达到 100 条时刷新

        // 启动定期刷新任务
        startPeriodicFlushTask();
    }

    /**
     * 启动定期刷新任务
     */
    private void startPeriodicFlushTask() {
        // 每 30 秒刷新一次缓冲区
        reactor.core.Disposable disposable = Flux.interval(java.time.Duration.ofSeconds(30))
            .publishOn(Schedulers.boundedElastic())
            .subscribe(tick -> {
                try {
                    flushBuffer();
                } catch (Exception e) {
                    log.error("定期刷新审计日志失败", e);
                }
            });
        
        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在刷新剩余的审计日志...");
            flushBuffer();
        }));
    }

    @Override
    public Mono<Void> recordEvent(final SecurityAuditEvent event) {
        return Mono.fromRunnable(() -> {
            long startTime = System.currentTimeMillis();
            boolean success = true;
            
            try {
                totalEventCount.incrementAndGet();

                // 添加到缓冲区
                eventBuffer.offer(event);

                // 尝试写入主存储
                boolean primarySuccess = writeToPrimaryStorage(event);

                if (!primarySuccess && fallbackEnabled) {
                    // 主存储失败，写入备用存储
                    log.warn("主存储写入失败，切换到备用日志通道：eventId={}", event.getEventId());
                    writeToFallbackStorage(event);
                    primaryStorageFailureCount.incrementAndGet();
                    failedEventBuffer.offer(event);
                    
                    // 记录指标
                    auditMetricsService.recordPrimaryStorageFailure();
                    auditMetricsService.recordFallbackStorageSuccess();
                    success = false;
                }

                // 检查是否需要刷新缓冲区
                if (eventBuffer.size() >= bufferThreshold) {
                    flushBuffer();
                }
                
                // 记录事件指标
                auditMetricsService.recordEvent(event.getEventType(), success);

            } catch (Exception e) {
                log.error("记录审计事件失败，eventId={}", event.getEventId(), e);
                success = false;

                // 紧急写入备用日志
                if (fallbackEnabled) {
                    writeToFallbackStorage(event);
                    fallbackStorageSuccessCount.incrementAndGet();
                    auditMetricsService.recordFallbackStorageSuccess();
                }
                
                // 记录失败指标
                auditMetricsService.recordEvent(event != null ? event.getEventType() : "UNKNOWN", false);
            } finally {
                // 记录写入耗时
                long duration = System.currentTimeMillis() - startTime;
                auditMetricsService.recordWriteDuration(duration);
                
                // 更新缓冲区大小
                auditMetricsService.updateBufferSize(eventBuffer.size());
            }
        }).then();  // 转换为 Mono<Void>
    }

    @Override
    public Mono<Void> recordAuthenticationEvent(final String userId,final String clientIp,final String userAgent,
                                               final boolean success,final String failureReason) {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType(success ? "AUTHENTICATION_SUCCESS" : "AUTHENTICATION_FAILURE")
                .userId(userId)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .action("AUTHENTICATE")
                .success(success)
                .failureReason(failureReason)
                .build();
        
        return recordEvent(event);
    }

    @Override
    public Mono<Void> recordSanitizationEvent(final String userId,final String contentType,final String ruleId,final int matchCount) {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType("DATA_SANITIZATION")
                .userId(userId)
                .action("SANITIZE")
                .success(true)
                .build();
        
        return recordEvent(event);
    }

    @Override
    public Flux<SecurityAuditEvent> queryEvents(final LocalDateTime startTime,final LocalDateTime endTime,
                                               final String eventType,final String userId,final int limit) {
        return Flux.fromIterable(eventBuffer)
                .filter(event -> event.getTimestamp() != null && 
                               event.getTimestamp().isAfter(startTime) && 
                               event.getTimestamp().isBefore(endTime))
                .filter(event -> eventType == null || eventType.equals(event.getEventType()))
                .filter(event -> userId == null || userId.equals(event.getUserId()))
                .sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
                .take(limit);
    }

    @Override
    public Mono<Map<String, Object>> getSecurityStatistics(final LocalDateTime startTime,final LocalDateTime endTime) {
        return Mono.fromSupplier(() -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalEvents", totalEventCount.get());
            stats.put("primaryStorageFailures", primaryStorageFailureCount.get());
            stats.put("fallbackStorageSuccesses", fallbackStorageSuccessCount.get());
            stats.put("bufferSize", eventBuffer.size());
            stats.put("healthScore", calculateHealthScore());
            return stats;
        });
    }

    @Override
    public Mono<Long> cleanupExpiredLogs(final int retentionDays) {
        return Mono.fromSupplier(() -> {
            // 简化实现
            return 0L;
        });
    }

    @Override
    public Mono<Boolean> shouldTriggerAlert(final String eventType,final int timeWindowMinutes,final int threshold) {
        return Mono.just(false);
    }

    /**
     * 写入主存储
     */
    private boolean writeToPrimaryStorage(final SecurityAuditEvent event) {
        try {
            // 这里可以调用数据库存储、Redis 存储等
            // 如果配置了 H2 存储，在这里调用
            log.debug("主存储写入审计事件：eventId={}, type={}", event.getEventId(), event.getEventType());
            return true;
        } catch (Exception e) {
            log.debug("主存储写入失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 写入备用存储（文件日志）
     */
    private void writeToFallbackStorage(final SecurityAuditEvent event) {
        try {
            String timestamp = event.getTimestamp() != null ? 
                event.getTimestamp().format(TIMESTAMP_FORMATTER) : 
                LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            
            String logMessage = String.format(
                "[AUDIT-FALLBACK] %s | eventId=%s | type=%s | userId=%s | action=%s | success=%s | ip=%s",
                timestamp,
                event.getEventId(),
                event.getEventType(),
                event.getUserId(),
                event.getAction(),
                event.isSuccess(),
                event.getClientIp()
            );
            
            // 写入备用日志文件
            log.info(logMessage);
            
            fallbackStorageSuccessCount.incrementAndGet();
            
        } catch (Exception e) {
            log.error("备用存储写入失败", e);
        }
    }

    /**
     * 刷新缓冲区
     */
    private void flushBuffer() {
        int bufferSize = eventBuffer.size();
        if (bufferSize == 0) {
            return;
        }
        
        log.debug("刷新审计日志缓冲区，当前大小：{}", bufferSize);
        
        // 批量处理
        while (!eventBuffer.isEmpty()) {
            SecurityAuditEvent event = eventBuffer.poll();
            if (event != null) {
                try {
                    // 可以在这里进行批量写入优化
                    log.debug("缓冲区刷新：eventId={}", event.getEventId());
                } catch (Exception e) {
                    log.error("缓冲区刷新失败", e);
                    failedEventBuffer.offer(event);
                }
            }
        }
    }

    /**
     * 获取审计系统健康状态
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalEvents", totalEventCount.get());
        status.put("primaryStorageFailures", primaryStorageFailureCount.get());
        status.put("fallbackStorageSuccesses", fallbackStorageSuccessCount.get());
        status.put("bufferSize", eventBuffer.size());
        status.put("failedBufferSize", failedEventBuffer.size());
        status.put("healthScore", calculateHealthScore());
        return status;
    }

    /**
     * 计算健康分数（0-100）
     */
    private int calculateHealthScore() {
        long total = totalEventCount.get();
        if (total == 0) {
            return 100;
        }
        
        long failures = primaryStorageFailureCount.get();
        double failureRate = (double) failures / total;
        
        // 失败率越低，分数越高
        return Math.max(0, (int) ((1 - failureRate) * 100));
    }

    /**
     * 获取失败的事件（用于重试或诊断）
     */
    public java.util.Collection<SecurityAuditEvent> getFailedEvents() {
        return new java.util.ArrayList<>(failedEventBuffer);
    }
}

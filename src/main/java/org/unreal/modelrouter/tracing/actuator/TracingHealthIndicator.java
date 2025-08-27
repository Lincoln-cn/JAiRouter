package org.unreal.modelrouter.tracing.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.tracing.performance.TracingPerformanceMonitor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 追踪系统健康检查指示器
 * 
 * 集成到Spring Boot Actuator的健康检查端点，提供追踪系统的健康状态信息
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component("tracing")
@RequiredArgsConstructor
public class TracingHealthIndicator implements HealthIndicator {

    private final TracingConfiguration tracingConfiguration;
    private final TracingPerformanceMonitor performanceMonitor;
    private final AsyncTracingProcessor asyncTracingProcessor;
    private final TracingMemoryManager memoryManager;

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // 基本状态
            boolean tracingEnabled = tracingConfiguration.isEnabled();
            details.put("enabled", tracingEnabled);
            details.put("serviceName", tracingConfiguration.getServiceName());
            details.put("checkTime", LocalDateTime.now());
            
            if (!tracingEnabled) {
                return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "追踪系统已禁用")
                    .build();
            }
            
            // 获取性能监控健康状态
            Health performanceHealth = performanceMonitor.health();
            details.put("performanceStatus", performanceHealth.getStatus().getCode());
            details.putAll(performanceHealth.getDetails());
            
            // 检查异步处理器状态
            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();
            details.put("asyncProcessor", Map.of(
                "running", processingStats.isRunning(),
                "queueSize", processingStats.getQueueSize(),
                "processedCount", processingStats.getProcessedCount(),
                "droppedCount", processingStats.getDroppedCount(),
                "successRate", processingStats.getSuccessRate()
            ));
            
            // 检查内存管理器状态
            TracingMemoryManager.MemoryStats memoryStats = memoryManager.getMemoryStats();
            details.put("memoryManager", Map.of(
                "heapUsageRatio", memoryStats.getHeapUsageRatio(),
                "cacheSize", memoryStats.getCacheSize(),
                "cacheHitRatio", memoryStats.getHitRatio(),
                "pressureLevel", memoryStats.getPressureLevel().name()
            ));
            
            // 检查OpenTelemetry状态
            TracingConfiguration.OpenTelemetryConfig otelConfig = tracingConfiguration.getOpenTelemetry();
            details.put("openTelemetry", Map.of(
                "enabled", otelConfig.isEnabled(),
                "sdkDisabled", otelConfig.getSdk().isDisabled()
            ));
            
            // 检查导出器状态
            TracingConfiguration.ExporterConfig exporterConfig = tracingConfiguration.getExporter();
            details.put("exporter", Map.of(
                "type", exporterConfig.getType(),
                "loggingEnabled", exporterConfig.getLogging().isEnabled()
            ));
            
            // 综合健康状态判断
            Health.Builder healthBuilder = Health.up();
            
            // 检查异步处理器
            if (!processingStats.isRunning()) {
                healthBuilder = Health.down().withDetail("issue", "异步处理器未运行");
            }
            
            // 检查丢弃率
            if (processingStats.getDropRate() > 0.1) { // 丢弃率超过10%
                healthBuilder = Health.down().withDetail("issue", "数据丢弃率过高: " + 
                    String.format("%.2f%%", processingStats.getDropRate() * 100));
            }
            
            // 检查内存压力
            if (memoryStats.getPressureLevel() == TracingMemoryManager.MemoryPressureLevel.CRITICAL) {
                healthBuilder = Health.down().withDetail("issue", "内存压力严重");
            } else if (memoryStats.getPressureLevel() == TracingMemoryManager.MemoryPressureLevel.HIGH) {
                healthBuilder = Health.status("WARNING").withDetail("warning", "内存压力较高");
            }
            
            // 检查性能监控状态
            if (performanceHealth.getStatus().getCode().equals("DOWN")) {
                healthBuilder = Health.down().withDetail("issue", "性能监控系统异常");
            }
            
            return healthBuilder.withDetails(details).build();
            
        } catch (Exception e) {
            log.error("追踪健康检查失败", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("errorClass", e.getClass().getSimpleName())
                .withDetail("checkTime", LocalDateTime.now())
                .build();
        }
    }
}
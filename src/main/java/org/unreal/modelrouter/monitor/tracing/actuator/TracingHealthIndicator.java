package org.unreal.modelrouter.monitor.tracing.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryPressureLevel;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * span 丢弃率的「降级」告警阈值。
     *
     * <p>超过它只把该组件标成 {@code WARNING}（HTTP 仍为 200），**不再**判 {@code DOWN}：
     * 计数器是生命周期累计值，判 DOWN 会让实例永久 unhealthy（详见 {@link #health()} 内注释）。</p>
     */
    static final double DROP_RATE_WARNING_THRESHOLD = 0.1;

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
                // #849：关闭追踪是合法运行态（配置项就是为此存在），不能把实例判成 unhealthy：
                // Dockerfile 的 HEALTHCHECK 与不少部署都把 /actuator/health 当存活探针，
                // 一旦这里 DOWN，容器会被反复重启，而重启根本不解决「追踪被关掉」。
                return Health.up()
                    .withDetails(details)
                    .withDetail("reason", "追踪系统已禁用（可选组件，不影响请求处理）")
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
            MemoryStats memoryStats = memoryManager.getMemoryStats();
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
            //
            // ⚠️ 两类判定必须分开，且**汇总后再定状态**：
            // 原实现是 `healthBuilder = Health.down()...` 逐条覆盖，后一条会把前一条的
            // 状态与 details 一起丢掉（例如「处理器未运行」会被随后「内存压力高」改写），
            // 属于顺序相关的信息丢失。现在把 DOWN 级与降级级分别收集。
            final Health.Builder healthBuilder = Health.up();
            final List<String> downIssues = new ArrayList<>();
            final List<String> warnings = new ArrayList<>();

            // 检查异步处理器：这是真的"追踪不可用"
            if (!processingStats.isRunning()) {
                downIssues.add("异步处理器未运行");
            }

            // 检查丢弃率：**不得判 DOWN**。
            // 理由一（口径）：丢 span 是**可观测性**降级，不是服务不可用 —— 上游调用照常成功。
            // 理由二（可用性）：dropRate 取自 AsyncTracingProcessor.ProcessingStats 的
            //   processedCount / droppedCount，是**生命周期累计**值，一旦越过阈值就再也回不到
            //   阈值以下（只有重启才归零）⇒ 判 DOWN 等于让实例**永久** unhealthy。
            //   而三份 docker-compose*.yml 的容器健康检查都是 `curl -f /actuator/health`
            //   （wget --spider），于是容器被判 unhealthy，重启又并不解决丢 span。
            //   这与本仓既有口径一致：monitoring-base.yml 刻意把 db/redis 排除在探针分组外
            //   ——「下游依赖抖动应由就绪摘流处理，不应触发重启」。
            // 数值仍完整留在 details 的 asyncProcessor / processingDropRate 里，告警规则照旧可用。
            if (processingStats.getDropRate() > DROP_RATE_WARNING_THRESHOLD) {
                warnings.add("数据丢弃率过高: "
                        + String.format("%.2f%%", processingStats.getDropRate() * 100));
            }

            // 检查内存压力
            if (memoryStats.getPressureLevel() == MemoryPressureLevel.CRITICAL) {
                downIssues.add("内存压力严重");
            } else if (memoryStats.getPressureLevel() == MemoryPressureLevel.HIGH) {
                warnings.add("内存压力较高");
            }

            // 检查性能监控状态
            if (performanceHealth.getStatus().getCode().equals("DOWN")) {
                downIssues.add("性能监控系统异常");
            }

            details.put("downIssues", downIssues);
            details.put("warnings", warnings);

            if (!downIssues.isEmpty()) {
                return Health.down()
                        .withDetails(details)
                        .withDetail("issue", String.join("; ", downIssues))
                        .build();
            }
            if (!warnings.isEmpty()) {
                return Health.status("WARNING")
                        .withDetails(details)
                        .withDetail("warning", String.join("; ", warnings))
                        .build();
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
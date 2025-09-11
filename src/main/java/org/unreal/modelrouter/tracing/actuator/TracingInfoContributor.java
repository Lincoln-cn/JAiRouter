package org.unreal.modelrouter.tracing.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.tracing.performance.TracingPerformanceMonitor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 追踪系统信息贡献器
 * 
 * 为Spring Boot Actuator的info端点提供追踪系统相关信息
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingInfoContributor implements InfoContributor {

    private final TracingConfiguration tracingConfiguration;
    private final TracingPerformanceMonitor performanceMonitor;
    private final AsyncTracingProcessor asyncTracingProcessor;
    private final TracingMemoryManager memoryManager;

    @Override
    public void contribute(Info.Builder builder) {
        try {
            Map<String, Object> tracingInfo = new HashMap<>();
            
            // 基本配置信息
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", tracingConfiguration.isEnabled());
            config.put("serviceName", tracingConfiguration.getServiceName());
            config.put("serviceVersion", tracingConfiguration.getServiceVersion());
            config.put("serviceNamespace", tracingConfiguration.getServiceNamespace());
            tracingInfo.put("config", config);
            
            // OpenTelemetry信息
            Map<String, Object> openTelemetry = new HashMap<>();
            TracingConfiguration.OpenTelemetryConfig otelConfig = tracingConfiguration.getOpenTelemetry();
            openTelemetry.put("enabled", otelConfig.isEnabled());
            openTelemetry.put("sdkDisabled", otelConfig.getSdk().isDisabled());
            tracingInfo.put("openTelemetry", openTelemetry);
            
            // 采样配置信息
            Map<String, Object> sampling = new HashMap<>();
            TracingConfiguration.SamplingConfig samplingConfig = tracingConfiguration.getSampling();
            sampling.put("globalRatio", samplingConfig.getRatio());
            sampling.put("adaptiveEnabled", samplingConfig.getAdaptive().isEnabled());
            sampling.put("targetSpansPerSecond", samplingConfig.getAdaptive().getTargetSpansPerSecond());
            sampling.put("serviceRatiosCount", samplingConfig.getServiceRatios().size());
            sampling.put("alwaysSampleCount", samplingConfig.getAlwaysSample().size());
            sampling.put("neverSampleCount", samplingConfig.getNeverSample().size());
            sampling.put("rulesCount", samplingConfig.getRules().size());
            tracingInfo.put("sampling", sampling);
            
            // 导出器信息
            Map<String, Object> exporter = new HashMap<>();
            TracingConfiguration.ExporterConfig exporterConfig = tracingConfiguration.getExporter();
            exporter.put("type", exporterConfig.getType());
            exporter.put("loggingEnabled", exporterConfig.getLogging().isEnabled());
            
            // 根据导出器类型添加具体配置
            switch (exporterConfig.getType()) {
                case "jaeger":
                    exporter.put("endpoint", exporterConfig.getJaeger().getEndpoint());
                    exporter.put("timeout", exporterConfig.getJaeger().getTimeout().toSeconds() + "s");
                    break;
                case "zipkin":
                    exporter.put("endpoint", exporterConfig.getZipkin().getEndpoint());
                    exporter.put("timeout", exporterConfig.getZipkin().getTimeout().toSeconds() + "s");
                    break;
                case "otlp":
                    exporter.put("endpoint", exporterConfig.getOtlp().getEndpoint());
                    exporter.put("timeout", exporterConfig.getOtlp().getTimeout().toSeconds() + "s");
                    exporter.put("compression", exporterConfig.getOtlp().getCompression());
                    break;
            }
            tracingInfo.put("exporter", exporter);
            
            // 性能配置信息
            Map<String, Object> performance = new HashMap<>();
            TracingConfiguration.PerformanceConfig perfConfig = tracingConfiguration.getPerformance();
            performance.put("asyncProcessing", perfConfig.isAsyncProcessing());
            
            Map<String, Object> threadPool = new HashMap<>();
            threadPool.put("coreSize", perfConfig.getThreadPool().getCoreSize());
            threadPool.put("maxSize", perfConfig.getThreadPool().getMaxSize());
            threadPool.put("queueCapacity", perfConfig.getThreadPool().getQueueCapacity());
            performance.put("threadPool", threadPool);
            
            Map<String, Object> buffer = new HashMap<>();
            buffer.put("size", perfConfig.getBuffer().getSize());
            buffer.put("flushInterval", perfConfig.getBuffer().getFlushInterval().toSeconds() + "s");
            buffer.put("maxWaitTime", perfConfig.getBuffer().getMaxWaitTime().toSeconds() + "s");
            performance.put("buffer", buffer);
            
            Map<String, Object> memory = new HashMap<>();
            memory.put("maxSpansInMemory", perfConfig.getMemory().getMaxSpansInMemory());
            memory.put("memoryLimitMb", perfConfig.getMemory().getMemoryLimitMb());
            memory.put("gcInterval", perfConfig.getMemory().getGcInterval().toSeconds() + "s");
            performance.put("memory", memory);
            
            Map<String, Object> batch = new HashMap<>();
            batch.put("size", perfConfig.getBatch().getSize());
            batch.put("timeout", perfConfig.getBatch().getTimeout().toSeconds() + "s");
            batch.put("maxConcurrentBatches", perfConfig.getBatch().getMaxConcurrentBatches());
            performance.put("batch", batch);
            
            tracingInfo.put("performance", performance);
            
            // 运行时统计信息
            if (tracingConfiguration.isEnabled()) {
                Map<String, Object> runtime = new HashMap<>();
                
                // 异步处理器统计
                AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();
                Map<String, Object> processingInfo = new HashMap<>();
                processingInfo.put("running", processingStats.isRunning());
                processingInfo.put("processedCount", processingStats.getProcessedCount());
                processingInfo.put("droppedCount", processingStats.getDroppedCount());
                processingInfo.put("batchCount", processingStats.getBatchCount());
                processingInfo.put("queueSize", processingStats.getQueueSize());
                processingInfo.put("successRate", String.format("%.2f%%", processingStats.getSuccessRate() * 100));
                processingInfo.put("dropRate", String.format("%.2f%%", processingStats.getDropRate() * 100));
                runtime.put("processing", processingInfo);
                
                // 内存管理器统计
                TracingMemoryManager.MemoryStats memoryStats = memoryManager.getMemoryStats();
                Map<String, Object> memoryInfo = new HashMap<>();
                memoryInfo.put("heapUsedMb", memoryStats.getUsedHeap() / (1024 * 1024));
                memoryInfo.put("heapMaxMb", memoryStats.getMaxHeap() / (1024 * 1024));
                memoryInfo.put("heapUsagePercent", String.format("%.2f%%", memoryStats.getHeapUsageRatio() * 100));
                memoryInfo.put("cacheSize", memoryStats.getCacheSize());
                memoryInfo.put("spanCacheCount", memoryStats.getSpanCacheCount());
                memoryInfo.put("cacheHitRatio", String.format("%.2f%%", memoryStats.getHitRatio() * 100));
                memoryInfo.put("pressureLevel", memoryStats.getPressureLevel().name());
                memoryInfo.put("evictionCount", memoryStats.getEvictionCount());
                memoryInfo.put("gcCount", memoryStats.getGcCount());
                runtime.put("memory", memoryInfo);
                
                // 性能监控统计
                org.springframework.boot.actuate.health.Health health = performanceMonitor.health();
                Map<String, Object> healthInfo = new HashMap<>();
                healthInfo.put("status", health.getStatus().getCode());
                healthInfo.put("details", health.getDetails());
                runtime.put("health", healthInfo);
                
                tracingInfo.put("runtime", runtime);
            }
            
            // 组件状态
            Map<String, Object> components = new HashMap<>();
            TracingConfiguration.ComponentsConfig componentsConfig = tracingConfiguration.getComponents();
            
            components.put("http", Map.of(
                "enabled", componentsConfig.getHttp().isEnabled(),
                "captureHeaders", componentsConfig.getHttp().isCaptureHeaders(),
                "captureBody", componentsConfig.getHttp().isCaptureBody(),
                "excludedPathsCount", componentsConfig.getHttp().getExcludedPaths().size()
            ));
            
            components.put("loadBalancer", Map.of(
                "enabled", componentsConfig.getLoadBalancer().isEnabled(),
                "captureStrategy", componentsConfig.getLoadBalancer().isCaptureStrategy(),
                "captureStatistics", componentsConfig.getLoadBalancer().isCaptureStatistics()
            ));
            
            components.put("rateLimiter", Map.of(
                "enabled", componentsConfig.getRateLimiter().isEnabled(),
                "captureAlgorithm", componentsConfig.getRateLimiter().isCaptureAlgorithm(),
                "captureStatistics", componentsConfig.getRateLimiter().isCaptureStatistics()
            ));
            
            components.put("circuitBreaker", Map.of(
                "enabled", componentsConfig.getCircuitBreaker().isEnabled(),
                "captureState", componentsConfig.getCircuitBreaker().isCaptureState(),
                "captureStatistics", componentsConfig.getCircuitBreaker().isCaptureStatistics()
            ));
            
            tracingInfo.put("components", components);
            
            // 安全配置信息
            Map<String, Object> security = new HashMap<>();
            TracingConfiguration.SecurityConfig securityConfig = tracingConfiguration.getSecurity();
            
            security.put("sanitization", Map.of(
                "enabled", securityConfig.getSanitization().isEnabled(),
                "inheritGlobalRules", securityConfig.getSanitization().isInheritGlobalRules(),
                "additionalPatternsCount", securityConfig.getSanitization().getAdditionalPatterns().size(),
                "sensitiveAttributesCount", securityConfig.getSanitization().getSensitiveAttributes().size()
            ));
            
            security.put("accessControl", Map.of(
                "restrictTraceAccess", securityConfig.getAccessControl().isRestrictTraceAccess(),
                "allowedRolesCount", securityConfig.getAccessControl().getAllowedRoles().size(),
                "enableRoleBasedFiltering", securityConfig.getAccessControl().isEnableRoleBasedFiltering()
            ));
            
            security.put("encryption", Map.of(
                "enabled", securityConfig.getEncryption().isEnabled(),
                "algorithm", securityConfig.getEncryption().getAlgorithm(),
                "keySize", securityConfig.getEncryption().getKeySize(),
                "autoRotation", securityConfig.getEncryption().getKeyManagement().isAutoRotation()
            ));
            
            tracingInfo.put("security", security);
            
            // 添加时间戳
            tracingInfo.put("generatedAt", LocalDateTime.now());
            
            builder.withDetail("tracing", tracingInfo);
            
        } catch (Exception e) {
            log.error("构建追踪信息失败", e);
            builder.withDetail("tracing", Map.of(
                "error", "信息收集失败: " + e.getMessage(),
                "generatedAt", LocalDateTime.now()
            ));
        }
    }
}
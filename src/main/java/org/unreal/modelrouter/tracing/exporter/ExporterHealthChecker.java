package org.unreal.modelrouter.tracing.exporter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 导出器健康检查器
 * 
 * 监控Span导出器的健康状态，包括：
 * - 导出器连接状态检查
 * - 导出成功率监控
 * - 导出延迟监控
 * - 错误统计和告警
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExporterHealthChecker implements HealthIndicator {
    
    private final SpanExporter spanExporter;
    private final TracingConfiguration tracingConfig;
    private final Tracer tracer;
    
    // 健康状态
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastCheckTime = new AtomicReference<>(Instant.now());
    
    // 统计信息
    private final AtomicLong totalExports = new AtomicLong(0);
    private final AtomicLong successfulExports = new AtomicLong(0);
    private final AtomicLong failedExports = new AtomicLong(0);
    private final AtomicLong totalExportTime = new AtomicLong(0);
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        boolean healthy = isHealthy.get();
        String error = lastError.get();
        Instant lastSuccess = lastSuccessTime.get();
        Instant lastCheck = lastCheckTime.get();
        
        if (healthy) {
            builder.up();
        } else {
            builder.down();
            if (error != null) {
                builder.withDetail("lastError", error);
            }
        }
        
        // 添加统计信息
        builder.withDetail("totalExports", totalExports.get())
                .withDetail("successfulExports", successfulExports.get())
                .withDetail("failedExports", failedExports.get())
                .withDetail("successRate", calculateSuccessRate())
                .withDetail("averageExportTime", calculateAverageExportTime())
                .withDetail("lastSuccessTime", lastSuccess.toString())
                .withDetail("lastCheckTime", lastCheck.toString())
                .withDetail("exporterType", getExporterType());
        
        return builder.build();
    }
    
    /**
     * 定期健康检查
     */
    @Scheduled(fixedDelayString = "${jairouter.tracing.monitoring.health.check-interval:30000}")
    public void performHealthCheck() {
        if (!tracingConfig.getMonitoring().getHealth().isEnabled()) {
            return;
        }
        
        log.debug("执行导出器健康检查");
        
        try {
            boolean result = checkExporterHealth();
            updateHealthStatus(result, null);
        } catch (Exception e) {
            log.warn("导出器健康检查失败", e);
            updateHealthStatus(false, e.getMessage());
        }
    }
    
    /**
     * 检查导出器健康状态
     */
    private boolean checkExporterHealth() {
        try {
            // 创建测试Span数据
            SpanData testSpan = createTestSpanData();
            
            // 尝试导出测试Span
            Instant startTime = Instant.now();
            CompletableFuture<Void> exportFuture = CompletableFuture.runAsync(() -> {
                try {
                    spanExporter.export(Collections.singletonList(testSpan));
                } catch (Exception e) {
                    throw new RuntimeException("导出失败", e);
                }
            });
            
            // 等待导出完成，设置超时
            Duration timeout = tracingConfig.getMonitoring().getHealth().getCheckInterval();
            exportFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            // 记录成功
            long exportTime = Duration.between(startTime, Instant.now()).toMillis();
            recordExportSuccess(exportTime);
            
            return true;
        } catch (Exception e) {
            log.debug("导出器健康检查失败", e);
            recordExportFailure();
            return false;
        }
    }
    
    /**
     * 创建测试Span数据
     */
    private SpanData createTestSpanData() {
        Span testSpan = tracer.spanBuilder("health-check")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("test", true)
                .setAttribute("timestamp", Instant.now().toString())
                .startSpan();
        
        testSpan.setStatus(StatusCode.OK, "Health check test span");
        testSpan.end();
        
        // 这里需要将Span转换为SpanData
        // 实际实现中可能需要使用OpenTelemetry的内部API
        // 或者创建一个简单的SpanData实现
        return new TestSpanData();
    }
    
    /**
     * 更新健康状态
     */
    private void updateHealthStatus(boolean healthy, String error) {
        lastCheckTime.set(Instant.now());
        
        if (healthy) {
            if (!isHealthy.get()) {
                log.info("导出器健康状态恢复");
            }
            isHealthy.set(true);
            lastError.set(null);
            lastSuccessTime.set(Instant.now());
        } else {
            if (isHealthy.get()) {
                log.warn("导出器健康状态异常: {}", error);
            }
            isHealthy.set(false);
            lastError.set(error);
        }
    }
    
    /**
     * 记录导出成功
     */
    private void recordExportSuccess(long exportTime) {
        totalExports.incrementAndGet();
        successfulExports.incrementAndGet();
        totalExportTime.addAndGet(exportTime);
    }
    
    /**
     * 记录导出失败
     */
    private void recordExportFailure() {
        totalExports.incrementAndGet();
        failedExports.incrementAndGet();
    }
    
    /**
     * 计算成功率
     */
    private double calculateSuccessRate() {
        long total = totalExports.get();
        if (total == 0) {
            return 1.0;
        }
        return (double) successfulExports.get() / total;
    }
    
    /**
     * 计算平均导出时间
     */
    private double calculateAverageExportTime() {
        long successful = successfulExports.get();
        if (successful == 0) {
            return 0.0;
        }
        return (double) totalExportTime.get() / successful;
    }
    
    /**
     * 获取导出器类型
     */
    private String getExporterType() {
        return tracingConfig.getExporter().getType();
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalExports.set(0);
        successfulExports.set(0);
        failedExports.set(0);
        totalExportTime.set(0);
        log.info("重置导出器统计信息");
    }
    
    /**
     * 获取统计信息
     */
    public ExporterStatistics getStatistics() {
        return ExporterStatistics.builder()
                .totalExports(totalExports.get())
                .successfulExports(successfulExports.get())
                .failedExports(failedExports.get())
                .successRate(calculateSuccessRate())
                .averageExportTime(calculateAverageExportTime())
                .isHealthy(isHealthy.get())
                .lastError(lastError.get())
                .lastSuccessTime(lastSuccessTime.get())
                .lastCheckTime(lastCheckTime.get())
                .build();
    }
    
    /**
     * 导出器统计信息
     */
    public static class ExporterStatistics {
        private final long totalExports;
        private final long successfulExports;
        private final long failedExports;
        private final double successRate;
        private final double averageExportTime;
        private final boolean isHealthy;
        private final String lastError;
        private final Instant lastSuccessTime;
        private final Instant lastCheckTime;
        
        private ExporterStatistics(Builder builder) {
            this.totalExports = builder.totalExports;
            this.successfulExports = builder.successfulExports;
            this.failedExports = builder.failedExports;
            this.successRate = builder.successRate;
            this.averageExportTime = builder.averageExportTime;
            this.isHealthy = builder.isHealthy;
            this.lastError = builder.lastError;
            this.lastSuccessTime = builder.lastSuccessTime;
            this.lastCheckTime = builder.lastCheckTime;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public long getTotalExports() { return totalExports; }
        public long getSuccessfulExports() { return successfulExports; }
        public long getFailedExports() { return failedExports; }
        public double getSuccessRate() { return successRate; }
        public double getAverageExportTime() { return averageExportTime; }
        public boolean isHealthy() { return isHealthy; }
        public String getLastError() { return lastError; }
        public Instant getLastSuccessTime() { return lastSuccessTime; }
        public Instant getLastCheckTime() { return lastCheckTime; }
        
        public static class Builder {
            private long totalExports;
            private long successfulExports;
            private long failedExports;
            private double successRate;
            private double averageExportTime;
            private boolean isHealthy;
            private String lastError;
            private Instant lastSuccessTime;
            private Instant lastCheckTime;
            
            public Builder totalExports(long totalExports) {
                this.totalExports = totalExports;
                return this;
            }
            
            public Builder successfulExports(long successfulExports) {
                this.successfulExports = successfulExports;
                return this;
            }
            
            public Builder failedExports(long failedExports) {
                this.failedExports = failedExports;
                return this;
            }
            
            public Builder successRate(double successRate) {
                this.successRate = successRate;
                return this;
            }
            
            public Builder averageExportTime(double averageExportTime) {
                this.averageExportTime = averageExportTime;
                return this;
            }
            
            public Builder isHealthy(boolean isHealthy) {
                this.isHealthy = isHealthy;
                return this;
            }
            
            public Builder lastError(String lastError) {
                this.lastError = lastError;
                return this;
            }
            
            public Builder lastSuccessTime(Instant lastSuccessTime) {
                this.lastSuccessTime = lastSuccessTime;
                return this;
            }
            
            public Builder lastCheckTime(Instant lastCheckTime) {
                this.lastCheckTime = lastCheckTime;
                return this;
            }
            
            public ExporterStatistics build() {
                return new ExporterStatistics(this);
            }
        }
    }
    
    /**
     * 测试用的SpanData实现
     */
    private static class TestSpanData implements SpanData {
        // 这里需要实现SpanData接口的所有方法
        // 为了简化，这里只提供一个空的实现
        // 实际使用中可能需要更完整的实现
        
        @Override
        public String getName() {
            return "health-check";
        }
        
        @Override
        public SpanKind getKind() {
            return SpanKind.INTERNAL;
        }
        
        @Override
        public io.opentelemetry.api.trace.SpanContext getSpanContext() {
            return io.opentelemetry.api.trace.SpanContext.getInvalid();
        }
        
        @Override
        public io.opentelemetry.api.trace.SpanContext getParentSpanContext() {
            return io.opentelemetry.api.trace.SpanContext.getInvalid();
        }
        
        @Override
        public io.opentelemetry.sdk.trace.data.StatusData getStatus() {
            return io.opentelemetry.sdk.trace.data.StatusData.ok();
        }
        
        @Override
        public long getStartEpochNanos() {
            return System.nanoTime();
        }
        
        @Override
        public Attributes getAttributes() {
            return Attributes.empty();
        }
        
        @Override
        public java.util.List<io.opentelemetry.sdk.trace.data.EventData> getEvents() {
            return Collections.emptyList();
        }
        
        @Override
        public java.util.List<io.opentelemetry.sdk.trace.data.LinkData> getLinks() {
            return Collections.emptyList();
        }
        
        @Override
        public long getEndEpochNanos() {
            return System.nanoTime();
        }
        
        @Override
        public boolean hasEnded() {
            return true;
        }
        
        @Override
        public int getTotalRecordedEvents() {
            return 0;
        }
        
        @Override
        public int getTotalRecordedLinks() {
            return 0;
        }
        
        @Override
        public int getTotalAttributeCount() {
            return 0;
        }
        
        @Override
        public io.opentelemetry.sdk.resources.Resource getResource() {
            return io.opentelemetry.sdk.resources.Resource.getDefault();
        }
        
        @Override
        public io.opentelemetry.sdk.common.InstrumentationScopeInfo getInstrumentationScopeInfo() {
            return io.opentelemetry.sdk.common.InstrumentationScopeInfo.empty();
        }
    }
}
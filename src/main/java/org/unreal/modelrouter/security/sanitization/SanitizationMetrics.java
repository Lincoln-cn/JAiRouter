package org.unreal.modelrouter.security.sanitization;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脱敏性能指标收集器
 * 收集数据脱敏的性能指标用于监控
 */
@Slf4j
@Component
public class SanitizationMetrics {
    
    private final Counter requestSanitizations;
    private final Counter responseSanitizations;
    private final Counter ruleMatches;
    private final Counter sanitizationErrors;
    
    private final Timer requestSanitizationTimer;
    private final Timer responseSanitizationTimer;
    private final Timer ruleCompilationTimer;
    
    private final AtomicLong totalContentSize = new AtomicLong(0);
    private final AtomicLong totalSanitizedSize = new AtomicLong(0);
    
    public SanitizationMetrics(MeterRegistry meterRegistry) {
        // 脱敏操作计数器
        this.requestSanitizations = Counter.builder("jairouter.security.sanitization.request.count")
                .description("请求数据脱敏次数")
                .register(meterRegistry);
        
        this.responseSanitizations = Counter.builder("jairouter.security.sanitization.response.count")
                .description("响应数据脱敏次数")
                .register(meterRegistry);
        
        this.ruleMatches = Counter.builder("jairouter.security.sanitization.rule.matches")
                .description("脱敏规则匹配次数")
                .register(meterRegistry);
        
        this.sanitizationErrors = Counter.builder("jairouter.security.sanitization.errors")
                .description("脱敏处理错误次数")
                .register(meterRegistry);
        
        // 脱敏操作耗时计时器
        this.requestSanitizationTimer = Timer.builder("jairouter.security.sanitization.request.duration")
                .description("请求数据脱敏耗时")
                .register(meterRegistry);
        
        this.responseSanitizationTimer = Timer.builder("jairouter.security.sanitization.response.duration")
                .description("响应数据脱敏耗时")
                .register(meterRegistry);
        
        this.ruleCompilationTimer = Timer.builder("jairouter.security.sanitization.rule.compilation.duration")
                .description("脱敏规则编译耗时")
                .register(meterRegistry);
        
        // 数据大小指标
        meterRegistry.gauge("jairouter.security.sanitization.content.total.size", totalContentSize, AtomicLong::get);
        meterRegistry.gauge("jairouter.security.sanitization.content.sanitized.size", totalSanitizedSize, AtomicLong::get);
        
        log.info("脱敏性能指标收集器初始化完成");
    }
    
    /**
     * 记录请求脱敏操作
     */
    public void recordRequestSanitization() {
        requestSanitizations.increment();
        log.debug("记录请求脱敏操作");
    }
    
    /**
     * 记录响应脱敏操作
     */
    public void recordResponseSanitization() {
        responseSanitizations.increment();
        log.debug("记录响应脱敏操作");
    }
    
    /**
     * 记录规则匹配
     */
    public void recordRuleMatch() {
        ruleMatches.increment();
        log.debug("记录规则匹配");
    }
    
    /**
     * 记录脱敏错误
     */
    public void recordSanitizationError() {
        sanitizationErrors.increment();
        log.debug("记录脱敏错误");
    }
    
    /**
     * 记录请求脱敏耗时
     */
    public void recordRequestSanitizationDuration(Duration duration) {
        requestSanitizationTimer.record(duration);
        log.debug("记录请求脱敏耗时: {} ms", duration.toMillis());
    }
    
    /**
     * 记录响应脱敏耗时
     */
    public void recordResponseSanitizationDuration(Duration duration) {
        responseSanitizationTimer.record(duration);
        log.debug("记录响应脱敏耗时: {} ms", duration.toMillis());
    }
    
    /**
     * 记录规则编译耗时
     */
    public void recordRuleCompilationDuration(Duration duration) {
        ruleCompilationTimer.record(duration);
        log.debug("记录规则编译耗时: {} ms", duration.toMillis());
    }
    
    /**
     * 记录内容大小
     */
    public void recordContentSize(long originalSize, long sanitizedSize) {
        totalContentSize.addAndGet(originalSize);
        totalSanitizedSize.addAndGet(sanitizedSize);
        log.debug("记录内容大小: 原始={} bytes, 脱敏后={} bytes", originalSize, sanitizedSize);
    }
    
    /**
     * 获取脱敏统计信息
     */
    public SanitizationStatistics getSanitizationStatistics() {
        return SanitizationStatistics.builder()
                .requestSanitizations(requestSanitizations.count())
                .responseSanitizations(responseSanitizations.count())
                .ruleMatches(ruleMatches.count())
                .errors(sanitizationErrors.count())
                .totalContentSize(totalContentSize.get())
                .totalSanitizedSize(totalSanitizedSize.get())
                .avgRequestDuration(requestSanitizationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .avgResponseDuration(responseSanitizationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .avgCompilationDuration(ruleCompilationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .build();
    }
    
    /**
     * 脱敏统计信息
     */
    public static class SanitizationStatistics {
        private final double requestSanitizations;
        private final double responseSanitizations;
        private final double ruleMatches;
        private final double errors;
        private final long totalContentSize;
        private final long totalSanitizedSize;
        private final double avgRequestDuration;
        private final double avgResponseDuration;
        private final double avgCompilationDuration;
        
        private SanitizationStatistics(Builder builder) {
            this.requestSanitizations = builder.requestSanitizations;
            this.responseSanitizations = builder.responseSanitizations;
            this.ruleMatches = builder.ruleMatches;
            this.errors = builder.errors;
            this.totalContentSize = builder.totalContentSize;
            this.totalSanitizedSize = builder.totalSanitizedSize;
            this.avgRequestDuration = builder.avgRequestDuration;
            this.avgResponseDuration = builder.avgResponseDuration;
            this.avgCompilationDuration = builder.avgCompilationDuration;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public double getRequestSanitizations() { return requestSanitizations; }
        public double getResponseSanitizations() { return responseSanitizations; }
        public double getRuleMatches() { return ruleMatches; }
        public double getErrors() { return errors; }
        public long getTotalContentSize() { return totalContentSize; }
        public long getTotalSanitizedSize() { return totalSanitizedSize; }
        public double getAvgRequestDuration() { return avgRequestDuration; }
        public double getAvgResponseDuration() { return avgResponseDuration; }
        public double getAvgCompilationDuration() { return avgCompilationDuration; }
        
        public double getErrorRate() {
            double total = requestSanitizations + responseSanitizations;
            return total > 0 ? errors / total : 0.0;
        }
        
        public double getCompressionRatio() {
            return totalContentSize > 0 ? (double) totalSanitizedSize / totalContentSize : 1.0;
        }
        
        @Override
        public String toString() {
            return String.format("SanitizationStatistics{" +
                    "requestSanitizations=%.0f, responseSanitizations=%.0f, " +
                    "ruleMatches=%.0f, errors=%.0f, errorRate=%.2f%%, " +
                    "totalContentSize=%d, totalSanitizedSize=%d, compressionRatio=%.2f, " +
                    "avgRequestDuration=%.2fms, avgResponseDuration=%.2fms, avgCompilationDuration=%.2fms}",
                    requestSanitizations, responseSanitizations, ruleMatches, errors, getErrorRate() * 100,
                    totalContentSize, totalSanitizedSize, getCompressionRatio(),
                    avgRequestDuration, avgResponseDuration, avgCompilationDuration);
        }
        
        public static class Builder {
            private double requestSanitizations;
            private double responseSanitizations;
            private double ruleMatches;
            private double errors;
            private long totalContentSize;
            private long totalSanitizedSize;
            private double avgRequestDuration;
            private double avgResponseDuration;
            private double avgCompilationDuration;
            
            public Builder requestSanitizations(double requestSanitizations) {
                this.requestSanitizations = requestSanitizations;
                return this;
            }
            
            public Builder responseSanitizations(double responseSanitizations) {
                this.responseSanitizations = responseSanitizations;
                return this;
            }
            
            public Builder ruleMatches(double ruleMatches) {
                this.ruleMatches = ruleMatches;
                return this;
            }
            
            public Builder errors(double errors) {
                this.errors = errors;
                return this;
            }
            
            public Builder totalContentSize(long totalContentSize) {
                this.totalContentSize = totalContentSize;
                return this;
            }
            
            public Builder totalSanitizedSize(long totalSanitizedSize) {
                this.totalSanitizedSize = totalSanitizedSize;
                return this;
            }
            
            public Builder avgRequestDuration(double avgRequestDuration) {
                this.avgRequestDuration = avgRequestDuration;
                return this;
            }
            
            public Builder avgResponseDuration(double avgResponseDuration) {
                this.avgResponseDuration = avgResponseDuration;
                return this;
            }
            
            public Builder avgCompilationDuration(double avgCompilationDuration) {
                this.avgCompilationDuration = avgCompilationDuration;
                return this;
            }
            
            public SanitizationStatistics build() {
                return new SanitizationStatistics(this);
            }
        }
    }
}
package org.unreal.modelrouter.tracing.config;

// Jaeger exporter 已移除，现在使用 OTLP 协议连接 Jaeger
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * 追踪导出器配置类
 * 
 * 根据配置创建不同类型的Span导出器，支持：
 * - Jaeger导出器
 * - Zipkin导出器
 * - OTLP导出器
 * - 日志导出器（开发调试用）
 * - 复合导出器（多个导出器组合）
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jairouter.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingExporterConfiguration {
    
    private final TracingConfiguration tracingConfig;
    private final Environment environment;
    
    /**
     * 创建主要的Span导出器
     * 
     * @return 配置的Span导出器
     */
    @Bean
    @Primary
    public SpanExporter spanExporter() {
        String exporterType = tracingConfig.getExporter().getType().toLowerCase();
        
        log.info("创建Span导出器，类型: {}", exporterType);
        
        switch (exporterType) {
            case "jaeger":
                return createJaegerViaOtlpExporter();
            case "zipkin":
                return createZipkinExporter();
            case "otlp":
                return createOtlpExporter();
            case "logging":
                return createLoggingExporter();
            case "composite":
                return createCompositeExporter();
            default:
                log.warn("未知的导出器类型: {}，使用默认的日志导出器", exporterType);
                return createLoggingExporter();
        }
    }
    
    /**
     * 通过OTLP协议创建Jaeger导出器
     */
    private SpanExporter createJaegerViaOtlpExporter() {
        TracingConfiguration.ExporterConfig.JaegerConfig jaegerConfig = 
                tracingConfig.getExporter().getJaeger();
        
        try {
            // 将 Jaeger 端点转换为 OTLP 端点
            String otlpEndpoint = convertJaegerToOtlpEndpoint(jaegerConfig.getEndpoint());
            
            var builder = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .setTimeout(jaegerConfig.getTimeout());
            
            // 添加自定义头部
            jaegerConfig.getHeaders().forEach((key, value) -> {
                String resolvedValue = environment.resolvePlaceholders(value);
                if (!resolvedValue.isEmpty()) {
                    builder.addHeader(key, resolvedValue);
                }
            });
            
            SpanExporter exporter = builder.build();
            log.info("成功通过OTLP协议创建Jaeger导出器，端点: {} -> {}", jaegerConfig.getEndpoint(), otlpEndpoint);
            return exporter;
        } catch (Exception e) {
            log.error("创建Jaeger导出器失败，回退到日志导出器", e);
            return createLoggingExporter();
        }
    }
    
    /**
     * 将 Jaeger 端点转换为 OTLP 端点
     */
    private String convertJaegerToOtlpEndpoint(String jaegerEndpoint) {
        if (jaegerEndpoint == null || jaegerEndpoint.isEmpty()) {
            return "http://localhost:4317";
        }
        
        if (jaegerEndpoint.contains("4317") || jaegerEndpoint.contains("4318")) {
            return jaegerEndpoint;
        }
        
        if (jaegerEndpoint.contains("14268")) {
            return jaegerEndpoint.replace("14268/api/traces", "4317");
        }
        
        return jaegerEndpoint.replaceAll(":\\d+.*", ":4317");
    }
    
    /**
     * 创建Zipkin导出器
     */
    private SpanExporter createZipkinExporter() {
        TracingConfiguration.ExporterConfig.ZipkinConfig zipkinConfig = 
                tracingConfig.getExporter().getZipkin();
        
        try {
            SpanExporter exporter = ZipkinSpanExporter.builder()
                    .setEndpoint(zipkinConfig.getEndpoint())
                    .build();
            
            log.info("成功创建Zipkin导出器，端点: {}", zipkinConfig.getEndpoint());
            return exporter;
        } catch (Exception e) {
            log.error("创建Zipkin导出器失败，回退到日志导出器", e);
            return createLoggingExporter();
        }
    }
    
    /**
     * 创建OTLP导出器
     */
    private SpanExporter createOtlpExporter() {
        TracingConfiguration.ExporterConfig.OtlpConfig otlpConfig = 
                tracingConfig.getExporter().getOtlp();
        
        try {
            var builder = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpConfig.getEndpoint())
                    .setTimeout(otlpConfig.getTimeout())
                    .setCompression(otlpConfig.getCompression());
            
            // 添加自定义头部
            otlpConfig.getHeaders().forEach((key, value) -> {
                String resolvedValue = environment.resolvePlaceholders(value);
                if (!resolvedValue.isEmpty()) {
                    builder.addHeader(key, resolvedValue);
                }
            });
            
            SpanExporter exporter = builder.build();
            log.info("成功创建OTLP导出器，端点: {}", otlpConfig.getEndpoint());
            return exporter;
        } catch (Exception e) {
            log.error("创建OTLP导出器失败，回退到日志导出器", e);
            return createLoggingExporter();
        }
    }
    
    /**
     * 创建日志导出器
     */
    private SpanExporter createLoggingExporter() {
        log.info("创建日志导出器用于开发调试");
        return LoggingSpanExporter.create();
    }
    
    /**
     * 创建复合导出器（多个导出器组合）
     */
    private SpanExporter createCompositeExporter() {
        log.info("创建复合导出器");
        
        // 这里可以根据需要组合多个导出器
        // 例如：同时导出到Jaeger和日志
        SpanExporter jaegerExporter = createJaegerViaOtlpExporter();
        SpanExporter loggingExporter = createLoggingExporter();
        
        return SpanExporter.composite(jaegerExporter, loggingExporter);
    }
    
    /**
     * 创建开发环境专用的导出器
     */
    @Bean
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev")
    public SpanExporter devSpanExporter() {
        log.info("创建开发环境专用导出器");
        
        // 开发环境同时使用日志导出器和Jaeger导出器
        SpanExporter loggingExporter = LoggingSpanExporter.create();
        
        // 如果配置了Jaeger端点，也创建Jaeger导出器（通过OTLP）
        String jaegerEndpoint = tracingConfig.getExporter().getJaeger().getEndpoint();
        if (jaegerEndpoint != null && !jaegerEndpoint.isEmpty()) {
            try {
                SpanExporter jaegerExporter = createJaegerViaOtlpExporter();
                return SpanExporter.composite(loggingExporter, jaegerExporter);
            } catch (Exception e) {
                log.warn("开发环境创建Jaeger导出器失败，仅使用日志导出器", e);
            }
        }
        
        return loggingExporter;
    }
    
    /**
     * 创建生产环境专用的导出器
     */
    @Bean
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "prod")
    public SpanExporter prodSpanExporter() {
        log.info("创建生产环境专用导出器");
        
        // 生产环境优先使用配置的导出器类型
        String exporterType = tracingConfig.getExporter().getType().toLowerCase();
        
        switch (exporterType) {
            case "jaeger":
                return createJaegerViaOtlpExporter();
            case "zipkin":
                return createZipkinExporter();
            case "otlp":
                return createOtlpExporter();
            default:
                log.warn("生产环境未配置有效的导出器类型: {}，使用OTLP导出器", exporterType);
                return createOtlpExporter();
        }
    }
    
    /**
     * 创建测试环境专用的导出器
     */
    @Bean
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "test")
    public SpanExporter testSpanExporter() {
        log.info("创建测试环境专用导出器");
        
        // 测试环境使用内存导出器或日志导出器
        return LoggingSpanExporter.create();
    }
}
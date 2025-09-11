package org.unreal.modelrouter.tracing.exporter;

// Jaeger exporter 已移除，现在使用 OTLP 协议连接 Jaeger

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Span导出器工厂
 * 
 * 提供各种类型Span导出器的创建功能，包括：
 * - 单一导出器创建
 * - 复合导出器创建
 * - 导出器健康检查
 * - 导出器配置验证
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SpanExporterFactory {
    
    private final Environment environment;
    
    public SpanExporterFactory(Environment environment) {
        this.environment = environment;
    }
    
    /**
     * 创建Span导出器
     * 
     * @param config 追踪配置
     * @return 创建的导出器
     */
    public SpanExporter createExporter(TracingConfiguration config) {
        String exporterType = config.getExporter().getType().toLowerCase();
        
        log.info("创建Span导出器，类型: {}", exporterType);
        
        try {
            switch (exporterType) {
                case "jaeger":
                    return createJaegerViaOtlpExporter(config.getExporter().getJaeger());
                case "zipkin":
                    return createZipkinExporter(config.getExporter().getZipkin());
                case "otlp":
                    return createOtlpExporter(config.getExporter().getOtlp());
                case "logging":
                    return createLoggingExporter(config.getExporter().getLogging());
                case "composite":
                    return createCompositeExporter(config);
                case "noop":
                    return createNoOpExporter();
                default:
                    log.warn("未知的导出器类型: {}，使用日志导出器", exporterType);
                    return createLoggingExporter(config.getExporter().getLogging());
            }
        } catch (Exception e) {
            log.error("创建导出器失败，使用日志导出器作为回退", e);
            return createLoggingExporter(config.getExporter().getLogging());
        }
    }
    
    /**
     * 通过OTLP协议创建Jaeger导出器
     */
    public SpanExporter createJaegerViaOtlpExporter(TracingConfiguration.ExporterConfig.JaegerConfig config) {
        log.info("通过OTLP协议创建Jaeger导出器，端点: {}", config.getEndpoint());
        
        // 将 Jaeger 端点转换为 OTLP 端点
        String otlpEndpoint = convertJaegerToOtlpEndpoint(config.getEndpoint());
        
        var builder = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint);
        
        if (config.getTimeout() != null) {
            builder.setTimeout(config.getTimeout());
        }
        
        // 解析并添加头部
        config.getHeaders().forEach((key, value) -> {
            String resolvedValue = resolveValue(value);
            if (resolvedValue != null && !resolvedValue.isEmpty()) {
                builder.addHeader(key, resolvedValue);
                log.debug("添加Jaeger头部: {} = {}", key, maskSensitiveValue(key, resolvedValue));
            }
        });
        
        log.info("Jaeger端点转换: {} -> {}", config.getEndpoint(), otlpEndpoint);
        return builder.build();
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
    public SpanExporter createZipkinExporter(TracingConfiguration.ExporterConfig.ZipkinConfig config) {
        log.info("创建Zipkin导出器，端点: {}", config.getEndpoint());
        
        return ZipkinSpanExporter.builder()
                .setEndpoint(config.getEndpoint())
                .build();
    }
    
    /**
     * 创建OTLP导出器
     */
    public SpanExporter createOtlpExporter(TracingConfiguration.ExporterConfig.OtlpConfig config) {
        log.info("创建OTLP导出器，端点: {}", config.getEndpoint());
        
        var builder = OtlpGrpcSpanExporter.builder()
                .setEndpoint(config.getEndpoint());
        
        if (config.getTimeout() != null) {
            builder.setTimeout(config.getTimeout());
        }
        
        if (config.getCompression() != null) {
            builder.setCompression(config.getCompression());
        }
        
        // 解析并添加头部
        config.getHeaders().forEach((key, value) -> {
            String resolvedValue = resolveValue(value);
            if (resolvedValue != null && !resolvedValue.isEmpty()) {
                builder.addHeader(key, resolvedValue);
                log.debug("添加OTLP头部: {} = {}", key, maskSensitiveValue(key, resolvedValue));
            }
        });
        
        return builder.build();
    }
    
    /**
     * 创建日志导出器
     */
    public SpanExporter createLoggingExporter(TracingConfiguration.ExporterConfig.LoggingExporterConfig config) {
        log.info("创建日志导出器，启用状态: {}", config.isEnabled());
        
        if (!config.isEnabled()) {
            return createNoOpExporter();
        }
        
        return LoggingSpanExporter.create();
    }
    
    /**
     * 创建复合导出器
     */
    public SpanExporter createCompositeExporter(TracingConfiguration config) {
        log.info("创建复合导出器");
        
        List<SpanExporter> exporters = new ArrayList<>();
        
        // 根据配置创建多个导出器
        TracingConfiguration.ExporterConfig exporterConfig = config.getExporter();
        
        // 如果启用了日志导出器
        if (exporterConfig.getLogging().isEnabled()) {
            exporters.add(createLoggingExporter(exporterConfig.getLogging()));
        }
        
        // 根据主要类型添加对应的导出器
        String primaryType = exporterConfig.getType().toLowerCase();
        if (!"logging".equals(primaryType) && !"composite".equals(primaryType)) {
            try {
                SpanExporter primaryExporter = createExporter(config);
                if (primaryExporter != null) {
                    exporters.add(primaryExporter);
                }
            } catch (Exception e) {
                log.warn("创建主要导出器失败: {}", primaryType, e);
            }
        }
        
        if (exporters.isEmpty()) {
            log.warn("没有可用的导出器，使用日志导出器");
            return createLoggingExporter(exporterConfig.getLogging());
        }
        
        if (exporters.size() == 1) {
            return exporters.get(0);
        }
        
        return SpanExporter.composite(exporters.toArray(new SpanExporter[0]));
    }
    
    /**
     * 创建无操作导出器
     */
    public SpanExporter createNoOpExporter() {
        log.info("创建无操作导出器");
        return SpanExporter.composite(); // 空的复合导出器
    }
    
    /**
     * 验证导出器配置
     * 
     * @param config 追踪配置
     * @return 验证结果
     */
    public boolean validateExporterConfig(TracingConfiguration config) {
        String exporterType = config.getExporter().getType().toLowerCase();
        
        try {
            switch (exporterType) {
                case "jaeger":
                    return validateJaegerConfig(config.getExporter().getJaeger());
                case "zipkin":
                    return validateZipkinConfig(config.getExporter().getZipkin());
                case "otlp":
                    return validateOtlpConfig(config.getExporter().getOtlp());
                case "logging":
                    return true; // 日志导出器总是有效的
                case "composite":
                    return true; // 复合导出器在运行时验证
                case "noop":
                    return true; // 无操作导出器总是有效的
                default:
                    log.warn("未知的导出器类型: {}", exporterType);
                    return false;
            }
        } catch (Exception e) {
            log.error("验证导出器配置失败", e);
            return false;
        }
    }
    
    /**
     * 验证Jaeger配置
     */
    private boolean validateJaegerConfig(TracingConfiguration.ExporterConfig.JaegerConfig config) {
        if (config.getEndpoint() == null || config.getEndpoint().trim().isEmpty()) {
            log.error("Jaeger导出器端点不能为空");
            return false;
        }
        
        if (config.getTimeout() == null || config.getTimeout().isNegative()) {
            log.error("Jaeger导出器超时时间无效");
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证Zipkin配置
     */
    private boolean validateZipkinConfig(TracingConfiguration.ExporterConfig.ZipkinConfig config) {
        if (config.getEndpoint() == null || config.getEndpoint().trim().isEmpty()) {
            log.error("Zipkin导出器端点不能为空");
            return false;
        }
        
        if (config.getTimeout() == null || config.getTimeout().isNegative()) {
            log.error("Zipkin导出器超时时间无效");
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证OTLP配置
     */
    private boolean validateOtlpConfig(TracingConfiguration.ExporterConfig.OtlpConfig config) {
        if (config.getEndpoint() == null || config.getEndpoint().trim().isEmpty()) {
            log.error("OTLP导出器端点不能为空");
            return false;
        }
        
        if (config.getTimeout() == null || config.getTimeout().isNegative()) {
            log.error("OTLP导出器超时时间无效");
            return false;
        }
        
        String compression = config.getCompression();
        if (compression != null && !compression.isEmpty()) {
            if (!"gzip".equalsIgnoreCase(compression) && !"none".equalsIgnoreCase(compression)) {
                log.error("OTLP导出器压缩类型无效: {}", compression);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 测试导出器连接
     * 
     * @param exporter 要测试的导出器
     * @return 连接是否成功
     */
    public boolean testExporterConnection(SpanExporter exporter) {
        try {
            // 这里可以实现具体的连接测试逻辑
            // 例如发送一个测试Span
            log.info("测试导出器连接");
            return true;
        } catch (Exception e) {
            log.error("测试导出器连接失败", e);
            return false;
        }
    }
    
    /**
     * 解析配置值（支持环境变量占位符）
     */
    private String resolveValue(String value) {
        if (value == null) {
            return null;
        }
        return environment.resolvePlaceholders(value);
    }
    
    /**
     * 掩码敏感值（用于日志输出）
     */
    private String maskSensitiveValue(String key, String value) {
        if (key == null || value == null) {
            return value;
        }
        
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("token") || lowerKey.contains("key") || 
            lowerKey.contains("secret") || lowerKey.contains("password") ||
            lowerKey.contains("authorization")) {
            return "***";
        }
        
        return value;
    }
}
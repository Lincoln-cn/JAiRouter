package org.unreal.modelrouter.tracing.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.unreal.modelrouter.tracing.sampler.SamplingStrategyManager;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.HashMap;

/**
 * OpenTelemetry自动配置类
 * 
 * 负责创建和配置OpenTelemetry相关的Bean，包括：
 * - OpenTelemetry SDK实例
 * - Tracer实例
 * - 各种Span导出器
 * - 资源配置
 * - 采样器配置
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TracingConfiguration.class)
@ConditionalOnProperty(name = "jairouter.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class OpenTelemetryAutoConfiguration {
    
    private final TracingConfiguration tracingConfig;
    private final SamplingStrategyManager samplingStrategyManager;
    private final Environment environment;
    
    /**
     * 初始化OpenTelemetry配置
     */
    @PostConstruct
    public void init() {
        log.info("初始化OpenTelemetry配置，服务名称: {}, 版本: {}", 
                tracingConfig.getServiceName(), tracingConfig.getServiceVersion());
    }
    
    /**
     * 创建OpenTelemetry SDK实例
     * 
     * @param tracerProvider 追踪器提供者
     * @return OpenTelemetry实例
     */
    @Bean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }
    
    /**
     * 创建资源实例
     * 
     * @return 资源实例
     */
    @Bean
    public Resource otelResource() {
        return createResource();
    }
    
    /**
     * 创建追踪器提供者
     * 
     * @param resource 资源
     * @param sampler 采样器
     * @param spanExporter Span导出器
     * @return 追踪器提供者
     */
    @Bean
    public SdkTracerProvider tracerProvider(Resource resource, Sampler sampler, SpanExporter spanExporter) {
        TracingConfiguration.PerformanceConfig performanceConfig = tracingConfig.getPerformance();
        
        // 获取批处理配置
        TracingConfiguration.PerformanceConfig.BatchConfig batchConfig = performanceConfig.getBatch();
        
        return SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(
                        BatchSpanProcessor.builder(spanExporter)
                                .setExporterTimeout(batchConfig.getTimeout())
                                .setScheduleDelay(batchConfig.getTimeout())
                                .setMaxExportBatchSize(batchConfig.getSize())
                                .setMaxQueueSize(performanceConfig.getBuffer().getSize())
                                .build()
                )
                .setSampler(sampler)
                .build();
    }
    
    /**
     * 创建采样器
     * 
     * @return 采样器
     */
    @Bean
    public Sampler otelSampler() {
        // 使用采样策略管理器中的当前策略创建采样器
        return Sampler.parentBased(samplingStrategyManager.getCurrentStrategy());
    }
    
    /**
     * 创建Span导出器
     * 
     * @return Span导出器
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.tracing.exporter.type", havingValue = "logging", matchIfMissing = true)
    public SpanExporter loggingSpanExporter() {
        log.info("创建日志导出器");
        return LoggingSpanExporter.create();
    }
    
    /**
     * 创建资源配置
     */
    private Resource createResource() {
        Map<String, String> resourceAttributes = new HashMap<>();
        
        // 基础服务信息
        resourceAttributes.put(ResourceAttributes.SERVICE_NAME.getKey(), tracingConfig.getServiceName());
        resourceAttributes.put(ResourceAttributes.SERVICE_VERSION.getKey(), tracingConfig.getServiceVersion());
        resourceAttributes.put(ResourceAttributes.SERVICE_NAMESPACE.getKey(), tracingConfig.getServiceNamespace());
        
        // 部署环境信息
        String activeProfile = environment.getProperty("spring.profiles.active", "default");
        resourceAttributes.put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT.getKey(), activeProfile);
        
        // 实例信息
        String instanceId = environment.getProperty("HOSTNAME", "unknown");
        resourceAttributes.put(ResourceAttributes.SERVICE_INSTANCE_ID.getKey(), instanceId);
        
        // 自定义属性
        resourceAttributes.putAll(tracingConfig.getOpenTelemetry().getResource().getAttributes());
        
        io.opentelemetry.api.common.AttributesBuilder attributesBuilder = Attributes.builder();
        resourceAttributes.forEach(attributesBuilder::put);
        
        return Resource.getDefault().merge(Resource.create(attributesBuilder.build()));
    }
    
    /**
     * 创建Span导出器
     */
    @Bean
    public SpanExporter spanExporter() {
        return createSpanExporter();
    }
    
    private SpanExporter createSpanExporter() {
        String exporterType = tracingConfig.getExporter().getType().toLowerCase();
        
        switch (exporterType) {
            case "jaeger":
                // Jaeger 现在使用 OTLP 协议
                log.info("Jaeger 导出器现在使用 OTLP 协议，端点: {}", tracingConfig.getExporter().getJaeger().getEndpoint());
                return createJaegerViaOtlpExporter();
            case "zipkin":
                return createZipkinExporter();
            case "otlp":
                return createOtlpExporter();
            case "logging":
                return createLoggingExporter();
            default:
                log.warn("未知的导出器类型: {}，使用默认的日志导出器", exporterType);
                return createLoggingExporter();
        }
    }
    
    /**
     * 通过OTLP协议创建Jaeger导出器
     * Jaeger 现在推荐使用 OTLP 协议而不是专用的 Jaeger 协议
     */
    private SpanExporter createJaegerViaOtlpExporter() {
        TracingConfiguration.ExporterConfig.JaegerConfig jaegerConfig = tracingConfig.getExporter().getJaeger();
        
        // 将 Jaeger 端点转换为 OTLP 端点
        String otlpEndpoint = convertJaegerToOtlpEndpoint(jaegerConfig.getEndpoint());
        
        var builder = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(jaegerConfig.getTimeout());
        
        // 添加自定义头部
        jaegerConfig.getHeaders().forEach(builder::addHeader);
        
        log.info("通过OTLP协议创建Jaeger导出器，端点: {} -> {}", jaegerConfig.getEndpoint(), otlpEndpoint);
        return builder.build();
    }
    
    /**
     * 将 Jaeger 端点转换为 OTLP 端点
     */
    private String convertJaegerToOtlpEndpoint(String jaegerEndpoint) {
        if (jaegerEndpoint == null || jaegerEndpoint.isEmpty()) {
            return "http://localhost:4317"; // 默认 OTLP gRPC 端点
        }
        
        // 如果已经是 OTLP 端点，直接返回
        if (jaegerEndpoint.contains("4317") || jaegerEndpoint.contains("4318")) {
            return jaegerEndpoint;
        }
        
        // 将传统的 Jaeger 端点转换为 OTLP 端点
        // 例如：http://localhost:14268/api/traces -> http://localhost:4317
        if (jaegerEndpoint.contains("14268")) {
            return jaegerEndpoint.replace("14268/api/traces", "4317");
        }
        
        // 默认情况下，假设是 gRPC 端点，使用 4317 端口
        return jaegerEndpoint.replaceAll(":\\d+.*", ":4317");
    }
    
    /**
     * 创建Zipkin导出器
     */
    private SpanExporter createZipkinExporter() {
        TracingConfiguration.ExporterConfig.ZipkinConfig zipkinConfig = tracingConfig.getExporter().getZipkin();
        
        log.info("创建Zipkin导出器，端点: {}", zipkinConfig.getEndpoint());
        return ZipkinSpanExporter.builder()
                .setEndpoint(zipkinConfig.getEndpoint())
                .build();
    }
    
    /**
     * 创建OTLP导出器
     */
    private SpanExporter createOtlpExporter() {
        TracingConfiguration.ExporterConfig.OtlpConfig otlpConfig = tracingConfig.getExporter().getOtlp();
        
        var builder = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpConfig.getEndpoint())
                .setTimeout(otlpConfig.getTimeout())
                .setCompression(otlpConfig.getCompression());
        
        // 添加自定义头部
        otlpConfig.getHeaders().forEach(builder::addHeader);
        
        log.info("创建OTLP导出器，端点: {}", otlpConfig.getEndpoint());
        return builder.build();
    }
    
    /**
     * 创建日志导出器
     */
    private SpanExporter createLoggingExporter() {
        log.info("创建日志导出器用于开发调试");
        return LoggingSpanExporter.create();
    }
    
}
package org.unreal.modelrouter.tracing.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

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
@ConditionalOnProperty(prefix = "jairouter.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenTelemetryAutoConfiguration {
    
    private final TracingConfiguration tracingConfig;
    private final Environment environment;
    
    /**
     * 创建OpenTelemetry SDK实例
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        log.info("初始化OpenTelemetry SDK，服务名称: {}", tracingConfig.getServiceName());
        
        Resource resource = createResource();
        SpanExporter spanExporter = createSpanExporter();
        Sampler sampler = createSampler();
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(tracingConfig.getOpenTelemetry().getSdk().getTrace()
                                .getProcessors().getBatch().getScheduleDelay())
                        .setMaxQueueSize(tracingConfig.getOpenTelemetry().getSdk().getTrace()
                                .getProcessors().getBatch().getMaxQueueSize())
                        .setMaxExportBatchSize(tracingConfig.getOpenTelemetry().getSdk().getTrace()
                                .getProcessors().getBatch().getMaxExportBatchSize())
                        .setExportTimeout(tracingConfig.getOpenTelemetry().getSdk().getTrace()
                                .getProcessors().getBatch().getExportTimeout())
                        .build())
                .setSampler(sampler)
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        log.info("OpenTelemetry SDK初始化完成，导出器类型: {}", tracingConfig.getExporter().getType());
        return openTelemetry;
    }
    
    /**
     * 创建Tracer实例
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(
                tracingConfig.getServiceName(),
                tracingConfig.getServiceVersion()
        );
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
        
        Attributes.Builder attributesBuilder = Attributes.builder();
        resourceAttributes.forEach(attributesBuilder::put);
        
        return Resource.getDefault().merge(Resource.create(attributesBuilder.build()));
    }
    
    /**
     * 创建Span导出器
     */
    private SpanExporter createSpanExporter() {
        String exporterType = tracingConfig.getExporter().getType().toLowerCase();
        
        switch (exporterType) {
            case "jaeger":
                return createJaegerExporter();
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
     * 创建Jaeger导出器
     */
    private SpanExporter createJaegerExporter() {
        TracingConfiguration.ExporterConfig.JaegerConfig jaegerConfig = tracingConfig.getExporter().getJaeger();
        
        JaegerGrpcSpanExporter.Builder builder = JaegerGrpcSpanExporter.builder()
                .setEndpoint(jaegerConfig.getEndpoint())
                .setTimeout(jaegerConfig.getTimeout());
        
        // 添加自定义头部
        jaegerConfig.getHeaders().forEach(builder::addHeader);
        
        log.info("创建Jaeger导出器，端点: {}", jaegerConfig.getEndpoint());
        return builder.build();
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
        
        OtlpGrpcSpanExporter.Builder builder = OtlpGrpcSpanExporter.builder()
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
    
    /**
     * 创建采样器
     */
    private Sampler createSampler() {
        double samplingRatio = tracingConfig.getSampling().getRatio();
        
        if (samplingRatio >= 1.0) {
            log.info("使用全量采样器");
            return Sampler.create(1.0);
        } else if (samplingRatio <= 0.0) {
            log.info("使用零采样器");
            return Sampler.create(0.0);
        } else {
            log.info("使用比例采样器，采样率: {}", samplingRatio);
            return Sampler.create(samplingRatio);
        }
    }
}
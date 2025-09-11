package org.unreal.modelrouter.tracing.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.unreal.modelrouter.tracing.sampler.SamplingStrategyManager;

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
     * 创建Tracer实例
     * 
     * @param openTelemetry OpenTelemetry实例
     * @return Tracer实例
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("jairouter");
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
}
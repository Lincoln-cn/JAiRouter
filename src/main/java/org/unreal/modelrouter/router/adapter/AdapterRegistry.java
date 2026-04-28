package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.router.adapter.impl.*;
import org.unreal.modelrouter.common.model.ModelRouterProperties;
import org.unreal.modelrouter.common.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.handler.ResponseHandler;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Configuration
public class AdapterRegistry {

    private final Map<String, ServiceCapability> adapters;
    private final ModelRouterProperties properties;
    private final ModelServiceRegistry registry;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;
    private final ModelCallStatsRepository statsRepository;
    private final RequestBuilder requestBuilder;
    private final ResponseHandler responseHandler;
    private final InstanceSelector instanceSelector;
    private final ResponseTransformer responseTransformer;
    private final CapabilityChecker capabilityChecker;
    private final AdapterErrorHandler errorHandler;
    private final RetryPolicy retryPolicy;
    private final HttpRequestProcessor httpRequestProcessor;
    private final ResponseMapper responseMapper;
    private final AdapterMetricsRecorder metricsRecorder;
    private final AdapterTracingManager tracingManager;

    public AdapterRegistry(final ModelRouterProperties properties,
                           final ModelServiceRegistry registry,
                           final MetricsCollector metricsCollector,
                           final ObjectMapper objectMapper,
                           final ModelCallStatsRepository statsRepository,
                           final RequestBuilder requestBuilder,
                           final ResponseHandler responseHandler,
                           final InstanceSelector instanceSelector,
                           final ResponseTransformer responseTransformer,
                           final CapabilityChecker capabilityChecker,
                           final HttpRequestProcessor httpRequestProcessor,
                           final ResponseMapper responseMapper,
                           final AdapterErrorHandler errorHandler,
                           final RetryPolicy retryPolicy,
                           final AdapterMetricsRecorder metricsRecorder,
                           final AdapterTracingManager tracingManager) {
        this.properties = properties;
        this.registry = registry;
        this.metricsCollector = metricsCollector;
        this.objectMapper = objectMapper;
        this.statsRepository = statsRepository;
        this.requestBuilder = requestBuilder;
        this.responseHandler = responseHandler;
        this.instanceSelector = instanceSelector;
        this.responseTransformer = responseTransformer;
        this.capabilityChecker = capabilityChecker;
        this.errorHandler = errorHandler;
        this.retryPolicy = retryPolicy;
        this.httpRequestProcessor = httpRequestProcessor;
        this.responseMapper = responseMapper;
        this.metricsRecorder = metricsRecorder;
        this.tracingManager = tracingManager;
        this.adapters = new HashMap<>();
        initializeAdapters();
    }

    private void initializeAdapters() {
        // 注册各种adapter实现，传入MetricsCollector
        adapters.put("normal", new NormalOpenAiAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder, tracingManager));
        adapters.put("gpustack", new GpuStackAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder, tracingManager));
        adapters.put("ollama", new OllamaAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder, tracingManager));
        adapters.put("vllm", new VllmAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder, tracingManager));
        adapters.put("xinference", new XinferenceAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder, tracingManager));
        adapters.put("localai", new LocalAiAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder, tracingManager));
    }

    /**
     * 根据服务类型获取对应的Adapter
     */
    public ServiceCapability getAdapter(final ModelServiceRegistry.ServiceType serviceType) {
        String adapterName = getAdapterName(serviceType);
        ServiceCapability adapter = adapters.get(adapterName.toLowerCase());

        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported adapter: " + adapterName);
        }

        return adapter;
    }

    /**
     * 根据实例获取对应的Adapter（实例级适配器优先）
     */
    public ServiceCapability getAdapter(final ModelServiceRegistry.ServiceType serviceType, 
                                       final ModelRouterProperties.ModelInstance instance) {
        String adapterName = getAdapterName(serviceType, instance);
        ServiceCapability adapter = adapters.get(adapterName.toLowerCase());

        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported adapter: " + adapterName);
        }

        return adapter;
    }

    /**
     * 获取指定服务类型的adapter名称
     */
    private String getAdapterName(final ModelServiceRegistry.ServiceType serviceType) {
        // 优先使用服务级配置
        String adapterName = registry.getServiceAdapter(serviceType);

        // 回退到全局配置
        if (adapterName == null) {
            adapterName = Optional.ofNullable(properties.getAdapter())
                    .orElse("normal");
        }

        return adapterName;
    }

    /**
     * 获取指定实例的adapter名称（实例级适配器优先）
     */
    private String getAdapterName(final ModelServiceRegistry.ServiceType serviceType, 
                                 final ModelRouterProperties.ModelInstance instance) {
        // 1. 优先使用实例级配置
        if (instance != null && instance.getAdapter() != null && !instance.getAdapter().trim().isEmpty()) {
            return instance.getAdapter();
        }

        // 2. 回退到服务级配置
        String adapterName = registry.getServiceAdapter(serviceType);
        if (adapterName != null && !adapterName.trim().isEmpty()) {
            return adapterName;
        }

        // 3. 最后回退到全局配置
        return Optional.ofNullable(properties.getAdapter())
                .orElse("normal");
    }

    /**
     * 检查adapter是否支持指定的服务类型
     */
    public boolean isAdapterSupported(final String adapterName) {
        return adapters.containsKey(adapterName.toLowerCase());
    }

    /**
     * 获取所有可用的adapter
     */
    public Map<String, ServiceCapability> getAllAdapters() {
        return new HashMap<>(adapters);
    }
}

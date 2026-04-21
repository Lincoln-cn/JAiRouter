package org.unreal.modelrouter.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.adapter.impl.*;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.adapter.handler.ResponseHandler;


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


    public AdapterRegistry(final ModelRouterProperties properties, 
                           final ModelServiceRegistry registry, 
                           final MetricsCollector metricsCollector,
                           final ObjectMapper objectMapper,
                           final ModelCallStatsRepository statsRepository,
                           final RequestBuilder requestBuilder,
                           final ResponseHandler responseHandler) {
        this.properties = properties;
        this.registry = registry;
        this.metricsCollector = metricsCollector;
        this.objectMapper = objectMapper;
        this.statsRepository = statsRepository;
        this.requestBuilder = requestBuilder;
        this.responseHandler = responseHandler;
        this.adapters = new HashMap<>();
        initializeAdapters();
    }

    private void initializeAdapters() {
        // 注册各种adapter实现，传入MetricsCollector
        adapters.put("normal", new NormalOpenAiAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler));
        adapters.put("gpustack", new GpuStackAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler));
        adapters.put("ollama", new OllamaAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler));
        adapters.put("vllm", new VllmAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler));
        adapters.put("xinference", new XinferenceAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler));
        adapters.put("localai", new LocalAiAdapter(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler));
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

package org.unreal.modelrouter.adapter;

import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.adapter.impl.*;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.MetricsCollector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Configuration
public class AdapterRegistry {

    private final Map<String, ServiceCapability> adapters;
    private final ModelRouterProperties properties;
    private final ModelServiceRegistry registry;
    private final MetricsCollector metricsCollector;

    public AdapterRegistry(final ModelRouterProperties properties, final ModelServiceRegistry registry, final MetricsCollector metricsCollector) {
        this.properties = properties;
        this.registry = registry;
        this.metricsCollector = metricsCollector;
        this.adapters = new HashMap<>();
        initializeAdapters();
    }

    private void initializeAdapters() {
        // 注册各种adapter实现，传入MetricsCollector
        adapters.put("normal", new NormalOpenAiAdapter(registry, metricsCollector));
        adapters.put("gpustack", new GpuStackAdapter(registry, metricsCollector));
        adapters.put("ollama", new OllamaAdapter(registry, metricsCollector));
        adapters.put("vllm", new VllmAdapter(registry, metricsCollector));
        adapters.put("xinference", new XinferenceAdapter(registry, metricsCollector));
        adapters.put("localai", new LocalAiAdapter(registry, metricsCollector));
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

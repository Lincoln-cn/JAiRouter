package org.unreal.modelrouter.router.adapter;

import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.router.adapter.impl.ClaudeAdapter;
import org.unreal.modelrouter.router.adapter.impl.GpuStackAdapter;
import org.unreal.modelrouter.router.adapter.impl.LocalAiAdapter;
import org.unreal.modelrouter.router.adapter.impl.NormalOpenAiAdapter;
import org.unreal.modelrouter.router.adapter.impl.OllamaAdapter;
import org.unreal.modelrouter.router.adapter.impl.VllmAdapter;
import org.unreal.modelrouter.router.adapter.impl.XinferenceAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AdapterRegistry - v2.28.0 重构版
 * 使用聚合组件简化构造函数和依赖注入。
 */
@Configuration
public class AdapterRegistry {

    private final Map<String, ServiceCapability> adapters;
    private final ModelRouterProperties properties;
    private final ModelServiceRegistry registry;
    private final AdapterContext context;
    private final RequestProcessingSupport requestSupport;
    private final ResilienceSupport resilienceSupport;
    private final OpenAiRequestTransformer openAiRequestTransformer;
    private final OpenAiResponseTransformer openAiResponseTransformer;

    public AdapterRegistry(final ModelRouterProperties properties,
                           final ModelServiceRegistry registry,
                           final AdapterContext context,
                           final RequestProcessingSupport requestSupport,
                           final ResilienceSupport resilienceSupport,
                           final OpenAiRequestTransformer openAiRequestTransformer,
                           final OpenAiResponseTransformer openAiResponseTransformer) {
        this.properties = properties;
        this.registry = registry;
        this.context = context;
        this.requestSupport = requestSupport;
        this.resilienceSupport = resilienceSupport;
        this.openAiRequestTransformer = openAiRequestTransformer;
        this.openAiResponseTransformer = openAiResponseTransformer;
        this.adapters = new HashMap<>();
        initializeAdapters();
    }

    private void initializeAdapters() {
        adapters.put("normal", new NormalOpenAiAdapter(context, requestSupport, resilienceSupport,
                openAiRequestTransformer, openAiResponseTransformer));
        adapters.put("claude", new ClaudeAdapter(context, requestSupport, resilienceSupport,
                openAiRequestTransformer, openAiResponseTransformer));
        adapters.put("gpustack", new GpuStackAdapter(context, requestSupport, resilienceSupport));
        adapters.put("ollama", new OllamaAdapter(context, requestSupport, resilienceSupport));
        adapters.put("vllm", new VllmAdapter(context, requestSupport, resilienceSupport));
        adapters.put("xinference", new XinferenceAdapter(context, requestSupport, resilienceSupport));
        adapters.put("localai", new LocalAiAdapter(context, requestSupport, resilienceSupport));
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
        String adapterName = registry.getServiceAdapter(serviceType);

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
        if (instance != null && instance.getAdapter() != null && !instance.getAdapter().trim().isEmpty()) {
            return instance.getAdapter();
        }

        String adapterName = registry.getServiceAdapter(serviceType);
        if (adapterName != null && !adapterName.trim().isEmpty()) {
            return adapterName;
        }

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

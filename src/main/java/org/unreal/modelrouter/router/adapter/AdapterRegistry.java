package org.unreal.modelrouter.router.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.impl.ClaudeAdapter;
import org.unreal.modelrouter.router.adapter.impl.ConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.impl.OllamaConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.impl.GeminiAdapter;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AdapterRegistry - v2.28.0 重构版
 * 使用聚合组件简化构造函数和依赖注入。
 */
@Configuration
public class AdapterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AdapterRegistry.class);

    private static final Set<String> BUILTIN_ADAPTER_NAMES = new HashSet<>();

    private final Map<String, ServiceCapability> adapters;
    private final ModelRouterProperties properties;
    private final ModelServiceRegistry registry;
    private final AdapterContext context;
    private final RequestProcessingSupport requestSupport;
    private final ResilienceSupport resilienceSupport;
    private final OpenAiRequestTransformer openAiRequestTransformer;
    private final OpenAiResponseTransformer openAiResponseTransformer;
    private final AdapterDefinitionProperties adapterDefinitionProperties;

    public AdapterRegistry(final ModelRouterProperties properties,
                           final ModelServiceRegistry registry,
                           final AdapterContext context,
                           final RequestProcessingSupport requestSupport,
                           final ResilienceSupport resilienceSupport,
                           final OpenAiRequestTransformer openAiRequestTransformer,
                           final OpenAiResponseTransformer openAiResponseTransformer,
                           final AdapterDefinitionProperties adapterDefinitionProperties) {
        this.properties = properties;
        this.registry = registry;
        this.context = context;
        this.requestSupport = requestSupport;
        this.resilienceSupport = resilienceSupport;
        this.openAiRequestTransformer = openAiRequestTransformer;
        this.openAiResponseTransformer = openAiResponseTransformer;
        this.adapterDefinitionProperties = adapterDefinitionProperties;
        this.adapters = new HashMap<>();
        initializeAdapters();
    }

    private void initializeAdapters() {
        adapters.put("normal", new NormalOpenAiAdapter(context, requestSupport, resilienceSupport,
                openAiRequestTransformer, openAiResponseTransformer));
        adapters.put("claude", new ClaudeAdapter(context, requestSupport, resilienceSupport,
                openAiRequestTransformer, openAiResponseTransformer));
        adapters.put("gemini", new GeminiAdapter(context, requestSupport, resilienceSupport,
                openAiRequestTransformer, openAiResponseTransformer));
        adapters.put("gpustack", new GpuStackAdapter(context, requestSupport, resilienceSupport));
        adapters.put("ollama", new OllamaAdapter(context, requestSupport, resilienceSupport));
        adapters.put("vllm", new VllmAdapter(context, requestSupport, resilienceSupport));
        adapters.put("xinference", new XinferenceAdapter(context, requestSupport, resilienceSupport));
        adapters.put("localai", new LocalAiAdapter(context, requestSupport, resilienceSupport));

        BUILTIN_ADAPTER_NAMES.addAll(adapters.keySet());

        loadConfigurableAdapters();
    }

    private void loadConfigurableAdapters() {
        Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions =
                adapterDefinitionProperties.getAdapterDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return;
        }

        for (Map.Entry<String, AdapterDefinitionProperties.AdapterDefinition> entry : definitions.entrySet()) {
            String adapterName = entry.getKey();
            AdapterDefinitionProperties.AdapterDefinition definition = entry.getValue();

            if (!adapters.containsKey(adapterName.toLowerCase())) {
                try {
                    String type = definition.getType() != null ? definition.getType() : "openai-compatible";
                    ServiceCapability adapter;

                    if ("ollama-compatible".equals(type)) {
                        adapter = createOllamaConfigurableAdapter(adapterName, definition);
                    } else {
                        adapter = createConfigurableAdapter(adapterName, definition);
                    }

                    adapters.put(adapterName.toLowerCase(), adapter);
                    logger.info("Loaded configurable adapter from YAML: {} (type: {})", adapterName, type);
                } catch (Exception e) {
                    logger.error("Failed to load configurable adapter {}: {}", adapterName, e.getMessage());
                }
            }
        }
    }

    private ConfigurableAdapter createConfigurableAdapter(final String name,
                                                          final AdapterDefinitionProperties.AdapterDefinition definition) {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(definition.getCapabilities().isChat())
                .embedding(definition.getCapabilities().isEmbedding())
                .rerank(definition.getCapabilities().isRerank())
                .tts(definition.getCapabilities().isTts())
                .stt(definition.getCapabilities().isStt())
                .imageGenerate(definition.getCapabilities().isImgGen())
                .imageEdit(definition.getCapabilities().isImgEdit())
                .streaming(definition.getCapabilities().isStreaming())
                .build();

        return new ConfigurableAdapter(
                context, requestSupport, resilienceSupport,
                name, capabilities,
                definition.getAuth().getHeaderName(),
                definition.getAuth().getHeaderPrefix(),
                definition.getAdditionalHeaders(),
                openAiRequestTransformer, openAiResponseTransformer
        );
    }

    private OllamaConfigurableAdapter createOllamaConfigurableAdapter(final String name,
                                                                      final AdapterDefinitionProperties.AdapterDefinition definition) {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(definition.getCapabilities().isChat())
                .embedding(definition.getCapabilities().isEmbedding())
                .rerank(definition.getCapabilities().isRerank())
                .tts(definition.getCapabilities().isTts())
                .stt(definition.getCapabilities().isStt())
                .imageGenerate(definition.getCapabilities().isImgGen())
                .imageEdit(definition.getCapabilities().isImgEdit())
                .streaming(definition.getCapabilities().isStreaming())
                .build();

        return new OllamaConfigurableAdapter(
                context, requestSupport, resilienceSupport,
                name, capabilities,
                definition.getAuth().getHeaderName(),
                definition.getAuth().getHeaderPrefix(),
                definition.getAdditionalHeaders()
        );
    }

    /**
     * 动态注册一个adapter
     */
    public void registerAdapter(final String name, final ServiceCapability adapter) {
        adapters.put(name.toLowerCase(), adapter);
        logger.info("Registered adapter: {}", name);
    }

    /**
     * 动态移除一个adapter（仅限配置驱动的adapter）
     */
    public boolean removeAdapter(final String name) {
        if (isBuiltinAdapter(name)) {
            logger.warn("Cannot remove builtin adapter: {}", name);
            return false;
        }
        ServiceCapability removed = adapters.remove(name.toLowerCase());
        if (removed != null) {
            logger.info("Removed adapter: {}", name);
            return true;
        }
        return false;
    }

    /**
     * 判断是否是内置adapter
     */
    public boolean isBuiltinAdapter(final String name) {
        return BUILTIN_ADAPTER_NAMES.contains(name.toLowerCase());
    }

    /**
     * 获取所有内置adapter名称
     */
    public Set<String> getBuiltinAdapterNames() {
        return new HashSet<>(BUILTIN_ADAPTER_NAMES);
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

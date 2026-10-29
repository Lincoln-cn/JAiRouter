package org.unreal.modelrouter.router.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.impl.ClaudeAdapter;
import org.unreal.modelrouter.router.adapter.impl.ConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.impl.ExtendedAdapter;
import org.unreal.modelrouter.router.adapter.impl.OllamaConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.impl.GeminiAdapter;
import org.unreal.modelrouter.router.adapter.impl.GpuStackAdapter;
import org.unreal.modelrouter.router.adapter.impl.LocalAiAdapter;
import org.unreal.modelrouter.router.adapter.impl.NormalOpenAiAdapter;
import org.unreal.modelrouter.router.adapter.impl.OllamaAdapter;
import org.unreal.modelrouter.router.adapter.impl.VllmAdapter;
import org.unreal.modelrouter.router.adapter.impl.XinferenceAdapter;
import org.unreal.modelrouter.router.adapter.persistence.AdapterDefinitionPersistenceService;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * #778: 加载失败清单（adapter 名称 → 失败原因）。
     *
     * <p>修复前这里只有一行 {@code logger.error}，失败的适配器既不进注册表、也没有任何
     * 「谁没加载成功」的出口 ⇒ 控制台写入侧全绿，直到真正路由时才发现「没有可用适配器」。</p>
     */
    private final Map<String, String> loadFailures = new ConcurrentHashMap<>();
    private final ModelRouterProperties properties;
    private final ModelServiceRegistry registry;
    private final AdapterContext context;
    private final RequestProcessingSupport requestSupport;
    private final ResilienceSupport resilienceSupport;
    private final OpenAiRequestTransformer openAiRequestTransformer;
    private final OpenAiResponseTransformer openAiResponseTransformer;
    private final AdapterDefinitionProperties adapterDefinitionProperties;
    private final AdapterDefinitionPersistenceService persistenceService;

    public AdapterRegistry(final ModelRouterProperties properties,
                           final ModelServiceRegistry registry,
                           final AdapterContext context,
                           final RequestProcessingSupport requestSupport,
                           final ResilienceSupport resilienceSupport,
                           final OpenAiRequestTransformer openAiRequestTransformer,
                           final OpenAiResponseTransformer openAiResponseTransformer,
                           final AdapterDefinitionProperties adapterDefinitionProperties,
                           final AdapterDefinitionPersistenceService persistenceService) {
        this.properties = properties;
        this.registry = registry;
        this.context = context;
        this.requestSupport = requestSupport;
        this.resilienceSupport = resilienceSupport;
        this.openAiRequestTransformer = openAiRequestTransformer;
        this.openAiResponseTransformer = openAiResponseTransformer;
        this.adapterDefinitionProperties = adapterDefinitionProperties;
        this.persistenceService = persistenceService;
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
        // 第一步：加载 YAML 配置的 adapter
        Map<String, AdapterDefinitionProperties.AdapterDefinition> yamlDefinitions =
                adapterDefinitionProperties.getAdapterDefinitions();
        if (yamlDefinitions != null) {
            loadAdapterDefinitions(yamlDefinitions, "YAML");
        }

        // 第二步：加载持久化的 adapter（跳过与 YAML 同名的）
        try {
            Map<String, AdapterDefinitionProperties.AdapterDefinition> persistedDefinitions =
                    persistenceService.loadAllDefinitions();
            if (persistedDefinitions != null && !persistedDefinitions.isEmpty()) {
                Map<String, AdapterDefinitionProperties.AdapterDefinition> toLoad = new HashMap<>();
                for (Map.Entry<String, AdapterDefinitionProperties.AdapterDefinition> entry
                        : persistedDefinitions.entrySet()) {
                    if (!adapters.containsKey(entry.getKey().toLowerCase())) {
                        toLoad.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!toLoad.isEmpty()) {
                    loadAdapterDefinitions(toLoad, "持久化");
                }
            }
        } catch (Exception e) {
            logger.warn("加载持久化适配器定义失败，跳过: {}", e.getMessage());
        }
    }

    private void loadAdapterDefinitions(final Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions,
                                         final String source) {
        // 第一遍：加载非extend类型的adapter
        for (Map.Entry<String, AdapterDefinitionProperties.AdapterDefinition> entry : definitions.entrySet()) {
            String adapterName = entry.getKey();
            AdapterDefinitionProperties.AdapterDefinition definition = entry.getValue();

            if (!adapters.containsKey(adapterName.toLowerCase())) {
                try {
                    String type = definition.getType() != null ? definition.getType() : "openai-compatible";
                    ServiceCapability adapter;

                    if ("extend".equals(type)) {
                        continue; // 延迟处理extend类型
                    } else if ("ollama-compatible".equals(type)) {
                        adapter = createOllamaConfigurableAdapter(adapterName, definition);
                    } else {
                        adapter = createConfigurableAdapter(adapterName, definition);
                    }

                    adapters.put(adapterName.toLowerCase(), adapter);
                    loadFailures.remove(adapterName.toLowerCase());
                    logger.info("Loaded configurable adapter from {}: {} (type: {})", source, adapterName, type);
                } catch (Exception e) {
                    // #778: 除了日志，还要留下「谁没加载成功、为什么」的可查证据
                    recordLoadFailure(adapterName, source, e);
                }
            }
        }

        // 第二遍：加载extend类型的adapter（需要依赖父adapter）
        for (Map.Entry<String, AdapterDefinitionProperties.AdapterDefinition> entry : definitions.entrySet()) {
            String adapterName = entry.getKey();
            AdapterDefinitionProperties.AdapterDefinition definition = entry.getValue();

            if (!adapters.containsKey(adapterName.toLowerCase()) && "extend".equals(definition.getType())) {
                try {
                    ServiceCapability adapter = createExtendedAdapter(adapterName, definition);
                    adapters.put(adapterName.toLowerCase(), adapter);
                    loadFailures.remove(adapterName.toLowerCase());
                    logger.info("Loaded extended adapter from {}: {} (parent: {})",
                            source, adapterName, definition.getParent());
                } catch (Exception e) {
                    // #778: extend 的 parent 缺失同样要能被上层查到
                    recordLoadFailure(adapterName, source, e);
                }
            }
        }
    }

    private ConfigurableAdapter createConfigurableAdapter(
            final String name,
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

    private OllamaConfigurableAdapter createOllamaConfigurableAdapter(
            final String name,
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

    private ExtendedAdapter createExtendedAdapter(final String name,
                                                  final AdapterDefinitionProperties.AdapterDefinition definition) {
        String parentName = definition.getParent();
        if (parentName == null || parentName.isBlank()) {
            throw new IllegalArgumentException("Extended adapter '" + name + "' must specify a parent adapter");
        }

        ServiceCapability parentAdapter = adapters.get(parentName.toLowerCase());
        if (parentAdapter == null) {
            throw new IllegalArgumentException(
                    "Parent adapter '" + parentName
                    + "' not found for extended adapter '" + name + "'");
        }

        AdapterCapabilities capabilities = null;
        if (definition.getCapabilities() != null) {
            capabilities = AdapterCapabilities.builder()
                    .chat(definition.getCapabilities().isChat())
                    .embedding(definition.getCapabilities().isEmbedding())
                    .rerank(definition.getCapabilities().isRerank())
                    .tts(definition.getCapabilities().isTts())
                    .stt(definition.getCapabilities().isStt())
                    .imageGenerate(definition.getCapabilities().isImgGen())
                    .imageEdit(definition.getCapabilities().isImgEdit())
                    .streaming(definition.getCapabilities().isStreaming())
                    .build();
        }

        return new ExtendedAdapter(
                parentAdapter, name, capabilities,
                definition.getAuth().getHeaderName(),
                definition.getAuth().getHeaderPrefix(),
                definition.getAdditionalHeaders()
        );
    }

    /**
     * #778: 记录一次加载失败（adapter 名称 → {@code 来源: 原因}）。
     */
    private void recordLoadFailure(final String adapterName, final String source, final Exception cause) {
        final String key = adapterName.toLowerCase();
        loadFailures.put(key, source + ": " + describeCause(cause));
        logger.error("Failed to load adapter {} from {}", adapterName, source, cause);
    }

    private static String describeCause(final Exception cause) {
        final String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            return cause.getClass().getSimpleName() + ": " + message;
        }
        return cause.getClass().getSimpleName();
    }

    /**
     * #778: 当前仍在「加载失败」状态的 adapter 清单（不可变副本，按名称小写为键）。
     *
     * <p>供健康检查与运维查询使用：列表非空即表示「配置里声明了这个 adapter，但运行时没有它」，
     * 此时限制到该 adapter 的请求会失败。写入侧（{@code POST/PUT /api/config/adapter}）在保存前
     * 就会尝试构造（#709），因此这里的条目大多来自 YAML / 历史持久化定义 / 父 adapter 被删后的
     * extend 子项。</p>
     *
     * @return adapter 名称 → 失败原因（空 Map 表示全部加载成功）
     */
    public Map<String, String> getLoadFailures() {
        return Collections.unmodifiableMap(new HashMap<>(loadFailures));
    }

    /**
     * 动态注册一个adapter
     */
    public void registerAdapter(final String name, final ServiceCapability adapter) {
        adapters.put(name.toLowerCase(), adapter);
        // #778: 写入侧修好同一个 adapter 后，之前的加载失败记录不应继续挂着
        loadFailures.remove(name.toLowerCase());
        logger.info("Registered adapter: {}", name);
    }

    /**
     * 根据名称获取adapter（不区分大小写）
     */
    public ServiceCapability getAdapterByName(final String name) {
        if (name == null) {
            return null;
        }
        return adapters.get(name.toLowerCase());
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
            throw new IllegalArgumentException("Unsupported adapter: " + adapterName
                    + " (service=" + serviceType + "; " + describeRegisteredAdapters() + ")");
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
            String instanceName = instance != null ? instance.getName() : null;
            throw new IllegalArgumentException("Unsupported adapter: " + adapterName
                    + " (service=" + serviceType
                    + (instanceName != null ? ", instance=" + instanceName : "")
                    + "; " + describeRegisteredAdapters() + ")");
        }

        return adapter;
    }

    /**
     * 已注册 adapter 名清单，用于把「写错了名字」的报错直接指到可用取值上（#638）.
     *
     * @return 形如 {@code registered=[normal, claude, ...]} 的描述
     */
    private String describeRegisteredAdapters() {
        final java.util.TreeSet<String> names = new java.util.TreeSet<>(adapters.keySet());
        return "registered=" + names;
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

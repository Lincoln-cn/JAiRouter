package org.unreal.modelrouter.router.model;

import org.springframework.stereotype.Service;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型目录服务（v3.1）.
 *
 * <p>汇总所有服务类型的可用模型，供两个入口面共用：
 * <ul>
 *   <li>控制台面 {@code GET /api/models}：返回 {@code RouterResponse} 包裹体；</li>
 *   <li>OpenAI 原生面 {@code GET /v1/models}：返回原生 {@code {object:"list", data:[...]}}。</li>
 * </ul>
 *
 * <p>抽取自原 {@code ModelInfoController} 的内联逻辑，字段集合保持完全一致，避免两处漂移。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@Service
public class ModelCatalogService {

    private final ModelServiceRegistry registry;
    private final AdapterRegistry adapterRegistry;

    /**
     * 构造函数.
     *
     * @param registry        模型服务注册表（提供各服务类型的可用模型）
     * @param adapterRegistry 适配器注册表（提供各服务类型当前适配器名）
     */
    public ModelCatalogService(final ModelServiceRegistry registry,
                               final AdapterRegistry adapterRegistry) {
        this.registry = registry;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 列出全部可用模型.
     *
     * @return 模型信息列表（id / object / created / owned_by / service_type / adapter）
     */
    public List<Map<String, Object>> listAllModels() {
        final List<Map<String, Object>> allModels = new ArrayList<>();

        for (ModelServiceRegistry.ServiceType serviceType : ModelServiceRegistry.ServiceType.values()) {
            final Set<String> availableModels = registry.getAvailableModels(serviceType);
            if (availableModels == null || availableModels.isEmpty()) {
                continue;
            }
            for (String modelName : availableModels) {
                final Map<String, Object> modelInfo = new LinkedHashMap<>();
                modelInfo.put("id", modelName);
                modelInfo.put("object", "model");
                modelInfo.put("created", System.currentTimeMillis() / 1000);
                modelInfo.put("owned_by", "model-router");
                modelInfo.put("service_type", serviceType.name());
                modelInfo.put("adapter", resolveAdapterName(serviceType));
                allModels.add(modelInfo);
            }
        }

        return allModels;
    }

    /**
     * 以 OpenAI 列表结构返回全部可用模型.
     *
     * @return {@code {object:"list", data:[{...}]}}，可直接作为 {@code GET /v1/models} 响应体
     */
    public Map<String, Object> listAllModelsAsOpenAiList() {
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("object", "list");
        response.put("data", listAllModels());
        return response;
    }

    /**
     * 解析服务类型当前适配器的简单类名.
     *
     * @param serviceType 服务类型
     * @return 适配器简单类名；不可用时返回 {@code unknown}
     */
    private String resolveAdapterName(final ModelServiceRegistry.ServiceType serviceType) {
        try {
            final ServiceCapability adapter = adapterRegistry.getAdapter(serviceType);
            return adapter != null ? adapter.getClass().getSimpleName() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}

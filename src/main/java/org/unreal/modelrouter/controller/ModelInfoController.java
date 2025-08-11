package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.response.ApiResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型信息控制器 - 处理模型信息查询相关接口
 */
@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = "*")
public class ModelInfoController {

    private static final Logger logger = LoggerFactory.getLogger(ModelInfoController.class);

    private final ModelServiceRegistry registry;
    private final AdapterRegistry adapterRegistry;

    public ModelInfoController(ModelServiceRegistry registry,
                               AdapterRegistry adapterRegistry) {
        this.registry = registry;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 获取所有可用模型
     */
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<Object>>> getModels() {
        try {
            List<Map<String, Object>> allModels = new ArrayList<>();

            // 收集所有服务类型的模型
            for (ModelServiceRegistry.ServiceType serviceType : ModelServiceRegistry.ServiceType.values()) {
                var availableModels = registry.getAvailableModels(serviceType);

                for (String modelName : availableModels) {
                    Map<String, Object> modelInfo = new HashMap<>();
                    modelInfo.put("id", modelName);
                    modelInfo.put("object", "model");
                    modelInfo.put("created", System.currentTimeMillis() / 1000);
                    modelInfo.put("owned_by", "model-router");
                    modelInfo.put("service_type", serviceType.name());

                    // 获取适配器信息
                    try {
                        var adapter = adapterRegistry.getAdapter(serviceType);
                        modelInfo.put("adapter", adapter.getClass().getSimpleName());
                    } catch (Exception e) {
                        modelInfo.put("adapter", "unknown");
                    }

                    allModels.add(modelInfo);
                }
            }

            var response = new HashMap<String, Object>();
            response.put("object", "list");
            response.put("data", allModels);
            return Mono.just(ResponseEntity.ok(ApiResponse.success(response, "获取模型列表成功")));
        } catch (Exception e) {
            logger.error("获取模型列表失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(ApiResponse.error("获取模型列表失败: " + e.getMessage())));
        }
    }
}

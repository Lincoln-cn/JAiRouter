package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.model.ModelServiceRegistry;
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
@Tag(name = "模型信息接口", description = "提供模型信息查询相关接口")
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
    @Operation(
        summary = "获取所有可用模型",
        description = "获取系统中所有可用的模型列表，包括模型基本信息和服务类型",
        responses = {
            @ApiResponse(
                responseCode = "200", 
                description = "成功获取模型列表",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "500", 
                description = "服务器内部错误",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)
                )
            )
        }
    )
    public Mono<RouterResponse<Object>> getModels() {
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
            return Mono.just(RouterResponse.success(response, "获取模型列表成功"));
        } catch (Exception e) {
            logger.error("获取模型列表失败", e);
            return Mono.just(RouterResponse.error("获取模型列表失败: " + e.getMessage()));
        }
    }
}

package org.unreal.modelrouter.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 配置统计控制器 - 处理配置统计信息查询相关接口
 */
@RestController
@RequestMapping("/api/models/stats")
@CrossOrigin(origins = "*")
@Tag(name = "配置统计管理", description = "提供配置统计信息查询相关接口")
public class ModelStatsController {

    private static final Logger logger = LoggerFactory.getLogger(ModelStatsController.class);

    private final ModelServiceRegistry registry;

    public ModelStatsController(ModelServiceRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取配置统计信息
     */
    @GetMapping
    @Operation(summary = "获取配置统计信息", description = "获取系统中所有服务和实例的统计信息")
    @ApiResponse(responseCode = "200", description = "成功获取配置统计",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<Object>>> getConfigurationStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 获取服务统计
            Set<String> serviceTypes = registry.getAllServiceTypes();
            stats.put("totalServices", serviceTypes.size());

            // 获取实例统计
            Map<String, Integer> instanceCounts = new HashMap<>();
            Map<String, Integer> modelCounts = new HashMap<>();
            int totalInstances = 0;
            int totalModels = 0;

            for (ModelServiceRegistry.ServiceType serviceType : ModelServiceRegistry.ServiceType.values()) {
                var instances = registry.getAllInstances().get(serviceType);
                var models = registry.getAvailableModels(serviceType);

                int instanceCount = instances != null ? instances.size() : 0;
                int modelCount = models.size();

                instanceCounts.put(serviceType.name(), instanceCount);
                modelCounts.put(serviceType.name(), modelCount);

                totalInstances += instanceCount;
                totalModels += modelCount;
            }

            stats.put("totalInstances", totalInstances);
            stats.put("totalModels", totalModels);
            stats.put("instancesByService", instanceCounts);
            stats.put("modelsByService", modelCounts);
            stats.put("serviceTypes", serviceTypes);

            return Mono.just(ResponseEntity.ok(RouterResponse.success(stats, "获取配置统计成功")));
        } catch (Exception e) {
            logger.error("获取配置统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取配置统计失败: " + e.getMessage())));
        }
    }
}

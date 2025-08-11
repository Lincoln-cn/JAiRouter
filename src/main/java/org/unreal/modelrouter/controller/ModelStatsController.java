package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.response.ApiResponse;
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
    public Mono<ResponseEntity<ApiResponse<Object>>> getConfigurationStats() {
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

            return Mono.just(ResponseEntity.ok(ApiResponse.success(stats, "获取配置统计成功")));
        } catch (Exception e) {
            logger.error("获取配置统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(ApiResponse.error("获取配置统计失败: " + e.getMessage())));
        }
    }
}

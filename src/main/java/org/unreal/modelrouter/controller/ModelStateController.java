package org.unreal.modelrouter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型管理控制器 - 处理模型监控和管理相关的接口
 */
@RestController
@RequestMapping("/api/models")
public class ModelStateController {

    private final ModelServiceRegistry registry;
    private final ServiceStateManager serviceStateManager;
    private final AdapterRegistry adapterRegistry;


    public ModelStateController(ModelServiceRegistry registry,
                                     ServiceStateManager serviceStateManager,
                                     AdapterRegistry adapterRegistry) {
        this.registry = registry;
        this.serviceStateManager = serviceStateManager;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 获取所有可用模型
     */
    @GetMapping
    public Mono<ResponseEntity<Object>> getModels() {
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
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 获取特定服务的状态
     */
    @GetMapping("/status/{serviceType}")
    public Mono<ResponseEntity<Object>> getServiceStatus(@PathVariable String serviceType) {
        try {
            ModelServiceRegistry.ServiceType type = ModelServiceRegistry.ServiceType.valueOf(serviceType.toLowerCase());

            var availableModels = registry.getAvailableModels(type);
            var loadBalanceStrategy = registry.getLoadBalanceStrategy(type);
            var allInstances = registry.getAllInstances().get(type);
            var isServiceHealthy = serviceStateManager.isServiceHealthy(type.name());

            // 实例健康状态
            List<Map<String, Object>> instancesWithHealth = new ArrayList<>();
            if (allInstances != null) {
                for (var instance : allInstances) {
                    Map<String, Object> instanceInfo = new HashMap<>();
                    instanceInfo.put("name", instance.getName());
                    instanceInfo.put("baseUrl", instance.getBaseUrl());
                    instanceInfo.put("path", instance.getPath());
                    instanceInfo.put("weight", instance.getWeight());
                    instanceInfo.put("healthy", serviceStateManager.isInstanceHealthy(type.name(), instance));
                    instancesWithHealth.add(instanceInfo);
                }
            }

            // 获取当前适配器
            String currentAdapter = "unknown";
            try {
                var adapter = adapterRegistry.getAdapter(type);
                currentAdapter = adapter.getClass().getSimpleName();
            } catch (Exception e) {
                currentAdapter = "error: " + e.getMessage();
            }

            var status = new HashMap<String, Object>();
            status.put("service_type", type.name());
            status.put("adapter", currentAdapter);
            status.put("load_balance_strategy", loadBalanceStrategy);
            status.put("available_models", availableModels);
            status.put("total_instances", allInstances != null ? allInstances.size() : 0);
            status.put("service_healthy", isServiceHealthy);
            status.put("instances", instancesWithHealth);
            status.put("timestamp", java.time.Instant.now().toString());

            return Mono.just(ResponseEntity.ok(status));

        } catch (IllegalArgumentException e) {
            var error = new HashMap<String, Object>();
            error.put("error", "Invalid service type: " + serviceType);
            error.put("valid_types", ModelServiceRegistry.ServiceType.values());
            return Mono.just(ResponseEntity.badRequest().body(error));
        }
    }

    /**
     * 获取系统总体状态
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Object>> getSystemStatus() {
        var systemStatus = new HashMap<String, Object>();
        var serviceStatuses = new HashMap<String, Object>();

        // 收集所有服务状态
        for (ModelServiceRegistry.ServiceType serviceType : ModelServiceRegistry.ServiceType.values()) {
            var serviceStatus = new HashMap<String, Object>();

            var availableModels = registry.getAvailableModels(serviceType);
            var allInstances = registry.getAllInstances().get(serviceType);
            var isServiceHealthy = serviceStateManager.isServiceHealthy(serviceType.name());

            serviceStatus.put("healthy", isServiceHealthy);
            serviceStatus.put("model_count", availableModels.size());
            serviceStatus.put("instance_count", allInstances != null ? allInstances.size() : 0);

            // 获取适配器信息
            try {
                var adapter = adapterRegistry.getAdapter(serviceType);
                serviceStatus.put("adapter", adapter.getClass().getSimpleName());
            } catch (Exception e) {
                serviceStatus.put("adapter", "error");
            }

            serviceStatuses.put(serviceType.name(), serviceStatus);
        }

        systemStatus.put("services", serviceStatuses);
        systemStatus.put("timestamp", java.time.Instant.now().toString());
        systemStatus.put("version", "1.0.0");
        systemStatus.put("name", "Model Router Gateway");

        return Mono.just(ResponseEntity.ok(systemStatus));
    }
}

package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.dto.UpdateInstanceDTO;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.response.ApiResponse;

import java.util.*;

/**
 * 模型管理控制器 - 重构版
 * 提供完整的服务和实例管理REST API
 * 支持服务的增删改查、实例的增删改查、以及批量操作
 */
@RestController
@RequestMapping("/api/config/type")
@CrossOrigin(origins = "*")
public class ServiceTypeController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceTypeController.class);

    private final ConfigurationService configurationService;

    @Autowired
    public ServiceTypeController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    // ==================== 全局配置管理 ====================

    /**
     * 获取当前所有配置
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllConfigurations() {
        try {
            Map<String, Object> configs = configurationService.getAllConfigurations();
            return ResponseEntity.ok(ApiResponse.success(configs, "获取配置成功"));
        } catch (Exception e) {
            logger.error("获取所有配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取配置失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定服务的所有可用模型
     */
    @GetMapping("/{serviceType}/models")
    public ResponseEntity<org.unreal.modelrouter.response.ApiResponse<Set<String>>> getAvailableModels(
            @PathVariable String serviceType) {
        try {
            Set<String> models = configurationService.getAvailableModels(serviceType);
            return ResponseEntity.ok(org.unreal.modelrouter.response.ApiResponse.success(models, "获取模型列表成功"));
        } catch (Exception e) {
            logger.error("获取模型列表失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(org.unreal.modelrouter.response.ApiResponse.error("获取模型列表失败: " + e.getMessage()));
        }
    }


    /**
     * 批量更新配置
     */
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> batchUpdateConfigurations(
            @RequestBody Map<String, Object> configs) {
        try {
            configurationService.batchUpdateConfigurations(configs);
            return ResponseEntity.ok(ApiResponse.success(null, "批量更新配置成功"));
        } catch (Exception e) {
            logger.error("批量更新配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量更新配置失败: " + e.getMessage()));
        }
    }

    /**
     * 重置配置为默认值
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetToDefaultConfig() {
        try {
            configurationService.resetToDefaultConfig();
            return ResponseEntity.ok(ApiResponse.success(null, "配置已重置为默认值"));
        } catch (Exception e) {
            logger.error("重置配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("重置配置失败: " + e.getMessage()));
        }
    }

    // ==================== 服务管理 ====================

    /**
     * 获取所有可用服务类型
     */
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<Set<String>>> getAvailableServiceTypes() {
        try {
            Set<String> serviceTypes = configurationService.getAvailableServiceTypes();
            return ResponseEntity.ok(ApiResponse.success(serviceTypes, "获取服务类型成功"));
        } catch (Exception e) {
            logger.error("获取服务类型失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取服务类型失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定服务的配置
     */
    @GetMapping("/services/{serviceType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getServiceConfig(
            @PathVariable String serviceType) {
        try {
            Map<String, Object> serviceConfig = configurationService.getServiceConfig(serviceType);
            if (serviceConfig == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("服务类型不存在: " + serviceType));
            }
            return ResponseEntity.ok(ApiResponse.success(serviceConfig, "获取服务配置成功"));
        } catch (Exception e) {
            logger.error("获取服务配置失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取服务配置失败: " + e.getMessage()));
        }
    }

    /**
     * 创建新服务
     */
    @PostMapping("/services/{serviceType}")
    public ResponseEntity<ApiResponse<Void>> createService(
            @PathVariable String serviceType,
            @RequestBody Map<String, Object> serviceConfig) {
        try {
            configurationService.createService(serviceType, serviceConfig);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(null, "服务创建成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("创建服务失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建服务失败: " + e.getMessage()));
        }
    }

    /**
     * 更新服务配置
     */
    @PutMapping("/services/{serviceType}")
    public ResponseEntity<ApiResponse<Void>> updateServiceConfig(
            @PathVariable String serviceType,
            @RequestBody Map<String, Object> serviceConfig) {
        try {
            configurationService.updateServiceConfig(serviceType, serviceConfig);
            return ResponseEntity.ok(ApiResponse.success(null, "服务配置更新成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新服务配置失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新服务配置失败: " + e.getMessage()));
        }
    }

    /**
     * 删除服务
     */
    @DeleteMapping("/services/{serviceType}")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable String serviceType) {
        try {
            configurationService.deleteService(serviceType);
            return ResponseEntity.ok(ApiResponse.success(null, "服务删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除服务失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除服务失败: " + e.getMessage()));
        }
    }



    /**
     * 验证基本配置
     */
    private void validateBasicConfiguration(Map<String, Object> config,
                                            List<String> errors,
                                            List<String> warnings) {
        // 验证全局适配器
        if (config.containsKey("adapter")) {
            String adapter = (String) config.get("adapter");
            if (adapter == null || adapter.trim().isEmpty()) {
                warnings.add("全局适配器配置为空");
            }
        }

        // 验证全局负载均衡配置
        if (config.containsKey("loadBalance")) {
            validateLoadBalanceConfig(config.get("loadBalance"), "全局", errors, warnings);
        }
    }

    /**
     * 验证服务配置
     */
    @SuppressWarnings("unchecked")
    private void validateServicesConfiguration(Map<String, Object> config,
                                               List<String> errors,
                                               List<String> warnings) {
        if (!config.containsKey("services")) {
            warnings.add("未配置任何服务");
            return;
        }

        Object servicesObj = config.get("services");
        if (!(servicesObj instanceof Map)) {
            errors.add("服务配置格式错误");
            return;
        }

        Map<String, Object> services = (Map<String, Object>) servicesObj;

        for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
            String serviceType = serviceEntry.getKey();
            Object serviceConfigObj = serviceEntry.getValue();

            if (!(serviceConfigObj instanceof Map)) {
                errors.add("服务 " + serviceType + " 配置格式错误");
                continue;
            }

            Map<String, Object> serviceConfig = (Map<String, Object>) serviceConfigObj;
            validateSingleServiceConfig(serviceType, serviceConfig, errors, warnings);
        }
    }

    /**
     * 验证单个服务配置
     */
    @SuppressWarnings("unchecked")
    private void validateSingleServiceConfig(String serviceType,
                                             Map<String, Object> serviceConfig,
                                             List<String> errors,
                                             List<String> warnings) {
        // 验证实例配置
        if (!serviceConfig.containsKey("instances")) {
            warnings.add("服务 " + serviceType + " 未配置实例");
            return;
        }

        Object instancesObj = serviceConfig.get("instances");
        if (!(instancesObj instanceof List)) {
            errors.add("服务 " + serviceType + " 的实例配置格式错误");
            return;
        }

        List<Object> instances = (List<Object>) instancesObj;
        if (instances.isEmpty()) {
            warnings.add("服务 " + serviceType + " 实例列表为空");
            return;
        }

        // 验证每个实例
        Set<String> instanceIds = new HashSet<>();
        for (int i = 0; i < instances.size(); i++) {
            Object instanceObj = instances.get(i);
            if (!(instanceObj instanceof Map)) {
                errors.add("服务 " + serviceType + " 第 " + (i + 1) + " 个实例配置格式错误");
                continue;
            }

            Map<String, Object> instance = (Map<String, Object>) instanceObj;
            validateInstanceConfig(serviceType, i + 1, instance, instanceIds, errors, warnings);
        }

        // 验证负载均衡配置
        if (serviceConfig.containsKey("loadBalance")) {
            validateLoadBalanceConfig(serviceConfig.get("loadBalance"),
                    "服务 " + serviceType, errors, warnings);
        }
    }

    /**
     * 验证实例配置
     */
    private void validateInstanceConfig(String serviceType,
                                        int instanceIndex,
                                        Map<String, Object> instance,
                                        Set<String> instanceIds,
                                        List<String> errors,
                                        List<String> warnings) {
        String prefix = "服务 " + serviceType + " 第 " + instanceIndex + " 个实例";

        // 验证必需字段
        if (!instance.containsKey("name") || instance.get("name") == null) {
            errors.add(prefix + " 缺少name字段");
            return;
        }

        if (!instance.containsKey("baseUrl") || instance.get("baseUrl") == null) {
            errors.add(prefix + " 缺少baseUrl字段");
            return;
        }

        String name = (String) instance.get("name");
        String baseUrl = (String) instance.get("baseUrl");
        String instanceId = name + "@" + baseUrl;

        // 检查重复实例
        if (instanceIds.contains(instanceId)) {
            errors.add(prefix + " 实例ID重复: " + instanceId);
        } else {
            instanceIds.add(instanceId);
        }

        // 验证URL格式
        if (!isValidUrl(baseUrl)) {
            errors.add(prefix + " baseUrl格式错误: " + baseUrl);
        }

        // 验证权重
        if (instance.containsKey("weight")) {
            Object weightObj = instance.get("weight");
            if (!(weightObj instanceof Number) || ((Number) weightObj).intValue() <= 0) {
                warnings.add(prefix + " weight应为正整数");
            }
        }
    }

    /**
     * 验证负载均衡配置
     */
    @SuppressWarnings("unchecked")
    private void validateLoadBalanceConfig(Object loadBalanceObj,
                                           String context,
                                           List<String> errors,
                                           List<String> warnings) {
        if (!(loadBalanceObj instanceof Map)) {
            errors.add(context + " 负载均衡配置格式错误");
            return;
        }

        Map<String, Object> loadBalance = (Map<String, Object>) loadBalanceObj;

        if (loadBalance.containsKey("type")) {
            String type = (String) loadBalance.get("type");
            if (!isValidLoadBalanceType(type)) {
                errors.add(context + " 负载均衡类型无效: " + type);
            }
        }
    }

    /**
     * 验证URL格式
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * 验证负载均衡类型
     */
    private boolean isValidLoadBalanceType(String type) {
        if (type == null) return false;
        String lowerType = type.toLowerCase();
        return "random".equals(lowerType) ||
                "round-robin".equals(lowerType) ||
                "least-connections".equals(lowerType) ||
                "ip-hash".equals(lowerType);
    }
}
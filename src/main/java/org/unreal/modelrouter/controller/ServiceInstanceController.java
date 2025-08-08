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

import java.util.*;

@RestController
@RequestMapping("/api/config/instance")
@CrossOrigin(origins = "*")
public class ServiceInstanceController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceController.class);

    private final ConfigurationService configurationService;

    @Autowired
    public ServiceInstanceController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }


    // ==================== 实例管理 ====================

    /**
     * 获取指定服务的所有实例
     */
    @GetMapping("/type/{serviceType}")
    public ResponseEntity<org.unreal.modelrouter.response.ApiResponse<List<Map<String, Object>>>> getServiceInstances(
            @PathVariable String serviceType) {
        try {
            List<Map<String, Object>> instances = configurationService.getServiceInstances(serviceType);
            return ResponseEntity.ok(org.unreal.modelrouter.response.ApiResponse.success(instances, "获取实例列表成功"));
        } catch (Exception e) {
            logger.error("获取实例列表失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(org.unreal.modelrouter.response.ApiResponse.error("获取实例列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定实例的详细信息
     */
    @GetMapping("info/{serviceType}")
    public ResponseEntity<org.unreal.modelrouter.response.ApiResponse<Map<String, Object>>> getServiceInstance(
            @PathVariable String serviceType,
            @RequestParam String modelName,
            @RequestParam String baseUrl) {
        try {
            // URL解码实例ID
            String decodedInstanceId = buildInstanceId(modelName, baseUrl);
            Map<String, Object> instance = configurationService.getServiceInstance(serviceType, decodedInstanceId);

            if (instance == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(org.unreal.modelrouter.response.ApiResponse.error("实例不存在: " + decodedInstanceId));
            }

            return ResponseEntity.ok(org.unreal.modelrouter.response.ApiResponse.success(instance, "获取实例信息成功"));
        } catch (Exception e) {
            logger.error("获取实例信息失败: serviceType={}, modelName={} , baseUrl={}", serviceType, modelName, baseUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(org.unreal.modelrouter.response.ApiResponse.error("获取实例信息失败: " + e.getMessage()));
        }
    }

    /**
     * 添加服务实例
     */
    @PostMapping("/add/{serviceType}")
    public ResponseEntity<org.unreal.modelrouter.response.ApiResponse<Void>> addServiceInstance(
            @PathVariable String serviceType,
            @RequestBody ModelRouterProperties.ModelInstance instanceConfig) {
        try {
            configurationService.addServiceInstance(serviceType, instanceConfig);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(org.unreal.modelrouter.response.ApiResponse.success(null, "实例添加成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(org.unreal.modelrouter.response.ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("添加实例失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(org.unreal.modelrouter.response.ApiResponse.error("添加实例失败: " + e.getMessage()));
        }
    }

    /**
     * 更新服务实例
     */
    @PutMapping("/update/{serviceType}")
    public ResponseEntity<org.unreal.modelrouter.response.ApiResponse<Void>> updateServiceInstance(
            @PathVariable String serviceType,
            @RequestBody UpdateInstanceDTO instanceConfig) {
        try {
            // URL解码实例ID
            String decodedInstanceId = instanceConfig.getInstanceId();
            configurationService.updateServiceInstance(serviceType, decodedInstanceId, instanceConfig.getInstance().covertTo());
            return ResponseEntity.ok(org.unreal.modelrouter.response.ApiResponse.success(null, "实例更新成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(org.unreal.modelrouter.response.ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("获取实例信息失败: serviceType={}, modelName={} , baseUrl={}", serviceType, instanceConfig.getInstance().getName(), instanceConfig.getInstance().getBaseUrl(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(org.unreal.modelrouter.response.ApiResponse.error("更新实例失败: " + e.getMessage()));
        }
    }

    /**
     * 删除服务实例
     */
    @DeleteMapping("/del/{serviceType}")
    public ResponseEntity<org.unreal.modelrouter.response.ApiResponse<Void>> deleteServiceInstance(
            @PathVariable String serviceType,
            @RequestParam String modelName,
            @RequestParam String baseUrl) {
        try {
            // URL解码实例ID
            String decodedInstanceId = buildInstanceId(modelName, baseUrl);
            configurationService.deleteServiceInstance(serviceType, decodedInstanceId);
            return ResponseEntity.ok(org.unreal.modelrouter.response.ApiResponse.success(null, "实例删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(org.unreal.modelrouter.response.ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("获取实例信息失败: serviceType={}, modelName={} , baseUrl={}", serviceType, modelName, baseUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(org.unreal.modelrouter.response.ApiResponse.error("删除实例失败: " + e.getMessage()));
        }
    }

    private String buildInstanceId(String moduleName, String baseUrl) {
        if (moduleName != null && baseUrl != null) {
            return moduleName + "@" + baseUrl;
        }
        return "unknown";
    }
}

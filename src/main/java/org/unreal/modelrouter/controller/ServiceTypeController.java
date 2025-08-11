package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.config.ConfigurationValidator;
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
    private final ConfigurationValidator configurationValidator;

    @Autowired
    public ServiceTypeController(ConfigurationService configurationService, ConfigurationValidator configurationValidator) {
        this.configurationService = configurationService;
        this.configurationValidator = configurationValidator;
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
    public ResponseEntity<ApiResponse<Set<String>>> getAvailableModels(
            @PathVariable String serviceType) {
        try {
            // 验证服务类型参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            Set<String> models = configurationService.getAvailableModels(serviceType);
            return ResponseEntity.ok(ApiResponse.success(models, "获取模型列表成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数验证失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("获取模型列表失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取模型列表失败: " + e.getMessage()));
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
            // 验证服务类型参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            Map<String, Object> serviceConfig = configurationService.getServiceConfig(serviceType);
            if (serviceConfig == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("服务类型不存在: " + serviceType));
            }
            return ResponseEntity.ok(ApiResponse.success(serviceConfig, "获取服务配置成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数验证失败: " + e.getMessage()));
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
            // 验证参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            configurationValidator.validateServiceConfig(serviceType, serviceConfig, errors, warnings);
            
            if (!errors.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("参数验证失败: " + String.join(", ", errors)));
            }
            
            configurationService.createService(serviceType, serviceConfig);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(null, "服务创建成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数验证失败: " + e.getMessage()));
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
            // 验证参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            configurationValidator.validateServiceConfig(serviceType, serviceConfig, errors, warnings);
            
            if (!errors.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("参数验证失败: " + String.join(", ", errors)));
            }
            
            configurationService.updateServiceConfig(serviceType, serviceConfig);
            return ResponseEntity.ok(ApiResponse.success(null, "服务配置更新成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数验证失败: " + e.getMessage()));
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
            // 验证服务类型参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            configurationService.deleteService(serviceType);
            return ResponseEntity.ok(ApiResponse.success(null, "服务删除成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数验证失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("删除服务失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除服务失败: " + e.getMessage()));
        }
    }

}
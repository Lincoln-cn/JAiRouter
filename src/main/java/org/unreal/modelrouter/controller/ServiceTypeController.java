package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.config.ConfigurationValidator;
import org.unreal.modelrouter.controller.response.RouterResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型管理控制器 - 重构版
 * 提供完整的服务和实例管理REST API
 * 支持服务的增删改查、实例的增删改查、以及批量操作
 */
@RestController
@RequestMapping("/api/config/type")
@CrossOrigin(origins = "*")
@Tag(name = "服务类型管理", description = "提供服务类型的增删改查及相关配置管理接口")
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
    @Operation(summary = "获取所有配置", description = "获取当前系统的所有配置信息")
    @ApiResponse(responseCode = "200", description = "成功获取配置",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getAllConfigurations() {
        try {
            Map<String, Object> configs = configurationService.getAllConfigurations();
            return ResponseEntity.ok(RouterResponse.success(configs, "获取配置成功"));
        } catch (Exception e) {
            logger.error("获取所有配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取配置失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定服务的所有可用模型
     */
    @GetMapping("/{serviceType}/models")
    @Operation(summary = "获取服务可用模型", description = "根据服务类型获取该服务下的所有可用模型")
    @ApiResponse(responseCode = "200", description = "成功获取模型列表",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Set<String>>> getAvailableModels(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType) {
        try {
            // 验证服务类型参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            Set<String> models = configurationService.getAvailableModels(serviceType);
            return ResponseEntity.ok(RouterResponse.success(models, "获取模型列表成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("参数验证失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("获取模型列表失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取模型列表失败: " + e.getMessage()));
        }
    }


    /**
     * 重置配置为默认值
     */
    @PostMapping("/reset")
    @Operation(summary = "重置配置", description = "将系统配置重置为默认值")
    @ApiResponse(responseCode = "200", description = "配置重置成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> resetToDefaultConfig() {
        try {
            configurationService.resetToDefaultConfig();
            return ResponseEntity.ok(RouterResponse.success(null, "配置已重置为默认值"));
        } catch (Exception e) {
            logger.error("重置配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("重置配置失败: " + e.getMessage()));
        }
    }

    // ==================== 服务管理 ====================

    /**
     * 获取所有可用服务类型
     */
    @GetMapping("/services")
    @Operation(summary = "获取所有服务类型", description = "获取系统中所有可用的服务类型")
    @ApiResponse(responseCode = "200", description = "成功获取服务类型",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Set<String>>> getAvailableServiceTypes() {
        try {
            Set<String> serviceTypes = configurationService.getAvailableServiceTypes();
            return ResponseEntity.ok(RouterResponse.success(serviceTypes, "获取服务类型成功"));
        } catch (Exception e) {
            logger.error("获取服务类型失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取服务类型失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定服务的配置
     */
    @GetMapping("/services/{serviceType}")
    @Operation(summary = "获取服务配置", description = "根据服务类型获取该服务的配置信息")
    @ApiResponse(responseCode = "200", description = "成功获取服务配置",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "服务类型不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getServiceConfig(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType) {
        try {
            // 验证服务类型参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            Map<String, Object> serviceConfig = configurationService.getServiceConfig(serviceType);
            if (serviceConfig == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务类型不存在: " + serviceType));
            }
            return ResponseEntity.ok(RouterResponse.success(serviceConfig, "获取服务配置成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("参数验证失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("获取服务配置失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取服务配置失败: " + e.getMessage()));
        }
    }

    /**
     * 创建新服务
     */
    @PostMapping("/services/{serviceType}")
    @Operation(summary = "创建新服务", description = "创建一个新的服务类型并配置相关信息")
    @ApiResponse(responseCode = "201", description = "服务创建成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> createService(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "服务配置信息")
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
                        .body(RouterResponse.error("参数验证失败: " + String.join(", ", errors)));
            }
            
            configurationService.createService(serviceType, serviceConfig);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RouterResponse.success(null, "服务创建成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("参数验证失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("创建服务失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("创建服务失败: " + e.getMessage()));
        }
    }

    /**
     * 更新服务配置
     */
    @PutMapping("/services/{serviceType}")
    @Operation(summary = "更新服务配置", description = "更新指定服务类型的配置信息")
    @ApiResponse(responseCode = "200", description = "服务配置更新成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> updateServiceConfig(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "服务配置信息")
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
                        .body(RouterResponse.error("参数验证失败: " + String.join(", ", errors)));
            }
            
            configurationService.updateServiceConfig(serviceType, serviceConfig);
            return ResponseEntity.ok(RouterResponse.success(null, "服务配置更新成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("参数验证失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("更新服务配置失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("更新服务配置失败: " + e.getMessage()));
        }
    }

    /**
     * 删除服务
     */
    @DeleteMapping("/services/{serviceType}")
    @Operation(summary = "删除服务", description = "删除指定的服务类型及其所有配置")
    @ApiResponse(responseCode = "200", description = "服务删除成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> deleteService(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType) {
        try {
            // 验证服务类型参数
            if (!configurationValidator.isValidServiceType(serviceType)) {
                throw new IllegalArgumentException("无效的服务类型: " + serviceType);
            }
            
            configurationService.deleteService(serviceType);
            return ResponseEntity.ok(RouterResponse.success(null, "服务删除成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("参数验证失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("删除服务失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("删除服务失败: " + e.getMessage()));
        }
    }

}
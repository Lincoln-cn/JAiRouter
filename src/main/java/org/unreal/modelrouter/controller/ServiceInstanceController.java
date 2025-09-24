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
import org.unreal.modelrouter.dto.UpdateInstanceDTO;
import org.unreal.modelrouter.model.ModelRouterProperties;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config/instance")
@CrossOrigin(origins = "*")
@Tag(name = "服务实例管理", description = "提供服务实例的增删改查相关接口")
public class ServiceInstanceController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceController.class);

    private final ConfigurationService configurationService;
    private final ConfigurationValidator configurationValidator;

    @Autowired
    public ServiceInstanceController(ConfigurationService configurationService, ConfigurationValidator configurationValidator) {
        this.configurationService = configurationService;
        this.configurationValidator = configurationValidator;
    }


    // ==================== 实例管理 ====================

    /**
     * 获取指定服务的所有实例
     */
    @GetMapping("/type/{serviceType}")
    @Operation(summary = "获取服务实例列表", description = "根据服务类型获取该服务下的所有实例")
    @ApiResponse(responseCode = "200", description = "成功获取实例列表",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<List<Map<String, Object>>>> getServiceInstances(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType) {
        try {
            List<Map<String, Object>> instances = configurationService.getServiceInstances(serviceType);
            return ResponseEntity.ok(RouterResponse.success(instances, "获取实例列表成功"));
        } catch (Exception e) {
            logger.error("获取实例列表失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取实例列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定实例的详细信息
     */
    @GetMapping("info/{serviceType}")
    @Operation(summary = "获取服务实例详情", description = "根据服务类型、模型名称和基础URL获取特定实例的详细信息")
    @ApiResponse(responseCode = "200", description = "成功获取实例信息",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "404", description = "实例不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getServiceInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType,
            @Parameter(description = "模型名称", example = "qwen2:7b")
            @RequestParam String modelName,
            @Parameter(description = "基础URL", example = "http://localhost:8000")
            @RequestParam String baseUrl) {
        try {
            // 使用ConfigurationService生成实例ID
            String decodedInstanceId = configurationService.buildInstanceId(modelName, baseUrl);
            Map<String, Object> instance = configurationService.getServiceInstance(serviceType, decodedInstanceId);

            if (instance == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("实例不存在: " + decodedInstanceId));
            }

            return ResponseEntity.ok(RouterResponse.success(instance, "获取实例信息成功"));
        } catch (Exception e) {
            logger.error("获取实例信息失败: serviceType={}, modelName={} , baseUrl={}", serviceType, modelName, baseUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取实例信息失败: " + e.getMessage()));
        }
    }

    /**
     * 添加服务实例
     */
    @PostMapping("/add/{serviceType}")
    @Operation(summary = "添加服务实例", description = "为指定服务类型添加新的实例")
    @ApiResponse(responseCode = "201", description = "实例添加成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> addServiceInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType,
            @Parameter(description = "是否创建新版本", example = "false")
            @RequestParam(defaultValue = "true") boolean createNewVersion,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "实例配置信息")
            @RequestBody ModelRouterProperties.ModelInstance instanceConfig) {
        try {
            logger.info("接收到添加实例请求: serviceType={}, createNewVersion={}, instanceConfig={}", 
                serviceType, createNewVersion, instanceConfig);
            
            // 验证实例配置
            if (!configurationValidator.validateServiceAddress(instanceConfig.getBaseUrl())) {
                logger.warn("实例baseUrl格式不正确: {}", instanceConfig.getBaseUrl());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(RouterResponse.error("实例baseUrl格式不正确"));
            }

            configurationService.addServiceInstance(serviceType, instanceConfig);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RouterResponse.success(null, "实例添加成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("添加实例参数错误: serviceType={}, message={}", serviceType, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("添加实例失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("添加实例失败: " + e.getMessage()));
        }
    }

    /**
     * 更新服务实例
     */
    @PutMapping("/update/{serviceType}")
    @Operation(summary = "更新服务实例", description = "更新指定服务类型的实例配置")
    @ApiResponse(responseCode = "200", description = "实例更新成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> updateServiceInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType,
            @Parameter(description = "是否创建新版本", example = "false")
            @RequestParam(defaultValue = "true") boolean createNewVersion,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "实例更新信息")
            @RequestBody UpdateInstanceDTO instanceConfig) {
        try {
            // 记录详细的请求信息，包括线程和时间戳，帮助分析多版本创建问题
            logger.info("接收到更新实例请求 - 服务类型: {}, 创建新版本: {}, 线程: {}, 时间戳: {}",
                    serviceType, createNewVersion, Thread.currentThread().getName(), System.currentTimeMillis());

            if (logger.isDebugEnabled()) {
                logger.debug("更新实例请求详情: instanceConfig={}", instanceConfig);
            }
            
            // 检查请求参数是否为空
            if (instanceConfig == null) {
                logger.warn("更新实例请求参数为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(RouterResponse.error("请求参数不能为空"));
            }
            
            if (instanceConfig.getInstance() == null) {
                logger.warn("更新实例请求中的instance数据为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(RouterResponse.error("实例数据不能为空"));
            }
            
            // 验证实例配置
            if (!configurationValidator.validateServiceAddress(instanceConfig.getInstance().getBaseUrl())) {
                logger.warn("实例baseUrl格式不正确: {}", instanceConfig.getInstance().getBaseUrl());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(RouterResponse.error("实例baseUrl格式不正确"));
            }

            configurationService.updateServiceInstance(serviceType, instanceConfig.getInstanceId(), instanceConfig.getInstance().covertTo());
            
            return ResponseEntity.ok(RouterResponse.success(null, "实例更新成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("更新实例参数错误: serviceType={}, message={}", serviceType, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新实例失败: serviceType={}", serviceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("更新实例失败: " + e.getMessage()));
        }
    }

    /**
     * 删除服务实例
     */
    @DeleteMapping("/del/{serviceType}")
    @Operation(summary = "删除服务实例", description = "根据服务类型、模型名称和基础URL删除特定实例")
    @ApiResponse(responseCode = "200", description = "实例删除成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> deleteServiceInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable String serviceType,
            @Parameter(description = "是否创建新版本", example = "false")
            @RequestParam(defaultValue = "true") boolean createNewVersion,
            @Parameter(description = "模型名称", example = "qwen2:7b")
            @RequestParam String modelName,
            @Parameter(description = "基础URL", example = "http://localhost:8000")
            @RequestParam String baseUrl) {
        try {
            // 使用ConfigurationService生成实例ID
            String decodedInstanceId = configurationService.buildInstanceId(modelName, baseUrl);
            configurationService.deleteServiceInstance(serviceType, decodedInstanceId);
            return ResponseEntity.ok(RouterResponse.success(null, "实例删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("获取实例信息失败: serviceType={}, modelName={} , baseUrl={}", serviceType, modelName, baseUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("删除实例失败: " + e.getMessage()));
        }
    }
}
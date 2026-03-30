package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.DatabaseConfigService;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.InstanceUpdateRequest;
import org.unreal.modelrouter.dto.ServiceInstanceDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务实例控制器 - 负责服务实例配置管理
 * 提供实例的增删改查独立接口
 */
@Slf4j
@RestController
@RequestMapping("/api/config/instance")
@RequiredArgsConstructor
@Tag(name = "服务实例管理", description = "提供服务实例的独立管理接口")
public class ServiceInstanceConfigController {

    private final DatabaseConfigService databaseConfigService;

    /**
     * 获取服务实例列表
     */
    @GetMapping("/{serviceType}")
    @Operation(summary = "获取服务实例列表", description = "获取指定服务的所有实例")
    @ApiResponse(responseCode = "200", description = "成功获取实例列表")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<List<ServiceInstanceDTO>>> getInstances(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType) {
        try {
            log.info("获取服务实例列表：serviceType={}", serviceType);
            
            Map<String, Object> serviceConfig = databaseConfigService.getServiceConfig(serviceType);
            if (serviceConfig == null || serviceConfig.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.get("instances");
            
            return ResponseEntity.ok(RouterResponse.success(convertToInstanceDTOList(instances), "获取实例列表成功"));
        } catch (Exception e) {
            log.error("获取实例列表失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取实例列表失败：" + e.getMessage()));
        }
    }

    /**
     * 获取单个实例详情
     */
    @GetMapping("/{serviceType}/{instanceId:[a-zA-Z0-9-]+}")
    @Operation(summary = "获取实例详情", description = "获取指定实例的详细信息")
    @ApiResponse(responseCode = "200", description = "成功获取实例详情")
    @ApiResponse(responseCode = "404", description = "实例不存在")
    public ResponseEntity<RouterResponse<ServiceInstanceDTO>> getInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例 ID")
            @PathVariable("instanceId") String instanceId) {
        try {
            log.info("获取实例详情：serviceType={}, instanceId={}", serviceType, instanceId);
            
            Map<String, Object> serviceConfig = databaseConfigService.getServiceConfig(serviceType);
            if (serviceConfig == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.get("instances");
            
            if (instances == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("实例不存在：" + instanceId));
            }
            
            Map<String, Object> targetInstance = null;
            for (Map<String, Object> instance : instances) {
                String id = (String) instance.get("instanceId");
                if (instanceId.equals(id)) {
                    targetInstance = instance;
                    break;
                }
            }
            
            if (targetInstance == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("实例不存在：" + instanceId));
            }
            
            return ResponseEntity.ok(RouterResponse.success(convertToInstanceDTO(targetInstance), "获取实例详情成功"));
        } catch (Exception e) {
            log.error("获取实例详情失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取实例详情失败：" + e.getMessage()));
        }
    }

    /**
     * 添加服务实例
     */
    @PostMapping("/{serviceType}")
    @Operation(summary = "添加服务实例", description = "为指定服务添加新实例")
    @ApiResponse(responseCode = "200", description = "实例添加成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<ServiceInstanceDTO>> addInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例配置", required = true,
                    schema = @Schema(implementation = InstanceUpdateRequest.class))
            @RequestBody InstanceUpdateRequest request) {
        try {
            log.info("添加服务实例：serviceType={}, name={}", serviceType, request.getName());
            
            // 验证参数
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("实例名称不能为空"));
            }
            if (request.getBaseUrl() == null || request.getBaseUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("baseUrl 不能为空"));
            }
            
            // 构建实例配置
            Map<String, Object> instanceConfig = new HashMap<>();
            instanceConfig.put("name", request.getName());
            instanceConfig.put("baseUrl", request.getBaseUrl());
            if (request.getPath() != null) {
                instanceConfig.put("path", request.getPath());
            }
            if (request.getWeight() != null) {
                instanceConfig.put("weight", request.getWeight());
            } else {
                instanceConfig.put("weight", 1);
            }
            if (request.getStatus() != null) {
                instanceConfig.put("status", request.getStatus());
            } else {
                instanceConfig.put("status", "active");
            }
            if (request.getHeaders() != null) {
                instanceConfig.put("headers", request.getHeaders());
            }
            
            // 限流配置
            if (request.getRateLimitEnabled() != null) {
                Map<String, Object> rateLimit = new HashMap<>();
                rateLimit.put("enabled", request.getRateLimitEnabled());
                if (request.getRateLimitAlgorithm() != null) {
                    rateLimit.put("algorithm", request.getRateLimitAlgorithm());
                }
                if (request.getRateLimitCapacity() != null) {
                    rateLimit.put("capacity", request.getRateLimitCapacity());
                }
                if (request.getRateLimitRate() != null) {
                    rateLimit.put("rate", request.getRateLimitRate());
                }
                if (request.getRateLimitScope() != null) {
                    rateLimit.put("scope", request.getRateLimitScope());
                }
                if (request.getRateLimitClientIpEnable() != null) {
                    rateLimit.put("clientIpEnable", request.getRateLimitClientIpEnable());
                }
                instanceConfig.put("rateLimit", rateLimit);
            }
            
            // 调用数据库服务添加实例
            Map<String, Object> addedInstance = databaseConfigService.addServiceInstance(serviceType, instanceConfig);
            
            log.info("实例添加成功：serviceType={}, instanceId={}", serviceType, 
                addedInstance != null ? addedInstance.get("instanceId") : "unknown");
            
            return ResponseEntity.ok(RouterResponse.success(convertToInstanceDTO(addedInstance), "实例添加成功"));
        } catch (IllegalArgumentException e) {
            log.error("添加实例失败：serviceType={}, error={}", serviceType, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("添加实例失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("添加失败：" + e.getMessage()));
        }
    }

    /**
     * 更新服务实例
     */
    @PutMapping("/{serviceType}/{instanceId}")
    @Operation(summary = "更新服务实例", description = "更新指定实例的配置")
    @ApiResponse(responseCode = "200", description = "实例更新成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "实例不存在")
    public ResponseEntity<RouterResponse<ServiceInstanceDTO>> updateInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例 ID")
            @PathVariable("instanceId") String instanceId,
            @Parameter(description = "实例配置", required = true,
                    schema = @Schema(implementation = InstanceUpdateRequest.class))
            @RequestBody InstanceUpdateRequest request) {
        try {
            log.info("更新服务实例：serviceType={}, instanceId={}, name={}", serviceType, instanceId, request.getName());
            
            // 验证参数
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("实例名称不能为空"));
            }
            if (request.getBaseUrl() == null || request.getBaseUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("baseUrl 不能为空"));
            }
            
            // 构建实例配置
            Map<String, Object> instanceConfig = new HashMap<>();
            instanceConfig.put("name", request.getName());
            instanceConfig.put("baseUrl", request.getBaseUrl());
            if (request.getPath() != null) {
                instanceConfig.put("path", request.getPath());
            }
            if (request.getWeight() != null) {
                instanceConfig.put("weight", request.getWeight());
            }
            if (request.getStatus() != null) {
                instanceConfig.put("status", request.getStatus());
            }
            if (request.getHeaders() != null) {
                instanceConfig.put("headers", request.getHeaders());
            }
            
            // 限流配置
            if (request.getRateLimitEnabled() != null) {
                Map<String, Object> rateLimit = new HashMap<>();
                rateLimit.put("enabled", request.getRateLimitEnabled());
                if (request.getRateLimitAlgorithm() != null) {
                    rateLimit.put("algorithm", request.getRateLimitAlgorithm());
                }
                if (request.getRateLimitCapacity() != null) {
                    rateLimit.put("capacity", request.getRateLimitCapacity());
                }
                if (request.getRateLimitRate() != null) {
                    rateLimit.put("rate", request.getRateLimitRate());
                }
                if (request.getRateLimitScope() != null) {
                    rateLimit.put("scope", request.getRateLimitScope());
                }
                if (request.getRateLimitClientIpEnable() != null) {
                    rateLimit.put("clientIpEnable", request.getRateLimitClientIpEnable());
                }
                instanceConfig.put("rateLimit", rateLimit);
            }
            
            // 调用数据库服务更新实例
            Map<String, Object> updatedInstance = databaseConfigService.updateServiceInstance(serviceType, instanceId, instanceConfig);
            
            log.info("实例更新成功：serviceType={}, instanceId={}", serviceType, instanceId);
            
            return ResponseEntity.ok(RouterResponse.success(convertToInstanceDTO(updatedInstance), "实例更新成功"));
        } catch (IllegalArgumentException e) {
            log.error("更新实例失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("更新实例失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("更新失败：" + e.getMessage()));
        }
    }

    /**
     * 删除服务实例
     */
    @DeleteMapping("/{serviceType}/{instanceId}")
    @Operation(summary = "删除服务实例", description = "删除指定的实例")
    @ApiResponse(responseCode = "200", description = "实例删除成功")
    @ApiResponse(responseCode = "404", description = "实例不存在")
    public ResponseEntity<RouterResponse<Void>> deleteInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例 ID")
            @PathVariable("instanceId") String instanceId) {
        try {
            log.info("删除服务实例：serviceType={}, instanceId={}", serviceType, instanceId);
            
            boolean deleted = databaseConfigService.deleteServiceInstance(serviceType, instanceId);
            
            if (deleted) {
                log.info("实例删除成功：serviceType={}, instanceId={}", serviceType, instanceId);
                return ResponseEntity.ok(RouterResponse.success(null, "实例删除成功"));
            } else {
                return ResponseEntity.internalServerError()
                        .body(RouterResponse.error("实例删除失败"));
            }
        } catch (IllegalArgumentException e) {
            log.error("删除实例失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("删除实例失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("删除失败：" + e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private ServiceInstanceDTO convertToInstanceDTO(Map<String, Object> instance) {
        if (instance == null) {
            return null;
        }
        
        ServiceInstanceDTO.RateLimitDTO rateLimit = null;
        if (instance.containsKey("rateLimit") && instance.get("rateLimit") instanceof Map) {
            Map<String, Object> rl = (Map<String, Object>) instance.get("rateLimit");
            rateLimit = ServiceInstanceDTO.RateLimitDTO.builder()
                    .enabled((Boolean) rl.get("enabled"))
                    .algorithm((String) rl.get("algorithm"))
                    .capacity(rl.get("capacity") != null ? ((Number) rl.get("capacity")).intValue() : null)
                    .rate(rl.get("rate") != null ? ((Number) rl.get("rate")).intValue() : null)
                    .scope((String) rl.get("scope"))
                    .key((String) rl.get("key"))
                    .clientIpEnable((Boolean) rl.get("clientIpEnable"))
                    .build();
        }
        
        return ServiceInstanceDTO.builder()
                .instanceId((String) instance.get("instanceId"))
                .name((String) instance.get("name"))
                .baseUrl((String) instance.get("baseUrl"))
                .path((String) instance.get("path"))
                .weight(instance.get("weight") != null ? ((Number) instance.get("weight")).intValue() : null)
                .status((String) instance.get("status"))
                .healthStatus((String) instance.get("healthStatus"))
                .headers(instance.get("headers"))
                .rateLimit(rateLimit)
                .build();
    }

    @SuppressWarnings("unchecked")
    private java.util.List<ServiceInstanceDTO> convertToInstanceDTOList(List<Map<String, Object>> instances) {
        if (instances == null) {
            return java.util.Collections.emptyList();
        }
        
        return instances.stream()
                .map(this::convertToInstanceDTO)
                .collect(java.util.stream.Collectors.toList());
    }
}

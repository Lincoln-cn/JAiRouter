package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.InstanceCreateRequest;
import org.unreal.modelrouter.dto.InstanceUpdateFlatRequest;
import org.unreal.modelrouter.dto.InstanceUpdateRequest;
import org.unreal.modelrouter.service.InstanceConfigService;
import org.unreal.modelrouter.vo.ServiceInstanceVO;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例配置控制器 - 负责实例的增删改查
 */
@Slf4j
@RestController
@RequestMapping("/api/config/instance")
@RequiredArgsConstructor
@Tag(name = "实例配置管理", description = "提供实例配置的增删改查接口")
public class InstanceConfigController {

    private final InstanceConfigService instanceConfigService;

    /**
     * 获取服务实例列表
     */
    @GetMapping("/{serviceType}")
    @Operation(summary = "获取服务实例列表", description = "获取指定服务的所有实例")
    @ApiResponse(responseCode = "200", description = "成功获取实例列表")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public Mono<ResponseEntity<RouterResponse<List<ServiceInstanceVO>>>> getInstances(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType) {
        log.info("获取服务实例列表：serviceType={}", serviceType);
        return instanceConfigService.getInstances(serviceType)
                .map(instances -> ResponseEntity.ok(RouterResponse.success(instances, "获取实例列表成功")))
                .onErrorResume(e -> {
                    log.error("获取实例列表失败：serviceType={}, error={}", serviceType, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(RouterResponse.error("获取实例列表失败：" + e.getMessage())));
                });
    }

    /**
     * 获取单个实例详情
     */
    @GetMapping("/{serviceType}/{instanceId}")
    @Operation(summary = "获取实例详情", description = "获取指定实例的详细信息")
    @ApiResponse(responseCode = "200", description = "成功获取实例详情")
    @ApiResponse(responseCode = "404", description = "实例不存在")
    public Mono<ResponseEntity<RouterResponse<ServiceInstanceVO>>> getInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例 ID")
            @PathVariable("instanceId") String instanceId) {
        log.info("获取实例详情：serviceType={}, instanceId={}", serviceType, instanceId);
        return instanceConfigService.getInstance(serviceType, instanceId)
                .<ResponseEntity<RouterResponse<ServiceInstanceVO>>>map(instance -> {
                    if (instance == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(RouterResponse.error("实例不存在：" + instanceId));
                    }
                    return ResponseEntity.ok(RouterResponse.success(instance, "获取实例详情成功"));
                })
                .onErrorResume(e -> {
                    log.error("获取实例详情失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(RouterResponse.error("获取实例详情失败：" + e.getMessage())));
                });
    }

    /**
     * 添加实例
     */
    @PostMapping("/{serviceType}")
    @Operation(summary = "添加实例", description = "添加新的服务实例")
    @ApiResponse(responseCode = "200", description = "实例添加成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    public Mono<ResponseEntity<RouterResponse<ServiceInstanceVO>>> addInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例配置", required = true)
            @RequestBody InstanceCreateRequest request) {
        log.info("添加实例：serviceType={}, name={}", serviceType, request.getName());
        
        // 验证必填字段
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("实例名称不能为空")));
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("baseUrl 不能为空")));
        }
        
        // 转换为 Map
        Map<String, Object> instanceConfig = convertToMap(request);
        
        return instanceConfigService.addInstance(serviceType, instanceConfig)
                .<ResponseEntity<RouterResponse<ServiceInstanceVO>>>map(instance -> 
                    ResponseEntity.ok(RouterResponse.success(instance, "实例添加成功")))
                .onErrorResume(e -> {
                    log.error("添加实例失败：serviceType={}, error={}", serviceType, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(RouterResponse.error("添加实例失败：" + e.getMessage())));
                });
    }

    /**
     * 更新实例
     */
    @PutMapping("/{serviceType}/{instanceId}")
    @Operation(summary = "更新实例", description = "更新指定实例的配置")
    @ApiResponse(responseCode = "200", description = "实例更新成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "实例不存在")
    public Mono<ResponseEntity<RouterResponse<ServiceInstanceVO>>> updateInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例 ID")
            @PathVariable("instanceId") String instanceId,
            @Parameter(description = "实例配置", required = true)
            @RequestBody InstanceUpdateRequest request) {
        log.info("更新实例：serviceType={}, instanceId={}, name={}", serviceType, instanceId, request.getName());
        
        // 验证必填字段
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("实例名称不能为空")));
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("baseUrl 不能为空")));
        }
        
        // 转换为 Map
        Map<String, Object> instanceConfig = convertToMap(request);
        
        return instanceConfigService.updateInstance(serviceType, instanceId, instanceConfig)
                .<ResponseEntity<RouterResponse<ServiceInstanceVO>>>map(instance -> {
                    if (instance == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(RouterResponse.error("实例不存在：" + instanceId));
                    }
                    return ResponseEntity.ok(RouterResponse.success(instance, "实例更新成功"));
                })
                .onErrorResume(e -> {
                    log.error("更新实例失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(RouterResponse.error("更新实例失败：" + e.getMessage())));
                });
    }

    /**
     * 删除实例
     */
    @DeleteMapping("/{serviceType}/{instanceId}")
    @Operation(summary = "删除实例", description = "删除指定的实例")
    @ApiResponse(responseCode = "200", description = "实例删除成功")
    @ApiResponse(responseCode = "404", description = "实例不存在")
    public Mono<ResponseEntity<RouterResponse<Void>>> deleteInstance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例 ID")
            @PathVariable("instanceId") String instanceId) {
        log.info("删除实例：serviceType={}, instanceId={}", serviceType, instanceId);
        return instanceConfigService.deleteInstance(serviceType, instanceId)
                .then(Mono.<ResponseEntity<RouterResponse<Void>>>just(ResponseEntity.ok(RouterResponse.success(null, "实例删除成功"))))
                .onErrorResume(e -> {
                    log.error("删除实例失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(RouterResponse.error("删除实例失败：" + e.getMessage())));
                });
    }

    /**
     * 将 DTO 转换为 Map
     */
    private Map<String, Object> convertToMap(Object request) {
        Map<String, Object> config = new HashMap<>();
        config.put("name", request instanceof InstanceCreateRequest ? 
            ((InstanceCreateRequest) request).getName() : ((InstanceUpdateRequest) request).getName());
        config.put("baseUrl", request instanceof InstanceCreateRequest ? 
            ((InstanceCreateRequest) request).getBaseUrl() : ((InstanceUpdateRequest) request).getBaseUrl());
        
        if (request instanceof InstanceCreateRequest) {
            InstanceCreateRequest r = (InstanceCreateRequest) request;
            if (r.getPath() != null) config.put("path", r.getPath());
            if (r.getWeight() != null) config.put("weight", r.getWeight());
            if (r.getStatus() != null) config.put("status", r.getStatus());
            if (r.getHeaders() != null) config.put("headers", r.getHeaders());
            buildRateLimitConfig(config, r.getRateLimitEnabled(), r.getRateLimitAlgorithm(),
                    r.getRateLimitCapacity(), r.getRateLimitRate(), r.getRateLimitScope(),
                    r.getRateLimitClientIpEnable());
        } else {
            InstanceUpdateRequest r = (InstanceUpdateRequest) request;
            if (r.getPath() != null) config.put("path", r.getPath());
            if (r.getWeight() != null) config.put("weight", r.getWeight());
            if (r.getStatus() != null) config.put("status", r.getStatus());
            if (r.getHeaders() != null) config.put("headers", r.getHeaders());
            buildRateLimitConfig(config, r.getRateLimitEnabled(), r.getRateLimitAlgorithm(),
                    r.getRateLimitCapacity(), r.getRateLimitRate(), r.getRateLimitScope(),
                    r.getRateLimitClientIpEnable());
        }
        
        return config;
    }

    /**
     * 构建限流配置
     */
    private void buildRateLimitConfig(Map<String, Object> config, Boolean enabled, String algorithm,
                                      Integer capacity, Integer rate, String scope, Boolean clientIpEnable) {
        if (enabled != null) {
            Map<String, Object> rateLimit = new HashMap<>();
            rateLimit.put("enabled", enabled);
            if (algorithm != null) rateLimit.put("algorithm", algorithm);
            if (capacity != null) rateLimit.put("capacity", capacity);
            if (rate != null) rateLimit.put("rate", rate);
            if (scope != null) rateLimit.put("scope", scope);
            if (clientIpEnable != null) rateLimit.put("clientIpEnable", clientIpEnable);
            config.put("rateLimit", rateLimit);
        }
    }

    /**
     * 更新实例（简化版 - 直接接收扁平化格式）
     * 新增接口，不影响现有逻辑
     */
    @PutMapping("/{serviceType}/{instanceId}/flat")
    @Operation(summary = "更新实例（简化版）", description = "直接接收扁平化格式的实例配置")
    @ApiResponse(responseCode = "200", description = "实例更新成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "实例不存在")
    public Mono<ResponseEntity<RouterResponse<ServiceInstanceVO>>> updateInstanceFlat(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "实例 ID")
            @PathVariable("instanceId") String instanceId,
            @Parameter(description = "实例配置（扁平化格式）", required = true)
            @RequestBody InstanceUpdateFlatRequest request) {
        log.info("简化版更新实例：serviceType={}, instanceId={}, name={}", serviceType, instanceId, request.getName());

        // 验证必填字段
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("实例名称不能为空")));
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("baseUrl 不能为空")));
        }

        // 转换为 Map（扁平格式）
        Map<String, Object> instanceConfig = flatRequestToMap(request);

        return instanceConfigService.updateInstance(serviceType, instanceId, instanceConfig)
                .<ResponseEntity<RouterResponse<ServiceInstanceVO>>>map(instance -> {
                    if (instance == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(RouterResponse.error("实例不存在：" + instanceId));
                    }
                    return ResponseEntity.ok(RouterResponse.success(instance, "实例更新成功"));
                })
                .onErrorResume(e -> {
                    log.error("更新实例失败：serviceType={}, instanceId={}, error={}", serviceType, instanceId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(RouterResponse.error("更新实例失败：" + e.getMessage())));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将扁平化 DTO 转换为 Map（辅助方法）
     */
    private Map<String, Object> flatRequestToMap(InstanceUpdateFlatRequest request) {
        Map<String, Object> config = new HashMap<>();
        config.put("name", request.getName());
        config.put("baseUrl", request.getBaseUrl());
        if (request.getPath() != null) config.put("path", request.getPath());
        if (request.getWeight() != null) config.put("weight", request.getWeight());
        if (request.getStatus() != null) config.put("status", request.getStatus());
        if (request.getAdapter() != null) config.put("adapter", request.getAdapter());
        if (request.getHeaders() != null) config.put("headers", request.getHeaders());
        
        // 限流器配置 - 直接传递扁平字段
        if (request.getRateLimitEnabled() != null) {
            config.put("rateLimitEnabled", request.getRateLimitEnabled());
        }
        if (request.getRateLimitAlgorithm() != null) {
            config.put("rateLimitAlgorithm", request.getRateLimitAlgorithm());
        }
        if (request.getRateLimitCapacity() != null) {
            config.put("rateLimitCapacity", request.getRateLimitCapacity());
        }
        if (request.getRateLimitRate() != null) {
            config.put("rateLimitRate", request.getRateLimitRate());
        }
        if (request.getRateLimitScope() != null) {
            config.put("rateLimitScope", request.getRateLimitScope());
        }
        if (request.getRateLimitKey() != null) {
            config.put("rateLimitKey", request.getRateLimitKey());
        }
        if (request.getRateLimitClientIpEnable() != null) {
            config.put("rateLimitClientIpEnable", request.getRateLimitClientIpEnable());
        }
        
        // 熔断器配置 - 直接传递扁平字段
        if (request.getCircuitBreakerEnabled() != null) {
            config.put("circuitBreakerEnabled", request.getCircuitBreakerEnabled());
        }
        if (request.getCircuitBreakerFailureThreshold() != null) {
            config.put("circuitBreakerFailureThreshold", request.getCircuitBreakerFailureThreshold());
        }
        if (request.getCircuitBreakerTimeout() != null) {
            config.put("circuitBreakerTimeout", request.getCircuitBreakerTimeout());
        }
        if (request.getCircuitBreakerSuccessThreshold() != null) {
            config.put("circuitBreakerSuccessThreshold", request.getCircuitBreakerSuccessThreshold());
        }
        
        return config;
    }

}

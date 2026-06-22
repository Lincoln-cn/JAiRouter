package org.unreal.modelrouter.config.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.common.dto.InstanceRateLimitDTO;
import org.unreal.modelrouter.common.dto.InstanceCircuitBreakerDTO;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.config.core.ServiceInstanceManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;

import java.util.List;
import java.util.Map;

/**
 * 按服务类型获取实例配置的控制器
 * 用于前端管理界面按服务类型查询实例
 */
@Slf4j
@RestController
@RequestMapping("/api/config/instance")
@RequiredArgsConstructor
public class ServiceTypeInstanceController {

    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceManager serviceInstanceManager;
    private final CircuitBreakerManager circuitBreakerManager;

    /**
     * 获取指定服务类型的所有实例配置
     * @param serviceType 服务类型 (如 chat, embedding, rerank 等)
     * @return 该服务类型的实例列表（包装在 RouterResponse 中）
     */
    @GetMapping("/{serviceType}")
    public ResponseEntity<RouterResponse<List<ServiceInstanceDTO>>> getInstancesByServiceType(
            @PathVariable final String serviceType) {
        log.debug("Getting instances for service type: {}", serviceType);

        // 根据 serviceType 查找 serviceConfig
        ServiceConfigEntity serviceConfig = serviceConfigRepository
                .findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .orElse(null);

        if (serviceConfig == null) {
            log.warn("Service config not found for type: {}", serviceType);
            return ResponseEntity.ok(RouterResponse.success(List.of()));
        }

        // 根据 serviceConfigId 获取实例列表
        List<ServiceInstanceDTO> instances = serviceInstanceManager
                .getInstancesByServiceConfigId(serviceConfig.getId());

        log.debug("Found {} instances for service type: {}", instances.size(), serviceType);
        return ResponseEntity.ok(RouterResponse.success(instances));
    }

    /**
     * 更新指定服务类型的实例配置（通过数据库ID）
     * @param serviceType 服务类型
     * @param instanceId 实例数据库ID
     * @param request 实例配置请求
     * @return 更新后的实例配置
     */
    @PutMapping("/{serviceType}/{instanceId}")
    public ResponseEntity<RouterResponse<ServiceInstanceDTO>> updateInstanceByServiceType(
            @PathVariable final String serviceType,
            @PathVariable final Long instanceId,
            @RequestBody final CreateServiceInstanceRequest request) {
        log.info("Updating instance: serviceType={}, instanceId={}", serviceType, instanceId);

        // 直接通过数据库ID更新实例
        ServiceInstanceDTO updated = serviceInstanceManager.updateInstance(instanceId, request);

        return ResponseEntity.ok(RouterResponse.success(updated, "实例配置更新成功"));
    }

    /**
     * 添加实例到指定服务类型
     * @param serviceType 服务类型
     * @param request 实例配置请求
     * @return 创建的实例配置
     */
    @PostMapping("/{serviceType}")
    public ResponseEntity<RouterResponse<ServiceInstanceDTO>> addInstanceByServiceType(
            @PathVariable final String serviceType,
            @RequestBody final CreateServiceInstanceRequest request) {
        log.info("Adding instance for service type: {}", serviceType);

        // 根据 serviceType 查找 serviceConfig
        ServiceConfigEntity serviceConfig = serviceConfigRepository
                .findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .orElse(null);

        if (serviceConfig == null) {
            return ResponseEntity.ok(RouterResponse.error("服务配置不存在: " + serviceType));
        }

        ServiceInstanceDTO created = serviceInstanceManager.createInstance(serviceConfig.getId(), request);
        return ResponseEntity.ok(RouterResponse.success(created, "实例添加成功"));
    }

    /**
     * 删除指定服务类型的实例（通过数据库ID）
     * @param serviceType 服务类型
     * @param instanceId 实例数据库ID
     * @return 操作结果
     */
    @DeleteMapping("/{serviceType}/{instanceId}")
    public ResponseEntity<RouterResponse<Void>> deleteInstanceByServiceType(
            @PathVariable final String serviceType,
            @PathVariable final Long instanceId) {
        log.info("Deleting instance: serviceType={}, instanceId={}", serviceType, instanceId);

        // 直接通过数据库ID删除实例
        serviceInstanceManager.deleteInstance(instanceId);

        return ResponseEntity.ok(RouterResponse.success(null, "实例删除成功"));
    }

    // ==================== 限流器配置 API ====================

    /**
     * 获取实例的限流器配置
     */
    @GetMapping("/{serviceType}/{instanceId}/rate-limit")
    public ResponseEntity<RouterResponse<InstanceRateLimitDTO>> getRateLimitConfig(
            @PathVariable final String serviceType,
            @PathVariable final Long instanceId) {
        log.info("Getting rate limit config: instanceId={}", instanceId);

        InstanceRateLimitDTO config = serviceInstanceManager.getRateLimitConfig(instanceId)
                .orElse(InstanceRateLimitDTO.builder()
                        .instanceId(instanceId)
                        .enabled(false)
                        .algorithm("token-bucket")
                        .capacity(100)
                        .rate(10)
                        .scope("instance")
                        .clientIpEnable(false)
                        .build());

        return ResponseEntity.ok(RouterResponse.success(config));
    }

    /**
     * 保存实例的限流器配置
     */
    @PutMapping("/{serviceType}/{instanceId}/rate-limit")
    public ResponseEntity<RouterResponse<InstanceRateLimitDTO>> saveRateLimitConfig(
            @PathVariable final String serviceType,
            @PathVariable final Long instanceId,
            @RequestBody final InstanceRateLimitDTO config) {
        log.info("Saving rate limit config: instanceId={}, enabled={}", instanceId, config.getEnabled());

        InstanceRateLimitDTO saved = serviceInstanceManager.saveRateLimitConfig(instanceId, config);
        return ResponseEntity.ok(RouterResponse.success(saved, "限流器配置保存成功"));
    }

    // ==================== 熔断器配置 API ====================

    /**
     * 获取实例的熔断器配置
     */
    @GetMapping("/{serviceType}/{instanceId}/circuit-breaker")
    public ResponseEntity<RouterResponse<InstanceCircuitBreakerDTO>> getCircuitBreakerConfig(
            @PathVariable final String serviceType,
            @PathVariable final Long instanceId) {
        log.info("Getting circuit breaker config: instanceId={}", instanceId);

        InstanceCircuitBreakerDTO config = serviceInstanceManager.getCircuitBreakerConfig(instanceId)
                .orElse(InstanceCircuitBreakerDTO.builder()
                        .instanceId(instanceId)
                        .enabled(false)
                        .failureThreshold(5)
                        .timeout(60000)
                        .successThreshold(2)
                        .build());

        return ResponseEntity.ok(RouterResponse.success(config));
    }

    /**
     * 保存实例的熔断器配置
     */
    @PutMapping("/{serviceType}/{instanceId}/circuit-breaker")
    public ResponseEntity<RouterResponse<InstanceCircuitBreakerDTO>> saveCircuitBreakerConfig(
            @PathVariable final String serviceType,
            @PathVariable final Long instanceId,
            @RequestBody final InstanceCircuitBreakerDTO config) {
        log.info("Saving circuit breaker config: instanceId={}, enabled={}", instanceId, config.getEnabled());

        InstanceCircuitBreakerDTO saved = serviceInstanceManager.saveCircuitBreakerConfig(instanceId, config);
        return ResponseEntity.ok(RouterResponse.success(saved, "熔断器配置保存成功"));
    }

    /**
     * 重置实例的内存熔断器状态
     * 当实例熔断器配置禁用时，可以通过此 API 清除内存中的熔断器状态
     */
    @PostMapping("/{serviceType}/{instanceId}/circuit-breaker/reset")
    public ResponseEntity<RouterResponse<Object>> resetCircuitBreakerState(
            @PathVariable final String serviceType,
            @PathVariable final Long instanceId) {
        log.info("Resetting circuit breaker state for instance: {}", instanceId);

        // 获取实例信息以获取 baseUrl
        serviceInstanceManager.getInstance(instanceId).ifPresent(instance -> {
            String instanceUrl = instance.getBaseUrl();
            circuitBreakerManager.resetCircuitBreaker(String.valueOf(instanceId), instanceUrl);
            log.info("Circuit breaker reset for instance: {} (url: {})", instanceId, instanceUrl);
        });

        return ResponseEntity.ok(RouterResponse.success(null, "熔断器状态已重置"));
    }

    /**
     * 清除所有实例的内存熔断器状态
     */
    @PostMapping("/circuit-breaker/clear-all")
    public ResponseEntity<RouterResponse<Object>> clearAllCircuitBreakers() {
        log.info("Clearing all circuit breakers");
        circuitBreakerManager.clearAllCircuitBreakers();
        return ResponseEntity.ok(RouterResponse.success(null, "所有熔断器状态已清除"));
    }

    /**
     * 获取所有实例的熔断器状态
     */
    @GetMapping("/circuit-breaker/states")
    public ResponseEntity<RouterResponse<Map<String, String>>> getAllCircuitBreakerStates() {
        Map<String, CircuitBreaker.State> states = circuitBreakerManager.getAllCircuitBreakerStates();
        Map<String, String> result = new java.util.LinkedHashMap<>();
        states.forEach((key, state) -> result.put(key, state.name()));
        return ResponseEntity.ok(RouterResponse.success(result));
    }

    /**
     * 重置指定实例的熔断器（通过 instanceId）
     */
    @PostMapping("/circuit-breaker/reset")
    public ResponseEntity<RouterResponse<Object>> resetCircuitBreakerById(
            @RequestBody final Map<String, String> request) {
        String instanceId = request.get("instanceId");
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("instanceId 不能为空"));
        }
        
        log.info("Resetting circuit breaker for instance: {}", instanceId);
        circuitBreakerManager.resetCircuitBreaker(instanceId, null);
        return ResponseEntity.ok(RouterResponse.success(null, "熔断器已重置"));
    }
}
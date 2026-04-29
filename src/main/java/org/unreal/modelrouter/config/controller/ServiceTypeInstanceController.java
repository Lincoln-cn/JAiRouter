package org.unreal.modelrouter.config.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.common.dto.InstanceRateLimitDTO;
import org.unreal.modelrouter.common.dto.InstanceCircuitBreakerDTO;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.config.core.ServiceInstanceManager;

import java.util.List;

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
}
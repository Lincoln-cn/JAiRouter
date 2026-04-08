package org.unreal.modelrouter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.service.ServiceInstanceManager;

import java.util.List;
import java.util.Map;

/**
 * 服务实例控制器
 * v1.5.2: 使用 JPA 实现，使用 DTO 替代 Map
 */
@Slf4j
@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
public class ServiceInstanceController {

    private final ServiceInstanceManager serviceInstanceManager;

    /**
     * 获取所有实例
     */
    @GetMapping
    public ResponseEntity<List<ServiceInstanceDTO>> getAllInstances() {
        log.debug("Getting all service instances");
        return ResponseEntity.ok(serviceInstanceManager.getAllInstances());
    }

    /**
     * 获取指定服务的所有实例
     */
    @GetMapping("/service/{serviceConfigId}")
    public ResponseEntity<List<ServiceInstanceDTO>> getInstancesByService(
            @PathVariable Long serviceConfigId) {
        log.debug("Getting instances for service config: {}", serviceConfigId);
        return ResponseEntity.ok(serviceInstanceManager.getInstancesByServiceConfigId(serviceConfigId));
    }

    /**
     * 获取单个实例
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceInstanceDTO> getInstance(@PathVariable Long id) {
        log.debug("Getting instance: {}", id);
        return serviceInstanceManager.getInstance(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建实例
     */
    @PostMapping("/service/{serviceConfigId}")
    public ResponseEntity<ServiceInstanceDTO> createInstance(
            @PathVariable Long serviceConfigId,
            @RequestBody CreateServiceInstanceRequest request) {
        log.info("Creating instance for service config: {}", serviceConfigId);
        ServiceInstanceDTO created = serviceInstanceManager.createInstance(serviceConfigId, request);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新实例
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceInstanceDTO> updateInstance(
            @PathVariable Long id,
            @RequestBody CreateServiceInstanceRequest request) {
        log.info("Updating instance: {}", id);
        ServiceInstanceDTO updated = serviceInstanceManager.updateInstance(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除实例
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstance(@PathVariable Long id) {
        log.info("Deleting instance: {}", id);
        serviceInstanceManager.deleteInstance(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 更新健康状态
     */
    @PostMapping("/{id}/health")
    public ResponseEntity<Void> updateHealthStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> healthData) {
        log.info("Updating health status for instance: {}", id);
        serviceInstanceManager.updateHealthStatus(
                id,
                healthData.get("healthStatus"),
                healthData.get("errorMessage"));
        return ResponseEntity.ok().build();
    }
}

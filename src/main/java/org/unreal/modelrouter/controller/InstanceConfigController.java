package org.unreal.modelrouter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.service.ServiceInstanceManager;

import java.util.List;

/**
 * 实例配置控制器
 * v1.5.2: 代理到 ServiceInstanceController 的简化版本，使用 DTO
 */
@Slf4j
@RestController
@RequestMapping("/api/instance-configs")
@RequiredArgsConstructor
public class InstanceConfigController {

    private final ServiceInstanceManager serviceInstanceManager;

    /**
     * 获取指定服务的所有实例配置
     */
    @GetMapping("/service/{serviceConfigId}")
    public ResponseEntity<List<ServiceInstanceDTO>> getInstanceConfigs(
            @PathVariable final Long serviceConfigId) {
        log.debug("Getting instance configs for service: {}", serviceConfigId);
        return ResponseEntity.ok(serviceInstanceManager.getInstancesByServiceConfigId(serviceConfigId));
    }

    /**
     * 创建实例配置
     */
    @PostMapping("/service/{serviceConfigId}")
    public ResponseEntity<ServiceInstanceDTO> createInstanceConfig(
            @PathVariable final Long serviceConfigId,
            @RequestBody final CreateServiceInstanceRequest request) {
        log.info("Creating instance config for service: {}", serviceConfigId);
        ServiceInstanceDTO created = serviceInstanceManager.createInstance(serviceConfigId, request);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新实例配置
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceInstanceDTO> updateInstanceConfig(
            @PathVariable final Long id,
            @RequestBody final CreateServiceInstanceRequest request) {
        log.info("Updating instance config: {}", id);
        ServiceInstanceDTO updated = serviceInstanceManager.updateInstance(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除实例配置
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstanceConfig(@PathVariable final Long id) {
        log.info("Deleting instance config: {}", id);
        serviceInstanceManager.deleteInstance(id);
        return ResponseEntity.ok().build();
    }
}

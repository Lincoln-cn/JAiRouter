package org.unreal.modelrouter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.dto.ServiceConfigDTO;
import org.unreal.modelrouter.service.ServiceConfigManager;

import java.util.List;

/**
 * 服务配置控制器
 * v1.5.2: 使用 JPA 实现，使用 DTO 替代 Map
 */
@Slf4j
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceConfigController {

    private final ServiceConfigManager serviceConfigManager;

    /**
     * 获取所有服务配置
     */
    @GetMapping
    public ResponseEntity<List<ServiceConfigDTO>> getAllServices() {
        log.debug("Getting all service configs");
        return ResponseEntity.ok(serviceConfigManager.getAllServiceConfigs());
    }

    /**
     * 获取指定类型的服务配置
     */
    @GetMapping("/{serviceType}")
    public ResponseEntity<ServiceConfigDTO> getService(@PathVariable final String serviceType) {
        log.debug("Getting service config for type: {}", serviceType);
        return serviceConfigManager.getServiceConfig(serviceType)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建或更新服务配置
     */
    @PostMapping("/{serviceType}")
    public ResponseEntity<ServiceConfigDTO> saveService(
            @PathVariable final String serviceType,
            @RequestBody final CreateServiceConfigRequest request) {
        log.info("Saving service config for type: {}", serviceType);
        ServiceConfigDTO saved = serviceConfigManager.saveServiceConfig(serviceType, request);
        return ResponseEntity.ok(saved);
    }

    /**
     * 删除服务配置
     */
    @DeleteMapping("/{serviceType}")
    public ResponseEntity<Void> deleteService(@PathVariable final String serviceType) {
        log.info("Deleting service config for type: {}", serviceType);
        serviceConfigManager.deleteServiceConfig(serviceType);
        return ResponseEntity.ok().build();
    }
}

package org.unreal.modelrouter.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.response.ErrorResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ModelManagerController {

    private final ConfigurationService configurationService;

    @Autowired
    public ModelManagerController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * 获取当前所有配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCurrentConfig() {
        return ResponseEntity.ok(configurationService.getAllConfigurations());
    }

    /**
     * 添加新的服务实例
     */
    @PostMapping("/services/{serviceType}")
    public ResponseEntity<Void> addServiceInstance(
            @PathVariable String serviceType,
            @RequestBody ModelRouterProperties.ModelInstance modelInstance) {
        configurationService.addServiceInstance(serviceType, modelInstance);
        return ResponseEntity.ok().build();
    }

    /**
     * 更新服务配置
     */
    @PutMapping("/services/{serviceType}")
    public ResponseEntity<Void> updateServiceConfig(
            @PathVariable String serviceType,
            @RequestBody Map<String, Object> serviceConfig) {
        configurationService.updateServiceConfig(serviceType, serviceConfig);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除服务实例
     */
    @PostMapping("/services/{serviceType}/instances")
    public ResponseEntity<Void> removeServiceInstance(
            @PathVariable String serviceType,
            @RequestBody ModelRouterProperties.ModelInstance modelInstance) {
        String instanceId = modelInstance.getName() + "@" + modelInstance.getBaseUrl();
        configurationService.removeServiceInstance(serviceType, instanceId);
        return ResponseEntity.ok().build();
    }
}

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
import org.unreal.modelrouter.dto.ServiceConfigDTO;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务配置控制器 - 负责服务基础配置管理
 * 提供适配器、负载均衡等基础配置的独立接口
 */
@Slf4j
@RestController
@RequestMapping("/api/config/service")
@RequiredArgsConstructor
@Tag(name = "服务配置管理", description = "提供服务基础配置（适配器、负载均衡等）的独立管理接口")
public class ServiceConfigController {

    private final DatabaseConfigService databaseConfigService;

    /**
     * 更新服务适配器配置
     */
    @PutMapping("/{serviceType}/adapter")
    @Operation(summary = "更新服务适配器", description = "更新指定服务的适配器配置")
    @ApiResponse(responseCode = "200", description = "适配器更新成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<ServiceConfigDTO>> updateAdapter(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "适配器配置", required = true,
                    schema = @Schema(implementation = AdapterUpdateRequest.class))
            @RequestBody AdapterUpdateRequest request) {
        try {
            log.info("更新服务适配器：serviceType={}, adapter={}", serviceType, request.getAdapter());
            
            // 验证参数
            if (request.getAdapter() == null || request.getAdapter().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("adapter 不能为空"));
            }
            
            // 获取现有配置
            Map<String, Object> existingConfig = databaseConfigService.getServiceConfig(serviceType);
            if (existingConfig == null || existingConfig.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            // 构建更新配置 - 保留所有现有配置，只更新 adapter
            Map<String, Object> updateConfig = new HashMap<>(existingConfig);
            updateConfig.put("adapter", request.getAdapter());
            
            // 调用数据库服务更新
            Map<String, Object> updatedConfig = databaseConfigService.updateServiceConfig(serviceType, updateConfig);
            
            log.info("服务适配器更新成功：serviceType={}, adapter={}", serviceType, request.getAdapter());
            
            // 构建返回 DTO
            ServiceConfigDTO responseDTO = ServiceConfigDTO.builder()
                    .serviceType(serviceType)
                    .adapter(request.getAdapter())
                    .loadBalanceType(getNestedValue(existingConfig, "loadBalance", "type"))
                    .loadBalanceHashAlgorithm(getNestedValue(existingConfig, "loadBalance", "hashAlgorithm"))
                    .description((String) existingConfig.get("description"))
                    .build();
            
            return ResponseEntity.ok(RouterResponse.success(responseDTO, "适配器更新成功"));
            
        } catch (IllegalArgumentException e) {
            log.error("更新适配器失败：serviceType={}, error={}", serviceType, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("更新适配器失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("更新失败：" + e.getMessage()));
        }
    }

    /**
     * 更新服务负载均衡配置
     */
    @PutMapping("/{serviceType}/load-balance")
    @Operation(summary = "更新负载均衡配置", description = "更新指定服务的负载均衡配置")
    @ApiResponse(responseCode = "200", description = "负载均衡配置更新成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<ServiceConfigDTO>> updateLoadBalance(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "负载均衡配置", required = true,
                    schema = @Schema(implementation = LoadBalanceUpdateRequest.class))
            @RequestBody LoadBalanceUpdateRequest request) {
        try {
            log.info("更新负载均衡配置：serviceType={}, type={}", serviceType, request.getType());
            
            // 验证参数
            if (request.getType() == null || request.getType().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("负载均衡类型不能为空"));
            }
            
            // 验证负载均衡类型
            if (!isValidLoadBalanceType(request.getType())) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("无效的负载均衡类型：" + request.getType() + 
                            "。支持的类型：random, round-robin, least-connections, ip-hash"));
            }
            
            // 获取现有配置
            Map<String, Object> existingConfig = databaseConfigService.getServiceConfig(serviceType);
            if (existingConfig == null || existingConfig.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            // 构建更新配置 - 保留所有现有配置，只更新 loadBalance
            Map<String, Object> updateConfig = new HashMap<>(existingConfig);
            Map<String, Object> loadBalance = new HashMap<>();
            loadBalance.put("type", request.getType());
            if (request.getHashAlgorithm() != null) {
                loadBalance.put("hashAlgorithm", request.getHashAlgorithm());
            }
            updateConfig.put("loadBalance", loadBalance);
            
            // 调用数据库服务更新
            Map<String, Object> updatedConfig = databaseConfigService.updateServiceConfig(serviceType, updateConfig);
            
            log.info("负载均衡配置更新成功：serviceType={}, type={}", serviceType, request.getType());
            
            // 构建返回 DTO
            ServiceConfigDTO responseDTO = ServiceConfigDTO.builder()
                    .serviceType(serviceType)
                    .adapter((String) existingConfig.get("adapter"))
                    .loadBalanceType(request.getType())
                    .loadBalanceHashAlgorithm(request.getHashAlgorithm())
                    .description((String) existingConfig.get("description"))
                    .build();
            
            return ResponseEntity.ok(RouterResponse.success(responseDTO, "负载均衡配置更新成功"));
            
        } catch (IllegalArgumentException e) {
            log.error("更新负载均衡配置失败：serviceType={}, error={}", serviceType, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("更新负载均衡配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("更新失败：" + e.getMessage()));
        }
    }

    /**
     * 获取服务配置
     */
    @GetMapping("/{serviceType}")
    @Operation(summary = "获取服务配置", description = "获取指定服务的完整配置")
    @ApiResponse(responseCode = "200", description = "成功获取配置")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<ServiceConfigDTO>> getServiceConfig(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType) {
        try {
            Map<String, Object> config = databaseConfigService.getServiceConfig(serviceType);
            if (config == null || config.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            // 构建返回 DTO
            ServiceConfigDTO responseDTO = ServiceConfigDTO.builder()
                    .serviceType(serviceType)
                    .adapter((String) config.get("adapter"))
                    .loadBalanceType(getNestedValue(config, "loadBalance", "type"))
                    .loadBalanceHashAlgorithm(getNestedValue(config, "loadBalance", "hashAlgorithm"))
                    .description((String) config.get("description"))
                    .build();
            
            return ResponseEntity.ok(RouterResponse.success(responseDTO, "获取配置成功"));
        } catch (Exception e) {
            log.error("获取服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取配置失败：" + e.getMessage()));
        }
    }

    /**
     * 删除服务
     */
    @DeleteMapping("/{serviceType}")
    @Operation(summary = "删除服务", description = "删除指定的服务类型")
    @ApiResponse(responseCode = "200", description = "服务删除成功")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<Void>> deleteService(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType) {
        try {
            log.info("删除服务：serviceType={}", serviceType);
            
            // 获取现有配置，验证服务是否存在
            Map<String, Object> existingConfig = databaseConfigService.getServiceConfig(serviceType);
            if (existingConfig == null || existingConfig.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            // 调用数据库服务删除
            boolean deleted = databaseConfigService.deleteService(serviceType);
            
            if (deleted) {
                log.info("服务删除成功：serviceType={}", serviceType);
                return ResponseEntity.ok(RouterResponse.success(null, "服务删除成功"));
            } else {
                return ResponseEntity.internalServerError()
                        .body(RouterResponse.error("服务删除失败"));
            }
            
        } catch (IllegalArgumentException e) {
            log.error("删除服务失败：serviceType={}, error={}", serviceType, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("删除服务失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("删除失败：" + e.getMessage()));
        }
    }

    /**
     * 验证负载均衡类型是否有效
     */
    private boolean isValidLoadBalanceType(String type) {
        if (type == null) return false;
        String normalizedType = type.toLowerCase().replaceAll("[\\s_-]+", "");
        return "random".equals(normalizedType) ||
               "roundrobin".equals(normalizedType) ||
               "round-robin".equals(normalizedType) ||
               "leastconnections".equals(normalizedType) ||
               "least-connections".equals(normalizedType) ||
               "iphash".equals(normalizedType) ||
               "ip-hash".equals(normalizedType);
    }

    /**
     * 获取嵌套 Map 中的值
     */
    @SuppressWarnings("unchecked")
    private String getNestedValue(Map<String, Object> map, String... keys) {
        Map<String, Object> current = map;
        for (int i = 0; i < keys.length - 1; i++) {
            if (current == null || !current.containsKey(keys[i])) {
                return null;
            }
            Object value = current.get(keys[i]);
            if (value instanceof Map) {
                current = (Map<String, Object>) value;
            } else {
                return null;
            }
        }
        if (current != null && current.containsKey(keys[keys.length - 1])) {
            return String.valueOf(current.get(keys[keys.length - 1]));
        }
        return null;
    }

    /**
     * 适配器更新请求 DTO
     */
    public static class AdapterUpdateRequest {
        private String adapter;
        
        public String getAdapter() { return adapter; }
        public void setAdapter(String adapter) { this.adapter = adapter; }
    }

    /**
     * 负载均衡更新请求 DTO
     */
    public static class LoadBalanceUpdateRequest {
        private String type;
        private String hashAlgorithm;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getHashAlgorithm() { return hashAlgorithm; }
        public void setHashAlgorithm(String hashAlgorithm) { this.hashAlgorithm = hashAlgorithm; }
    }
}

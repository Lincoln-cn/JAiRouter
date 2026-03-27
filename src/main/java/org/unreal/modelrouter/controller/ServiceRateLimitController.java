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

import java.util.HashMap;
import java.util.Map;

/**
 * 服务限流配置控制器 - 负责服务限流配置管理
 */
@Slf4j
@RestController
@RequestMapping("/api/config/service")
@RequiredArgsConstructor
@Tag(name = "限流配置管理", description = "提供服务限流配置的独立管理接口")
public class ServiceRateLimitController {

    private final DatabaseConfigService databaseConfigService;

    /**
     * 更新服务限流配置
     */
    @PutMapping("/{serviceType}/rate-limit")
    @Operation(summary = "更新限流配置", description = "更新指定服务的限流配置")
    @ApiResponse(responseCode = "200", description = "限流配置更新成功")
    @ApiResponse(responseCode = "400", description = "参数验证失败")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateRateLimit(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "限流配置", required = true,
                    schema = @Schema(example = "{\"enabled\": true, \"algorithm\": \"token-bucket\", \"capacity\": 100, \"rate\": 10, \"scope\": \"service\"}"))
            @RequestBody Map<String, Object> rateLimitConfig) {
        try {
            log.info("更新限流配置：serviceType={}, config={}", serviceType, rateLimitConfig);
            
            // 验证参数
            if (rateLimitConfig == null) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("限流配置不能为空"));
            }
            
            // 获取现有配置
            Map<String, Object> existingConfig = databaseConfigService.getServiceConfig(serviceType);
            if (existingConfig == null || existingConfig.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            // 构建更新配置
            Map<String, Object> updateConfig = new HashMap<>();
            updateConfig.put("rateLimit", rateLimitConfig);
            
            // 保留现有配置
            if (existingConfig.containsKey("adapter")) {
                updateConfig.put("adapter", existingConfig.get("adapter"));
            }
            if (existingConfig.containsKey("loadBalance")) {
                updateConfig.put("loadBalance", existingConfig.get("loadBalance"));
            }
            if (existingConfig.containsKey("circuitBreaker")) {
                updateConfig.put("circuitBreaker", existingConfig.get("circuitBreaker"));
            }
            if (existingConfig.containsKey("fallback")) {
                updateConfig.put("fallback", existingConfig.get("fallback"));
            }
            if (existingConfig.containsKey("instances")) {
                updateConfig.put("instances", existingConfig.get("instances"));
            }
            
            // 更新配置
            Map<String, Object> updatedConfig = databaseConfigService.updateServiceConfig(serviceType, updateConfig);
            
            log.info("限流配置更新成功：serviceType={}", serviceType);
            return ResponseEntity.ok(RouterResponse.success(updatedConfig, "限流配置更新成功"));
            
        } catch (IllegalArgumentException e) {
            log.error("更新限流配置失败：serviceType={}, error={}", serviceType, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("更新限流配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("更新失败：" + e.getMessage()));
        }
    }

    /**
     * 启用/禁用限流
     */
    @PatchMapping("/{serviceType}/rate-limit/enable")
    @Operation(summary = "切换限流开关", description = "启用或禁用指定服务的限流功能")
    @ApiResponse(responseCode = "200", description = "操作成功")
    @ApiResponse(responseCode = "404", description = "服务不存在")
    public ResponseEntity<RouterResponse<Map<String, Object>>> toggleRateLimit(
            @Parameter(description = "服务类型", example = "chat")
            @PathVariable("serviceType") String serviceType,
            @Parameter(description = "是否启用", required = true)
            @RequestParam("enabled") boolean enabled) {
        try {
            log.info("切换限流开关：serviceType={}, enabled={}", serviceType, enabled);
            
            // 获取现有配置
            Map<String, Object> existingConfig = databaseConfigService.getServiceConfig(serviceType);
            if (existingConfig == null || existingConfig.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("服务不存在：" + serviceType));
            }
            
            // 构建更新配置
            Map<String, Object> updateConfig = new HashMap<>();
            Map<String, Object> rateLimit = new HashMap<>();
            
            // 保留现有限流配置
            if (existingConfig.containsKey("rateLimit") && existingConfig.get("rateLimit") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existingRateLimit = (Map<String, Object>) existingConfig.get("rateLimit");
                rateLimit.putAll(existingRateLimit);
            }
            
            // 更新开关状态
            rateLimit.put("enabled", enabled);
            updateConfig.put("rateLimit", rateLimit);
            
            // 保留其他配置
            if (existingConfig.containsKey("adapter")) {
                updateConfig.put("adapter", existingConfig.get("adapter"));
            }
            if (existingConfig.containsKey("loadBalance")) {
                updateConfig.put("loadBalance", existingConfig.get("loadBalance"));
            }
            if (existingConfig.containsKey("circuitBreaker")) {
                updateConfig.put("circuitBreaker", existingConfig.get("circuitBreaker"));
            }
            if (existingConfig.containsKey("fallback")) {
                updateConfig.put("fallback", existingConfig.get("fallback"));
            }
            if (existingConfig.containsKey("instances")) {
                updateConfig.put("instances", existingConfig.get("instances"));
            }
            
            // 更新配置
            Map<String, Object> updatedConfig = databaseConfigService.updateServiceConfig(serviceType, updateConfig);
            
            log.info("限流开关切换成功：serviceType={}, enabled={}", serviceType, enabled);
            return ResponseEntity.ok(RouterResponse.success(updatedConfig, 
                    enabled ? "限流已启用" : "限流已禁用"));
            
        } catch (IllegalArgumentException e) {
            log.error("切换限流开关失败：serviceType={}, error={}", serviceType, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("切换限流开关失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("操作失败：" + e.getMessage()));
        }
    }
}

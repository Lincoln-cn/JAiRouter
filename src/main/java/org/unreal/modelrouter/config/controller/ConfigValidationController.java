package org.unreal.modelrouter.config.controller;

import org.unreal.modelrouter.config.core.tracker.ConfigSourceTracker;
import org.unreal.modelrouter.config.core.validator.SensitiveConfigValidator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置校验 API 控制器
 * 提供配置状态查询和校验规则定义
 *
 * <p>v2.8.7 新增：配置校验 API</p>
 */
@RestController
@RequestMapping("/api/config")
public class ConfigValidationController {

    private final SensitiveConfigValidator sensitiveConfigValidator;
    private final ConfigSourceTracker configSourceTracker;

    public ConfigValidationController(
            SensitiveConfigValidator sensitiveConfigValidator,
            ConfigSourceTracker configSourceTracker) {
        this.sensitiveConfigValidator = sensitiveConfigValidator;
        this.configSourceTracker = configSourceTracker;
    }

    /**
     * 获取配置来源信息
     * 显示各配置项的加载来源
     */
    @GetMapping("/sources")
    public Map<String, Object> getConfigSources() {
        return configSourceTracker.getConfigSourcesInfo();
    }

    /**
     * 获取校验规则定义
     * 返回各类配置的校验规则
     */
    @GetMapping("/validation-rules")
    public Map<String, Object> getValidationRules() {
        Map<String, Object> rules = new LinkedHashMap<>();

        // 敏感配置规则
        Map<String, Object> sensitiveRules = new HashMap<>();
        sensitiveRules.put("enabled", true);
        sensitiveRules.put("minLength", 32);
        sensitiveRules.put("description", "敏感配置必须使用环境变量");
        sensitiveRules.put("envVars", new String[]{
            "JWT_SECRET",
            "INITIAL_ADMIN_PASSWORD",
            "GPUSTACK_API_TOKEN"
        });
        rules.put("sensitiveConfig", sensitiveRules);

        // 服务器端口规则
        Map<String, Object> integrityRules = new HashMap<>();
        integrityRules.put("server.port", Map.of(
            "type", "integer",
            "min", 1,
            "max", 65535,
            "defaultValue", 8080
        ));
        integrityRules.put("model.load-balance.type", Map.of(
            "type", "enum",
            "validValues", new String[]{"round-robin", "weighted", "least-connections", "ip-hash", "random"},
            "defaultValue", "round-robin"
        ));
        integrityRules.put("model.circuit-breaker.enabled", Map.of(
            "type", "boolean",
            "defaultValue", true
        ));
        integrityRules.put("model.circuit-breaker.failureThreshold", Map.of(
            "type", "integer",
            "min", 1,
            "defaultValue", 5
        ));
        integrityRules.put("model.circuit-breaker.timeoutMs", Map.of(
            "type", "integer",
            "min", 100,
            "max", 300000,
            "defaultValue", 60000,
            "unit", "ms"
        ));
        integrityRules.put("store.type", Map.of(
            "type", "enum",
            "validValues", new String[]{"jpa", "redis", "memory"},
            "defaultValue", "jpa"
        ));
        rules.put("integrityConfig", integrityRules);

        // 配置优先级
        Map<String, Object> priority = new LinkedHashMap<>();
        priority.put("description", "配置加载优先级（从高到低）");
        priority.put("order", new String[]{
            "命令行参数",
            "系统属性",
            "环境变量",
            "外部配置文件",
            "环境特定配置",
            "模块配置文件",
            "默认配置"
        });
        priority.put("recommendation", "生产环境应使用外部配置文件（优先级 4）");
        rules.put("priority", priority);

        return rules;
    }

    /**
     * 获取环境变量指南
     * 返回所有支持的环境变量及其说明
     */
    @GetMapping("/environment-variables")
    public Map<String, Object> getEnvironmentVariablesGuide() {
        Map<String, Object> envVars = new HashMap<>();

        // 必须配置的环境变量
        Map<String, Object> required = new LinkedHashMap<>();
        required.put("JWT_SECRET", Map.of(
            "description", "JWT 密钥",
            "required", true,
            "minLength", 32,
            "example", "openssl rand -base64 32"
        ));
        required.put("INITIAL_ADMIN_PASSWORD", Map.of(
            "description", "管理员密码",
            "required", true,
            "minLength", 8,
            "note", "首次启动后建议修改"
        ));
        envVars.put("required", required);

        // 可选配置
        Map<String, Object> optional = new LinkedHashMap<>();
        optional.put("GPUSTACK_API_TOKEN", Map.of(
            "description", "GPUStack API Token",
            "required", false
        ));
        optional.put("JWT_EXPIRATION_MINUTES", Map.of(
            "description", "令牌过期时间（分钟）",
            "defaultValue", 60
        ));
        optional.put("JWT_REFRESH_EXPIRATION_DAYS", Map.of(
            "description", "刷新令牌过期时间（天）",
            "defaultValue", 7
        ));
        optional.put("REDIS_HOST", Map.of(
            "description", "Redis 主机",
            "defaultValue", "localhost"
        ));
        optional.put("REDIS_PORT", Map.of(
            "description", "Redis 端口",
            "defaultValue", 6379
        ));
        optional.put("REDIS_PASSWORD", Map.of(
            "description", "Redis 密码",
            "required", false
        ));
        optional.put("SERVER_PORT", Map.of(
            "description", "服务器端口",
            "defaultValue", 8080
        ));
        envVars.put("optional", optional);

        return envVars;
    }
}
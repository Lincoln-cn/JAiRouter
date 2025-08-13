package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.monitoring.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitoring.MonitoringProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 监控配置管理控制器
 * 提供监控配置的查询和动态更新功能
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);

    private final MonitoringProperties monitoringProperties;
    private final DynamicMonitoringConfigUpdater configUpdater;

    public MonitoringController(MonitoringProperties monitoringProperties,
                              DynamicMonitoringConfigUpdater configUpdater) {
        this.monitoringProperties = monitoringProperties;
        this.configUpdater = configUpdater;
    }

    /**
     * 获取当前监控配置
     */
    @GetMapping("/config")
    public ResponseEntity<MonitoringConfigResponse> getConfiguration() {
        try {
            MonitoringConfigResponse response = new MonitoringConfigResponse();
            response.enabled = monitoringProperties.isEnabled();
            response.prefix = monitoringProperties.getPrefix();
            response.collectionInterval = monitoringProperties.getCollectionInterval().toString();
            response.enabledCategories = monitoringProperties.getEnabledCategories();
            response.customTags = monitoringProperties.getCustomTags();
            response.sampling = monitoringProperties.getSampling();
            response.performance = monitoringProperties.getPerformance();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取监控配置失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新监控启用状态
     */
    @PutMapping("/config/enabled")
    public ResponseEntity<String> updateEnabled(@RequestBody Map<String, Boolean> request) {
        try {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                return ResponseEntity.badRequest().body("Missing 'enabled' parameter");
            }

            boolean updated = configUpdater.updateEnabled(enabled);
            if (updated) {
                return ResponseEntity.ok("监控状态已更新为: " + enabled);
            } else {
                return ResponseEntity.ok("监控状态无变化");
            }
        } catch (Exception e) {
            logger.error("更新监控启用状态失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新指标前缀
     */
    @PutMapping("/config/prefix")
    public ResponseEntity<String> updatePrefix(@RequestBody Map<String, String> request) {
        try {
            String prefix = request.get("prefix");
            if (prefix == null || prefix.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Missing or empty 'prefix' parameter");
            }

            if (!configUpdater.validateConfigurationChange("prefix", prefix)) {
                return ResponseEntity.badRequest().body("Invalid prefix format");
            }

            boolean updated = configUpdater.updatePrefix(prefix);
            if (updated) {
                return ResponseEntity.ok("指标前缀已更新为: " + prefix);
            } else {
                return ResponseEntity.ok("指标前缀无变化");
            }
        } catch (Exception e) {
            logger.error("更新指标前缀失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新收集间隔
     */
    @PutMapping("/config/collection-interval")
    public ResponseEntity<String> updateCollectionInterval(@RequestBody Map<String, String> request) {
        try {
            String intervalStr = request.get("interval");
            if (intervalStr == null || intervalStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Missing 'interval' parameter");
            }

            Duration interval = Duration.parse(intervalStr);
            if (!configUpdater.validateConfigurationChange("collectionInterval", interval)) {
                return ResponseEntity.badRequest().body("Invalid interval format");
            }

            boolean updated = configUpdater.updateCollectionInterval(interval);
            if (updated) {
                return ResponseEntity.ok("收集间隔已更新为: " + interval);
            } else {
                return ResponseEntity.ok("收集间隔无变化");
            }
        } catch (Exception e) {
            logger.error("更新收集间隔失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新启用的类别
     */
    @PutMapping("/config/categories")
    public ResponseEntity<String> updateEnabledCategories(@RequestBody Map<String, Set<String>> request) {
        try {
            Set<String> categories = request.get("categories");
            if (categories == null) {
                return ResponseEntity.badRequest().body("Missing 'categories' parameter");
            }

            if (!configUpdater.validateConfigurationChange("enabledCategories", categories)) {
                return ResponseEntity.badRequest().body("Invalid categories");
            }

            boolean updated = configUpdater.updateEnabledCategories(categories);
            if (updated) {
                return ResponseEntity.ok("启用类别已更新为: " + categories);
            } else {
                return ResponseEntity.ok("启用类别无变化");
            }
        } catch (Exception e) {
            logger.error("更新启用类别失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新自定义标签
     */
    @PutMapping("/config/custom-tags")
    public ResponseEntity<String> updateCustomTags(@RequestBody Map<String, Map<String, String>> request) {
        try {
            Map<String, String> customTags = request.get("customTags");
            if (customTags == null) {
                return ResponseEntity.badRequest().body("Missing 'customTags' parameter");
            }

            boolean updated = configUpdater.updateCustomTags(customTags);
            if (updated) {
                return ResponseEntity.ok("自定义标签已更新");
            } else {
                return ResponseEntity.ok("自定义标签无变化");
            }
        } catch (Exception e) {
            logger.error("更新自定义标签失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置快照
     */
    @GetMapping("/config/snapshot")
    public ResponseEntity<Map<String, Object>> getConfigurationSnapshot() {
        try {
            Map<String, Object> snapshot = configUpdater.getCurrentConfiguration();
            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            logger.error("获取配置快照失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 监控配置响应对象
     */
    public static class MonitoringConfigResponse {
        public boolean enabled;
        public String prefix;
        public String collectionInterval;
        public Set<String> enabledCategories;
        public Map<String, String> customTags;
        public MonitoringProperties.SamplingConfig sampling;
        public MonitoringProperties.PerformanceConfig performance;
    }
}
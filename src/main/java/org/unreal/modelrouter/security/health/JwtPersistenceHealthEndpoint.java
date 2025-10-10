package org.unreal.modelrouter.security.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.unreal.modelrouter.security.metrics.JwtPersistenceMetricsService;
import org.unreal.modelrouter.security.metrics.StorageHealthMetricsService;
import org.unreal.modelrouter.security.metrics.CleanupMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT持久化健康检查控制器
 * 提供详细的JWT持久化系统健康状态和指标信息
 */
@Controller
@RequestMapping("/actuator/jwt-persistence-health")
@ConditionalOnProperty(name = "management.endpoint.jwt-persistence-health.enabled", havingValue = "true", matchIfMissing = true)
public class JwtPersistenceHealthEndpoint {
    
    private static final Logger log = LoggerFactory.getLogger(JwtPersistenceHealthEndpoint.class);
    
    @Autowired(required = false)
    private JwtPersistenceMetricsService metricsService;
    
    @Autowired(required = false)
    private StorageHealthMetricsService storageHealthService;
    
    @Autowired(required = false)
    private CleanupMetricsService cleanupMetricsService;
    
    @Autowired
    private JwtPersistenceHealthIndicator healthIndicator;
    
    /**
     * 获取完整的健康状态信息
     */
    @GetMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 基本健康状态
            health.put("timestamp", LocalDateTime.now().toString());
            health.put("status", "UP");
            
            // 获取核心健康指标
            Map<String, Object> coreHealth = getCoreHealthStatus();
            health.put("core", coreHealth);
            
            // 获取指标摘要
            if (metricsService != null) {
                Map<String, Object> metrics = metricsService.getMetricsSummary();
                health.put("metrics", metrics);
            }
            
            // 获取存储健康状态
            if (storageHealthService != null) {
                Map<String, Object> storageHealth = storageHealthService.getHealthSummary();
                health.put("storage", storageHealth);
            }
            
            // 获取清理统计
            if (cleanupMetricsService != null) {
                Map<String, Object> cleanupStats = cleanupMetricsService.getCleanupStats();
                health.put("cleanup", cleanupStats);
            }
            
            // 计算整体健康状态
            boolean overallHealthy = calculateOverallHealth(coreHealth);
            health.put("status", overallHealthy ? "UP" : "DOWN");
            
        } catch (Exception e) {
            log.error("获取JWT持久化健康状态失败", e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now().toString());
        }
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 获取特定组件的健康状态
     */
    @GetMapping("/{component}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> healthByComponent(@PathVariable String component) {
        Map<String, Object> componentHealth = new HashMap<>();
        
        try {
            componentHealth.put("timestamp", LocalDateTime.now().toString());
            componentHealth.put("component", component);
            
            switch (component.toLowerCase()) {
                case "core":
                    componentHealth.putAll(getCoreHealthStatus());
                    break;
                    
                case "metrics":
                    if (metricsService != null) {
                        componentHealth.putAll(metricsService.getMetricsSummary());
                    } else {
                        componentHealth.put("status", "DISABLED");
                        componentHealth.put("message", "Metrics service not available");
                    }
                    break;
                    
                case "storage":
                    if (storageHealthService != null) {
                        componentHealth.putAll(storageHealthService.getHealthSummary());
                    } else {
                        componentHealth.put("status", "DISABLED");
                        componentHealth.put("message", "Storage health service not available");
                    }
                    break;
                    
                case "cleanup":
                    if (cleanupMetricsService != null) {
                        componentHealth.putAll(cleanupMetricsService.getCleanupStats());
                        componentHealth.put("cleanupHealthy", cleanupMetricsService.isCleanupHealthy());
                    } else {
                        componentHealth.put("status", "DISABLED");
                        componentHealth.put("message", "Cleanup metrics service not available");
                    }
                    break;
                    
                default:
                    componentHealth.put("status", "NOT_FOUND");
                    componentHealth.put("message", "Unknown component: " + component);
                    componentHealth.put("availableComponents", new String[]{"core", "metrics", "storage", "cleanup"});
                    break;
            }
            
        } catch (Exception e) {
            log.error("获取组件 {} 健康状态失败", component, e);
            componentHealth.put("status", "ERROR");
            componentHealth.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(componentHealth);
    }
    
    /**
     * 获取核心健康状态
     */
    private Map<String, Object> getCoreHealthStatus() {
        Map<String, Object> coreHealth = new HashMap<>();
        
        try {
            // 使用健康指示器获取核心状态
            Map<String, Object> health = healthIndicator.getHealthStatus();
            coreHealth.putAll(health);
            
        } catch (Exception e) {
            log.warn("获取核心健康状态失败: {}", e.getMessage());
            coreHealth.put("status", "DOWN");
            coreHealth.put("error", e.getMessage());
        }
        
        return coreHealth;
    }
    
    /**
     * 计算整体健康状态
     */
    private boolean calculateOverallHealth(Map<String, Object> coreHealth) {
        try {
            // 检查核心状态
            String coreStatus = (String) coreHealth.get("status");
            if (!"UP".equals(coreStatus)) {
                return false;
            }
            
            // 检查存储健康状态
            if (storageHealthService != null) {
                Map<String, Object> storageHealth = storageHealthService.getHealthSummary();
                Boolean compositeHealthy = (Boolean) storageHealth.get("compositeHealthy");
                if (compositeHealthy != null && !compositeHealthy) {
                    return false;
                }
            }
            
            // 检查清理健康状态
            if (cleanupMetricsService != null) {
                if (!cleanupMetricsService.isCleanupHealthy()) {
                    // 清理不健康不影响整体状态，只记录警告
                    log.warn("清理操作不健康，但不影响整体系统状态");
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("计算整体健康状态失败", e);
            return false;
        }
    }
}
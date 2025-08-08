package org.unreal.modelrouter.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.model.ModelRouterProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置管理服务
 * 负责在应用启动时合并配置文件和持久化存储中的配置
 */
@Service
public class ConfigurationStoreService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationStoreService.class);
    private static final String CONFIG_KEY = "model-router-config";

    private final StoreManager storeManager;
    private final ModelRouterProperties modelRouterProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfigurationStoreService(StoreManager storeManager, ModelRouterProperties modelRouterProperties) {
        this.storeManager = storeManager;
        this.modelRouterProperties = modelRouterProperties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 在应用启动时合并配置
     */
    @PostConstruct
    public void initializeConfiguration() {
        logger.info("开始初始化配置合并");
        mergeConfigurations();
    }

    /**
     * 合并配置，存储中的配置优先级更高
     */
    public void mergeConfigurations() {
        try {
            // 检查存储中是否存在配置
            if (storeManager.exists(CONFIG_KEY)) {
                logger.info("发现存储中的配置，开始合并配置");
                
                // 获取存储中的配置
                Map<String, Object> storedConfig = storeManager.getConfig(CONFIG_KEY);
                if (storedConfig != null && !storedConfig.isEmpty()) {
                    logger.info("存储中的配置加载成功");
                    // 将存储中的配置更新到当前配置中
                    updatePropertiesFromMap(modelRouterProperties, storedConfig);
                }
            } else {
                logger.info("未发现存储中的配置，使用应用默认配置");
                // 将当前应用配置保存到存储中
                saveCurrentConfiguration();
            }
        } catch (Exception e) {
            logger.error("配置合并过程中发生错误", e);
        }
    }

    /**
     * 保存当前应用配置到存储中
     */
    public void saveCurrentConfiguration() {
        try {
            Map<String, Object> configMap = objectMapper.convertValue(modelRouterProperties, Map.class);
            storeManager.saveConfig(CONFIG_KEY, configMap);
            logger.info("当前配置已保存到存储中");
        } catch (Exception e) {
            logger.error("保存当前配置时发生错误", e);
        }
    }

    /**
     * 更新配置并保存到存储中
     * @param newConfig 新配置
     */
    public void updateConfiguration(Map<String, Object> newConfig) {
        try {
            storeManager.updateConfig(CONFIG_KEY, newConfig);
            // 同时更新内存中的配置
            updatePropertiesFromMap(modelRouterProperties, newConfig);
            logger.info("配置已更新并保存到存储中");
        } catch (Exception e) {
            logger.error("更新配置时发生错误", e);
            throw new RuntimeException("配置更新失败", e);
        }
    }

    /**
     * 获取当前生效的配置
     * @return 配置Map
     */
    public Map<String, Object> getCurrentConfiguration() {
        if (storeManager.exists(CONFIG_KEY)) {
            return storeManager.getConfig(CONFIG_KEY);
        }
        return new HashMap<>();
    }
    
    /**
     * 将Map转换为ModelRouterProperties对象并更新到当前配置中
     * @param properties 目标配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    private void updatePropertiesFromMap(ModelRouterProperties properties, Map<String, Object> map) {
        if (map.containsKey("loadBalance")) {
            Map<String, Object> loadBalanceMap = (Map<String, Object>) map.get("loadBalance");
            ModelRouterProperties.LoadBalanceConfig loadBalance = properties.getLoadBalance();
            if (loadBalanceMap.containsKey("type")) {
                loadBalance.setType((String) loadBalanceMap.get("type"));
            }
            if (loadBalanceMap.containsKey("hashAlgorithm")) {
                loadBalance.setHashAlgorithm((String) loadBalanceMap.get("hashAlgorithm"));
            }
        }
        
        if (map.containsKey("adapter")) {
            properties.setAdapter((String) map.get("adapter"));
        }
        
        if (map.containsKey("rateLimit")) {
            updateRateLimitConfig(properties.getRateLimit(), (Map<String, Object>) map.get("rateLimit"));
        }
        
        if (map.containsKey("circuitBreaker")) {
            updateCircuitBreakerConfig(properties.getCircuitBreaker(), (Map<String, Object>) map.get("circuitBreaker"));
        }
        
        if (map.containsKey("fallback")) {
            updateFallbackConfig(properties.getFallback(), (Map<String, Object>) map.get("fallback"));
        }
        
        if (map.containsKey("services")) {
            // 简化处理，实际应该更复杂地处理服务配置更新
            properties.setServices(objectMapper.convertValue(map.get("services"), Map.class));
        }
    }
    
    /**
     * 更新限流配置
     * @param rateLimit 目标限流配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    private void updateRateLimitConfig(ModelRouterProperties.RateLimitConfig rateLimit, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            rateLimit.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("algorithm")) {
            rateLimit.setAlgorithm((String) map.get("algorithm"));
        }
        if (map.containsKey("capacity")) {
            rateLimit.setCapacity(((Number) map.get("capacity")).longValue());
        }
        if (map.containsKey("rate")) {
            rateLimit.setRate(((Number) map.get("rate")).longValue());
        }
        if (map.containsKey("scope")) {
            rateLimit.setScope((String) map.get("scope"));
        }
        if (map.containsKey("key")) {
            rateLimit.setKey((String) map.get("key"));
        }
        if (map.containsKey("clientIpEnable")) {
            rateLimit.setClientIpEnable((Boolean) map.get("clientIpEnable"));
        }
    }
    
    /**
     * 更新熔断器配置
     * @param circuitBreaker 目标熔断器配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    private void updateCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig circuitBreaker, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            circuitBreaker.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("failureThreshold")) {
            circuitBreaker.setFailureThreshold((Integer) map.get("failureThreshold"));
        }
        if (map.containsKey("timeout")) {
            circuitBreaker.setTimeout(((Number) map.get("timeout")).longValue());
        }
        if (map.containsKey("successThreshold")) {
            circuitBreaker.setSuccessThreshold((Integer) map.get("successThreshold"));
        }
    }
    
    /**
     * 更新降级配置
     * @param fallback 目标降级配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    private void updateFallbackConfig(ModelRouterProperties.FallbackConfig fallback, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            fallback.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("strategy")) {
            fallback.setStrategy((String) map.get("strategy"));
        }
        if (map.containsKey("cacheSize")) {
            fallback.setCacheSize((Integer) map.get("cacheSize"));
        }
        if (map.containsKey("cacheTtl")) {
            fallback.setCacheTtl(((Number) map.get("cacheTtl")).longValue());
        }
    }
}

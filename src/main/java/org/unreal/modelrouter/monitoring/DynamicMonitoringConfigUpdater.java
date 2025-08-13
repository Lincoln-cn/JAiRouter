package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态监控配置更新器
 * 支持运行时更新监控配置而无需重启应用
 */
@Component
public class DynamicMonitoringConfigUpdater {

    private static final Logger logger = LoggerFactory.getLogger(DynamicMonitoringConfigUpdater.class);

    private final MonitoringProperties monitoringProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final ConfigurableEnvironment environment;

    // 缓存当前配置状态
    private final Map<String, Object> currentConfig = new ConcurrentHashMap<>();

    public DynamicMonitoringConfigUpdater(MonitoringProperties monitoringProperties,
                                        ApplicationEventPublisher eventPublisher,
                                        ConfigurableEnvironment environment) {
        this.monitoringProperties = monitoringProperties;
        this.eventPublisher = eventPublisher;
        this.environment = environment;
        
        // 初始化当前配置缓存
        cacheCurrentConfiguration();
    }

    /**
     * 更新监控启用状态
     */
    public boolean updateEnabled(boolean enabled) {
        try {
            boolean oldValue = monitoringProperties.isEnabled();
            if (oldValue != enabled) {
                monitoringProperties.setEnabled(enabled);
                currentConfig.put("enabled", enabled);
                
                logger.info("监控功能状态已更新: {} -> {}", oldValue, enabled);
                publishConfigurationChangeEvent("enabled", oldValue, enabled);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("更新监控启用状态失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 更新指标前缀
     */
    public boolean updatePrefix(String prefix) {
        try {
            String oldValue = monitoringProperties.getPrefix();
            if (!oldValue.equals(prefix)) {
                monitoringProperties.setPrefix(prefix);
                currentConfig.put("prefix", prefix);
                
                logger.info("监控指标前缀已更新: {} -> {}", oldValue, prefix);
                publishConfigurationChangeEvent("prefix", oldValue, prefix);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("更新监控指标前缀失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 更新收集间隔
     */
    public boolean updateCollectionInterval(Duration interval) {
        try {
            Duration oldValue = monitoringProperties.getCollectionInterval();
            if (!oldValue.equals(interval)) {
                monitoringProperties.setCollectionInterval(interval);
                currentConfig.put("collectionInterval", interval);
                
                logger.info("监控收集间隔已更新: {} -> {}", oldValue, interval);
                publishConfigurationChangeEvent("collectionInterval", oldValue, interval);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("更新监控收集间隔失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 更新启用的类别
     */
    public boolean updateEnabledCategories(Set<String> categories) {
        try {
            Set<String> oldValue = monitoringProperties.getEnabledCategories();
            if (!oldValue.equals(categories)) {
                monitoringProperties.setEnabledCategories(categories);
                currentConfig.put("enabledCategories", categories);
                
                logger.info("监控启用类别已更新: {} -> {}", oldValue, categories);
                publishConfigurationChangeEvent("enabledCategories", oldValue, categories);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("更新监控启用类别失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 更新自定义标签
     */
    public boolean updateCustomTags(Map<String, String> customTags) {
        try {
            Map<String, String> oldValue = monitoringProperties.getCustomTags();
            if (!oldValue.equals(customTags)) {
                monitoringProperties.setCustomTags(customTags);
                currentConfig.put("customTags", customTags);
                
                logger.info("监控自定义标签已更新: {} -> {}", oldValue, customTags);
                publishConfigurationChangeEvent("customTags", oldValue, customTags);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("更新监控自定义标签失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 更新采样率配置
     */
    public boolean updateSamplingConfig(MonitoringProperties.SamplingConfig samplingConfig) {
        try {
            MonitoringProperties.SamplingConfig oldValue = monitoringProperties.getSampling();
            
            // 比较采样配置是否有变化
            boolean changed = false;
            if (oldValue.getRequestMetrics() != samplingConfig.getRequestMetrics()) {
                oldValue.setRequestMetrics(samplingConfig.getRequestMetrics());
                changed = true;
            }
            if (oldValue.getBackendMetrics() != samplingConfig.getBackendMetrics()) {
                oldValue.setBackendMetrics(samplingConfig.getBackendMetrics());
                changed = true;
            }
            if (oldValue.getInfrastructureMetrics() != samplingConfig.getInfrastructureMetrics()) {
                oldValue.setInfrastructureMetrics(samplingConfig.getInfrastructureMetrics());
                changed = true;
            }
            
            if (changed) {
                currentConfig.put("sampling", samplingConfig);
                logger.info("监控采样配置已更新");
                publishConfigurationChangeEvent("sampling", oldValue, samplingConfig);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("更新监控采样配置失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前配置快照
     */
    public Map<String, Object> getCurrentConfiguration() {
        return Map.copyOf(currentConfig);
    }

    /**
     * 验证配置变更
     */
    public boolean validateConfigurationChange(String key, Object newValue) {
        try {
            switch (key) {
                case "enabled":
                    return newValue instanceof Boolean;
                case "prefix":
                    return newValue instanceof String && 
                           ((String) newValue).matches("^[a-zA-Z][a-zA-Z0-9_]*$");
                case "collectionInterval":
                    return newValue instanceof Duration && 
                           !((Duration) newValue).isNegative() && 
                           !((Duration) newValue).isZero();
                case "enabledCategories":
                    if (!(newValue instanceof Set)) return false;
                    Set<?> categories = (Set<?>) newValue;
                    Set<String> validCategories = Set.of("system", "business", "infrastructure");
                    return categories.stream().allMatch(validCategories::contains);
                default:
                    return true; // 其他配置项默认允许
            }
        } catch (Exception e) {
            logger.warn("配置验证失败: key={}, value={}, error={}", key, newValue, e.getMessage());
            return false;
        }
    }

    /**
     * 回滚配置变更
     */
    public boolean rollbackConfiguration(String key) {
        try {
            Object cachedValue = currentConfig.get(key);
            if (cachedValue == null) {
                logger.warn("无法回滚配置，未找到缓存值: {}", key);
                return false;
            }

            // 根据key类型进行回滚
            switch (key) {
                case "enabled":
                    monitoringProperties.setEnabled((Boolean) cachedValue);
                    break;
                case "prefix":
                    monitoringProperties.setPrefix((String) cachedValue);
                    break;
                case "collectionInterval":
                    monitoringProperties.setCollectionInterval((Duration) cachedValue);
                    break;
                case "enabledCategories":
                    monitoringProperties.setEnabledCategories((Set<String>) cachedValue);
                    break;
                case "customTags":
                    monitoringProperties.setCustomTags((Map<String, String>) cachedValue);
                    break;
                default:
                    logger.warn("不支持回滚的配置项: {}", key);
                    return false;
            }

            logger.info("配置已回滚: key={}, value={}", key, cachedValue);
            return true;
        } catch (Exception e) {
            logger.error("配置回滚失败: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 缓存当前配置
     */
    private void cacheCurrentConfiguration() {
        currentConfig.put("enabled", monitoringProperties.isEnabled());
        currentConfig.put("prefix", monitoringProperties.getPrefix());
        currentConfig.put("collectionInterval", monitoringProperties.getCollectionInterval());
        currentConfig.put("enabledCategories", monitoringProperties.getEnabledCategories());
        currentConfig.put("customTags", monitoringProperties.getCustomTags());
        currentConfig.put("sampling", monitoringProperties.getSampling());
        currentConfig.put("performance", monitoringProperties.getPerformance());
    }

    /**
     * 发布配置变更事件
     */
    private void publishConfigurationChangeEvent(String key, Object oldValue, Object newValue) {
        try {
            MonitoringConfigurationChangeEvent event = new MonitoringConfigurationChangeEvent(
                this, key, oldValue, newValue);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("发布配置变更事件失败: {}", e.getMessage());
        }
    }

    /**
     * 监控配置变更事件
     */
    public static class MonitoringConfigurationChangeEvent {
        private final Object source;
        private final String key;
        private final Object oldValue;
        private final Object newValue;

        public MonitoringConfigurationChangeEvent(Object source, String key, Object oldValue, Object newValue) {
            this.source = source;
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public Object getSource() { return source; }
        public String getKey() { return key; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
    }
}
package org.unreal.modelrouter.monitoring.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.monitoring.config.MonitorConfigurationChangeEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 动态监控配置更新器
 * 支持运行时动态更新监控配置，无需重启应用
 */
@Component
public class DynamicMonitoringConfigUpdater {

    private static final Logger logger = LoggerFactory.getLogger(DynamicMonitoringConfigUpdater.class);
    
    private final MonitoringProperties monitoringProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, Object> currentConfig = new ConcurrentHashMap<>();

    public DynamicMonitoringConfigUpdater(MonitoringProperties monitoringProperties,
                                       ApplicationEventPublisher eventPublisher) {
        this.monitoringProperties = monitoringProperties;
        this.eventPublisher = eventPublisher;
        initializeCurrentConfig();
    }

    /**
     * 初始化当前配置快照
     */
    private void initializeCurrentConfig() {
        currentConfig.put("enabled", monitoringProperties.isEnabled());
        currentConfig.put("prefix", monitoringProperties.getPrefix());
        currentConfig.put("collectionInterval", monitoringProperties.getCollectionInterval());
        currentConfig.put("enabledCategories", monitoringProperties.getEnabledCategories());
        currentConfig.put("sampling", monitoringProperties.getSampling());
        currentConfig.put("performance", monitoringProperties.getPerformance());
        logger.info("初始化监控配置快照完成");
    }

    /**
     * 更新基础配置
     */
    public boolean updateBasicConfig(boolean enabled, String prefix, Duration collectionInterval, Set<String> enabledCategories) {
        try {
            boolean changed = false;
            
            if (monitoringProperties.isEnabled() != enabled) {
                monitoringProperties.setEnabled(enabled);
                currentConfig.put("enabled", enabled);
                changed = true;
            }
            
            if (!monitoringProperties.getPrefix().equals(prefix)) {
                monitoringProperties.setPrefix(prefix);
                currentConfig.put("prefix", prefix);
                changed = true;
            }
            
            if (!monitoringProperties.getCollectionInterval().equals(collectionInterval)) {
                monitoringProperties.setCollectionInterval(collectionInterval);
                currentConfig.put("collectionInterval", collectionInterval);
                changed = true;
            }
            
            if (!monitoringProperties.getEnabledCategories().equals(enabledCategories)) {
                monitoringProperties.setEnabledCategories(enabledCategories);
                currentConfig.put("enabledCategories", enabledCategories);
                changed = true;
            }
            
            if (changed) {
                logger.info("监控基础配置已更新: enabled={}, prefix={}, collectionInterval={}, enabledCategories={}",
                           enabled, prefix, collectionInterval, enabledCategories);
                publishConfigurationChangeEvent("basic", null, Map.of(
                    "enabled", enabled,
                    "prefix", prefix,
                    "collectionInterval", collectionInterval,
                    "enabledCategories", enabledCategories
                ));
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("更新监控基础配置失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 更新采样配置
     */
    public boolean updateSamplingConfig(MonitoringProperties.Sampling samplingConfig) {
        try {
            MonitoringProperties.Sampling oldValue = monitoringProperties.getSampling();
            
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
            if (oldValue.getTraceMetrics() != samplingConfig.getTraceMetrics()) {
                oldValue.setTraceMetrics(samplingConfig.getTraceMetrics());
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
    public void rollbackConfiguration(String key, Object oldValue) {
        try {
            switch (key) {
                case "enabled":
                    if (oldValue instanceof Boolean) {
                        monitoringProperties.setEnabled((Boolean) oldValue);
                        currentConfig.put(key, oldValue);
                    }
                    break;
                case "prefix":
                    if (oldValue instanceof String) {
                        monitoringProperties.setPrefix((String) oldValue);
                        currentConfig.put(key, oldValue);
                    }
                    break;
                case "collectionInterval":
                    if (oldValue instanceof Duration) {
                        monitoringProperties.setCollectionInterval((Duration) oldValue);
                        currentConfig.put(key, oldValue);
                    }
                    break;
                case "enabledCategories":
                    if (oldValue instanceof Set) {
                        monitoringProperties.setEnabledCategories((Set<String>) oldValue);
                        currentConfig.put(key, oldValue);
                    }
                    break;
                case "sampling":
                    if (oldValue instanceof MonitoringProperties.Sampling) {
                        MonitoringProperties.Sampling currentSampling = monitoringProperties.getSampling();
                        MonitoringProperties.Sampling oldSampling = (MonitoringProperties.Sampling) oldValue;
                        currentSampling.setRequestMetrics(oldSampling.getRequestMetrics());
                        currentSampling.setBackendMetrics(oldSampling.getBackendMetrics());
                        currentSampling.setInfrastructureMetrics(oldSampling.getInfrastructureMetrics());
                        currentSampling.setTraceMetrics(oldSampling.getTraceMetrics());
                        currentConfig.put(key, oldValue);
                    }
                    break;
                default:
                    logger.warn("不支持回滚的配置项: {}", key);
                    return;
            }
            logger.info("配置项 {} 已回滚到旧值", key);
        } catch (Exception e) {
            logger.error("回滚配置项 {} 失败: {}", key, e.getMessage());
        }
    }

    /**
     * 发布配置变更事件
     */
    private void publishConfigurationChangeEvent(String configType, Object oldValue, Object newValue) {
        String changeId = UUID.randomUUID().toString();
        MonitorConfigurationChangeEvent event = new MonitorConfigurationChangeEvent(this, changeId , configType, oldValue, newValue);
        eventPublisher.publishEvent(event);
        logger.debug("已发布配置变更事件: type={}, oldValue={}, newValue={}", configType, oldValue, newValue);
    }
}
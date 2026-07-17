package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.monitor.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪配置管理器 - v2.22.0
 *
 * 负责追踪采样配置的管理，从 ConfigurationService 提取。
 *
 * @author JAiRouter Team
 * @since v2.22.0
 */
@Component("configTracingConfigManager")
public class TracingConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(TracingConfigManager.class);

    private final StoreManager storeManager;
    private final ConfigMergeService configMergeService;
    private final ConfigVersionManager configVersionManager;
    private final SamplingConfigurationValidator samplingValidator;

    private static final String CURRENT_KEY = "model-router-config";

    public TracingConfigManager(final StoreManager storeManager,
                                final ConfigMergeService configMergeService,
                                final ConfigVersionManager configVersionManager,
                                final SamplingConfigurationValidator samplingValidator) {
        this.storeManager = storeManager;
        this.configMergeService = configMergeService;
        this.configVersionManager = configVersionManager;
        this.samplingValidator = samplingValidator;
    }

    /**
     * 获取追踪采样配置
     *
     * @return 采样配置Map
     */
    public Map<String, Object> getTracingSamplingConfig() {
        Map<String, Object> currentConfig = getCurrentConfig();

        // 提取追踪配置
        if (currentConfig.containsKey("tracing")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tracingConfig = (Map<String, Object>) currentConfig.get("tracing");

            // 提取采样配置
            if (tracingConfig.containsKey("sampling")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> samplingConfig = (Map<String, Object>) tracingConfig.get("sampling");
                // 确保返回的配置包含所有默认键
                Map<String, Object> result = createDefaultSamplingConfig();
                result.putAll(samplingConfig);
                return result;
            }
        }

        // 返回默认配置
        return createDefaultSamplingConfig();
    }

    /**
     * 更新追踪采样配置
     *
     * @param samplingConfig 新的采样配置
     * @param createNewVersion 是否创建新版本
     */
    public void updateTracingSamplingConfig(final Map<String, Object> samplingConfig,
                                            final boolean createNewVersion) {
        logger.info("更新追踪采样配置");

        // 验证配置
        validateSamplingConfig(samplingConfig);

        Map<String, Object> currentConfig;
        if (createNewVersion) {
            currentConfig = getCurrentPersistedConfig();
        } else {
            currentConfig = configMergeService.getPersistedConfig();
        }

        // 获取或创建追踪配置
        @SuppressWarnings("unchecked")
        Map<String, Object> tracingConfig = (Map<String, Object>) currentConfig.computeIfAbsent(
                "tracing", k -> new HashMap<String, Object>());

        // 更新采样配置
        tracingConfig.put("sampling", samplingConfig);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "updateTracingSampling");
        metadata.put("operationDetail", "更新追踪采样配置");
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        if (createNewVersion) {
            // 保存为新版本并刷新配置
            configVersionManager.saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }

        logger.info("追踪采样配置更新成功");
    }

    /**
     * 从版本配置中提取采样配置
     *
     * @param versionConfig 版本配置
     * @return 采样配置，如果不存在返回null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractSamplingConfigFromVersion(final Map<String, Object> versionConfig) {
        if (versionConfig == null) {
            return null;
        }

        if (versionConfig.containsKey("tracing")) {
            Map<String, Object> tracingConfig = (Map<String, Object>) versionConfig.get("tracing");
            if (tracingConfig != null && tracingConfig.containsKey("sampling")) {
                return (Map<String, Object>) tracingConfig.get("sampling");
            }
        }

        return null;
    }

    // ==================== 私有方法 ====================

    /**
     * 验证采样配置
     *
     * @param samplingConfig 采样配置
     */
    private void validateSamplingConfig(final Map<String, Object> samplingConfig) {
        if (samplingConfig == null || samplingConfig.isEmpty()) {
            return;
        }

        // 处理前端发送的serviceConfigs字段，转换为后端需要的serviceRatios字段
        if (samplingConfig.containsKey("serviceConfigs")) {
            Object serviceConfigsObj = samplingConfig.get("serviceConfigs");
            if (serviceConfigsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> serviceConfigsList = (List<Map<String, Object>>) serviceConfigsObj;
                Map<String, Double> serviceRatios = new HashMap<>();
                for (Map<String, Object> serviceConfig : serviceConfigsList) {
                    if (serviceConfig.containsKey("service") && serviceConfig.containsKey("rate")) {
                        Object serviceObj = serviceConfig.get("service");
                        Object rateObj = serviceConfig.get("rate");
                        if (serviceObj instanceof String && rateObj instanceof Number) {
                            String service = (String) serviceObj;
                            Double rate = ((Number) rateObj).doubleValue() / 100.0; // 前端以百分比形式发送
                            serviceRatios.put(service, rate);
                        }
                    }
                }
                // 移除serviceConfigs字段，添加serviceRatios字段
                samplingConfig.remove("serviceConfigs");
                samplingConfig.put("serviceRatios", serviceRatios);
            }
        }

        try {
            // 将Map转换为TracingConfiguration.SamplingConfig对象进行验证
            TracingConfiguration.SamplingConfig config = convertMapToSamplingConfig(samplingConfig);

            SamplingConfigurationValidator.ValidationResult result = samplingValidator.validateSamplingConfig(config);
            if (!result.isValid()) {
                throw new IllegalArgumentException("采样配置验证失败: " + result.getErrorMessage());
            }

            if (result.hasWarnings()) {
                logger.warn("采样配置验证警告: {}", result.getWarningMessage());
            }
        } catch (Exception e) {
            logger.warn("采样配置验证过程中发生错误，跳过验证: {}", e.getMessage());
        }
    }

    /**
     * 创建默认采样配置
     *
     * @return 默认采样配置
     */
    private Map<String, Object> createDefaultSamplingConfig() {
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("ratio", 1.0);
        defaultConfig.put("serviceRatios", new HashMap<String, Double>());
        defaultConfig.put("alwaysSample", new ArrayList<String>());
        defaultConfig.put("neverSample", new ArrayList<String>());
        defaultConfig.put("rules", new ArrayList<Map<String, Object>>());
        return defaultConfig;
    }

    /**
     * 将Map转换为SamplingConfig对象
     *
     * @param configMap 配置Map
     * @return SamplingConfig对象
     */
    @SuppressWarnings("unchecked")
    private TracingConfiguration.SamplingConfig convertMapToSamplingConfig(final Map<String, Object> configMap) {
        TracingConfiguration.SamplingConfig config = new TracingConfiguration.SamplingConfig();

        if (configMap.containsKey("ratio")) {
            Object ratioObj = configMap.get("ratio");
            if (ratioObj instanceof Number) {
                config.setRatio(((Number) ratioObj).doubleValue());
            }
        }

        if (configMap.containsKey("serviceRatios")) {
            Object serviceRatiosObj = configMap.get("serviceRatios");
            if (serviceRatiosObj instanceof Map) {
                Map<String, Object> serviceRatiosMap = (Map<String, Object>) serviceRatiosObj;
                Map<String, Double> serviceRatios = new HashMap<>();
                for (Map.Entry<String, Object> entry : serviceRatiosMap.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        serviceRatios.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                config.setServiceRatios(serviceRatios);
            }
        }

        if (configMap.containsKey("alwaysSample")) {
            Object alwaysSampleObj = configMap.get("alwaysSample");
            if (alwaysSampleObj instanceof List) {
                config.setAlwaysSample((List<String>) alwaysSampleObj);
            }
        }

        if (configMap.containsKey("neverSample")) {
            Object neverSampleObj = configMap.get("neverSample");
            if (neverSampleObj instanceof List) {
                config.setNeverSample((List<String>) neverSampleObj);
            }
        }

        if (configMap.containsKey("rules")) {
            Object rulesObj = configMap.get("rules");
            if (rulesObj instanceof List) {
                List<Map<String, Object>> rulesList = (List<Map<String, Object>>) rulesObj;
                List<TracingConfiguration.SamplingConfig.SamplingRule> rules = new ArrayList<>();

                for (Map<String, Object> ruleMap : rulesList) {
                    TracingConfiguration.SamplingConfig.SamplingRule rule =
                            new TracingConfiguration.SamplingConfig.SamplingRule();

                    if (ruleMap.containsKey("condition")) {
                        rule.setCondition((String) ruleMap.get("condition"));
                    }
                    if (ruleMap.containsKey("ratio")) {
                        Object ratioObj = ruleMap.get("ratio");
                        if (ratioObj instanceof Number) {
                            rule.setRatio(((Number) ratioObj).doubleValue());
                        }
                    }
                    rules.add(rule);
                }
                config.setRules(rules);
            }
        }

        // 处理自适应配置
        if (configMap.containsKey("adaptive")) {
            Object adaptiveObj = configMap.get("adaptive");
            if (adaptiveObj instanceof Map) {
                Map<String, Object> adaptiveMap = (Map<String, Object>) adaptiveObj;
                TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig =
                        new TracingConfiguration.SamplingConfig.AdaptiveConfig();

                if (adaptiveMap.containsKey("enabled")) {
                    Object enabledObj = adaptiveMap.get("enabled");
                    if (enabledObj instanceof Boolean) {
                        adaptiveConfig.setEnabled((Boolean) enabledObj);
                    }
                }
                if (adaptiveMap.containsKey("targetSpansPerSecond")) {
                    Object targetObj = adaptiveMap.get("targetSpansPerSecond");
                    if (targetObj instanceof Number) {
                        adaptiveConfig.setTargetSpansPerSecond(((Number) targetObj).longValue());
                    }
                }
                if (adaptiveMap.containsKey("minRatio")) {
                    Object minRatioObj = adaptiveMap.get("minRatio");
                    if (minRatioObj instanceof Number) {
                        adaptiveConfig.setMinRatio(((Number) minRatioObj).doubleValue());
                    }
                }
                if (adaptiveMap.containsKey("maxRatio")) {
                    Object maxRatioObj = adaptiveMap.get("maxRatio");
                    if (maxRatioObj instanceof Number) {
                        adaptiveConfig.setMaxRatio(((Number) maxRatioObj).doubleValue());
                    }
                }
                if (adaptiveMap.containsKey("adjustmentInterval")) {
                    Object intervalObj = adaptiveMap.get("adjustmentInterval");
                    if (intervalObj instanceof Number) {
                        adaptiveConfig.setAdjustmentInterval(((Number) intervalObj).longValue());
                    }
                }

                config.setAdaptive(adaptiveConfig);
            }
        }

        return config;
    }

    /**
     * 获取当前配置
     *
     * @return 当前配置
     */
    private Map<String, Object> getCurrentConfig() {
        return configMergeService.getPersistedConfig();
    }

    /**
     * 获取当前持久化配置
     *
     * @return 持久化配置
     */
    private Map<String, Object> getCurrentPersistedConfig() {
        return configMergeService.getPersistedConfig();
    }

    // ==================== TraceConfig 管理方法 (v2.6.15) ====================

    /**
     * 更新追踪配置
     *
     * @param traceConfigMap   追踪配置 Map
     * @param createNewVersion 是否创建新版本
     */
    public void updateTraceConfig(final Map<String, Object> traceConfigMap, final boolean createNewVersion) {
        logger.info("更新追踪配置");

        Map<String, Object> currentConfig;
        if (createNewVersion) {
            currentConfig = getCurrentPersistedConfig();
        } else {
            currentConfig = configMergeService.getPersistedConfig();
        }

        // 更新配置
        currentConfig.put("trace", traceConfigMap);

        if (createNewVersion) {
            configVersionManager.saveAsNewVersion(currentConfig);
        } else {
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }

        logger.info("追踪配置更新成功");
    }

    /**
     * 删除追踪配置
     *
     * @param createNewVersion 是否创建新版本
     */
    public void deleteTraceConfig(final boolean createNewVersion) {
        logger.info("删除追踪配置");

        Map<String, Object> currentConfig;
        if (createNewVersion) {
            currentConfig = getCurrentPersistedConfig();
        } else {
            currentConfig = configMergeService.getPersistedConfig();
        }

        // 删除追踪配置
        currentConfig.remove("trace");

        if (createNewVersion) {
            configVersionManager.saveAsNewVersion(currentConfig);
        } else {
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }

        logger.info("追踪配置删除成功");
    }
}
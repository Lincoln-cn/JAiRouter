package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置合并服务
 * 负责合并应用配置和持久化存储中的配置
 */
@Service
public class ConfigMergeService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigMergeService.class);

    private final StoreManager storeManager;
    private final ModelRouterProperties modelRouterProperties;
    private final ConfigurationHelper configurationHelper;

    @Autowired
    public ConfigMergeService(StoreManager storeManager, ModelRouterProperties modelRouterProperties, ConfigurationHelper configurationHelper) {
        this.storeManager = storeManager;
        this.modelRouterProperties = modelRouterProperties;
        this.configurationHelper = configurationHelper;
    }

    /**
     * 合并配置，存储中的配置优先级更高
     * @return 合并后的配置
     */
    public Map<String, Object> mergeConfigurations() {
        logger.info("开始合并配置文件和存储中的配置");

        // 获取存储中的配置
        Map<String, Object> storedConfig = new HashMap<>();
        Iterable<String> keys = storeManager.getAllKeys();
        for (String key : keys) {
            Map<String, Object> config = storeManager.getConfig(key);
            if (config != null) {
                storedConfig.put(key, config);
            }
        }

        // 获取应用配置
        Map<String, Object> appConfig = configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);

        // 合并配置，存储中的配置优先
        Map<String, Object> mergedConfig = new HashMap<>(appConfig);
        mergedConfig.putAll(storedConfig);

        // 处理服务实例去重：同name同baseUrl的实例只保留一个
        for (Map.Entry<String, Object> entry : mergedConfig.entrySet()) {
            Object serviceConfigObj = entry.getValue();
            if (serviceConfigObj instanceof Map) {
                Map<String, Object> serviceConfigMap = (Map<String, Object>) serviceConfigObj;
                Object instancesObj = serviceConfigMap.get("instances");
                if (instancesObj instanceof List) {
                    List<Map<String, Object>> instances = (List<Map<String, Object>>) instancesObj;
                    // 使用LinkedHashMap保持插入顺序并去重
                    Map<String, Map<String, Object>> uniqueInstances = new HashMap<>();
                    for (Map<String, Object> instance : instances) {
                        String name = (String) instance.get("name");
                        String baseUrl = (String) instance.get("baseUrl");
                        if (name != null && baseUrl != null) {
                            String uniqueKey = name + "@" + baseUrl;
                            uniqueInstances.put(uniqueKey, instance);
                        }
                    }
                    // 更新instances列表为去重后的列表
                    serviceConfigMap.put("instances", new ArrayList<>(uniqueInstances.values()));
                }
            }
        }

        logger.info("配置合并完成，存储中的配置优先级更高，并已完成实例去重处理");
        return mergedConfig;
    }
}

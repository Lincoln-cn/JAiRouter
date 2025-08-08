package org.unreal.modelrouter.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.StoreManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理服务
 * 提供配置的增删改查功能
 */
@Service
public class ConfigurationService {

    private final StoreManager storeManager;
    private final ModelRouterProperties modelRouterProperties;
    private final ModelServiceRegistry modelServiceRegistry;
    private final ConfigurationHelper configurationHelper;

    @Autowired
    public ConfigurationService(StoreManager storeManager,
                              ModelRouterProperties modelRouterProperties,
                              ModelServiceRegistry modelServiceRegistry,
                              ConfigurationHelper configurationHelper) {
        this.storeManager = storeManager;
        this.modelRouterProperties = modelRouterProperties;
        this.modelServiceRegistry = modelServiceRegistry;
        this.configurationHelper = configurationHelper;
    }

    /**
     * 获取所有配置
     * @return 配置Map
     */
    public Map<String, Object> getAllConfigurations() {
        return configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
    }

    /**
     * 更新服务配置
     * @param serviceType 服务类型
     * @param serviceConfig 服务配置
     */
    public void updateServiceConfig(String serviceType, Map<String, Object> serviceConfig) {
        // 更新内存中的配置
        if (modelRouterProperties.getServices() == null) {
            modelRouterProperties.setServices(new HashMap<>());
        }
        
        // 转换 Map 到 ServiceConfig 对象
        ModelRouterProperties.ServiceConfig config = configurationHelper.convertMapToServiceConfig(serviceConfig);
        modelRouterProperties.getServices().put(serviceType, config);
        
        // 更新 ModelServiceRegistry
        updateModelServiceRegistry(serviceType, config);
        
        // 持久化到存储
        persistConfiguration();
    }
    
    /**
     * 添加服务实例
     * @param serviceType 服务类型
     * @param instance 实例配置
     */
    public void addServiceInstance(String serviceType, ModelRouterProperties.ModelInstance instance) {
        // 获取当前服务配置
        Map<String, Object> config = configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
        
        Map<String, Object> serviceConfig = (Map<String, Object>) config.getOrDefault("services", new HashMap<>());
        Map<String, Object> specificServiceConfig = (Map<String, Object>) serviceConfig.getOrDefault(serviceType, new HashMap<>());
        List<Map<String, Object>> instances = (List<Map<String, Object>>) specificServiceConfig.getOrDefault("instances", new ArrayList<>());

        // 将ModelInstance转换为Map并添加
        Map<String, Object> instanceMap = configurationHelper.convertInstanceToMap(instance);
        instances.add(instanceMap);
        specificServiceConfig.put("instances", instances);
        serviceConfig.put(serviceType, specificServiceConfig);
        config.put("services", serviceConfig);

        // 更新内存配置
        if (modelRouterProperties.getServices() == null) {
            modelRouterProperties.setServices(new HashMap<>());
        }
        ModelRouterProperties.ServiceConfig serviceConfigObj = configurationHelper.convertMapToServiceConfig(serviceConfig);
        modelRouterProperties.getServices().put(serviceType, serviceConfigObj);
        
        // 更新ModelServiceRegistry中的实例注册表
        updateModelServiceRegistry(serviceType, serviceConfigObj);
        
        // 持久化到存储
        persistConfiguration();
    }
    
    /**
     * 删除服务实例
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     */
    public void removeServiceInstance(String serviceType, String instanceId) {
        // 获取当前服务配置
        Map<String, Object> config = configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
        
        Map<String, Object> serviceConfigMap = (Map<String, Object>) config.getOrDefault("services", new HashMap<>());
        Map<String, Object> specificServiceConfig = (Map<String, Object>) serviceConfigMap.getOrDefault(serviceType, new HashMap<>());
        List<Map<String, Object>> instances = (List<Map<String, Object>>) specificServiceConfig.getOrDefault("instances", new ArrayList<>());
        
        // 从列表中移除匹配的实例
        instances.removeIf(instance -> {
            String name = (String) instance.get("name");
            String baseUrl = (String) instance.get("baseUrl");
            String id = name + "@" + baseUrl;
            return id.equals(instanceId);
        });
        
        specificServiceConfig.put("instances", instances);
        serviceConfigMap.put(serviceType, specificServiceConfig);
        config.put("services", serviceConfigMap);

        // 更新内存配置
        if (modelRouterProperties.getServices() == null) {
            modelRouterProperties.setServices(new HashMap<>());
        }
        ModelRouterProperties.ServiceConfig serviceConfigObj = configurationHelper.convertMapToServiceConfig(serviceConfigMap);
        modelRouterProperties.getServices().put(serviceType, serviceConfigObj);
        
        // 更新ModelServiceRegistry中的实例注册表
        updateModelServiceRegistry(serviceType, serviceConfigObj);
        
        // 持久化到存储
        persistConfiguration();
    }

    private void updateModelServiceRegistry(String serviceType, ModelRouterProperties.ServiceConfig config) {
        try {
            ModelServiceRegistry.ServiceType type = ModelServiceRegistry.ServiceType.valueOf(serviceType.toLowerCase().replace("-", "_"));
            
            // 更新实例
            List<ModelRouterProperties.ModelInstance> modelInstances = config.getInstances() != null ? 
                new ArrayList<>(config.getInstances()) : new ArrayList<>();
            modelServiceRegistry.updateServiceInstances(type, modelInstances);
            
            // 更新适配器
            if (config.getAdapter() != null) {
                modelServiceRegistry.updateServiceAdapter(type, config.getAdapter());
            }
        } catch (Exception e) {
            // 记录错误但不中断操作
            System.err.println("Failed to update ModelServiceRegistry: " + e.getMessage());
        }
    }
    
    private void persistConfiguration() {
        Map<String, Object> configToStore = configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
        storeManager.updateConfig("model-router-config", configToStore);
    }

    /**
     * 批量更新配置
     * @param configs 配置Map
     */
    public void updateConfigurations(Map<String, Object> configs) {
        // 更新运行时配置
        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            // 这里可以添加更复杂的配置更新逻辑
        }
        
        // 保存到持久化存储
        if (storeManager.exists("model-router-config")) {
            Map<String, Object> storedConfig = storeManager.getConfig("model-router-config");
            if (storedConfig != null) {
                storedConfig.putAll(configs);
                storeManager.updateConfig("model-router-config", storedConfig);
            }
        } else {
            storeManager.saveConfig("model-router-config", configs);
        }
    }

    /**
     * 重置配置为默认值
     */
    public void resetToDefault() {
        // 清除存储中的配置
        if (storeManager.exists("model-router-config")) {
            storeManager.deleteConfig("model-router-config");
        }
        
        // 重新加载运行时配置（这里简化处理，实际应该重新加载默认配置）
        modelRouterProperties.setServices(null);
    }
}

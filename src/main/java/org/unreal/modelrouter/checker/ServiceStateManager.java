package org.unreal.modelrouter.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务状态管理器
 * 管理服务和实例的健康状态，避免循环依赖
 */
@Component
public class ServiceStateManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceStateManager.class);

    // 存储每个服务类型的健康状态
    private final Map<String, Boolean> serviceHealthStatus = new ConcurrentHashMap<>();

    // 存储每个具体实例的健康状态
    private final Map<String, Boolean> instanceHealthStatus = new ConcurrentHashMap<>();

    /**
     * 获取特定服务类型的健康状态
     */
    public boolean isServiceHealthy(String serviceType) {
        return serviceHealthStatus.getOrDefault(serviceType, true); // 默认认为是健康的
    }

    /**
     * 获取特定实例的健康状态
     *
     * @param serviceType 服务类型
     * @param instance    模型实例
     * @return 实例是否健康
     */
    public boolean isInstanceHealthy(String serviceType, ModelRouterProperties.ModelInstance instance) {
        String instanceKey = serviceType + ":" + instance.getName() + "@" + instance.getBaseUrl();
        return instanceHealthStatus.getOrDefault(instanceKey, true); // 默认认为是健康的
    }

    /**
     * 更新服务健康状态
     *
     * @param serviceType 服务类型
     * @param isHealthy   是否健康
     */
    public void updateServiceHealthStatus(String serviceType, boolean isHealthy) {
        serviceHealthStatus.put(serviceType, isHealthy);
    }

    /**
     * 更新实例健康状态
     *
     * @param serviceType 服务类型
     * @param instance    实例
     * @param isHealthy   是否健康
     */
    public void updateInstanceHealthStatus(String serviceType, ModelRouterProperties.ModelInstance instance, boolean isHealthy) {
        String instanceKey = serviceType + ":" + instance.getName() + "@" + instance.getBaseUrl();
        instanceHealthStatus.put(instanceKey, isHealthy);
    }

    /**
     * 获取所有服务的健康状态
     */
    public Map<String, Boolean> getAllServiceHealthStatus() {
        return new ConcurrentHashMap<>(serviceHealthStatus);
    }

    /**
     * 获取所有实例的健康状态
     */
    public Map<String, Boolean> getAllInstanceHealthStatus() {
        return new ConcurrentHashMap<>(instanceHealthStatus);
    }
}

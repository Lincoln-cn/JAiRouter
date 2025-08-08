package org.unreal.modelrouter.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.factory.ComponentFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * 重构后的负载均衡管理器
 * 消除了重复的创建逻辑，统一使用ComponentFactory
 */
@Component
public class LoadBalancerManager {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerManager.class);

    private final Map<ModelServiceRegistry.ServiceType, LoadBalancer> loadBalancers =
            new EnumMap<>(ModelServiceRegistry.ServiceType.class);

    private final ComponentFactory componentFactory;
    private final ConfigurationHelper configHelper;
    private final ModelRouterProperties properties;

    public LoadBalancerManager(ComponentFactory componentFactory,
                               ConfigurationHelper configHelper,
                               ModelRouterProperties properties) {
        this.componentFactory = componentFactory;
        this.configHelper = configHelper;
        this.properties = properties;
        initializeLoadBalancers();
    }

    /**
     * 初始化所有服务的负载均衡器
     */
    private void initializeLoadBalancers() {
        logger.info("Initializing load balancers for all service types");

        ModelRouterProperties.LoadBalanceConfig globalConfig = properties.getLoadBalance();

        for (ModelServiceRegistry.ServiceType serviceType : ModelServiceRegistry.ServiceType.values()) {
            try {
                ModelRouterProperties.LoadBalanceConfig serviceConfig =
                        getServiceLoadBalanceConfig(serviceType);
                ModelRouterProperties.LoadBalanceConfig effectiveConfig =
                        configHelper.getEffectiveLoadBalanceConfig(serviceConfig, globalConfig);

                LoadBalancer loadBalancer = componentFactory.createLoadBalancer(effectiveConfig);
                loadBalancers.put(serviceType, loadBalancer);

                logger.debug("Initialized load balancer for service type: {} with strategy: {}",
                        serviceType, effectiveConfig.getType());
            } catch (Exception e) {
                logger.error("Failed to initialize load balancer for service type: {}. Error: {}",
                        serviceType, e.getMessage());
                // 使用默认配置作为降级方案
                LoadBalancer defaultLoadBalancer = componentFactory.createLoadBalancer(
                        configHelper.createDefaultLoadBalanceConfig());
                loadBalancers.put(serviceType, defaultLoadBalancer);
            }
        }

        logger.info("Load balancer initialization completed. Total load balancers: {}",
                loadBalancers.size());
    }

    /**
     * 获取服务特定的负载均衡配置
     */
    private ModelRouterProperties.LoadBalanceConfig getServiceLoadBalanceConfig(
            ModelServiceRegistry.ServiceType serviceType) {
        if (properties.getServices() == null) {
            return null;
        }

        String serviceKey = configHelper.getServiceConfigKey(serviceType);
        ModelRouterProperties.ServiceConfig serviceConfig = properties.getServices().get(serviceKey);

        return serviceConfig != null ? serviceConfig.getLoadBalance() : null;
    }

    /**
     * 获取指定服务类型的负载均衡器
     */
    public LoadBalancer getLoadBalancer(ModelServiceRegistry.ServiceType serviceType) {
        LoadBalancer loadBalancer = loadBalancers.get(serviceType);
        if (loadBalancer == null) {
            logger.warn("No load balancer found for service type: {}, creating default", serviceType);
            // 创建默认负载均衡器
            loadBalancer = componentFactory.createLoadBalancer(
                    configHelper.createDefaultLoadBalanceConfig());
            loadBalancers.put(serviceType, loadBalancer);
        }
        return loadBalancer;
    }

    /**
     * 为指定服务类型设置负载均衡器
     */
    public void setLoadBalancer(ModelServiceRegistry.ServiceType serviceType, LoadBalancer loadBalancer) {
        if (loadBalancer == null) {
            throw new IllegalArgumentException("LoadBalancer cannot be null");
        }
        loadBalancers.put(serviceType, loadBalancer);
        logger.info("Set custom load balancer for service type: {}", serviceType);
    }

    /**
     * 动态更新单个服务的负载均衡配置
     */
    public void updateServiceLoadBalancer(ModelServiceRegistry.ServiceType serviceType,
                                          ModelRouterProperties.LoadBalanceConfig config) {
        try {
            LoadBalancer newLoadBalancer = componentFactory.createLoadBalancer(config);
            LoadBalancer oldLoadBalancer = loadBalancers.put(serviceType, newLoadBalancer);

            logger.info("Updated load balancer for service type: {} from {} to {}",
                    serviceType,
                    oldLoadBalancer != null ? oldLoadBalancer.getClass().getSimpleName() : "null",
                    newLoadBalancer.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Failed to update load balancer for service type: {}. Error: {}",
                    serviceType, e.getMessage());
            throw new RuntimeException("Failed to update load balancer", e);
        }
    }

    /**
     * 获取所有负载均衡器的状态信息
     */
    public Map<ModelServiceRegistry.ServiceType, String> getLoadBalancerStatus() {
        Map<ModelServiceRegistry.ServiceType, String> status =
                new EnumMap<>(ModelServiceRegistry.ServiceType.class);

        loadBalancers.forEach((serviceType, loadBalancer) -> {
            status.put(serviceType, loadBalancer.getClass().getSimpleName());
        });

        return status;
    }

    /**
     * 重新加载所有配置
     * 用于配置热更新
     */
    public synchronized void updateConfiguration() {
        logger.info("Updating load balancer configuration");

        Map<ModelServiceRegistry.ServiceType, LoadBalancer> oldLoadBalancers =
                new EnumMap<>(loadBalancers);

        try {
            loadBalancers.clear();
            initializeLoadBalancers();

            logger.info("Load balancer configuration updated successfully");
        } catch (Exception e) {
            logger.error("Failed to update load balancer configuration, rolling back. Error: {}",
                    e.getMessage());
            // 回滚到旧配置
            loadBalancers.clear();
            loadBalancers.putAll(oldLoadBalancers);
            throw new RuntimeException("Failed to update configuration", e);
        }
    }

    /**
     * 验证所有负载均衡器配置
     */
    public boolean validateConfiguration() {
        ModelRouterProperties.LoadBalanceConfig globalConfig = properties.getLoadBalance();

        // 验证全局配置
        if (!componentFactory.validateLoadBalanceConfig(globalConfig)) {
            logger.warn("Invalid global load balance configuration");
            return false;
        }

        // 验证各服务配置
        if (properties.getServices() != null) {
            for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry :
                    properties.getServices().entrySet()) {

                String serviceKey = entry.getKey();
                ModelRouterProperties.ServiceConfig serviceConfig = entry.getValue();

                if (serviceConfig.getLoadBalance() != null &&
                        !componentFactory.validateLoadBalanceConfig(serviceConfig.getLoadBalance())) {
                    logger.warn("Invalid load balance configuration for service: {}", serviceKey);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * LoadBalancerManager需要添加的方法
     * 用于支持动态配置更新时重新初始化负载均衡器
     */

    /**
     * 重新初始化指定服务的负载均衡器
     * @param serviceType 服务类型
     * @param loadBalanceConfig 新的负载均衡配置
     */
    public void reinitializeLoadBalancer(ModelServiceRegistry.ServiceType serviceType,
                                         ModelRouterProperties.LoadBalanceConfig loadBalanceConfig) {
        try {
            // 移除旧的负载均衡器
            loadBalancers.remove(serviceType);

            // 创建新的负载均衡器
            LoadBalancer newLoadBalancer = componentFactory.createLoadBalancer(loadBalanceConfig);
            loadBalancers.put(serviceType, newLoadBalancer);

            logger.info("服务 {} 的负载均衡器已重新初始化，类型: {}",
                    serviceType, loadBalanceConfig.getType());
        } catch (Exception e) {
            logger.warn("重新初始化服务 {} 的负载均衡器失败: {}", serviceType, e.getMessage());
            // 如果失败，创建默认负载均衡器
            LoadBalancer defaultLoadBalancer = createDefaultLoadBalancer();
            loadBalancers.put(serviceType, defaultLoadBalancer);
        }
    }

    /**
     * 创建默认负载均衡器
     * @return 默认负载均衡器
     */
    private LoadBalancer createDefaultLoadBalancer() {
        // 返回随机负载均衡器作为默认值
        ModelRouterProperties.LoadBalanceConfig defaultConfig = new ModelRouterProperties.LoadBalanceConfig();
        defaultConfig.setType("random");
        return componentFactory.createLoadBalancer(defaultConfig);
    }

    /**
     * 获取负载均衡器数量
     */
    public int getLoadBalancerCount() {
        return loadBalancers.size();
    }

    /**
     * 检查是否有指定服务类型的负载均衡器
     */
    public boolean hasLoadBalancer(ModelServiceRegistry.ServiceType serviceType) {
        return loadBalancers.containsKey(serviceType);
    }
}
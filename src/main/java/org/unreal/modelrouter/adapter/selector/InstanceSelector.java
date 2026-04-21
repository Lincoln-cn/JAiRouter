package org.unreal.modelrouter.adapter.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;

/**
 * 实例选择器
 * 
 * 负责从服务注册表中选择合适的服务实例
 * 支持负载均衡和健康检查
 * 
 * @author AI Assistant
 * @since v2.2.4
 */
@Component
public class InstanceSelector {

    private static final Logger logger = LoggerFactory.getLogger(InstanceSelector.class);

    private final ModelServiceRegistry registry;

    public InstanceSelector(final ModelServiceRegistry registry) {
        this.registry = registry;
    }

    /**
     * 选择服务实例
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param clientIp 客户端 IP
     * @return 选中的实例
     */
    public ModelRouterProperties.ModelInstance selectInstance(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final String clientIp) {
        
        logger.debug("选择实例：serviceType={}, modelName={}, clientIp={}",
                serviceType, modelName, clientIp);
        
        ModelRouterProperties.ModelInstance selected = registry.selectInstance(serviceType, modelName, clientIp);
        
        if (selected != null) {
            logger.debug("已选择实例：instanceId={}, baseUrl={}, weight={}",
                    selected.getInstanceId(), selected.getBaseUrl(), selected.getWeight());
        } else {
            logger.warn("未找到可用实例：serviceType={}, modelName={}", serviceType, modelName);
        }
        
        return selected;
    }

    /**
     * 获取模型路径
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 模型路径
     */
    public String getModelPath(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {
        
        return registry.getModelPath(serviceType, modelName);
    }

    /**
     * 检查实例健康状态
     *
     * @param serviceType 服务类型
     * @param instance 实例
     * @return true 如果实例健康
     */
    public boolean isInstanceHealthy(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        if (instance == null) {
            return false;
        }
        
        // 简单实现：检查实例状态
        return "active".equals(instance.getStatus());
    }

    /**
     * 记录实例调用完成
     *
     * @param serviceType 服务类型
     * @param instance 实例
     */
    public void recordInstanceCallComplete(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        registry.recordCallComplete(serviceType, instance);
    }

    /**
     * 记录实例调用失败
     *
     * @param serviceType 服务类型
     * @param instance 实例
     */
    public void recordInstanceCallFailure(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        registry.recordCallFailure(serviceType, instance);
    }
}

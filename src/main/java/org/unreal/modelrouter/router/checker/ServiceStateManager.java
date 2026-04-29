package org.unreal.modelrouter.router.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.controller.HealthStatusSseController;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务状态管理器
 * 管理服务和实例的健康状态，避免循环依赖
 */
@Component
public class ServiceStateManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceStateManager.class);

    // 注入SSE控制器
    @Autowired(required = false)
    private HealthStatusSseController healthStatusSseController;

    // 存储每个服务类型的健康状态
    private final Map<String, Boolean> serviceHealthStatus = new ConcurrentHashMap<>();

    // 存储每个具体实例的健康状态
    private final Map<String, Boolean> instanceHealthStatus = new ConcurrentHashMap<>();

    /**
     * 获取特定服务类型的健康状态
     */
    public boolean isServiceHealthy(final String serviceType) {
        return serviceHealthStatus.getOrDefault(serviceType, true); // 默认认为是健康的
    }

    /**
     * 获取特定实例的健康状态
     *
     * @param serviceType 服务类型
     * @param instance    模型实例
     * @return 实例是否健康
     */
    public boolean isInstanceHealthy(final String serviceType, final ModelRouterProperties.ModelInstance instance) {
        // 使用实例的唯一ID作为键
        String instanceKey = serviceType + ":" + instance.getInstanceId();
        return instanceHealthStatus.getOrDefault(instanceKey, true); // 默认认为是健康的
    }

    /**
     * 获取特定实例的健康状态
     *
     * @param serviceType 服务类型
     * @param instance    模型实例
     * @return 实例是否健康
     */
    public boolean isInstanceHealthyByKey(final String instanceKey) {
        Boolean status = instanceHealthStatus.get(instanceKey);
        // v2.3.3 修复：如果状态不存在，返回 false 表示未知，而不是默认 true
        return status != null ? status : false;
    }

    /**
     * 获取特定实例的健康状态（三态返回）
     * 
     * @param instanceKey 实例键值 (格式：serviceType:instanceId)
     * @return "HEALTHY" - 健康，"UNHEALTHY" - 不健康，"UNKNOWN" - 未知（未检查）
     * @since v2.3.3
     */
    public String getInstanceHealthStatus(final String instanceKey) {
        Boolean status = instanceHealthStatus.get(instanceKey);
        if (status == null) {
            return "UNKNOWN"; // 状态未知，健康检查还未运行
        }
        return status ? "HEALTHY" : "UNHEALTHY";
    }

    /**
     * 获取特定实例的健康状态（旧版本）
     *
     * @deprecated 此方法使用 name@baseUrl 作为键，与新的键格式不一致。
     *             请使用 {@link #isInstanceHealthyByKey(String)} 替代。
     *             <p>新键格式：serviceType:instanceId（使用数字ID而非名称）</p>
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码
     *             boolean healthy = manager.isInstanceHealthy("chat", "model-name", "http://localhost:8080");
     *             
     *             // 新代码
     *             String instanceKey = "chat:123"; // serviceType:instanceId
     *             boolean healthy = manager.isInstanceHealthyByKey(instanceKey);
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @param serviceType 服务类型
     * @param instanceName 实例名称（已废弃）
     * @param baseUrl 基础URL（已废弃）
     * @return 始终返回 true（无实际功能）
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    public boolean isInstanceHealthy(final String serviceType, final String instanceName , final String baseUrl) {
        // v2.3.3 修复：不再使用 name@baseUrl 作为键
        log.warn("isInstanceHealthy(String, String, String) 已废弃，请使用 isInstanceHealthyByKey(String)");
        return true;
    }

    /**
     * 更新服务健康状态
     *
     * @param serviceType 服务类型
     * @param isHealthy   是否健康
     */
    public void updateServiceHealthStatus(final String serviceType, final boolean isHealthy) {
        serviceHealthStatus.put(serviceType, isHealthy);
    }

    /**
     * 更新实例健康状态
     *
     * @param serviceType 服务类型
     * @param instance    实例
     * @param isHealthy   是否健康
     */
    public void updateInstanceHealthStatus(final String serviceType, final ModelRouterProperties.ModelInstance instance, final boolean isHealthy) {
        // 使用实例的唯一ID作为键
        String instanceKey = serviceType + ":" + instance.getInstanceId();
        Boolean previousStatus = instanceHealthStatus.get(instanceKey);
        
        // 只有状态发生变化时才更新
        if (previousStatus == null || previousStatus != isHealthy) {
            instanceHealthStatus.put(instanceKey, isHealthy);
            log.debug("实例健康状态更新: {} -> {}", instanceKey, isHealthy);
            
            // 通知SSE控制器推送更新
            if (healthStatusSseController != null) {
                try {
                    healthStatusSseController.notifyHealthStatusChange();
                    log.debug("已通知SSE控制器推送健康状态更新");
                } catch (Exception e) {
                    log.warn("通知SSE控制器推送健康状态更新时发生错误: {}", e.getMessage());
                }
            } else {
                log.debug("SSE控制器未注入，跳过推送更新");
            }
        } else {
            log.debug("实例健康状态未发生变化: {}", instanceKey);
        }
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
    
    /**
     * 清理过期的实例健康状态
     */
    public void clearExpiredInstanceHealthStatus() {
        log.info("清理过期的实例健康状态，清理前缓存大小: {}", instanceHealthStatus.size());
        instanceHealthStatus.clear();
        log.info("实例健康状态缓存清理完成");
    }
}
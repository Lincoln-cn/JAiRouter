// src/main/java/org/unreal/modelrouter/ratelimit/RateLimitManager.java
package org.unreal.modelrouter.ratelimit;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流管理器，负责管理所有限流器
 */
@Component
public class RateLimitManager {
    private final RateLimiterFactory rateLimiterFactory;
    private final Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters = new ConcurrentHashMap<>();
    private final ModelRouterProperties properties;

    public RateLimitManager(RateLimiterFactory rateLimiterFactory, ModelRouterProperties properties) {
        this.rateLimiterFactory = rateLimiterFactory;
        this.properties = properties;

        // 初始化限流器
        initializeRateLimiters();
    }

    /**
     * 初始化限流器
     */
    private void initializeRateLimiters() {
        if (properties.getServices() != null) {
            for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry : properties.getServices().entrySet()) {
                String serviceKey = entry.getKey();
                ModelRouterProperties.ServiceConfig serviceConfig = entry.getValue();

                // 尝试将服务键转换为服务类型
                ModelServiceRegistry.ServiceType serviceType = getServiceType(serviceKey);
                if (serviceType != null && serviceConfig.getRateLimit() != null) {
                    RateLimitConfig config = RateLimitConfig.from(serviceConfig.getRateLimit());
                    if (config != null && config.isEnabled()) {
                        RateLimiter limiter = rateLimiterFactory.createRateLimiter(config);
                        serviceLimiters.put(serviceType, limiter);
                    }
                }
            }
        }
    }

    /**
     * 根据服务键获取服务类型
     */
    private ModelServiceRegistry.ServiceType getServiceType(String serviceKey) {
        try {
            // 将连字符格式转换为下划线格式
            String enumKey = serviceKey.replace("-", "_");
            return ModelServiceRegistry.ServiceType.valueOf(enumKey);
        } catch (IllegalArgumentException e) {
            // 如果转换失败，尝试其他可能的映射
            switch (serviceKey.toLowerCase()) {
                case "imggen":
                    return ModelServiceRegistry.ServiceType.imgGen;
                case "imgedit":
                    return ModelServiceRegistry.ServiceType.imgEdit;
                default:
                    return null;
            }
        }
    }

    /**
     * 为服务类型设置限流器
     * @param serviceType 服务类型
     * @param config 限流配置
     */
    public void setRateLimiter(ModelServiceRegistry.ServiceType serviceType, RateLimitConfig config) {
        if (config != null && config.isEnabled()) {
            RateLimiter limiter = rateLimiterFactory.createRateLimiter(config);
            serviceLimiters.put(serviceType, limiter);
        } else {
            serviceLimiters.remove(serviceType);
        }
    }

    /**
     * 检查是否允许请求
     * @param context 限流上下文
     * @return true表示允许，false表示拒绝
     */
    public boolean tryAcquire(RateLimitContext context) {
        RateLimiter limiter = serviceLimiters.get(context.getServiceType());
        if (limiter == null) {
            // 没有限流器，允许请求
            return true;
        }

        return limiter.tryAcquire(context);
    }

    /**
     * 移除服务的限流器
     * @param serviceType 服务类型
     */
    public void removeRateLimiter(ModelServiceRegistry.ServiceType serviceType) {
        serviceLimiters.remove(serviceType);
    }

    /**
     * 更新配置
     */
    public void updateConfiguration() {
        serviceLimiters.clear();
        initializeRateLimiters();
    }
}

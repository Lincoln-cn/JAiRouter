package org.unreal.modelrouter.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.factory.ComponentFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 降级策略管理器
 * 负责管理所有服务的降级策略
 */
@Component
public class FallbackManager {
    private static final Logger logger = LoggerFactory.getLogger(FallbackManager.class);

    // 存储所有服务的降级策略
    private final Map<String, FallbackStrategy<ResponseEntity<?>>> fallbackStrategies = new ConcurrentHashMap<>();

    private final ComponentFactory componentFactory;

    // 全局降级配置
    private ModelRouterProperties.FallbackConfig globalFallbackConfig;

    public FallbackManager(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
    }

    /**
     * 根据配置初始化降级管理器
     * @param properties 配置属性
     */
    public void initialize(ModelRouterProperties properties) {
        if (properties.getFallback() != null) {
            this.globalFallbackConfig = properties.getFallback();
        }

        logger.info("FallbackManager initialized with global config: {}", globalFallbackConfig);
    }

    /**
     * 获取指定服务的降级策略
     * @param serviceType 服务类型
     * @param serviceConfig 服务配置
     * @return 降级策略实例
     */
    public FallbackStrategy<ResponseEntity<?>> getFallbackStrategy(
            String serviceType,
            ModelRouterProperties.ServiceConfig serviceConfig) {

        // 先从缓存中获取
        FallbackStrategy<ResponseEntity<?>> strategy = fallbackStrategies.get(serviceType);
        if (strategy != null) {
            return strategy;
        }

        // 检查服务级别配置
        ModelRouterProperties.FallbackConfig fallbackConfig = null;
        if (serviceConfig != null) {
            fallbackConfig = serviceConfig.getFallback();
        }

        // 如果服务级别没有配置，则使用全局配置
        if (fallbackConfig == null) {
            fallbackConfig = globalFallbackConfig;
        }

        // 创建降级策略
        strategy = componentFactory.createFallbackStrategy(fallbackConfig, serviceType);

        // 缓存策略（如果存在）
        if (strategy != null) {
            fallbackStrategies.put(serviceType, strategy);
        }

        return strategy;
    }

    /**
     * 执行降级处理
     * @param serviceType 服务类型
     * @param serviceConfig 服务配置
     * @param cause 异常原因
     * @return 降级响应
     */
    public ResponseEntity<?> fallback(String serviceType,
                                      ModelRouterProperties.ServiceConfig serviceConfig,
                                      Exception cause) {
        try {
            FallbackStrategy<ResponseEntity<?>> strategy = getFallbackStrategy(serviceType, serviceConfig);
            if (strategy != null) {
                return strategy.fallback(cause);
            }
        } catch (Exception e) {
            logger.warn("Failed to execute fallback for service: {}", serviceType, e);
        }

        // 如果没有降级策略或执行失败，返回null
        return null;
    }

    /**
     * 清除指定服务的降级策略缓存
     * @param serviceType 服务类型
     */
    public void clearFallbackStrategy(String serviceType) {
        fallbackStrategies.remove(serviceType);
        logger.info("Cleared fallback strategy for service: {}", serviceType);
    }

    /**
     * 清除所有降级策略缓存
     */
    public void clearAllFallbackStrategies() {
        fallbackStrategies.clear();
        logger.info("Cleared all fallback strategies");
    }

    /**
     * 获取当前缓存的降级策略数量
     * @return 策略数量
     */
    public int getFallbackStrategyCount() {
        return fallbackStrategies.size();
    }

    public ModelRouterProperties.FallbackConfig getDefaultFallbackConfig() {
        if(globalFallbackConfig!=null) {
            return globalFallbackConfig;
        }else{
            return new ModelRouterProperties.FallbackConfig();
        }
    }
}

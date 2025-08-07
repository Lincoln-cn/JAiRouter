package org.unreal.modelrouter.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器管理器
 * 负责管理所有服务实例的熔断器
 */
@Component
public class CircuitBreakerManager {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerManager.class);

    // 默认熔断器配置
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_TIMEOUT = 60000; // 60秒
    private static final int DEFAULT_SUCCESS_THRESHOLD = 2;

    // 存储所有实例的熔断器
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    // 全局熔断器配置
    private int failureThreshold = DEFAULT_FAILURE_THRESHOLD;
    private long timeout = DEFAULT_TIMEOUT;
    private int successThreshold = DEFAULT_SUCCESS_THRESHOLD;

    public CircuitBreakerManager() {
        // 默认构造函数
    }

    /**
     * 根据配置初始化熔断器参数
     * @param properties 配置属性
     */
    public void initialize(ModelRouterProperties properties) {
        if (properties.getCircuitBreaker() != null) {
            ModelRouterProperties.CircuitBreakerConfig config = properties.getCircuitBreaker();
            if (config.getFailureThreshold() != null) {
                this.failureThreshold = config.getFailureThreshold();
            }
            if (config.getTimeout() != null) {
                this.timeout = config.getTimeout();
            }
            if (config.getSuccessThreshold() != null) {
                this.successThreshold = config.getSuccessThreshold();
            }
        }

        logger.info("CircuitBreakerManager initialized with failureThreshold: {}, timeout: {}, successThreshold: {}",
                this.failureThreshold, this.timeout, this.successThreshold);
    }

    /**
     * 获取指定实例的熔断器
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     * @return 熔断器实例
     */
    public CircuitBreaker getCircuitBreaker(String instanceId, String instanceUrl) {
        // 使用实例URL作为唯一标识符
        String key = instanceUrl != null ? instanceUrl : instanceId;
        if (key == null) {
            throw new IllegalArgumentException("InstanceId and InstanceUrl cannot both be null");
        }

        return circuitBreakers.computeIfAbsent(key,
                k -> new DefaultCircuitBreaker(k, failureThreshold, timeout, successThreshold));
    }

    /**
     * 记录调用成功
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     */
    public void recordSuccess(String instanceId, String instanceUrl) {
        try {
            CircuitBreaker cb = getCircuitBreaker(instanceId, instanceUrl);
            cb.onSuccess();
            logger.debug("Recorded success for instance: {}", instanceUrl != null ? instanceUrl : instanceId);
        } catch (Exception e) {
            logger.warn("Failed to record success for instance: {}",
                    instanceUrl != null ? instanceUrl : instanceId, e);
        }
    }

    /**
     * 记录调用失败
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     */
    public void recordFailure(String instanceId, String instanceUrl) {
        try {
            CircuitBreaker cb = getCircuitBreaker(instanceId, instanceUrl);
            cb.onFailure();
            logger.debug("Recorded failure for instance: {}", instanceUrl != null ? instanceUrl : instanceId);
        } catch (Exception e) {
            logger.warn("Failed to record failure for instance: {}",
                    instanceUrl != null ? instanceUrl : instanceId, e);
        }
    }

    /**
     * 检查实例是否可以执行请求
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     * @return 是否可以执行
     */
    public boolean canExecute(String instanceId, String instanceUrl) {
        try {
            CircuitBreaker cb = getCircuitBreaker(instanceId, instanceUrl);
            boolean canExecute = cb.canExecute();
            if (!canExecute) {
                logger.debug("Instance {} is in circuit breaker state: {}",
                        instanceUrl != null ? instanceUrl : instanceId, cb.getState());
            }
            return canExecute;
        } catch (Exception e) {
            logger.warn("Error checking circuit breaker for instance: {}",
                    instanceUrl != null ? instanceUrl : instanceId, e);
            // 出错时默认允许执行
            return true;
        }
    }

    /**
     * 获取实例的熔断器状态
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     * @return 熔断器状态
     */
    public CircuitBreaker.State getState(String instanceId, String instanceUrl) {
        try {
            CircuitBreaker cb = getCircuitBreaker(instanceId, instanceUrl);
            return cb.getState();
        } catch (Exception e) {
            logger.warn("Error getting circuit breaker state for instance: {}",
                    instanceUrl != null ? instanceUrl : instanceId, e);
            return CircuitBreaker.State.CLOSED;
        }
    }

    /**
     * 获取所有熔断器的状态信息
     * @return 熔断器状态映射
     */
    public Map<String, CircuitBreaker.State> getAllCircuitBreakerStates() {
        Map<String, CircuitBreaker.State> states = new ConcurrentHashMap<>();
        circuitBreakers.forEach((key, circuitBreaker) -> {
            states.put(key, circuitBreaker.getState());
        });
        return states;
    }

    /**
     * 重置指定实例的熔断器
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     */
    public void resetCircuitBreaker(String instanceId, String instanceUrl) {
        String key = instanceUrl != null ? instanceUrl : instanceId;
        if (key != null) {
            circuitBreakers.remove(key);
            logger.info("Reset circuit breaker for instance: {}", key);
        }
    }

    /**
     * 清除所有熔断器
     */
    public void clearAllCircuitBreakers() {
        circuitBreakers.clear();
        logger.info("Cleared all circuit breakers");
    }

    /**
     * 获取熔断器数量
     * @return 熔断器数量
     */
    public int getCircuitBreakerCount() {
        return circuitBreakers.size();
    }
}

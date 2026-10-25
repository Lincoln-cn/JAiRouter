package org.unreal.modelrouter.router.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.event.CircuitBreakerStateChangeEvent;
import org.unreal.modelrouter.router.circuitbreaker.monitor.CircuitBreakerMonitorService;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

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

    // v3.5.4（#653）：自适应阈值按实例覆盖全局配置，在熔断器创建（含重建）时生效
    private final Map<String, Integer> adaptiveFailureThresholds = new ConcurrentHashMap<>();

    // 全局熔断器配置
    private int failureThreshold = DEFAULT_FAILURE_THRESHOLD;
    private long timeout = DEFAULT_TIMEOUT;
    private int successThreshold = DEFAULT_SUCCESS_THRESHOLD;

    // 事件发布器（可选，用于发布状态变化事件）
    private ApplicationEventPublisher eventPublisher;

    // 自适应阈值管理器（可选，通过 setter 注入）
    private AdaptiveThresholdManager adaptiveThresholdManager;

    // 监控服务（可选，通过 setter 注入）
    private CircuitBreakerMonitorService monitorService;

    public CircuitBreakerManager() {
        // 默认构造函数
    }

    /**
     * 设置事件发布器
     * 通过 setter 注入避免循环依赖
     */
    public void setEventPublisher(final ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 设置自适应阈值管理器
     * 通过 setter 注入避免循环依赖
     */
    public void setAdaptiveThresholdManager(final AdaptiveThresholdManager adaptiveThresholdManager) {
        this.adaptiveThresholdManager = adaptiveThresholdManager;
    }

    /**
     * 设置监控服务
     * 通过 setter 注入避免循环依赖
     */
    public void setMonitorService(final CircuitBreakerMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * 根据配置初始化熔断器参数
     * @param properties 配置属性
     */
    public void initialize(final ModelRouterProperties properties) {
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
    public CircuitBreaker getCircuitBreaker(final String instanceId, final String instanceUrl) {
        // 使用实例ID作为唯一标识符，如果ID为空则使用URL
        String key = resolveKey(instanceId, instanceUrl);

        return circuitBreakers.computeIfAbsent(key,
                k -> new LockFreeCircuitBreaker(k, resolveFailureThreshold(k), timeout, successThreshold));
    }

    /**
     * 按实例更新熔断失败阈值（v3.5.4，#653）。
     *
     * <p>阈值在熔断器创建时是 final 的，所以这里先记录覆盖值、再丢弃该实例的熔断器，
     * 下一次 {@link #getCircuitBreaker(String, String)} 会用新阈值重建。重建意味着该实例的失败计数
     * 与状态从 CLOSED 重新开始 —— 这是「判定基准变了」的必然结果；与旧实现的关键区别是
     * 只在阈值真的变化时发生一次，而不是每个自适应周期都清空一次。</p>
     *
     * @param instanceId 实例 ID
     * @param instanceUrl 实例 URL（instanceId 为空时作为键）
     * @param newFailureThreshold 新的失败阈值，&lt;=0 时忽略
     * @return 是否已接受该调整
     */
    public boolean updateFailureThreshold(final String instanceId, final String instanceUrl,
                                          final int newFailureThreshold) {
        if (newFailureThreshold <= 0) {
            logger.warn("忽略非法的熔断失败阈值: {}", newFailureThreshold);
            return false;
        }
        final String key = resolveKey(instanceId, instanceUrl);
        adaptiveFailureThresholds.put(key, newFailureThreshold);
        circuitBreakers.remove(key);
        logger.info("实例 {} 的熔断失败阈值已更新为 {}", key, newFailureThreshold);
        return true;
    }

    /**
     * 实例键：优先 instanceId，缺失时退化成 instanceUrl（与 {@link #getCircuitBreaker} 一致）。
     */
    private String resolveKey(final String instanceId, final String instanceUrl) {
        String key = instanceId != null && !instanceId.trim().isEmpty() ? instanceId : instanceUrl;
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Both instanceId and instanceUrl cannot be null or empty");
        }
        return key;
    }

    private int resolveFailureThreshold(final String key) {
        Integer override = adaptiveFailureThresholds.get(key);
        return override != null ? override : failureThreshold;
    }

    /**
     * 记录调用成功
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     */
    public void recordSuccess(final String instanceId, final String instanceUrl) {
        recordSuccess(instanceId, instanceUrl, null, null);
    }

    /**
     * 记录调用成功（带实例信息）
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     * @param instanceName 实例名称
     * @param serviceType 服务类型
     */
    public void recordSuccess(final String instanceId, final String instanceUrl,
            final String instanceName, final String serviceType) {
        try {
            CircuitBreaker cb = getCircuitBreaker(instanceId, instanceUrl);
            CircuitBreaker.State previousState = cb.getState();
            cb.onSuccess();
            CircuitBreaker.State currentState = cb.getState();
            logger.debug("Recorded success for instance: {}", instanceUrl != null ? instanceUrl : instanceId);

            // 记录到自适应阈值管理器
            if (adaptiveThresholdManager != null) {
                adaptiveThresholdManager.recordSuccess(
                        instanceId != null ? instanceId : instanceUrl,
                        instanceName,
                        serviceType);
            }

            // 记录到监控服务
            if (monitorService != null && cb instanceof LockFreeCircuitBreaker) {
                Map<String, Object> detail = ((LockFreeCircuitBreaker) cb).getStateDetail();
                if (detail != null) {
                    monitorService.recordSuccess(
                            instanceId != null ? instanceId : instanceUrl,
                            instanceName,
                            serviceType,
                            (Integer) detail.getOrDefault("failureCount", 0),
                            (Integer) detail.getOrDefault("successCount", 0));
                }
            }

            // 如果状态发生变化（HALF_OPEN -> CLOSED），发布事件
            if (previousState != currentState) {
                logger.info("熔断器状态变化: instance={}, {} -> {}",
                    instanceUrl != null ? instanceUrl : instanceId, previousState, currentState);
                publishStateChangeEvent(instanceId, instanceUrl, previousState, currentState,
                        "SUCCESS_THRESHOLD", cb);
            }
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
    public void recordFailure(final String instanceId, final String instanceUrl) {
        recordFailure(instanceId, instanceUrl, null, null);
    }

    /**
     * 记录调用失败（带实例信息）
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     * @param instanceName 实例名称
     * @param serviceType 服务类型
     */
    public void recordFailure(final String instanceId, final String instanceUrl,
            final String instanceName, final String serviceType) {
        try {
            CircuitBreaker cb = getCircuitBreaker(instanceId, instanceUrl);
            CircuitBreaker.State previousState = cb.getState();
            cb.onFailure();
            CircuitBreaker.State currentState = cb.getState();
            logger.debug("记录调用失败: instance={}, previousState={}, currentState={}",
                instanceUrl != null ? instanceUrl : instanceId, previousState, currentState);

            // 记录到自适应阈值管理器
            if (adaptiveThresholdManager != null) {
                adaptiveThresholdManager.recordFailure(
                        instanceId != null ? instanceId : instanceUrl,
                        instanceName,
                        serviceType);
            }

            // 记录到监控服务
            if (monitorService != null && cb instanceof LockFreeCircuitBreaker) {
                Map<String, Object> detail = ((LockFreeCircuitBreaker) cb).getStateDetail();
                if (detail != null) {
                    monitorService.recordFailure(
                            instanceId != null ? instanceId : instanceUrl,
                            instanceName,
                            serviceType,
                            (Integer) detail.getOrDefault("failureCount", 0),
                            (Integer) detail.getOrDefault("successCount", 0));
                }
            }

            // 如果状态发生变化，记录详细信息并发布事件
            if (previousState != currentState) {
                logger.info("熔断器状态变化: instance={}, {} -> {}",
                    instanceUrl != null ? instanceUrl : instanceId, previousState, currentState);
                publishStateChangeEvent(instanceId, instanceUrl, previousState, currentState,
                        "FAILURE_THRESHOLD", cb);
            }
        } catch (Exception e) {
            logger.warn("记录调用失败时出错: instance={}",
                instanceUrl != null ? instanceUrl : instanceId, e);
        }
    }

    /**
     * 发布状态变化事件
     */
    private void publishStateChangeEvent(
            final String instanceId,
            final String instanceUrl,
            final CircuitBreaker.State previousState,
            final CircuitBreaker.State currentState,
            final String triggerReason,
            final CircuitBreaker cb) {
        if (eventPublisher != null) {
            try {
                String key = instanceId != null && !instanceId.trim().isEmpty() ? instanceId : instanceUrl;
                Integer failureCount = null;
                Integer successCount = null;
                
                if (cb instanceof LockFreeCircuitBreaker) {
                    Map<String, Object> detail = ((LockFreeCircuitBreaker) cb).getStateDetail();
                    if (detail != null) {
                        failureCount = (Integer) detail.get("failureCount");
                        successCount = (Integer) detail.get("successCount");
                    }
                }
                
                CircuitBreakerStateChangeEvent event = CircuitBreakerStateChangeEvent.of(
                        this, key, previousState, currentState, triggerReason, 
                        failureCount, successCount);
                
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                logger.warn("Failed to publish circuit breaker state change event: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查实例是否可以执行请求
     * @param instanceId 实例ID
     * @param instanceUrl 实例URL
     * @return 是否可以执行
     */
    public boolean canExecute(final String instanceId, final String instanceUrl) {
        try {
            CircuitBreaker cb = getCircuitBreaker(instanceId, instanceUrl);
            boolean canExecute = cb.canExecute();
            if (!canExecute) {
                logger.debug("实例被熔断器阻止: instance={}, state={}",
                        instanceUrl != null ? instanceUrl : instanceId, cb.getState());
            } else {
                logger.trace("实例可以执行: instance={}, state={}",
                        instanceUrl != null ? instanceUrl : instanceId, cb.getState());
            }
            return canExecute;
        } catch (Exception e) {
            logger.warn("检查熔断器状态时出错: instance={}",
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
    public CircuitBreaker.State getState(final String instanceId, final String instanceUrl) {
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
     * 获取指定实例熔断器的详细信息
     * @param instanceId 实例ID
     * @return 熔断器详细信息，如果不存在返回 null
     */
    public Map<String, Object> getCircuitBreakerDetail(final String instanceId) {
        CircuitBreaker cb = circuitBreakers.get(instanceId);
        if (cb instanceof LockFreeCircuitBreaker) {
            return ((LockFreeCircuitBreaker) cb).getStateDetail();
        }
        return null;
    }

    /**
     * 获取所有熔断器的详细信息
     * @return 熔断器详细信息列表
     */
    public Map<String, Map<String, Object>> getAllCircuitBreakerDetails() {
        Map<String, Map<String, Object>> details = new ConcurrentHashMap<>();
        circuitBreakers.forEach((key, circuitBreaker) -> {
            if (circuitBreaker instanceof LockFreeCircuitBreaker) {
                details.put(key, ((LockFreeCircuitBreaker) circuitBreaker).getStateDetail());
            }
        });
        return details;
    }

    /**
     * 移除指定实例的熔断器（下次调用时按最新配置重建）
     *
     * <p>该语义用于「配置已变更，旧的阈值/地址不再适用」的场景：实例更新与熔断配置更新都会调用它。
     * 手动重置请使用 {@link #resetCircuitBreakerInPlace(String)}，后者会把实例保留在监控视图中。</p>
     *
     * @param instanceId 实例ID（UUID）
     * @param instanceUrl 实例URL（仅在 instanceId 为空时作为 key）
     * @return 重置前的状态；该实例没有熔断器时返回 {@code null}
     */
    public CircuitBreaker.State resetCircuitBreaker(final String instanceId, final String instanceUrl) {
        // 与 getCircuitBreaker 保持一致的 key 选择逻辑：优先 instanceId
        String key = instanceId != null && !instanceId.trim().isEmpty() ? instanceId : instanceUrl;
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        CircuitBreaker removed = circuitBreakers.remove(key);
        if (removed != null) {
            CircuitBreaker.State previous = removed.getState();
            logger.info("Removed circuit breaker for instance: key={}, id={}, url={}, previousState={}",
                    key, instanceId, instanceUrl, previous);
            return previous;
        }
        logger.debug("No circuit breaker found to reset: key={}, id={}, url={}", key, instanceId, instanceUrl);
        return null;
    }

    /**
     * 就地重置指定实例的熔断器（保留在监控视图中）
     *
     * <p>与 {@link #resetCircuitBreaker(String, String)} 的区别：不把实例从注册表移除，而是调用
     * {@link LockFreeCircuitBreaker#reset()} 把状态置为 {@code CLOSED} 并清零失败/成功计数，
     * 因此运维在监控页重置后仍能看到该实例（状态为 CLOSED、计数为 0）。</p>
     *
     * @param instanceId 实例ID（UUID）
     * @return 重置前的状态；该实例没有熔断器时返回 {@code null}
     */
    public CircuitBreaker.State resetCircuitBreakerInPlace(final String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return null;
        }
        CircuitBreaker breaker = circuitBreakers.get(instanceId);
        if (breaker == null) {
            logger.debug("No circuit breaker found to reset in place: id={}", instanceId);
            return null;
        }
        CircuitBreaker.State previous = breaker.getState();
        if (breaker instanceof LockFreeCircuitBreaker lockFreeCircuitBreaker) {
            lockFreeCircuitBreaker.reset();
        }
        logger.info("Reset circuit breaker in place: id={}, previousState={}, currentState={}",
                instanceId, previous, breaker.getState());
        return previous;
    }

    /**
     * 清除所有熔断器
     */
    public void clearAllCircuitBreakers() {
        circuitBreakers.clear();
        adaptiveFailureThresholds.clear();
        logger.info("Cleared all circuit breakers");
    }

    /**
     * 获取熔断器数量
     * @return 熔断器数量
     */
    public int getCircuitBreakerCount() {
        return circuitBreakers.size();
    }

    public ModelRouterProperties.CircuitBreakerConfig getDefaultCircuitBreakerConfig() {
        return new ModelRouterProperties.CircuitBreakerConfig();
    }
}

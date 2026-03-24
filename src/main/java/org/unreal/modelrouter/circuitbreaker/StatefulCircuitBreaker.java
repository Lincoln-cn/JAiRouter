package org.unreal.modelrouter.circuitbreaker;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.circuitbreaker.state.CircuitState;
import org.unreal.modelrouter.circuitbreaker.state.CircuitStateContext;
import org.unreal.modelrouter.circuitbreaker.state.ClosedState;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

/**
 * 使用状态模式实现的熔断器
 * 
 * 将状态转换逻辑委托给各个状态类，消除了 switch 语句，
 * 符合开闭原则，易于扩展新状态。
 * 
 * @see CircuitState 状态接口
 * @see ClosedState 关闭状态
 * @see OpenState 打开状态
 * @see HalfOpenState 半开状态
 */
public class StatefulCircuitBreaker implements CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(StatefulCircuitBreaker.class);

    @Getter
    private final String instanceId;

    private final CircuitStateContext context;

    /**
     * 创建熔断器
     * 
     * @param instanceId 实例 ID
     * @param failureThreshold 失败阈值
     * @param timeout 超时时间（毫秒）
     * @param successThreshold 成功阈值
     * @param metricsCollector 指标收集器（可选）
     */
    public StatefulCircuitBreaker(String instanceId, int failureThreshold, 
                                   long timeout, int successThreshold,
                                   MetricsCollector metricsCollector) {
        this.instanceId = instanceId;
        this.context = new CircuitStateContext(
            instanceId,
            failureThreshold,
            timeout,
            successThreshold,
            metricsCollector,
            new ClosedState() // 初始状态为关闭状态
        );
    }

    /**
     * 创建熔断器（无指标收集器）
     */
    public StatefulCircuitBreaker(String instanceId, int failureThreshold, 
                                   long timeout, int successThreshold) {
        this(instanceId, failureThreshold, timeout, successThreshold, null);
    }

    @Override
    public synchronized boolean canExecute() {
        boolean canExecute = context.getCurrentState().canExecute(context);
        
        if (canExecute) {
            logger.trace("熔断器允许执行请求：instanceId={}, state={}", 
                instanceId, context.getCurrentState().getStateName());
        } else {
            logger.debug("熔断器拒绝执行请求：instanceId={}, state={}", 
                instanceId, context.getCurrentState().getStateName());
        }
        
        return canExecute;
    }

    @Override
    public synchronized void onSuccess() {
        context.getCurrentState().onSuccess(context);
    }

    @Override
    public synchronized void onFailure() {
        context.getCurrentState().onFailure(context);
    }

    @Override
    public State getState() {
        return context.getState();
    }

    /**
     * 重置熔断器到初始状态
     */
    public synchronized void reset() {
        context.setCurrentState(new ClosedState());
        context.resetFailureCount();
        context.resetSuccessCount();
        logger.info("熔断器已重置：instanceId={}", instanceId);
        context.recordEvent("reset", "CLOSED");
    }

    /**
     * 获取失败计数（用于测试）
     */
    public int getFailureCount() {
        return context.getFailureCount();
    }

    /**
     * 获取成功计数（用于测试）
     */
    public int getSuccessCount() {
        return context.getSuccessCount();
    }

    /**
     * 获取最后失败时间（用于测试）
     */
    public long getLastFailureTime() {
        return context.getLastFailureTime();
    }
}

package org.unreal.modelrouter.router.circuitbreaker.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker.State;

/**
 * 半开状态（测试状态）
 * 
 * 允许部分请求通过，测试服务是否恢复。
 * 成功次数达到阈值时，转换为关闭状态。
 * 失败时，重新转换为打开状态。
 */
public class HalfOpenState implements CircuitState {

    private static final Logger logger = LoggerFactory.getLogger(HalfOpenState.class);

    @Override
    public boolean canExecute(final CircuitStateContext context) {
        return true;
    }

    @Override
    public void onSuccess(final CircuitStateContext context) {
        context.incrementSuccessCount();
        
        if (context.isSuccessThresholdReached()) {
            logger.info("熔断器关闭：instanceId={}, successCount={}, successThreshold={}",
                context.getInstanceId(), context.getSuccessCount(), context.getSuccessThreshold());
            context.resetFailureCount();
            context.setCurrentState(new ClosedState());
        } else {
            logger.debug("熔断器成功请求 (HALF_OPEN 状态): instanceId={}, successCount={}, successThreshold={}",
                context.getInstanceId(), context.getSuccessCount(), context.getSuccessThreshold());
        }
        
        context.recordEvent("success", getStateName());
    }

    @Override
    public void onFailure(final CircuitStateContext context) {
        context.incrementFailureCount();
        logger.info("熔断器重新打开：instanceId={}, failure in HALF_OPEN state", context.getInstanceId());
        context.setCurrentState(new OpenState());
        context.recordEvent("failure", getStateName());
    }

    @Override
    public String getStateName() {
        return "HALF_OPEN";
    }

    @Override
    public State getState() {
        return State.HALF_OPEN;
    }
}

package org.unreal.modelrouter.router.circuitbreaker.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker.State;

/**
 * 关闭状态（正常状态）
 * 
 * 熔断器正常工作，允许所有请求通过。
 * 当失败次数达到阈值时，转换为打开状态。
 */
public class ClosedState implements CircuitState {

    private static final Logger logger = LoggerFactory.getLogger(ClosedState.class);

    @Override
    public boolean canExecute(final CircuitStateContext context) {
        return true;
    }

    @Override
    public void onSuccess(final CircuitStateContext context) {
        // 成功后重置失败计数
        context.resetFailureCount();
        context.recordEvent("success", getStateName());
        logger.debug("熔断器成功请求 (CLOSED 状态): instanceId={}", context.getInstanceId());
    }

    @Override
    public void onFailure(final CircuitStateContext context) {
        context.incrementFailureCount();
        
        if (context.isFailureThresholdReached()) {
            logger.info("熔断器打开：instanceId={}, failureCount={}, failureThreshold={}",
                context.getInstanceId(), context.getFailureCount(), context.getFailureThreshold());
            context.setCurrentState(new OpenState());
        } else {
            logger.debug("熔断器记录失败 (CLOSED 状态): instanceId={}, failureCount={}, failureThreshold={}",
                context.getInstanceId(), context.getFailureCount(), context.getFailureThreshold());
        }
        
        context.recordEvent("failure", getStateName());
    }

    @Override
    public String getStateName() {
        return "CLOSED";
    }

    @Override
    public State getState() {
        return State.CLOSED;
    }
}

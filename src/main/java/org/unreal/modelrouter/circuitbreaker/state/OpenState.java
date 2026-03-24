package org.unreal.modelrouter.circuitbreaker.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker.State;

/**
 * 打开状态（熔断状态）
 * 
 * 熔断器打开，拒绝所有请求。
 * 当超时时间过去后，转换为半开状态。
 */
public class OpenState implements CircuitState {

    private static final Logger logger = LoggerFactory.getLogger(OpenState.class);

    @Override
    public boolean canExecute(CircuitStateContext context) {
        // 检查是否可以转为半开状态
        if (context.isTimeoutElapsed()) {
            logger.debug("熔断器转为半开状态：instanceId={}, elapsed={}ms, timeout={}ms",
                context.getInstanceId(), 
                System.currentTimeMillis() - context.getLastFailureTime(), 
                context.getTimeout());
            context.setCurrentState(new HalfOpenState());
            context.resetSuccessCount();
            return true;
        }
        
        logger.debug("熔断器拒绝请求 (OPEN 状态): instanceId={}, elapsed={}ms, timeout={}ms",
            context.getInstanceId(),
            System.currentTimeMillis() - context.getLastFailureTime(),
            context.getTimeout());
        return false;
    }

    @Override
    public void onSuccess(CircuitStateContext context) {
        // OPEN 状态下不记录成功（因为请求被拒绝了）
    }

    @Override
    public void onFailure(CircuitStateContext context) {
        // OPEN 状态下失败不改变状态
        logger.debug("熔断器记录失败 (OPEN 状态): instanceId={}", context.getInstanceId());
        context.recordEvent("failure", getStateName());
    }

    @Override
    public String getStateName() {
        return "OPEN";
    }

    @Override
    public State getState() {
        return State.OPEN;
    }
}

package org.unreal.modelrouter.router.circuitbreaker.state;

import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker.State;

/**
 * 熔断器状态接口
 * 
 * 使用状态模式封装熔断器的状态转换逻辑，每个状态实现此接口。
 * 状态转换由各个状态类自行决定，符合开闭原则。
 */
public interface CircuitState {

    /**
     * 检查是否可以执行请求
     * 
     * @param context 熔断器上下文
     * @return 如果可以执行返回 true
     */
    boolean canExecute(CircuitStateContext context);

    /**
     * 请求成功时的处理
     * 
     * @param context 熔断器上下文
     */
    void onSuccess(CircuitStateContext context);

    /**
     * 请求失败时的处理
     * 
     * @param context 熔断器上下文
     */
    void onFailure(CircuitStateContext context);

    /**
     * 获取状态名称
     * 
     * @return 状态名称
     */
    String getStateName();

    /**
     * 获取对应的枚举状态
     * 
     * @return 枚举状态
     */
    State getState();
}

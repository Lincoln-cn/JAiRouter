package org.unreal.modelrouter.circuitbreaker;

public interface CircuitBreaker {
    enum State {
        CLOSED,     // 正常状态
        OPEN,       // 熔断开启状态
        HALF_OPEN   // 半开状态
    }

    boolean canExecute();  // 检查是否允许执行请求
    void onSuccess();      // 记录成功
    void onFailure();      // 记录失败
    State getState();      // 获取当前状态
}

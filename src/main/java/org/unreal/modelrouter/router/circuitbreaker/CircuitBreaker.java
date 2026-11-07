package org.unreal.modelrouter.router.circuitbreaker;

public interface CircuitBreaker {
    enum State {
        CLOSED,     // 正常状态
        OPEN,       // 熔断开启状态
        HALF_OPEN   // 半开状态
    }

    boolean canExecute();  // 检查是否允许执行请求
    void onSuccess();      // 记录成功
    void onFailure();      // 记录失败

    /**
     * 记录失败（带失败详情，issue #1010）。
     *
     * <p>默认委托 {@link #onFailure()}：实现类可覆写以保存
     * {@code reason}（如 CONNECT_FAILED）与 {@code exceptionClass}（异常类名），
     * 供 503 响应体 / 管理端点 / 复位响应回显真实失败原因。</p>
     *
     * @param reason        失败原因标识（可空）
     * @param exceptionClass 异常类名（可空）
     * @since v3.8.0
     */
    default void onFailure(final String reason, final String exceptionClass) {
        onFailure();
    }

    State getState();      // 获取当前状态
}

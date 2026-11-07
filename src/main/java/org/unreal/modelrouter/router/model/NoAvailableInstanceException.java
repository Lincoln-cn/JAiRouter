package org.unreal.modelrouter.router.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * issue #1010：全部实例不可用（如熔断打开）时抛出，携带「最近一次真实失败」详情。
 *
 * <p>此前 503 只有汇总文案（「No available instances (all in circuit breaker state) ...」），
 * 把真实失败（连接失败/超时等）完全遮蔽；本异常把 lastFailure 摘要带到渲染层，
 * 由 {@code ReactiveGlobalExceptionHandler} 放进 {@code RouterResponse.data}。</p>
 *
 * @since v3.8.0
 */
public class NoAvailableInstanceException extends ResponseStatusException {

    private final transient Map<String, Object> lastFailure;

    public NoAvailableInstanceException(final HttpStatus status, final String reason,
                                        final Map<String, Object> lastFailure) {
        super(status, reason);
        this.lastFailure = lastFailure;
    }

    /**
     * @return 最近一次真实失败详情（键：instanceId/instanceName/reason/at/exceptionClass），可空
     */
    public Map<String, Object> getLastFailure() {
        return lastFailure;
    }
}

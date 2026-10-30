package org.unreal.modelrouter.router.fallback.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;

/**
 * 默认降级策略实现：不缓存任何内容，直接返回 503.
 *
 * <p>#823：错误体改成结构化 JSON（此前是裸字符串却标了 {@code application/json}），
 * 并且签名带上了请求键 —— 没有缓存的策略忽略它即可。</p>
 */
public class DefaultFallbackStrategy implements FallbackStrategy<ResponseEntity<?>> {
    private static final Logger logger = LoggerFactory.getLogger(DefaultFallbackStrategy.class);

    /** 降级响应的结构化错误体。 */
    static final String DEGRADED_BODY =
            "{\"error\":{\"message\":\"服务当前不可用，请稍后重试\",\"type\":\"service_unavailable\",\"code\":\"503\"}}";

    private final String serviceType;

    public DefaultFallbackStrategy(final String serviceType) {
        this.serviceType = serviceType;
    }

    @Override
    public ResponseEntity<?> fallback(final FallbackRequestKey key, final Exception cause) {
        logger.warn("Service {} fallback triggered due to: {} (key={})",
                serviceType, cause == null ? null : cause.getMessage(), key);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(DEGRADED_BODY);
    }

    /**
     * 结构化降级错误体（供测试与子类复用）.
     *
     * @return JSON 字符串
     */
    public static String degradedBody() {
        return DEGRADED_BODY;
    }
}

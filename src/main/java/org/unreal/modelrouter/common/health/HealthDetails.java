package org.unreal.modelrouter.common.health;

/**
 * 健康检查细节的取值工具。
 *
 * <p>{@link java.util.Map#of} 与 {@code Health.Builder#withDetail} 都拒绝 null 值，
 * 而异常里的 {@code getMessage()} 常常就是 null：Redis 超时、连接被重置、
 * {@code Mono.block()} 被取消，抛出的异常往往只带类型不带消息。</p>
 *
 * <p>一旦把 null 塞进这两者，健康检查本身会抛 {@code NullPointerException}，
 * 再经全局异常处理器渲染成 400「参数验证失败：'value' must not be null」，
 * 于是 {@code /actuator/health} 从「指示某个下游不可用」变成「网关端点报错」，
 * 与真实的 200/503 语义完全不同，探针与监控都会误判。</p>
 *
 * <p>因此凡是往健康详情里写异常原因的代码，都应经由本类取值。</p>
 *
 * @author JAiRouter
 * @since 3.8.6
 */
public final class HealthDetails {

    private HealthDetails() {
    }

    /**
     * 取异常的可读原因，保证非 null。
     *
     * @param e 异常，允许为 {@code null}
     * @return 异常的 message；为空时回落到类的简单名；异常本身为 null 时返回 {@code "unknown"}
     */
    public static String safeMessage(final Throwable e) {
        if (e == null) {
            return "unknown";
        }
        final String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }
}

package org.unreal.modelrouter.router.model;

/**
 * 实例运行态缓存键的统一解析（#636）.
 *
 * <p>健康状态、健康检查上一次状态等运行态缓存都以「服务类型 + 实例标识」为键。
 * 早期实现直接使用 {@code instance.getInstanceId()}，而该方法在未配置
 * {@code instance-id} 时返回 {@code null}，导致同一服务下所有未配 id 的实例
 * 共用 {@code chat:null} 一个键——一个实例不健康会把其它实例一起判成不健康。
 *
 * <p>回退顺序 = {@code instanceId → baseUrl → name}：{@code baseUrl} 在副本场景下
 * 才真正唯一（同一模型的多副本通常 {@code name} 相同），因此排在 {@code name} 之前，
 * 与熔断器的既有取舍一致（{@code CircuitBreakerManager} 同样是 instanceId 缺失回退 URL）。
 *
 * @author JAiRouter Team
 * @since v3.5.3
 */
public final class InstanceKeys {

    /** 缺少任何可用标识时的占位符（避免生成 {@code chat:null} 这类键）。 */
    public static final String UNKNOWN_IDENTIFIER = "unknown";

    private InstanceKeys() {
    }

    /**
     * 解析实例的运行态标识.
     *
     * @param instance 模型实例（可为 {@code null}）
     * @return 非空的实例标识：{@code instanceId} → {@code baseUrl} → {@code name} → {@link #UNKNOWN_IDENTIFIER}
     */
    public static String identifier(final ModelRouterProperties.ModelInstance instance) {
        if (instance == null) {
            return UNKNOWN_IDENTIFIER;
        }
        final String instanceId = instance.getInstanceId();
        if (instanceId != null && !instanceId.isBlank()) {
            return instanceId.trim();
        }
        final String baseUrl = instance.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim();
        }
        final String name = instance.getName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return UNKNOWN_IDENTIFIER;
    }

    /**
     * 解析实例的健康状态键.
     *
     * @param serviceType 服务类型
     * @param instance    模型实例
     * @return 形如 {@code chat:<instanceId | baseUrl | name>} 的键
     */
    public static String healthKey(final String serviceType, final ModelRouterProperties.ModelInstance instance) {
        return serviceType + ":" + identifier(instance);
    }
}

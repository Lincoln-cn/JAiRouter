package org.unreal.modelrouter.router.loadbalancer;

import org.unreal.modelrouter.router.model.ModelRouterProperties;

/**
 * 请求级"已选中实例"持有者。
 *
 * <p>网关（{@code ServiceRequestHandler}）在一次请求开始时会先选一次实例用于解析适配器，
 * 随后适配器（{@code BaseAdapter}）为了拿到目标 baseUrl 会再选一次。两次选择都会推进
 * 负载均衡器内部状态、扣减实例级限流令牌，并各自计入路由监控统计——表现为
 * “发一条消息，实例选中数 +3”。
 *
 * <p>这里用 ThreadLocal 把 handler 已选中的实例透传给适配器复用，使每个请求
 * 恰好只发生一次实例选择。适配器读取后不清理，由 handler 在请求结束时统一
 * {@link #clear()} 防止线程复用泄漏。
 *
 * <p>与 {@code ServiceRateLimitHolder} 同一套模式；当持有者为空（例如适配器被
 * 单独调用、未经过 handler）时，适配器回退为自行选择，行为与既有实现一致。
 *
 * @author JAiRouter Team
 * @since 3.2.2
 */
public final class SelectedInstanceHolder {

    private static final ThreadLocal<ModelRouterProperties.ModelInstance> INSTANCE = new ThreadLocal<>();

    private SelectedInstanceHolder() {
    }

    /**
     * 记录当前请求已选中的实例（handler 层调用）。
     *
     * @param instance 已选中的实例
     */
    public static void set(final ModelRouterProperties.ModelInstance instance) {
        if (instance != null) {
            INSTANCE.set(instance);
        }
    }

    /**
     * 获取当前请求已选中的实例。
     *
     * @return 已选中的实例；未设置时返回 {@code null}
     */
    public static ModelRouterProperties.ModelInstance get() {
        return INSTANCE.get();
    }

    /**
     * 清理当前线程的持有值（请求处理完成后调用，防泄漏）。
     */
    public static void clear() {
        INSTANCE.remove();
    }
}

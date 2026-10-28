package org.unreal.modelrouter.router.loadbalancer;

import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * #688 在途调用记账器：让「加计数」与「减计数」落在同一个 {@link LoadBalancer} 实例上。
 *
 * <p>背景：选择实例时对外层（池策略 LB / 规则策略 LB / sticky 包装器）调用 {@code recordCall}，
 * 而请求结束回调只带 {@code serviceType} 与实例，若按 {@code serviceType} 另取一个 LB 去减计数，
 * 两者落在不同对象上 —— {@code least-connections} 的在途数只增不减，退化成「历史累计最少者优先」。</p>
 *
 * <p>本类记录「某实例最近一次选择实际用的是哪个 LB」，完成/失败回调按同一实例取回。
 * 键 = {@code serviceType|instanceId|baseUrl}，值为「LB → 未完成调用数」。
 * 同一实例被多个 LB 选中时（不同池共用一个实例），取未完成数最多的那个，减到 0 即从映射中移除，
 * 因此不会随请求量增长。</p>
 *
 * <p>线程安全：外层 {@link ConcurrentHashMap}，内层 {@link ConcurrentHashMap}，
 * 「挑一个并减一」在 {@code computeIfPresent} 里原子完成。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
public final class LoadBalancerCallTracker {

    private final Map<String, Map<LoadBalancer, Integer>> inFlight = new ConcurrentHashMap<>();

    /**
     * 构造记账键。
     *
     * @param serviceType 服务类型名（已小写化）
     * @param instance    被选中的实例
     * @return 记账键
     */
    public static String key(final String serviceType, final ModelRouterProperties.ModelInstance instance) {
        final String id = instance != null && instance.getInstanceId() != null
                ? instance.getInstanceId()
                : (instance != null ? instance.getBaseUrl() : "");
        final String baseUrl = instance != null && instance.getBaseUrl() != null ? instance.getBaseUrl() : "";
        return serviceType + "|" + id + "|" + baseUrl;
    }

    /**
     * 记录一次选择：该实例的未完成调用记在 {@code loadBalancer} 头上。
     *
     * @param key          记账键，见 {@link #key(String, ModelRouterProperties.ModelInstance)}
     * @param loadBalancer 实际执行 {@code recordCall} 的 LB（可为 null，null 时忽略）
     */
    public void track(final String key, final LoadBalancer loadBalancer) {
        if (key == null || loadBalancer == null) {
            return;
        }
        inFlight.compute(key, (k, byLoadBalancer) -> {
            final Map<LoadBalancer, Integer> map =
                    byLoadBalancer != null ? byLoadBalancer : new ConcurrentHashMap<>();
            map.merge(loadBalancer, 1, Integer::sum);
            return map;
        });
    }

    /**
     * 取回该实例应当减计数的 LB，并把它的未完成数减一。
     *
     * @param key 记账键
     * @return 实际记账的 LB；没有在途记录时返回 {@code null}（调用方自行决定降级策略）
     */
    public LoadBalancer take(final String key) {
        if (key == null) {
            return null;
        }
        final LoadBalancer[] chosen = new LoadBalancer[1];
        inFlight.computeIfPresent(key, (k, byLoadBalancer) -> {
            LoadBalancer best = null;
            int bestCount = 0;
            for (Map.Entry<LoadBalancer, Integer> entry : byLoadBalancer.entrySet()) {
                final Integer count = entry.getValue();
                if (count != null && count > bestCount) {
                    best = entry.getKey();
                    bestCount = count;
                }
            }
            if (best == null) {
                return null;
            }
            final LoadBalancer selected = best;
            byLoadBalancer.computeIfPresent(selected, (lb, count) -> count > 1 ? count - 1 : null);
            chosen[0] = selected;
            return byLoadBalancer.isEmpty() ? null : byLoadBalancer;
        });
        return chosen[0];
    }

    /**
     * 该实例当前的未完成调用数（全部 LB 之和）。
     *
     * @param key 记账键
     * @return 未完成调用数
     */
    public int outstanding(final String key) {
        if (key == null) {
            return 0;
        }
        final Map<LoadBalancer, Integer> byLoadBalancer = inFlight.get(key);
        if (byLoadBalancer == null) {
            return 0;
        }
        int total = 0;
        for (Integer count : byLoadBalancer.values()) {
            if (count != null) {
                total += count;
            }
        }
        return total;
    }

    /**
     * 当前仍有在途记录的实例键数量（供测试与排障确认没有泄漏）。
     *
     * @return 键数量
     */
    public int trackedKeys() {
        return inFlight.size();
    }

    /**
     * 清空全部记账（仅测试与关闭时使用）。
     */
    public void clear() {
        inFlight.clear();
    }
}

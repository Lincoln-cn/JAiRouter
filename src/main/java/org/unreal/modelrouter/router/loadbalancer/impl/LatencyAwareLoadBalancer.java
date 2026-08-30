package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于EWMA（指数加权移动平均）延迟感知的负载均衡策略
 *
 * <p>v2.9.3: 根据每个实例的历史调用延迟动态调整选择概率，
 * 延迟越低的实例被选中的概率越高。
 *
 * <p>工作原理:
 * <ol>
 *   <li>维护每个实例的EWMA延迟值：{@code ewma = alpha * sample + (1 - alpha) * ewma}</li>
 *   <li>成功调用：以实际延迟作为样本更新EWMA</li>
 *   <li>失败调用：使用失败惩罚值（30秒）作为样本更新EWMA，惩罚高失败率实例</li>
 *   <li>选择实例时，按 {@code 1 / (1 + ewma)} 作为权重进行加权随机选择</li>
 *   <li>冷启动：未收到任何样本的实例权重为1.0（即等同于零延迟），确保公平探索</li>
 * </ol>
 *
 * <p>适用场景: 后端实例延迟差异显著（如跨地域部署、混合硬件环境）。
 */
public class LatencyAwareLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(LatencyAwareLoadBalancer.class);

    /** 默认EWMA平滑因子 alpha（0.0~1.0，值越大对新样本越敏感） */
    static final double DEFAULT_ALPHA = 0.2;

    /** 失败调用的惩罚延迟（毫秒）—— 等效于30秒严重超时 */
    static final double FAILURE_PENALTY_MS = 30_000.0;

    private final double alpha;
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    /** 实例Key → 当前EWMA延迟值（Double） */
    private final ConcurrentHashMap<String, AtomicReference<Double>> ewmaMap = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    /**
     * 使用默认 alpha (0.2) 构造
     */
    public LatencyAwareLoadBalancer() {
        this(DEFAULT_ALPHA);
    }

    /**
     * 使用指定 alpha 构造
     *
     * @param alpha EWMA平滑因子 (0.0, 1.0]，值越大对新样本越敏感
     */
    public LatencyAwareLoadBalancer(final double alpha) {
        this.alpha = (alpha > 0.0 && alpha <= 1.0) ? alpha : DEFAULT_ALPHA;
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String clientIp,
            final String serviceType) {

        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for latency-aware selection");
            return null;
        }

        // 单实例快速路径
        if (instances.size() == 1) {
            return instances.get(0);
        }

        // 清理不再出现在候选列表中的陈旧EWMA条目
        pruneStaleEntries(instances);

        // 计算每个实例的权重: 1 / (1 + ewma)，冷启动实例ewma=0时权重=1.0
        double totalWeight = 0.0;
        double[] weights = new double[instances.size()];
        for (int i = 0; i < instances.size(); i++) {
            String key = getInstanceKey(instances.get(i));
            AtomicReference<Double> ewmaRef = ewmaMap.get(key);
            double ewma = (ewmaRef != null) ? ewmaRef.get() : 0.0;
            weights[i] = 1.0 / (1.0 + ewma);
            totalWeight += weights[i];
        }

        // 加权随机选择
        double randomValue = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (int i = 0; i < instances.size(); i++) {
            cumulative += weights[i];
            if (randomValue < cumulative) {
                ModelRouterProperties.ModelInstance selected = instances.get(i);
                logger.debug("Selected instance {} (ewma-based weight={}) using latency-aware strategy for service {}",
                        selected.getName(), weights[i], serviceType);
                recordLoadBalancerSelection(serviceType, "latency_aware", selected.getName());
                return selected;
            }
        }

        // 兜底返回最后一个
        ModelRouterProperties.ModelInstance fallback = instances.get(instances.size() - 1);
        logger.debug("Fallback selection of instance {} for service {}", fallback.getName(), serviceType);
        recordLoadBalancerSelection(serviceType, "latency_aware", fallback.getName());
        return fallback;
    }

    @Override
    public void recordCallComplete(final ModelRouterProperties.ModelInstance instance,
                                   final long durationMs,
                                   final boolean success) {
        if (success) {
            updateEwma(getInstanceKey(instance), durationMs);
        }
        // 成功调用不需要额外处理，已在上面更新EWMA
    }

    @Override
    public void recordCallFailure(final ModelRouterProperties.ModelInstance instance,
                                  final long durationMs,
                                  final String errorCode) {
        String key = getInstanceKey(instance);
        // 失败时使用惩罚值和实际耗时中的较大值作为样本
        double penaltySample = Math.max(durationMs, FAILURE_PENALTY_MS);
        updateEwma(key, penaltySample);
    }

    /**
     * 使用EWMA公式更新实例延迟: ewma = alpha * sample + (1 - alpha) * ewma
     * 通过 AtomicReference 的 CAS 操作实现无锁并发更新。
     *
     * @param key    实例Key
     * @param sample 本次样本值（毫秒）
     */
    private void updateEwma(final String key, final double sample) {
        AtomicReference<Double> ewmaRef = ewmaMap.computeIfAbsent(key, k -> new AtomicReference<>(0.0));
        // CAS 循环更新
        Double current;
        Double updated;
        do {
            current = ewmaRef.get();
            updated = alpha * sample + (1.0 - alpha) * current;
        } while (!ewmaRef.compareAndSet(current, updated));

        if (logger.isDebugEnabled()) {
            logger.debug("Updated EWMA for instance {}: sample={}ms, oldEwma={}, newEwma={}",
                    key, sample, current, String.format("%.2f", updated));
        }
    }

    /**
     * 清理不在当前候选列表中的EWMA条目（惰性清理）
     */
    private void pruneStaleEntries(final List<ModelRouterProperties.ModelInstance> instances) {
        if (ewmaMap.size() > instances.size() * 2) {
            // 构建当前有效key集合
            java.util.Set<String> activeKeys = new java.util.HashSet<>(instances.size());
            for (ModelRouterProperties.ModelInstance instance : instances) {
                activeKeys.add(getInstanceKey(instance));
            }
            ewmaMap.entrySet().removeIf(entry -> !activeKeys.contains(entry.getKey()));
        }
    }

    /**
     * 获取实例的唯一标识Key，与 {@link LeastConnectionsLoadBalancer} 保持一致。
     */
    private String getInstanceKey(final ModelRouterProperties.ModelInstance instance) {
        return instance.getBaseUrl() + ":" + instance.getPath();
    }

    /**
     * 记录负载均衡器选择指标
     */
    private void recordLoadBalancerSelection(
            final String service, final String strategy,
            final String selectedInstance) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordLoadBalancer(service, strategy, selectedInstance);
            } catch (Exception e) {
                logger.warn("Failed to record load balancer metrics: {}", e.getMessage());
            }
        }
    }
}

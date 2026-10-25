package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.utils.WeightCalculator;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.security.SecureRandom;
import java.util.List;

/**
 * 随机负载均衡策略（加权）。
 *
 * <p>本类刻意保持**无状态**：实例的可用性（状态、健康检查、熔断）由
 * {@link org.unreal.modelrouter.router.model.InstanceAvailability} 与
 * {@link org.unreal.modelrouter.router.model.SelectInstanceOptimizer} 在选路之前统一判定，
 * 负载均衡器只负责「在给定候选里挑一个」。v3.5.4（#648）之前本类自带一套私有的
 * 熔断器（阈值硬编码 {@code 5/60s/2}，且忽略实例级 {@code circuit-breaker.enabled}），
 * 与 {@code CircuitBreakerManager} 形成双轨记账：同一实例在两个熔断器里状态不同步，
 * 出现「管理器认为已熔断、随机负载均衡仍把请求打过去」或相反的情况。</p>
 */
public class RandomLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalancer.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances, final String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String clientIp, final String serviceType) {

        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for random selection");
            throw new IllegalArgumentException("No instances available");
        }

        long totalWeight = WeightCalculator.calculateTotalWeight(instances);
        if (totalWeight <= 0) {
            ModelRouterProperties.ModelInstance selected = instances.get(RANDOM.nextInt(instances.size()));
            logger.debug("Selected instance {} using simple random strategy for service {}",
                    selected.getName(), serviceType);
            recordLoadBalancerSelection(serviceType, "random", selected.getName());
            return selected;
        }

        long randomWeight = nextWeight(totalWeight);
        long currentWeight = 0;
        for (ModelRouterProperties.ModelInstance instance : instances) {
            currentWeight += Math.max(0, instance.getWeight());
            if (randomWeight < currentWeight) {
                logger.debug("Selected instance {} using weighted random strategy for service {}",
                        instance.getName(), serviceType);
                recordLoadBalancerSelection(serviceType, "random", instance.getName());
                return instance;
            }
        }

        ModelRouterProperties.ModelInstance lastInstance = instances.get(instances.size() - 1);
        logger.debug("Fallback selection of instance {} for service {}", lastInstance.getName(), serviceType);
        recordLoadBalancerSelection(serviceType, "random", lastInstance.getName());
        return lastInstance;
    }

    /**
     * 取 [0, totalWeight) 上的随机权重。
     *
     * <p>使用类级 {@link SecureRandom} 实例（线程安全）。v3.5.4（#648/#655）之前每个请求都调用
     * {@code SecureRandom.getInstanceStrong()}，在 Linux 上会反复走 provider 解析、甚至阻塞在熵池上。</p>
     */
    private static long nextWeight(final long totalWeight) {
        return (long) (RANDOM.nextDouble() * totalWeight);
    }

    /**
     * 记录负载均衡器选择指标
     */
    private void recordLoadBalancerSelection(
            final String service, final String strategy, final String selectedInstance) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordLoadBalancer(service, strategy, selectedInstance);
            } catch (Exception e) {
                logger.warn("Failed to record load balancer metrics: {}", e.getMessage());
            }
        }
    }
}

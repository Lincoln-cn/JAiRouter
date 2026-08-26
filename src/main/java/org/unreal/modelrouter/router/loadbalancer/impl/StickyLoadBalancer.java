package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.9.0: 会话粘性负载均衡器
 *
 * <p>通过一致性哈希将相同亲和性键(apiKeyId|serviceType|modelName)的请求固定到同一实例，
 * 利用 LLM 的 KV 缓存前缀复用机制(prefill-only，减少重复计算)。
 *
 * <p>工作原理:
 * <ol>
 *   <li>根据 affinityKey 在一致性哈希环上查找目标实例</li>
 *   <li>如果目标实例可用，直接返回</li>
 *   <li>如果目标实例不可用(健康/熔断/限流)，回退到委托 LB 的正常选择</li>
 *   <li>affinityKey 为 null 时直接委托给 delegate</li>
 * </ol>
 *
 * <p>适用场景: 自建 vLLM/GPUStack 多实例部署(前缀缓存需要亲和性);
 * 云 API(OpenAI/DeepSeek 等单端点)不需要此功能。
 */
public class StickyLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(StickyLoadBalancer.class);
    private static final int DEFAULT_VIRTUAL_NODES = 150;

    private final LoadBalancer delegate;
    private final int virtualNodeCount;
    private final ServiceStateManager serviceStateManager;
    private final CircuitBreakerManager circuitBreakerManager;

    // 一致性哈希环
    private final TreeMap<Long, ModelRouterProperties.ModelInstance> hashCircle = new TreeMap<>();
    // 虚拟节点映射，用于清理
    private final Map<String, java.util.List<Long>> virtualNodesMap = new ConcurrentHashMap<>();

    public StickyLoadBalancer(final LoadBalancer delegate,
                              final ServiceStateManager serviceStateManager,
                              final CircuitBreakerManager circuitBreakerManager) {
        this(delegate, serviceStateManager, circuitBreakerManager, DEFAULT_VIRTUAL_NODES);
    }

    public StickyLoadBalancer(final LoadBalancer delegate,
                              final ServiceStateManager serviceStateManager,
                              final CircuitBreakerManager circuitBreakerManager,
                              final int virtualNodeCount) {
        this.delegate = delegate;
        this.serviceStateManager = serviceStateManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.virtualNodeCount = virtualNodeCount;
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String clientIp) {
        return delegate.selectInstance(instances, clientIp);
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String clientIp,
            final String serviceType) {
        return delegate.selectInstance(instances, clientIp, serviceType);
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String affinityKey,
            final String clientIp,
            final String serviceType) {
        // 无亲和性键时委托给原 LB
        if (affinityKey == null || affinityKey.isBlank()) {
            return delegate.selectInstance(instances, clientIp, serviceType);
        }
        if (instances == null || instances.isEmpty()) {
            return delegate.selectInstance(instances, clientIp, serviceType);
        }

        // 构建哈希环(使用当前实例列表)
        rebuildHashRing(instances);

        // 亲和性哈希选择
        long hash = murmurHash3(affinityKey);
        Map.Entry<Long, ModelRouterProperties.ModelInstance> entry = hashCircle.higherEntry(hash);
        if (entry == null) {
            entry = hashCircle.firstEntry();
        }

        ModelRouterProperties.ModelInstance stickyTarget = entry.getValue();

        // 检查粘性目标实例是否可用(健康 + 非熔断)
        if (isInstanceAvailable(stickyTarget)) {
            logger.debug("Sticky routing: key='{}' → instance='{}' (service={})",
                    truncateKey(affinityKey), stickyTarget.getName(), serviceType);
            return stickyTarget;
        }

        // 粘性目标不可用，回退到委托 LB
        logger.warn("Sticky target instance '{}' unavailable for key='{}', "
                + "falling back to delegate LB (service={})",
                stickyTarget.getName(), truncateKey(affinityKey), serviceType);
        return delegate.selectInstance(instances, clientIp, serviceType);
    }

    @Override
    public void recordCall(final ModelRouterProperties.ModelInstance instance) {
        delegate.recordCall(instance);
    }

    @Override
    public void recordCallComplete(final ModelRouterProperties.ModelInstance instance) {
        delegate.recordCallComplete(instance);
    }

    @Override
    public void recordCallFailure(final ModelRouterProperties.ModelInstance instance) {
        delegate.recordCallFailure(instance);
    }

    /**
     * 获取委托 LB
     */
    public LoadBalancer getDelegate() {
        return delegate;
    }

    /**
     * 重建一致性哈希环
     */
    private void rebuildHashRing(final List<ModelRouterProperties.ModelInstance> instances) {
        hashCircle.clear();
        virtualNodesMap.clear();

        for (ModelRouterProperties.ModelInstance instance : instances) {
            String instanceId = instance.getInstanceId() != null
                    ? instance.getInstanceId() : instance.getName();
            java.util.List<Long> nodeHashes = new java.util.ArrayList<>();

            for (int i = 0; i < virtualNodeCount; i++) {
                String virtualNodeKey = instanceId + "#" + i;
                long h = murmurHash3(virtualNodeKey);
                hashCircle.put(h, instance);
                nodeHashes.add(h);
            }
            virtualNodesMap.put(instanceId, nodeHashes);
        }
    }

    /**
     * 检查实例是否可用(健康 + 非熔断)
     */
    private boolean isInstanceAvailable(final ModelRouterProperties.ModelInstance instance) {
        if (instance == null) {
            return false;
        }
        // 检查实例健康状态
        if (instance.isHealthy() != null && !instance.isHealthy()) {
            return false;
        }
        // 检查熔断器状态(如果有 CircuitBreakerManager)
        if (circuitBreakerManager != null) {
            try {
                var state = circuitBreakerManager.getState(
                        instance.getInstanceId(), instance.getBaseUrl());
                if (state != null && state.name().equals("OPEN")) {
                    return false;
                }
            } catch (Exception e) {
                logger.debug("Failed to check circuit breaker state: {}", e.getMessage());
            }
        }
        return true;
    }

    /**
     * MurmurHash3 算法(与 ConsistentHashLoadBalancer 一致)
     */
    private long murmurHash3(final String input) {
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        int len = data.length;
        int seed = 0xcc9e2d51;
        int hash = seed;

        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        final int r1 = 15;
        final int r2 = 13;
        final int m = 5;
        final int n = 0xe6546b64;

        int i = 0;
        while (i <= len - 4) {
            int k = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k *= c1;
            k = (k << r1) | (k >>> (32 - r1));
            k *= c2;

            hash ^= k;
            hash = (hash << r2) | (hash >>> (32 - r2));
            hash = hash * m + n;

            i += 4;
        }

        int remaining = len - i;
        if (remaining > 0) {
            int k = 0;
            if (remaining == 3) {
                k ^= data[i + 2] << 16;
            }
            if (remaining >= 2) {
                k ^= data[i + 1] << 8;
            }
            if (remaining >= 1) {
                k ^= data[i];
            }
            k *= c1;
            k = (k << r1) | (k >>> (32 - r1));
            k *= c2;
            hash ^= k;
        }

        hash ^= len;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);

        return Math.abs((long) hash);
    }

    /**
     * 截断亲和性键用于日志(避免泄露 apiKeyId)
     */
    private String truncateKey(final String key) {
        if (key == null) {
            return "null";
        }
        int pipeIdx = key.indexOf('|');
        if (pipeIdx > 0) {
            String prefix = key.substring(0, Math.min(pipeIdx, 4));
            return prefix + "***" + key.substring(pipeIdx);
        }
        return "***";
    }
}

package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import org.unreal.modelrouter.router.model.InstanceKeys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IP Hash 负载均衡策略
 */
public class IpHashLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(IpHashLoadBalancer.class);
    /** 环缓存条目上限（与一致哈希的 #687 同口径）。 */
    private static final int MAX_CACHED_RINGS = 128;

    /** 每个实例的虚拟节点数乘数（沿用历史取值，改它会改变既有路由分布）。 */
    private static final int VIRTUAL_NODES_PER_WEIGHT = 100;

    private final String hashAlgorithm;

    /**
     * #692: 按「候选集指纹」缓存哈希环。
     *
     * <p>原实现每次 {@code selectInstance} 都新建 {@code TreeMap} 并为每个虚拟节点重新算一次摘要，
     * 等于每请求 {@code Σ(weight × 100)} 次哈希。环只与候选集（baseUrl/path/weight）有关，
     * 与 clientIp 无关，因此可以缓存。</p>
     */
    private final Map<String, Ring> ringCache = new ConcurrentHashMap<>();

    /** 环构建次数（供测试与排障使用）。 */
    private final AtomicLong ringBuildCount = new AtomicLong();

    /**
     * #692: 摘要实例按线程复用。
     *
     * <p>{@code MessageDigest} 不是线程安全的，因此用 {@code ThreadLocal} 而不是共享一个实例；
     * 算法不可用时返回 {@code null}，由调用方回退到字符串 hash（与原行为一致）。</p>
     */
    private final ThreadLocal<MessageDigest> digestHolder = new ThreadLocal<>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance(hashAlgorithm.toUpperCase());
            } catch (NoSuchAlgorithmException e) {
                logger.warn("Hash algorithm {} not available, falling back to string hash", hashAlgorithm);
                return null;
            }
        }
    };

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    public IpHashLoadBalancer(final String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm != null ? hashAlgorithm : "md5";
    }

    /** 不可变哈希环：#692 构建后只读，经 {@code putIfAbsent} 发布。 */
    static final class Ring {
        private final TreeMap<Long, ModelRouterProperties.ModelInstance> circle;

        Ring(final TreeMap<Long, ModelRouterProperties.ModelInstance> circle) {
            this.circle = circle;
        }

        Map.Entry<Long, ModelRouterProperties.ModelInstance> ceiling(final long hash) {
            final Map.Entry<Long, ModelRouterProperties.ModelInstance> entry = circle.ceilingEntry(hash);
            return entry != null ? entry : circle.firstEntry();
        }

        int size() {
            return circle.size();
        }
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
            final String clientIp, final String serviceType) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for IP hash selection");
            throw new IllegalArgumentException("No instances available");
        }

        if (clientIp == null || clientIp.trim().isEmpty()) {
            // 回退到随机意味着「粘性」名存实亡：同一客户端不再稳定命中同一实例。
            // 保留回退（取不到客户端标识就无法选实例），但必须显式可见——日志 + 指标，
            // 否则集群 + 无反代场景下取不到真实客户端 IP 时会静默失去亲和性（#161）。
            logger.warn("No client IP provided, falling back to random selection — "
                    + "ip-hash affinity is NOT effective for this request");
            if (metricsCollector != null) {
                metricsCollector.recordLoadBalancer(serviceType, "ip-hash",
                        "random-fallback-no-client-ip");
            }
            return new RandomLoadBalancer().selectInstance(instances, clientIp, serviceType);
        }

        // 使用一致性哈希算法
        long hash = calculateHash(clientIp);
        logger.debug("Calculated hash {} for client IP {}", hash, clientIp);

        // #692: 环按候选集指纹缓存，不再每请求重建
        final Ring ring = ringFor(instances);

        // 找到第一个大于等于hash值的节点（无则回落到环上的第一个节点）
        final Map.Entry<Long, ModelRouterProperties.ModelInstance> entry = ring.ceiling(hash);

        logger.debug("Selected instance {} using IP hash strategy for client IP {} and service {}",
                entry.getValue().getName(), clientIp, serviceType);
        recordLoadBalancerSelection(serviceType, "ip_hash", entry.getValue().getName());
        return entry.getValue();
    }

    /**
     * #692: 取（必要时构建）候选集对应的哈希环。
     *
     * @param instances 候选实例列表（非空，已由调用方校验）
     * @return 构建后只读的环
     */
    Ring ringFor(final List<ModelRouterProperties.ModelInstance> instances) {
        final String key = ringKey(instances);
        if (!ringCache.containsKey(key) && ringCache.size() >= MAX_CACHED_RINGS) {
            ringCache.clear();
        }
        // 用 computeIfAbsent 而不是 get/build/put：并发下同一候选集只允许构建一次
        // （#687 在一致哈希里踩过同一个坑，这里同口径处理）
        return ringCache.computeIfAbsent(key, ignored -> {
            final Ring built = buildRing(instances);
            ringBuildCount.incrementAndGet();
            return built;
        });
    }

    /** 当前缓存的环数量（供测试与排障使用）。 */
    int getCachedRingCount() {
        return ringCache.size();
    }

    /** 环构建次数（供测试与排障使用）。 */
    long getRingBuildCount() {
        return ringBuildCount.get();
    }

    /**
     * #692: 候选集指纹 —— 只包含决定环内容的字段（标识、path、weight）。
     *
     * <p>额外拼上 {@code System.identityHashCode}：配置热重载会换一批对象，
     * 让旧环自然失效而不是把流量继续导到已下线的对象上。</p>
     */
    static String ringKey(final List<ModelRouterProperties.ModelInstance> instances) {
        final List<String> tokens = new ArrayList<>(instances.size());
        for (ModelRouterProperties.ModelInstance instance : instances) {
            if (instance == null) {
                continue;
            }
            tokens.add(InstanceKeys.identifier(instance)
                    + "|" + (instance.getPath() != null ? instance.getPath() : "")
                    + "|" + Math.max(1, instance.getWeight())
                    + "|" + System.identityHashCode(instance));
        }
        tokens.sort(Comparator.naturalOrder());
        return String.join(",", tokens);
    }

    private Ring buildRing(final List<ModelRouterProperties.ModelInstance> instances) {
        final TreeMap<Long, ModelRouterProperties.ModelInstance> circle = new TreeMap<>();
        for (ModelRouterProperties.ModelInstance instance : instances) {
            if (instance == null) {
                continue;
            }
            final int weight = Math.max(1, instance.getWeight());
            for (int i = 0; i < weight * VIRTUAL_NODES_PER_WEIGHT; i++) {
                final String virtualNode = instance.getBaseUrl() + ":" + instance.getPath() + "#" + i;
                circle.put(calculateHash(virtualNode), instance);
            }
        }
        logger.debug("Built IP hash ring with {} virtual nodes", circle.size());
        return new Ring(circle);
    }

    long calculateHash(final String input) {
        final MessageDigest md = digestHolder.get();
        if (md == null) {
            // 算法不可用：与原实现一致，回落到字符串 hash
            return Math.abs(input.hashCode());
        }
        // ThreadLocal 复用：每次前 reset，避免上一条输入的状态影响本轮
        md.reset();
        final byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

        // 取前8个字节转换为long
        long hash = 0;
        for (int i = 0; i < Math.min(8, digest.length); i++) {
            hash = (hash << 8) | (digest[i] & 0xFF);
        }

        return Math.abs(hash);
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

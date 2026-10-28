package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.checker.ServiceStateManager;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡器
 * 
 * 使用一致性哈希算法解决IP Hash冲突问题，支持虚拟节点机制
 * 
 * @author JAiRouter Team
 * @since 2.4.0
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashLoadBalancer.class);
    
    // 默认虚拟节点数量
    private static final int DEFAULT_VIRTUAL_NODES = 150;

    // 环缓存的容量上限（#687：不同模型/服务类型的实例集合各自成环，避免无界增长）
    private static final int MAX_CACHED_RINGS = 128;

    /**
     * 不可变哈希环（#687）。
     *
     * <p>构建完成后只读，可被多线程安全共享：环内的 {@link TreeMap} 不再被修改，
     * 通过 {@link ConcurrentHashMap#putIfAbsent(Object, Object)} 发布，读线程看到的是完整对象。</p>
     */
    static final class HashRing {        private final TreeMap<Long, ModelRouterProperties.ModelInstance> circle;
        private final int instanceCount;

        HashRing(final TreeMap<Long, ModelRouterProperties.ModelInstance> circle, final int instanceCount) {
            this.circle = circle;
            this.instanceCount = instanceCount;
        }

        boolean isEmpty() {
            return circle.isEmpty();
        }

        int size() {
            return circle.size();
        }

        int getInstanceCount() {
            return instanceCount;
        }
    }

    /**
     * 环缓存（#687）：键 = 服务类型 + 实例集合指纹，值 = 构建好的不可变环。
     *
     * <p>此前是一个本类级别的 {@link TreeMap} 环，每次请求都按入参增删节点，导致
     * ① {@link TreeMap} 在并发读写时可能抛 {@code ConcurrentModificationException} 或读到结构性损坏
     * ② 不同模型/服务类型共用同一个环，A 模型的请求会把 B 模型的节点从环上摘掉。</p>
     */
    private final Map<String, HashRing> ringCache = new ConcurrentHashMap<>();

    // 环构建次数（#687）：只用于测试与可观测性，确认同一实例集合不会被反复重建
    private final java.util.concurrent.atomic.AtomicLong ringBuildCount =
            new java.util.concurrent.atomic.AtomicLong();

    // 虚拟节点数量
    private final int virtualNodeCount;

    /**
     * 供测试与可观测性使用：当前缓存的环数量（#687）。
     *
     * @return 已缓存的环个数
     */
    public int getCachedRingCount() {
        return ringCache.size();
    }

    /**
     * 供测试与可观测性使用：累计构建过的环数量（#687）。
     *
     * @return 构建次数
     */
    public long getRingBuildCount() {
        return ringBuildCount.get();
    }

    /**
     * 供测试与可观测性使用：缓存的环个数上限（#687）。
     *
     * @return 上限值
     */
    public static int getMaxCachedRings() {
        return MAX_CACHED_RINGS;
    }
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;
    
    @Autowired(required = false)
    private ServiceStateManager serviceStateManager;

    // 为测试添加 getter 方法
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    public ServiceStateManager getServiceStateManager() {
        return serviceStateManager;
    }

    public ConsistentHashLoadBalancer() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashLoadBalancer(final int virtualNodeCount) {
        this.virtualNodeCount = virtualNodeCount;
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
            logger.warn("No instances available for consistent hash selection");
            throw new IllegalArgumentException("No instances available");
        }

        // 取本次实例集合对应的不可变环（同一集合复用，不修改共享状态）
        final HashRing ring = ringFor(instances, serviceType);

        if (ring.isEmpty()) {
            logger.error("Hash circle is empty, unable to select instance");
            throw new RuntimeException("Hash circle is empty");
        }

        // 计算客户端IP的哈希值
        long hash = hash(clientIp);

        // 在哈希环上找到大于等于该哈希值的第一个节点
        Map.Entry<Long, ModelRouterProperties.ModelInstance> entry = ring.circle.higherEntry(hash);

        // 如果没有找到，则使用环上的第一个节点（形成环状结构）
        if (entry == null) {
            entry = ring.circle.firstEntry();
        }

        // 从哈希环中选择健康实例，如果当前选中的是不健康的，则查找下一个健康的
        ModelRouterProperties.ModelInstance selected = findHealthyInstance(ring, entry, serviceType);

        if (selected == null) {
            logger.warn("No healthy instance found, selecting any instance");
            // 如果找不到健康实例，尝试使用原始哈希值找到的实例
            selected = entry.getValue(); // 如果没有健康实例，返回任意实例
        }

        logger.debug("Selected instance {} using consistent hash for service {}, client IP: {}", 
                selected.getName(), serviceType, clientIp);

        recordLoadBalancerSelection(serviceType, "consistent_hash", selected.getName());

        return selected;
    }
    
    /**
     * 从哈希环中查找健康实例
     * 如果当前实例不健康，则继续查找下一个实例，直到找到健康实例或遍历完整个环
     *
     * @param ring 不可变哈希环（只读）
     * @param startEntry 起始条目
     * @param serviceType 服务类型
     * @return 健康实例或 null（如果没有健康实例）
     */
    private ModelRouterProperties.ModelInstance findHealthyInstance(
            final HashRing ring,
            final Map.Entry<Long, ModelRouterProperties.ModelInstance> startEntry,
            final String serviceType) {

        if (startEntry == null) {
            return null;
        }

        // 如果起始实例是健康的，直接返回
        if (isInstanceHealthy(serviceType, startEntry.getValue())) {
            return startEntry.getValue();
        }

        // 从哈希环中查找下一个健康实例
        Map.Entry<Long, ModelRouterProperties.ModelInstance> currentEntry = startEntry;
        int iterationCount = 0; // 防止无限循环
        int maxIterations = ring.size(); // 最大迭代次数为哈希环大小

        do {
            // 找到下一个条目
            currentEntry = ring.circle.higherEntry(currentEntry.getKey());

            // 如果到达末尾，回到开头（形成环状）
            if (currentEntry == null) {
                currentEntry = ring.circle.firstEntry();
            }

            // 检查当前实例是否健康
            if (isInstanceHealthy(serviceType, currentEntry.getValue())) {
                return currentEntry.getValue();
            }

            iterationCount++;

        } while (iterationCount < maxIterations && currentEntry != null
                && !currentEntry.getKey().equals(startEntry.getKey()));

        return null; // 没有找到健康实例
    }

    /**
     * 检查实例是否健康
     *
     * @param serviceType 服务类型
     * @param instance 实例
     * @return 是否健康
     */
    private boolean isInstanceHealthy(String serviceType, ModelRouterProperties.ModelInstance instance) {
        if (serviceStateManager != null) {
            return serviceStateManager.isInstanceHealthy(serviceType, instance);
        }
        // 如果没有服务状态管理器，则使用实例的健康状态字段
        return instance.isHealthy() != null ? instance.isHealthy() : true;
    }

    /**
     * 取（必要时构建）实例集合对应的不可变哈希环（#687）。
     *
     * <p>同一实例集合（同一批实例对象 + 同一服务类型）复用同一个环，不再每请求增删节点。</p>
     *
     * @param instances 实例列表
     * @param serviceType 服务类型
     * @return 不可变哈希环
     */
    HashRing ringFor(final List<ModelRouterProperties.ModelInstance> instances, final String serviceType) {
        final String key = ringKey(instances, serviceType);
        final HashRing cached = ringCache.get(key);
        if (cached != null) {
            return cached;
        }

        // 用 computeIfAbsent 而不是 get/build/putIfAbsent：后者在并发下会让多个线程同时构建同一个环
        // （构建次数随并发线程数增长，且各自的对象被丢弃），CI 上已经抓到过一次（#687 收尾时修）。
        final HashRing ring = ringCache.computeIfAbsent(key, ignored -> {
            final HashRing built = buildRing(instances);
            ringBuildCount.incrementAndGet();
            return built;
        });

        if (ringCache.size() > MAX_CACHED_RINGS) {
            logger.debug("Hash ring cache exceeded {} entries, clearing (key={})", MAX_CACHED_RINGS, key);
            ringCache.clear();
        }
        return ring;
    }

    /**
     * 计算实例集合指纹（#687）。
     *
     * <p>键包含服务类型、实例 ID、baseUrl 与对象身份哈希：同一批对象稳定复用同一个环；
     * 配置重载生成新对象时键随之变化，自然重建，不会把节点指向已失效的旧对象。</p>
     *
     * @param instances 实例列表
     * @param serviceType 服务类型
     * @return 指纹字符串
     */
    static String ringKey(final List<ModelRouterProperties.ModelInstance> instances, final String serviceType) {
        final StringBuilder builder = new StringBuilder(64 + instances.size() * 40);
        builder.append(serviceType != null ? serviceType : "unknown").append('|');
        instances.stream()
                .map(ConsistentHashLoadBalancer::instanceToken)
                .sorted()
                .forEach(token -> builder.append(token).append(';'));
        return builder.toString();
    }

    private static String instanceToken(final ModelRouterProperties.ModelInstance instance) {
        return (instance.getInstanceId() != null ? instance.getInstanceId() : "")
                + '@'
                + (instance.getBaseUrl() != null ? instance.getBaseUrl() : "")
                + '#'
                + System.identityHashCode(instance);
    }

    /**
     * 按实例列表构建不可变哈希环（#687）。
     *
     * @param instances 实例列表
     * @return 构建好的环
     */
    private HashRing buildRing(final List<ModelRouterProperties.ModelInstance> instances) {
        final TreeMap<Long, ModelRouterProperties.ModelInstance> circle = new TreeMap<>();
        int distinctInstances = 0;

        for (ModelRouterProperties.ModelInstance instance : instances) {
            if (instance == null) {
                continue;
            }
            distinctInstances++;
            for (int i = 0; i < virtualNodeCount; i++) {
                final String virtualNodeKey = instance.getInstanceId() + "#" + i;
                circle.put(hash(virtualNodeKey), instance);
            }
        }

        logger.debug("Built consistent hash ring with {} instances and {} virtual nodes",
                distinctInstances, circle.size());
        return new HashRing(circle, distinctInstances);
    }


    /**
     * 计算字符串的哈希值
     * 
     * 使用 MurmurHash 算法以获得更好的分布特性
     * 
     * @param key 输入字符串
     * @return 哈希值
     */
    private long hash(String key) {
        String effectiveKey = key != null ? key : "";        
        // 使用 MurmurHash 算法以获得更好的哈希分布
        // 这种算法具有很好的雪崩效应，能够均匀分布键值
        return murmurHash3(effectiveKey);
    }
    
    /**
     * MurmurHash3 算法实现
     * 
     * @param input 输入字符串
     * @return 哈希值
     */
    private long murmurHash3(String input) {
        byte[] data = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
        
        // 处理剩余字节
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

    @Override
    public void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
        // 可在此处添加统计逻辑
    }

    @Override
    public void recordCallFailure(ModelRouterProperties.ModelInstance instance) {
        // 可在此处添加统计逻辑
    }

    /**
     * 记录负载均衡器选择指标
     */
    private void recordLoadBalancerSelection(String service, String strategy, String selectedInstance) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordLoadBalancer(service, strategy, selectedInstance);
            } catch (Exception e) {
                logger.warn("Failed to record load balancer metrics: {}", e.getMessage());
            }
        }
    }
}
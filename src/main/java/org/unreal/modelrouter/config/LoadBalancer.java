package org.unreal.modelrouter.config;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实现了四种负载均衡策略：
 * RandomLoadBalancer: 随机策略（支持权重）
 * RoundRobinLoadBalancer: 轮询策略（支持权重）
 * LeastConnectionsLoadBalancer: 最少连接策略（支持权重）
 * IpHashLoadBalancer: IP Hash策略（一致性哈希，支持权重）
 */
public interface LoadBalancer {

    /**
     * 选择一个实例
     * @param instances 可用实例列表
     * @param clientIp 客户端IP (用于IP Hash策略)
     * @return 选中的实例
     */
    ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp);

    /**
     * 记录实例调用
     * @param instance 被调用的实例
     */
    default void recordCall(ModelRouterProperties.ModelInstance instance) {
        // 默认空实现
    }

    /**
     * 记录实例调用完成
     * @param instance 调用完成的实例
     */
    default void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
        // 默认空实现
    }

    /**
     * 随机负载均衡策略
     */
    class RandomLoadBalancer implements LoadBalancer {
        private final Random random = new Random();

        @Override
        public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
            if (instances == null || instances.isEmpty()) {
                throw new IllegalArgumentException("No instances available");
            }

            // 考虑权重的随机选择
            int totalWeight = instances.stream().mapToInt(ModelRouterProperties.ModelInstance::getWeight).sum();
            if (totalWeight <= 0) {
                // 如果没有权重，使用简单随机
                return instances.get(random.nextInt(instances.size()));
            }

            int randomWeight = random.nextInt(totalWeight);
            int currentWeight = 0;

            for (ModelRouterProperties.ModelInstance instance : instances) {
                currentWeight += instance.getWeight();
                if (randomWeight < currentWeight) {
                    return instance;
                }
            }

            // 兜底返回最后一个
            return instances.get(instances.size() - 1);
        }
    }

    /**
     * 轮询负载均衡策略
     */
    class RoundRobinLoadBalancer implements LoadBalancer {
        private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

        @Override
        public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
            if (instances == null || instances.isEmpty()) {
                throw new IllegalArgumentException("No instances available");
            }

            String key = generateInstancesKey(instances);
            AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));

            // 考虑权重的轮询
            List<ModelRouterProperties.ModelInstance> weightedInstances = createWeightedList(instances);
            int index = counter.getAndIncrement() % weightedInstances.size();

            return weightedInstances.get(index);
        }

        private String generateInstancesKey(List<ModelRouterProperties.ModelInstance> instances) {
            return instances.stream()
                    .map(i -> i.getBaseUrl() + ":" + i.getPath())
                    .sorted()
                    .reduce("", (a, b) -> a + "," + b);
        }

        private List<ModelRouterProperties.ModelInstance> createWeightedList(List<ModelRouterProperties.ModelInstance> instances) {
            List<ModelRouterProperties.ModelInstance> weightedList = new ArrayList<>();
            for (ModelRouterProperties.ModelInstance instance : instances) {
                int weight = Math.max(1, instance.getWeight());
                for (int i = 0; i < weight; i++) {
                    weightedList.add(instance);
                }
            }
            return weightedList;
        }
    }

    /**
     * 最少连接数负载均衡策略
     */
    class LeastConnectionsLoadBalancer implements LoadBalancer {
        private final Map<String, AtomicLong> connectionCounts = new ConcurrentHashMap<>();

        @Override
        public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
            if (instances == null || instances.isEmpty()) {
                throw new IllegalArgumentException("No instances available");
            }

            ModelRouterProperties.ModelInstance selectedInstance = null;
            long minConnections = Long.MAX_VALUE;
            double minWeightedConnections = Double.MAX_VALUE;

            for (ModelRouterProperties.ModelInstance instance : instances) {
                String key = getInstanceKey(instance);
                long connections = connectionCounts.getOrDefault(key, new AtomicLong(0)).get();

                // 考虑权重的最少连接算法: connections / weight
                double weightedConnections = instance.getWeight() > 0 ?
                        (double) connections / instance.getWeight() : connections;

                if (weightedConnections < minWeightedConnections ||
                        (weightedConnections == minWeightedConnections && connections < minConnections)) {
                    minWeightedConnections = weightedConnections;
                    minConnections = connections;
                    selectedInstance = instance;
                }
            }

            return selectedInstance;
        }

        @Override
        public void recordCall(ModelRouterProperties.ModelInstance instance) {
            String key = getInstanceKey(instance);
            connectionCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        }

        @Override
        public void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
            String key = getInstanceKey(instance);
            AtomicLong count = connectionCounts.get(key);
            if (count != null) {
                count.decrementAndGet();
            }
        }

        private String getInstanceKey(ModelRouterProperties.ModelInstance instance) {
            return instance.getBaseUrl() + ":" + instance.getPath();
        }
    }

    /**
     * IP Hash 负载均衡策略
     */
    class IpHashLoadBalancer implements LoadBalancer {
        private final String hashAlgorithm;

        public IpHashLoadBalancer(String hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm != null ? hashAlgorithm : "md5";
        }

        @Override
        public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
            if (instances == null || instances.isEmpty()) {
                throw new IllegalArgumentException("No instances available");
            }

            if (clientIp == null || clientIp.trim().isEmpty()) {
                // 如果没有客户端IP，回退到随机策略
                return new RandomLoadBalancer().selectInstance(instances, clientIp);
            }

            // 使用一致性哈希算法
            long hash = calculateHash(clientIp);

            // 考虑权重创建虚拟节点
            TreeMap<Long, ModelRouterProperties.ModelInstance> ring = new TreeMap<>();
            for (ModelRouterProperties.ModelInstance instance : instances) {
                int weight = Math.max(1, instance.getWeight());
                // 为每个实例根据权重创建虚拟节点
                for (int i = 0; i < weight * 100; i++) {
                    String virtualNode = instance.getBaseUrl() + ":" + instance.getPath() + "#" + i;
                    long virtualHash = calculateHash(virtualNode);
                    ring.put(virtualHash, instance);
                }
            }

            // 找到第一个大于等于hash值的节点
            Map.Entry<Long, ModelRouterProperties.ModelInstance> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                // 如果没有找到，使用第一个节点
                entry = ring.firstEntry();
            }

            return entry.getValue();
        }

        private long calculateHash(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance(hashAlgorithm.toUpperCase());
                byte[] digest = md.digest(input.getBytes());

                // 取前8个字节转换为long
                long hash = 0;
                for (int i = 0; i < Math.min(8, digest.length); i++) {
                    hash = (hash << 8) | (digest[i] & 0xFF);
                }

                return Math.abs(hash);
            } catch (NoSuchAlgorithmException e) {
                // 如果算法不支持，使用简单的字符串hash
                return Math.abs(input.hashCode());
            }
        }
    }
}
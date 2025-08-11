package org.unreal.modelrouter.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.model.ModelRouterProperties;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * IP Hash 负载均衡策略
 */
public class IpHashLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(IpHashLoadBalancer.class);
    private final String hashAlgorithm;

    public IpHashLoadBalancer(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm != null ? hashAlgorithm : "md5";
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for IP hash selection");
            throw new IllegalArgumentException("No instances available");
        }

        if (clientIp == null || clientIp.trim().isEmpty()) {
            logger.warn("No client IP provided, falling back to random selection");
            // 如果没有客户端IP，回退到随机策略
            return new RandomLoadBalancer().selectInstance(instances, clientIp);
        }

        // 使用一致性哈希算法
        long hash = calculateHash(clientIp);
        logger.debug("Calculated hash {} for client IP {}", hash, clientIp);

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

        logger.debug("Selected instance {} using IP hash strategy for client IP {}",
                entry.getValue().getName(), clientIp);
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
            logger.warn("Hash algorithm {} not available, falling back to string hash", hashAlgorithm);
            // 如果算法不支持，使用简单的字符串hash
            return Math.abs(input.hashCode());
        }
    }
}

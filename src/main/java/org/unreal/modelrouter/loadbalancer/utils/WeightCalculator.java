package org.unreal.modelrouter.loadbalancer.utils;

import org.unreal.modelrouter.model.ModelRouterProperties;

import java.util.List;

/**
 * 权重计算器
 * 
 * 用于安全地进行权重计算，避免整数溢出
 * 
 * @author JAiRouter Team
 * @since 2.4.0
 */
public class WeightCalculator {
    
    /**
     * 安全计算总权重，使用 long 类型避免溢出
     * 
     * @param instances 实例列表
     * @return 总权重值
     */
    public static long calculateTotalWeight(List<ModelRouterProperties.ModelInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return 0L;
        }
        
        return instances.stream()
                .mapToLong(instance -> Math.max(0, instance.getWeight()))
                .sum();
    }
    
    /**
     * 安全计算加权随机值
     * 
     * @param totalWeight 总权重
     * @return 随机权重值
     */
    public static long calculateRandomWeight(long totalWeight) {
        if (totalWeight <= 0) {
            return 0L;
        }
        
        // 使用更安全的 SecureRandom 生成 0 到 totalWeight-1 之间的随机数
        return (long) (java.security.SecureRandom.getInstanceStrong().nextDouble() * totalWeight);
    }
    
    /**
     * 安全计算实例累积权重
     * 
     * @param instances 实例列表
     * @param endIndex 结束索引（包含）
     * @return 累积权重值
     */
    public static long calculateCumulativeWeight(List<ModelRouterProperties.ModelInstance> instances, int endIndex) {
        if (instances == null || endIndex < 0) {
            return 0L;
        }
        
        long cumulativeWeight = 0L;
        int limit = Math.min(endIndex + 1, instances.size());
        
        for (int i = 0; i < limit; i++) {
            ModelRouterProperties.ModelInstance instance = instances.get(i);
            if (instance != null) {
                cumulativeWeight += Math.max(0, instance.getWeight());
            }
        }
        
        return cumulativeWeight;
    }
    
    /**
     * 检查权重是否有效
     * 
     * @param weight 权重值
     * @return 是否有效
     */
    public static boolean isValidWeight(int weight) {
        return weight >= 0 && weight <= Integer.MAX_VALUE / 2; // 防止溢出的安全上限
    }
    
    /**
     * 检查权重是否为正数
     * 
     * @param weight 权重值
     * @return 是否为正数
     */
    public static boolean isPositiveWeight(int weight) {
        return weight > 0;
    }
}
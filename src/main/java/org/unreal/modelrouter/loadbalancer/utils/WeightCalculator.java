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
    public static long calculateTotalWeight(final List<ModelRouterProperties.ModelInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return 0L;
        }
        
        return instances.stream()
                .mapToLong(instance -> Math.max(0, instance.getWeight()))
                .sum();
    }
    
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /**
     * 安全计算加权随机值
     *
     * @param totalWeight 总权重
     * @return 随机权重值
     */
    public static long calculateRandomWeight(final long totalWeight) {
        if (totalWeight <= 0) {
            return 0L;
        }

        // 使用预初始化的安全随机数生成器
        return (long) (SECURE_RANDOM.nextDouble() * totalWeight);
    }
    
    /**
     * 安全计算实例累积权重
     * 
     * @param instances 实例列表
     * @param endIndex 结束索引（包含）
     * @return 累积权重值
     */
    public static long calculateCumulativeWeight(final List<ModelRouterProperties.ModelInstance> instances,final int endIndex) {
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
    public static boolean isValidWeight(final int weight) {
        // 负权重在范围内也被认为是"有效"，但会在计算中当作0处理
        return weight <= Integer.MAX_VALUE / 2; // 防止溢出的安全上限，允许负值
    }
    
    /**
     * 检查权重是否为正数
     * 
     * @param weight 权重值
     * @return 是否为正数
     */
    public static boolean isPositiveWeight(final int weight) {
        return weight > 0;
    }
}
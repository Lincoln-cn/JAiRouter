package org.unreal.modelrouter.router.loadbalancer.utils;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.model.ModelRouterProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权重计算器单元测试
 * 
 * 测试权重计算相关的安全方法，防止整数溢出等问题
 */
class WeightCalculatorTest {

    @Test
    void testCalculateTotalWeight_EmptyList() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = Collections.emptyList();
        
        // When
        long result = WeightCalculator.calculateTotalWeight(instances);
        
        // Then
        assertEquals(0L, result);
    }
    
    @Test
    void testCalculateTotalWeight_NullList() {
        // When
        long result = WeightCalculator.calculateTotalWeight(null);
        
        // Then
        assertEquals(0L, result);
    }
    
    @Test
    void testCalculateTotalWeight_WithPositiveWeights() {
        // Given
        ModelRouterProperties.ModelInstance instance1 = createInstance("instance1", 10);
        ModelRouterProperties.ModelInstance instance2 = createInstance("instance2", 20);
        ModelRouterProperties.ModelInstance instance3 = createInstance("instance3", 30);
        List<ModelRouterProperties.ModelInstance> instances = Arrays.asList(instance1, instance2, instance3);
        
        // When
        long result = WeightCalculator.calculateTotalWeight(instances);
        
        // Then
        assertEquals(60L, result);
    }
    
    @Test
    void testCalculateTotalWeight_WithZeroWeights() {
        // Given
        ModelRouterProperties.ModelInstance instance1 = createInstance("instance1", 0);
        ModelRouterProperties.ModelInstance instance2 = createInstance("instance2", 0);
        List<ModelRouterProperties.ModelInstance> instances = Arrays.asList(instance1, instance2);
        
        // When
        long result = WeightCalculator.calculateTotalWeight(instances);
        
        // Then
        assertEquals(0L, result);
    }
    
    @Test
    void testCalculateTotalWeight_WithNegativeWeights() {
        // Given
        ModelRouterProperties.ModelInstance instance1 = createInstance("instance1", -10);
        ModelRouterProperties.ModelInstance instance2 = createInstance("instance2", 20);
        List<ModelRouterProperties.ModelInstance> instances = Arrays.asList(instance1, instance2);
        
        // When
        long result = WeightCalculator.calculateTotalWeight(instances);
        
        // Then
        // 负权重会被当作0处理，所以结果是20
        assertEquals(20L, result);
    }
    
    @Test
    void testCalculateTotalWeight_OverflowProtection() {
        // Given - 使用较大的权重值测试溢出保护
        ModelRouterProperties.ModelInstance instance1 = createInstance("instance1", Integer.MAX_VALUE / 2);
        ModelRouterProperties.ModelInstance instance2 = createInstance("instance2", Integer.MAX_VALUE / 2);
        List<ModelRouterProperties.ModelInstance> instances = Arrays.asList(instance1, instance2);
        
        // When
        long result = WeightCalculator.calculateTotalWeight(instances);
        
        // Then
        assertTrue(result > 0); // 确保结果是正数
        assertEquals((long) (Integer.MAX_VALUE / 2) * 2, result);
    }
    
    @Test
    void testCalculateRandomWeight_ZeroTotalWeight() {
        // When
        long result = WeightCalculator.calculateRandomWeight(0L);
        
        // Then
        assertEquals(0L, result);
    }
    
    @Test
    void testCalculateRandomWeight_NegativeTotalWeight() {
        // When
        long result = WeightCalculator.calculateRandomWeight(-100L);
        
        // Then
        assertEquals(0L, result);
    }
    
    @Test
    void testCalculateRandomWeight_PositiveTotalWeight() {
        // Given
        long totalWeight = 100L;
        
        // When
        long result = WeightCalculator.calculateRandomWeight(totalWeight);
        
        // Then
        assertTrue(result >= 0 && result < totalWeight, 
            "Random weight should be in range [0, " + totalWeight + ")");
    }
    
    @Test
    void testCalculateCumulativeWeight_EmptyList() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = Collections.emptyList();
        
        // When
        long result = WeightCalculator.calculateCumulativeWeight(instances, 5);
        
        // Then
        assertEquals(0L, result);
    }
    
    @Test
    void testCalculateCumulativeWeight_NullList() {
        // When
        long result = WeightCalculator.calculateCumulativeWeight(null, 5);
        
        // Then
        assertEquals(0L, result);
    }
    
    @Test
    void testCalculateCumulativeWeight_EndIndexOutOfRange() {
        // Given
        ModelRouterProperties.ModelInstance instance1 = createInstance("instance1", 10);
        ModelRouterProperties.ModelInstance instance2 = createInstance("instance2", 20);
        List<ModelRouterProperties.ModelInstance> instances = Arrays.asList(instance1, instance2);
        
        // When
        long result = WeightCalculator.calculateCumulativeWeight(instances, 10); // 超出范围
        
        // Then
        assertEquals(30L, result); // 应该计算所有实例的权重
    }
    
    @Test
    void testCalculateCumulativeWeight_ValidRange() {
        // Given
        ModelRouterProperties.ModelInstance instance1 = createInstance("instance1", 10);
        ModelRouterProperties.ModelInstance instance2 = createInstance("instance2", 20);
        ModelRouterProperties.ModelInstance instance3 = createInstance("instance3", 30);
        List<ModelRouterProperties.ModelInstance> instances = Arrays.asList(instance1, instance2, instance3);
        
        // When
        long result = WeightCalculator.calculateCumulativeWeight(instances, 1); // 计算前2个实例
        
        // Then
        assertEquals(30L, result); // 10 + 20 = 30
    }
    
    @Test
    void testIsValidWeight_ValidPositiveWeight() {
        // When
        boolean result = WeightCalculator.isValidWeight(100);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testIsValidWeight_ZeroWeight() {
        // When
        boolean result = WeightCalculator.isValidWeight(0);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testIsValidWeight_NegativeWeight() {
        // When
        boolean result = WeightCalculator.isValidWeight(-10);
        
        // Then
        assertTrue(result); // 负权重在范围内仍被视为"有效"，但会在计算中当作0处理
    }
    
    @Test
    void testIsValidWeight_TooLargeWeight() {
        // When
        boolean result = WeightCalculator.isValidWeight(Integer.MAX_VALUE);
        
        // Then
        assertFalse(result); // 太大的权重被认为无效以防止溢出
    }
    
    @Test
    void testIsPositiveWeight_Positive() {
        // When
        boolean result = WeightCalculator.isPositiveWeight(10);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testIsPositiveWeight_Zero() {
        // When
        boolean result = WeightCalculator.isPositiveWeight(0);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testIsPositiveWeight_Negative() {
        // When
        boolean result = WeightCalculator.isPositiveWeight(-10);
        
        // Then
        assertFalse(result);
    }
    
    private ModelRouterProperties.ModelInstance createInstance(String name, int weight) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setWeight(weight);
        return instance;
    }
}
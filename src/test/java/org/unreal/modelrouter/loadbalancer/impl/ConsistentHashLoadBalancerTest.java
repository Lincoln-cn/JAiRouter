package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import java.util.Collections;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 一致性哈希负载均衡器单元测试
 * 
 * 测试一致性哈希算法的正确性和冲突处理能力
 * 
 * @author JAiRouter Team
 * @since 2.4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsistentHashLoadBalancer 单元测试")
class ConsistentHashLoadBalancerTest {

    @Mock
    private MetricsCollector metricsCollector;

    private ConsistentHashLoadBalancer consistentHashLoadBalancer;

    @BeforeEach
    void setUp() throws Exception {
        consistentHashLoadBalancer = new ConsistentHashLoadBalancer(50); // 使用较小的虚拟节点数便于测试
        
        // 使用反射设置私有成员
        java.lang.reflect.Field metricsCollectorField = ConsistentHashLoadBalancer.class.getDeclaredField("metricsCollector");
        metricsCollectorField.setAccessible(true);
        metricsCollectorField.set(consistentHashLoadBalancer, metricsCollector);
    }

    @Test
    @DisplayName("选择实例 - 正常情况")
    void testSelectInstance_NormalCase() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3);
        String clientIp = "192.168.1.100";

        // When
        ModelRouterProperties.ModelInstance result = consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result));
        verify(consistentHashLoadBalancer.getMetricsCollector(), times(1)).recordLoadBalancer(eq("chat"), eq("consistent_hash"), anyString());
    }

    @Test
    @DisplayName("选择实例 - 空实例列表")
    void testSelectInstance_EmptyInstances() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        String clientIp = "192.168.1.100";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat")
        );
    }

    @Test
    @DisplayName("选择实例 - null 实例列表")
    void testSelectInstance_NullInstances() {
        // Given
        String clientIp = "192.168.1.100";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            consistentHashLoadBalancer.selectInstance(null, clientIp, "chat")
        );
    }

    @Test
    @DisplayName("选择实例 - 客户端 IP 为 null")
    void testSelectInstance_NullClientIp() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(2);
        String clientIp = null;

        // When
        ModelRouterProperties.ModelInstance result = consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result));
        verify(consistentHashLoadBalancer.getMetricsCollector(), times(1)).recordLoadBalancer(eq("chat"), eq("consistent_hash"), anyString());
    }

    @Test
    @DisplayName("选择实例 - 一致性哈希分布测试")
    void testSelectInstance_HashDistribution() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(5);
        List<String> ips = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            ips.add("192.168.1." + i);
        }

        // When
        List<ModelRouterProperties.ModelInstance> results = new ArrayList<>();
        for (String ip : ips) {
            ModelRouterProperties.ModelInstance result = consistentHashLoadBalancer.selectInstance(instances, ip, "chat");
            results.add(result);
        }

        // Then
        // 检查是否返回了有效结果
        assertEquals(100, results.size());
        
        // 统计每个实例被选择的次数
        java.util.Map<String, Integer> selectionCount = new java.util.HashMap<>();
        for (ModelRouterProperties.ModelInstance result : results) {
            String name = result.getName();
            selectionCount.put(name, selectionCount.getOrDefault(name, 0) + 1);
        }
        
        // 一致性哈希不要求所有实例都被选中，只要有一定分布即可
        // 至少应该有大部分实例被选中
        assertTrue(selectionCount.size() >= 3, 
            "大部分实例（>=3/5）都应该被选中，实际选中了 " + selectionCount.size() + " 个实例");
        
        // 记录选择分布情况
        System.out.println("一致性哈希分布情况:");
        selectionCount.forEach((name, count) -> 
            System.out.println("  Instance " + name + " selected " + count + " times"));
        
        // 检查分布是否相对均匀（没有极端偏斜）
        Integer maxSelections = Collections.max(selectionCount.values());
        Integer minSelections = Collections.min(selectionCount.values());
        double ratio = (double) maxSelections / minSelections;
        assertTrue(ratio <= 5.0, 
            "选择分布不应过于偏斜，最大/最小选择次数比为 " + ratio + "，应小于等于 5.0");
    }

    @Test
    @DisplayName("选择实例 - 相同 IP 总是返回相同实例")
    void testSelectInstance_IdempotentForSameIp() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3);
        String clientIp = "10.0.0.100";

        // When
        ModelRouterProperties.ModelInstance result1 = consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat");
        ModelRouterProperties.ModelInstance result2 = consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat");
        ModelRouterProperties.ModelInstance result3 = consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertEquals(result1, result2, "相同 IP 应该总是返回相同的实例");
        assertEquals(result2, result3, "相同 IP 应该总是返回相同的实例");
        verify(consistentHashLoadBalancer.getMetricsCollector(), times(3)).recordLoadBalancer(eq("chat"), eq("consistent_hash"), anyString());
    }

    @Test
    @DisplayName("选择实例 - 健康实例过滤")
    void testSelectInstance_HealthFiltering() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        
        // 创建一个健康实例和一个不健康实例
        ModelRouterProperties.ModelInstance healthyInstance = createInstance("healthy-instance", "http://healthy:8080", true);
        ModelRouterProperties.ModelInstance unhealthyInstance = createInstance("unhealthy-instance", "http://unhealthy:8080", false);
        
        instances.add(healthyInstance);
        instances.add(unhealthyInstance);
        
        String clientIp = "192.168.1.50";

        // When
        ModelRouterProperties.ModelInstance result = consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertNotNull(result);
        assertEquals("healthy-instance", result.getName(), "应该选择健康实例");
        verify(consistentHashLoadBalancer.getMetricsCollector(), times(1)).recordLoadBalancer(eq("chat"), eq("consistent_hash"), eq("healthy-instance"));
    }

    @Test
    @DisplayName("选择实例 - 所有实例都不健康时的处理")
    void testSelectInstance_AllUnhealthy() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        
        // 创建两个不健康实例
        ModelRouterProperties.ModelInstance instance1 = createInstance("unhealthy-instance-1", "http://unhealthy1:8080", false);
        ModelRouterProperties.ModelInstance instance2 = createInstance("unhealthy-instance-2", "http://unhealthy2:8080", false);
        
        instances.add(instance1);
        instances.add(instance2);
        
        String clientIp = "192.168.1.50";

        // When
        ModelRouterProperties.ModelInstance result = consistentHashLoadBalancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result), "应该返回其中一个实例");
        verify(consistentHashLoadBalancer.getMetricsCollector(), times(1)).recordLoadBalancer(eq("chat"), eq("consistent_hash"), anyString());
    }

    @Test
    @DisplayName("选择实例 - 服务类型参数测试")
    void testSelectInstance_ServiceTypeParameter() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(2);
        String clientIp = "192.168.2.100";
        String serviceType = "embedding";

        // When
        ModelRouterProperties.ModelInstance result = consistentHashLoadBalancer.selectInstance(instances, clientIp, serviceType);

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result));
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("embedding"), eq("consistent_hash"), anyString());
    }

    @Test
    @DisplayName("记录调用完成 - 无异常")
    void testRecordCallComplete_NoException() {
        // Given
        ModelRouterProperties.ModelInstance instance = createInstance("test-instance", "http://test:8080", true);

        // When & Then
        assertDoesNotThrow(() -> consistentHashLoadBalancer.recordCallComplete(instance));
    }

    @Test
    @DisplayName("记录调用失败 - 无异常")
    void testRecordCallFailure_NoException() {
        // Given
        ModelRouterProperties.ModelInstance instance = createInstance("test-instance", "http://test:8080", true);

        // When & Then
        assertDoesNotThrow(() -> consistentHashLoadBalancer.recordCallFailure(instance));
    }

    /**
     * 创建测试实例列表
     * 
     * @param count 实例数量
     * @return 测试实例列表
     */
    private List<ModelRouterProperties.ModelInstance> createTestInstances(int count) {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            instances.add(createInstance("instance-" + i, "http://instance" + i + ":8080", true));
        }
        return instances;
    }

    /**
     * 创建测试实例
     * 
     * @param name 实例名称
     * @param baseUrl 基础URL
     * @param healthy 是否健康
     * @return 测试实例
     */
    private ModelRouterProperties.ModelInstance createInstance(String name, String baseUrl, boolean healthy) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setId(name);
        instance.setName(name);
        instance.setBaseUrl(baseUrl);
        instance.setHealthy(healthy);
        instance.setWeight(1);
        return instance;
    }
}
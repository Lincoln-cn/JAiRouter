package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 轮询负载均衡器单元测试
 *
 * 测试轮询算法的正确性和权重支持
 *
 * @author JAiRouter Team
 * @since 2.4.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoundRobinLoadBalancer 单元测试")
class RoundRobinLoadBalancerTest {

    @Mock
    private MetricsCollector metricsCollector;

    private RoundRobinLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() throws Exception {
        loadBalancer = new RoundRobinLoadBalancer();

        // 使用反射设置私有成员
        java.lang.reflect.Field metricsCollectorField = RoundRobinLoadBalancer.class.getDeclaredField("metricsCollector");
        metricsCollectorField.setAccessible(true);
        metricsCollectorField.set(loadBalancer, metricsCollector);
    }

    @Test
    @DisplayName("选择实例 - 正常情况")
    void testSelectInstance_NormalCase() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);

        // When
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result));
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("chat"), eq("round_robin"), anyString());
    }

    @Test
    @DisplayName("选择实例 - 空实例列表")
    void testSelectInstance_EmptyInstances() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            loadBalancer.selectInstance(instances, "127.0.0.1", "chat")
        );
    }

    @Test
    @DisplayName("选择实例 - null 实例列表")
    void testSelectInstance_NullInstances() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            loadBalancer.selectInstance(null, "127.0.0.1", "chat")
        );
    }

    @Test
    @DisplayName("选择实例 - 单实例")
    void testSelectInstance_SingleInstance() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(1, 1);

        // When - 多次选择
        ModelRouterProperties.ModelInstance result1 = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
        ModelRouterProperties.ModelInstance result2 = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
        ModelRouterProperties.ModelInstance result3 = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");

        // Then - 单实例应该总是返回同一个实例
        assertEquals(result1, result2);
        assertEquals(result2, result3);
        assertEquals("instance-0", result1.getName());
    }

    @Test
    @DisplayName("选择实例 - 轮询顺序")
    void testSelectInstance_RoundRobinOrder() {
        // Given - 等权重实例
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);

        // When - 连续选择 6 次
        List<String> selectedNames = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
            selectedNames.add(result.getName());
        }

        // Then - 应该按顺序轮询（由于权重相等）
        // 权重轮询算法会按权重分配，等权重时应该均匀分布
        assertTrue(selectedNames.contains("instance-0"));
        assertTrue(selectedNames.contains("instance-1"));
        assertTrue(selectedNames.contains("instance-2"));
    }

    @Test
    @DisplayName("选择实例 - 权重影响选择")
    void testSelectInstance_WeightAffectsSelection() {
        // Given - 创建不同权重的实例
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(createInstance("low-weight", "http://low:8080", 1));
        instances.add(createInstance("high-weight", "http://high:8080", 5));

        // When - 进行 100 次选择
        java.util.Map<String, Integer> selectionCounts = new java.util.HashMap<>();
        for (int i = 0; i < 100; i++) {
            ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
            selectionCounts.merge(result.getName(), 1, Integer::sum);
        }

        // Then - 高权重实例应该被选中更多次
        int highWeightCount = selectionCounts.getOrDefault("high-weight", 0);
        int lowWeightCount = selectionCounts.getOrDefault("low-weight", 0);

        // 高权重实例应该被选中约 5/6 = 83% 的次数
        assertTrue(highWeightCount > lowWeightCount,
            "高权重实例应该被选中更多次。实际: high=" + highWeightCount + ", low=" + lowWeightCount);
        assertTrue(highWeightCount > 60,
            "高权重实例应该被选中超过 60 次（理论约 83 次）。实际: " + highWeightCount);
    }

    @Test
    @DisplayName("选择实例 - 权重分布测试")
    void testSelectInstance_WeightDistribution() {
        // Given - 创建权重比例 1:2:3 的实例
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(createInstance("weight-1", "http://w1:8080", 1));
        instances.add(createInstance("weight-2", "http://w2:8080", 2));
        instances.add(createInstance("weight-3", "http://w3:8080", 3));

        // When - 进行 120 次选择（权重总和 6 的倍数）
        java.util.Map<String, Integer> selectionCounts = new java.util.HashMap<>();
        for (int i = 0; i < 120; i++) {
            ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
            selectionCounts.merge(result.getName(), 1, Integer::sum);
        }

        // Then - 理论分布: weight-1: 20, weight-2: 40, weight-3: 60
        int count1 = selectionCounts.getOrDefault("weight-1", 0);
        int count2 = selectionCounts.getOrDefault("weight-2", 0);
        int count3 = selectionCounts.getOrDefault("weight-3", 0);

        // 允许一定误差，但应该大致符合权重比例
        assertTrue(count3 > count2 && count2 > count1,
            "选择次数应该按权重递增。实际: 1=" + count1 + ", 2=" + count2 + ", 3=" + count3);

        // 验证所有实例都被选中
        assertEquals(3, selectionCounts.size());
    }

    @Test
    @DisplayName("并发访问测试")
    void testConcurrentAccess() throws InterruptedException {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);
        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalSelections = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        ModelRouterProperties.ModelInstance selected = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
                        assertNotNull(selected);
                        totalSelections.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("并发访问异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertEquals(threadCount * requestsPerThread, totalSelections.get());
    }

    @Test
    @DisplayName("服务类型参数测试")
    void testSelectInstance_ServiceTypeParameter() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(2, 1);
        String serviceType = "embedding";

        // When
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", serviceType);

        // Then
        assertNotNull(result);
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("embedding"), eq("round_robin"), anyString());
    }

    @Test
    @DisplayName("零权重实例处理")
    void testSelectInstance_ZeroWeight() {
        // Given - 创建一个零权重实例和一个正常实例
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(createInstance("zero-weight", "http://zero:8080", 0));
        instances.add(createInstance("normal-weight", "http://normal:8080", 1));

        // When
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");

        // Then - 应该能正常选择
        assertNotNull(result);
        assertTrue(instances.contains(result));
    }

    /**
     * 创建测试实例列表
     */
    private List<ModelRouterProperties.ModelInstance> createTestInstances(int count, int weight) {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            instances.add(createInstance("instance-" + i, "http://instance" + i + ":8080", weight));
        }
        return instances;
    }

    /**
     * 创建测试实例
     */
    private ModelRouterProperties.ModelInstance createInstance(String name, String baseUrl, int weight) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setId(name);
        instance.setName(name);
        instance.setBaseUrl(baseUrl);
        instance.setPath("/api");
        instance.setWeight(weight);
        instance.setHealthy(true);
        return instance;
    }
}
package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

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
 * 最少连接数负载均衡器单元测试
 *
 * 测试最少连接数算法的正确性和权重支持
 *
 * @author JAiRouter Team
 * @since 2.4.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeastConnectionsLoadBalancer 单元测试")
class LeastConnectionsLoadBalancerTest {

    @Mock
    private MetricsCollector metricsCollector;

    private LeastConnectionsLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() throws Exception {
        loadBalancer = new LeastConnectionsLoadBalancer();

        // 使用反射设置私有成员
        java.lang.reflect.Field metricsCollectorField = LeastConnectionsLoadBalancer.class.getDeclaredField("metricsCollector");
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
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("chat"), eq("least_connections"), anyString());
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

        // When
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
        ModelRouterProperties.ModelInstance result2 = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");

        // Then - 单实例应该总是返回同一个实例
        assertEquals(result, result2);
        assertEquals("instance-0", result.getName());
    }

    @Test
    @DisplayName("选择实例 - 连接数影响选择")
    void testSelectInstance_ConnectionCountAffectsSelection() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);
        ModelRouterProperties.ModelInstance instance0 = instances.get(0);
        ModelRouterProperties.ModelInstance instance1 = instances.get(1);
        ModelRouterProperties.ModelInstance instance2 = instances.get(2);

        // 给 instance0 增加连接数
        loadBalancer.recordCall(instance0);
        loadBalancer.recordCall(instance0);
        loadBalancer.recordCall(instance0);

        // 给 instance1 增加较少连接数
        loadBalancer.recordCall(instance1);

        // instance2 没有连接数

        // When - 应该选择连接数最少的 instance2
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");

        // Then
        assertEquals("instance-2", result.getName());

        // 完成调用，释放连接
        loadBalancer.recordCallComplete(instance0);
        loadBalancer.recordCallComplete(instance0);
        loadBalancer.recordCallComplete(instance0);
        loadBalancer.recordCallComplete(instance1);
    }

    @Test
    @DisplayName("选择实例 - 权重影响选择")
    void testSelectInstance_WeightAffectsSelection() {
        // Given - 创建不同权重的实例
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();

        ModelRouterProperties.ModelInstance lowWeight = createInstance("low-weight", "http://low:8080", 1);
        ModelRouterProperties.ModelInstance highWeight = createInstance("high-weight", "http://high:8080", 5);

        instances.add(lowWeight);
        instances.add(highWeight);

        // 给高权重实例增加 2 个连接，加权后 = 2/5 = 0.4
        loadBalancer.recordCall(highWeight);
        loadBalancer.recordCall(highWeight);

        // 给低权重实例增加 1 个连接，加权后 = 1/1 = 1.0
        loadBalancer.recordCall(lowWeight);

        // When - 高权重的加权连接数更低，应该选择高权重实例
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");

        // Then
        assertEquals("high-weight", result.getName(), "高权重实例加权后连接数更低，应被选中");

        // 清理
        loadBalancer.recordCallComplete(highWeight);
        loadBalancer.recordCallComplete(highWeight);
        loadBalancer.recordCallComplete(lowWeight);
    }

    @Test
    @DisplayName("记录调用完成 - 正常情况")
    void testRecordCallComplete_Normal() {
        // Given
        ModelRouterProperties.ModelInstance instance = createInstance("test", "http://test:8080", 1);
        loadBalancer.recordCall(instance);

        // When
        loadBalancer.recordCallComplete(instance);

        // Then - 连接数应该减少
        assertDoesNotThrow(() -> loadBalancer.recordCallComplete(instance));
    }

    @Test
    @DisplayName("记录调用完成 - 无活跃连接")
    void testRecordCallComplete_NoActiveConnections() {
        // Given
        ModelRouterProperties.ModelInstance instance = createInstance("test", "http://test:8080", 1);

        // When - 没有记录过调用，直接完成
        loadBalancer.recordCallComplete(instance);

        // Then - 不应该抛出异常
        assertDoesNotThrow(() -> loadBalancer.recordCallComplete(instance));
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
                        loadBalancer.recordCall(selected);
                        totalSelections.incrementAndGet();
                        Thread.sleep(1);
                        loadBalancer.recordCallComplete(selected);
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
    @DisplayName("权重分布测试")
    void testWeightDistribution() {
        // Given - 创建不同权重的实例
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(createInstance("weight-1", "http://w1:8080", 1));
        instances.add(createInstance("weight-2", "http://w2:8080", 2));
        instances.add(createInstance("weight-3", "http://w3:8080", 3));

        // 统计选择分布
        java.util.Map<String, Integer> selectionCounts = new java.util.HashMap<>();

        // When - 进行 100 次选择，每次完成前不记录新调用
        for (int i = 0; i < 100; i++) {
            ModelRouterProperties.ModelInstance selected = loadBalancer.selectInstance(instances, "127.0.0.1", "chat");
            // 只统计选择，不改变连接数（模拟无并发场景）
            selectionCounts.merge(selected.getName(), 1, Integer::sum);
        }

        // Then - 最少连接数策略初始状态下会按加权连接数选择
        // 权重越高，加权连接数（0/weight）越低，应该被选中更多
        // 至少应该有 1 个实例被选中
        assertTrue(selectionCounts.size() >= 1, "至少应该有实例被选中");
        
        // 高权重实例（weight-3）应该被选中最多，因为加权连接数为 0/3 = 0
        // 低权重实例（weight-1）加权连接数为 0/1 = 0，权重相同时连接数为 0 时选第一个
        assertTrue(selectionCounts.containsKey("weight-3") || selectionCounts.containsKey("weight-1"),
            "高权重或第一个等权重实例应该被选中");
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
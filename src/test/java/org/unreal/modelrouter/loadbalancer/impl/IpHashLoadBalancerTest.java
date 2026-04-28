package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.common.model.ModelRouterProperties;
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
 * IP Hash 负载均衡器单元测试
 *
 * 测试 IP Hash 算法的正确性、一致性和权重支持
 *
 * @author JAiRouter Team
 * @since 2.4.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IpHashLoadBalancer 单元测试")
class IpHashLoadBalancerTest {

    @Mock
    private MetricsCollector metricsCollector;

    private IpHashLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() throws Exception {
        loadBalancer = new IpHashLoadBalancer("md5");

        // 使用反射设置私有成员
        java.lang.reflect.Field metricsCollectorField = IpHashLoadBalancer.class.getDeclaredField("metricsCollector");
        metricsCollectorField.setAccessible(true);
        metricsCollectorField.set(loadBalancer, metricsCollector);
    }

    @Test
    @DisplayName("选择实例 - 正常情况")
    void testSelectInstance_NormalCase() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);
        String clientIp = "192.168.1.100";

        // When
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result));
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("chat"), eq("ip_hash"), anyString());
    }

    @Test
    @DisplayName("选择实例 - 空实例列表")
    void testSelectInstance_EmptyInstances() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        String clientIp = "192.168.1.100";

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            loadBalancer.selectInstance(instances, clientIp, "chat")
        );
    }

    @Test
    @DisplayName("选择实例 - null 实例列表")
    void testSelectInstance_NullInstances() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            loadBalancer.selectInstance(null, "192.168.1.100", "chat")
        );
    }

    @Test
    @DisplayName("选择实例 - 相同 IP 选择相同实例")
    void testSelectInstance_SameIpSelectsSameInstance() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);
        String clientIp = "192.168.1.100";

        // When - 使用相同 IP 多次选择
        ModelRouterProperties.ModelInstance result1 = loadBalancer.selectInstance(instances, clientIp, "chat");
        ModelRouterProperties.ModelInstance result2 = loadBalancer.selectInstance(instances, clientIp, "chat");
        ModelRouterProperties.ModelInstance result3 = loadBalancer.selectInstance(instances, clientIp, "chat");

        // Then - 相同 IP 应该总是返回相同的实例
        assertEquals(result1.getName(), result2.getName());
        assertEquals(result2.getName(), result3.getName());
    }

    @Test
    @DisplayName("选择实例 - 不同 IP 可能选择不同实例")
    void testSelectInstance_DifferentIpMaySelectDifferentInstance() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(5, 1);

        // When - 使用不同 IP 选择
        java.util.Map<String, String> ipToInstance = new java.util.HashMap<>();
        for (int i = 1; i <= 20; i++) {
            String clientIp = "192.168.1." + i;
            ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, clientIp, "chat");
            ipToInstance.put(clientIp, result.getName());
        }

        // Then - 不同 IP 应该分布到不同实例（至少选择 2 个以上实例）
        long distinctInstances = ipToInstance.values().stream().distinct().count();
        assertTrue(distinctInstances >= 2, "不同 IP 应该分布到至少 2 个不同实例。实际分布到 " + distinctInstances + " 个实例");
    }

    @Test
    @DisplayName("选择实例 - null IP 回退到随机")
    void testSelectInstance_NullIpFallbackToRandom() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);

        // When - 使用 null IP
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, null, "chat");

        // Then - 应该能正常选择
        assertNotNull(result);
        assertTrue(instances.contains(result));
    }

    @Test
    @DisplayName("选择实例 - 空 IP 回退到随机")
    void testSelectInstance_EmptyIpFallbackToRandom() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);

        // When - 使用空字符串 IP
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, "", "chat");

        // Then - 应该能正常选择
        assertNotNull(result);
        assertTrue(instances.contains(result));
    }

    @Test
    @DisplayName("选择实例 - 单实例")
    void testSelectInstance_SingleInstance() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(1, 1);
        String clientIp = "192.168.1.100";

        // When
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, clientIp, "chat");

        // Then - 单实例应该总是返回同一个实例
        assertEquals("instance-0", result.getName());
    }

    @Test
    @DisplayName("选择实例 - 权重影响分布")
    void testSelectInstance_WeightAffectsDistribution() {
        // Given - 创建不同权重的实例
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(createInstance("low-weight", "http://low:8080", 1));
        instances.add(createInstance("high-weight", "http://high:8080", 5));

        // When - 使用不同 IP 进行 100 次选择
        java.util.Map<String, Integer> selectionCounts = new java.util.HashMap<>();
        for (int i = 1; i <= 100; i++) {
            String clientIp = "10.0.0." + i;
            ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, clientIp, "chat");
            selectionCounts.merge(result.getName(), 1, Integer::sum);
        }

        // Then - 高权重实例应该被选中更多次
        int highWeightCount = selectionCounts.getOrDefault("high-weight", 0);
        int lowWeightCount = selectionCounts.getOrDefault("low-weight", 0);

        // IP Hash 的权重分布不如轮询均匀，但高权重应该有更多选择
        assertTrue(highWeightCount >= lowWeightCount,
            "高权重实例应该被选中更多次。实际: high=" + highWeightCount + ", low=" + lowWeightCount);
    }

    @Test
    @DisplayName("选择实例 - Hash 算法 SHA256")
    void testSelectInstance_Sha256Algorithm() throws Exception {
        // Given
        IpHashLoadBalancer sha256Balancer = new IpHashLoadBalancer("sha256");
        java.lang.reflect.Field field = IpHashLoadBalancer.class.getDeclaredField("metricsCollector");
        field.setAccessible(true);
        field.set(sha256Balancer, metricsCollector);

        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);
        String clientIp = "192.168.1.100";

        // When
        ModelRouterProperties.ModelInstance result = sha256Balancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result));
    }

    @Test
    @DisplayName("选择实例 - Hash 算法 MurmurHash")
    void testSelectInstance_MurmurHashAlgorithm() throws Exception {
        // Given
        IpHashLoadBalancer murmurBalancer = new IpHashLoadBalancer("murmur");
        java.lang.reflect.Field field = IpHashLoadBalancer.class.getDeclaredField("metricsCollector");
        field.setAccessible(true);
        field.set(murmurBalancer, metricsCollector);

        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);
        String clientIp = "192.168.1.100";

        // When
        ModelRouterProperties.ModelInstance result = murmurBalancer.selectInstance(instances, clientIp, "chat");

        // Then
        assertNotNull(result);
        assertTrue(instances.contains(result));
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
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        String clientIp = "192.168." + threadId + "." + j;
                        ModelRouterProperties.ModelInstance selected = loadBalancer.selectInstance(instances, clientIp, "chat");
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
        String clientIp = "192.168.1.100";
        String serviceType = "embedding";

        // When
        ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, clientIp, serviceType);

        // Then
        assertNotNull(result);
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("embedding"), eq("ip_hash"), anyString());
    }

    @Test
    @DisplayName("IP 格式多样性测试")
    void testSelectInstance_IpFormatVariety() {
        // Given
        List<ModelRouterProperties.ModelInstance> instances = createTestInstances(3, 1);

        // When - 测试多种 IP 格式
        String[] ipFormats = {
            "192.168.1.1",
            "10.0.0.100",
            "172.16.0.50",
            "127.0.0.1",
            "255.255.255.255",
            "0.0.0.0",
            "::1",               // IPv6
            "2001:db8::1",       // IPv6
            "localhost"
        };

        for (String ip : ipFormats) {
            ModelRouterProperties.ModelInstance result = loadBalancer.selectInstance(instances, ip, "chat");

            // Then - 所有 IP 格式都应该能正常选择
            assertNotNull(result, "IP: " + ip + " 应该能正常选择实例");
            assertTrue(instances.contains(result));
        }
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
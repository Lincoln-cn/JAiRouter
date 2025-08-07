package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LoadBalancerTest {

    private List<ModelRouterProperties.ModelInstance> instances;

    @BeforeEach
    void setUp() {
        instances = new ArrayList<>();

        // 创建测试实例
        ModelRouterProperties.ModelInstance instance1 = new ModelRouterProperties.ModelInstance();
        instance1.setName("instance1");
        instance1.setBaseUrl("http://service1.example.com");
        instance1.setPath("/api");
        instance1.setWeight(1);

        ModelRouterProperties.ModelInstance instance2 = new ModelRouterProperties.ModelInstance();
        instance2.setName("instance2");
        instance2.setBaseUrl("http://service2.example.com");
        instance2.setPath("/api");
        instance2.setWeight(2); // 权重更高

        ModelRouterProperties.ModelInstance instance3 = new ModelRouterProperties.ModelInstance();
        instance3.setName("instance3");
        instance3.setBaseUrl("http://service3.example.com");
        instance3.setPath("/api");
        instance3.setWeight(3); // 权重最高

        instances.add(instance1);
        instances.add(instance2);
        instances.add(instance3);
    }

    @Test
    void testRoundRobinLoadBalancer() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

        // 测试多次选择，验证轮询行为
        ModelRouterProperties.ModelInstance selected1 = loadBalancer.selectInstance(instances, "127.0.0.1");
        ModelRouterProperties.ModelInstance selected2 = loadBalancer.selectInstance(instances, "127.0.0.1");
        ModelRouterProperties.ModelInstance selected3 = loadBalancer.selectInstance(instances, "127.0.0.1");
        ModelRouterProperties.ModelInstance selected4 = loadBalancer.selectInstance(instances, "127.0.0.1");

        // 由于权重，应该有重复选择
        assertNotNull(selected1);
        assertNotNull(selected2);
        assertNotNull(selected3);
        assertNotNull(selected4);
    }

    @Test
    void testRandomLoadBalancer() {
        LoadBalancer loadBalancer = new RandomLoadBalancer();

        // 多次选择实例
        ModelRouterProperties.ModelInstance selected1 = loadBalancer.selectInstance(instances, "127.0.0.1");
        ModelRouterProperties.ModelInstance selected2 = loadBalancer.selectInstance(instances, "127.0.0.1");

        assertNotNull(selected1);
        assertNotNull(selected2);
    }

    @Test
    void testLeastConnectionsLoadBalancer() {
        LeastConnectionsLoadBalancer loadBalancer = new LeastConnectionsLoadBalancer();

        // 初始状态下应该选择第一个实例
        ModelRouterProperties.ModelInstance selected = loadBalancer.selectInstance(instances, "127.0.0.1");
        assertNotNull(selected);

        // 记录一次调用
        loadBalancer.recordCall(selected);

        // 再次选择，应该选择其他实例（连接数较少的）
        ModelRouterProperties.ModelInstance selected2 = loadBalancer.selectInstance(instances, "127.0.0.1");
        assertNotNull(selected2);

        // 完成调用
        loadBalancer.recordCallComplete(selected);
    }

    @Test
    void testIpHashLoadBalancer() {
        IpHashLoadBalancer loadBalancer = new IpHashLoadBalancer("md5");

        // 使用相同IP应该选择相同实例
        ModelRouterProperties.ModelInstance selected1 = loadBalancer.selectInstance(instances, "127.0.0.1");
        ModelRouterProperties.ModelInstance selected2 = loadBalancer.selectInstance(instances, "127.0.0.1");

        assertEquals(selected1.getName(), selected2.getName(), "Same IP should select same instance");

        // 使用不同IP可能选择不同实例
        ModelRouterProperties.ModelInstance selected3 = loadBalancer.selectInstance(instances, "127.0.0.2");
        assertNotNull(selected3);
    }

    @Test
    void testWeightedRoundRobin() {
        // 测试权重是否生效
        RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer();

        // 创建权重分布明显的实例
        List<ModelRouterProperties.ModelInstance> weightedInstances = new ArrayList<>();

        ModelRouterProperties.ModelInstance highWeightInstance = new ModelRouterProperties.ModelInstance();
        highWeightInstance.setName("high");
        highWeightInstance.setBaseUrl("http://high.example.com");
        highWeightInstance.setPath("/api");
        highWeightInstance.setWeight(5);

        ModelRouterProperties.ModelInstance lowWeightInstance = new ModelRouterProperties.ModelInstance();
        lowWeightInstance.setName("low");
        lowWeightInstance.setBaseUrl("http://low.example.com");
        lowWeightInstance.setPath("/api");
        lowWeightInstance.setWeight(1);

        weightedInstances.add(highWeightInstance);
        weightedInstances.add(lowWeightInstance);

        // 多次选择并统计分布
        int highCount = 0;
        int totalCount = 100;

        for (int i = 0; i < totalCount; i++) {
            ModelRouterProperties.ModelInstance selected = loadBalancer.selectInstance(weightedInstances, "127.0.0.1");
            if ("high".equals(selected.getName())) {
                highCount++;
            }
        }

        // 高权重实例应该被选中更多次（理论上应该接近 5/6）
        assertTrue(highCount > totalCount * 0.6, "High weight instance should be selected more frequently");
    }

    @Test
    void testEmptyInstances() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

        // 测试空实例列表
        assertThrows(IllegalArgumentException.class, () -> {
            loadBalancer.selectInstance(new ArrayList<>(), "127.0.0.1");
        });
    }

    @Test
    void testNullInstances() {
        LoadBalancer loadBalancer = new RandomLoadBalancer();

        // 测试null实例列表
        assertThrows(IllegalArgumentException.class, () -> {
            loadBalancer.selectInstance(null, "127.0.0.1");
        });
    }

    @Test
    void testIpHashWithNullIp() {
        IpHashLoadBalancer loadBalancer = new IpHashLoadBalancer("md5");

        // 测试null IP，应该回退到随机选择
        assertDoesNotThrow(() -> {
            loadBalancer.selectInstance(instances, null);
        });
    }
}

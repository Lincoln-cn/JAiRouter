package org.unreal.moduler.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.circuitbreaker.DefaultCircuitBreaker;
import org.unreal.modelrouter.loadbalancer.impl.RandomLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.RoundRobinLoadBalancer;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.impl.TokenBucketRateLimiter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 基础设施组件指标集成测试
 * 验证负载均衡器、限流器、熔断器和健康检查的指标收集功能
 */
@ExtendWith(MockitoExtension.class)
public class InfrastructureMetricsIntegrationTest {

    @Mock
    private MetricsCollector metricsCollector;

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
        instance2.setWeight(2);

        instances.add(instance1);
        instances.add(instance2);
    }

    @Test
    void testLoadBalancerMetricsIntegration() {
        // 测试随机负载均衡器指标记录
        RandomLoadBalancer randomLoadBalancer = new RandomLoadBalancer();
        
        // 使用反射设置 metricsCollector
        setMetricsCollector(randomLoadBalancer, metricsCollector);

        // 执行负载均衡选择
        ModelRouterProperties.ModelInstance selected = randomLoadBalancer.selectInstance(instances, "127.0.0.1", "chat");

        // 验证指标记录被调用
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("chat"), eq("random"), anyString());
        assertNotNull(selected);
        assertTrue(instances.contains(selected));
    }

    @Test
    void testRoundRobinLoadBalancerMetricsIntegration() {
        // 测试轮询负载均衡器指标记录
        RoundRobinLoadBalancer roundRobinLoadBalancer = new RoundRobinLoadBalancer();
        
        // 使用反射设置 metricsCollector
        setMetricsCollector(roundRobinLoadBalancer, metricsCollector);

        // 执行负载均衡选择
        ModelRouterProperties.ModelInstance selected = roundRobinLoadBalancer.selectInstance(instances, "127.0.0.1", "embedding");

        // 验证指标记录被调用
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("embedding"), eq("round_robin"), anyString());
        assertNotNull(selected);
        assertTrue(instances.contains(selected));
    }

    @Test
    void testRateLimiterMetricsIntegration() {
        // 创建限流配置
        RateLimitConfig config = new RateLimitConfig();
        config.setRate(10);
        config.setCapacity(10);

        // 创建令牌桶限流器
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(config);
        
        // 使用反射设置 metricsCollector
        setMetricsCollector(rateLimiter, metricsCollector);

        // 创建限流上下文
        RateLimitContext context = new RateLimitContext(
            ModelServiceRegistry.ServiceType.chat, 
            "test-model", 
            "127.0.0.1", 
            1, 
            "instance1", 
            "http://service1.example.com"
        );

        // 执行限流检查
        boolean allowed = rateLimiter.tryAcquire(context);

        // 验证指标记录被调用
        verify(metricsCollector, times(1)).recordRateLimit(eq("chat"), eq("token_bucket"), eq(allowed));
        assertTrue(allowed); // 第一次请求应该被允许
    }

    @Test
    void testCircuitBreakerMetricsIntegration() {
        // 创建熔断器
        DefaultCircuitBreaker circuitBreaker = new DefaultCircuitBreaker("test-instance", 3, 60000, 2);
        
        // 使用反射设置 metricsCollector
        setMetricsCollector(circuitBreaker, metricsCollector);

        // 测试成功调用
        circuitBreaker.onSuccess();
        
        // 验证指标记录被调用
        verify(metricsCollector, atLeastOnce()).recordCircuitBreaker(eq("test-instance"), anyString(), anyString());

        // 测试失败调用
        circuitBreaker.onFailure();
        
        // 验证失败指标记录被调用
        verify(metricsCollector, atLeastOnce()).recordCircuitBreaker(eq("test-instance"), anyString(), eq("failure"));
    }

    @Test
    void testMultipleComponentsMetricsIntegration() {
        // 测试多个组件同时工作时的指标记录
        RandomLoadBalancer loadBalancer = new RandomLoadBalancer();
        setMetricsCollector(loadBalancer, metricsCollector);

        RateLimitConfig config = new RateLimitConfig();
        config.setRate(5);
        config.setCapacity(5);
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(config);
        setMetricsCollector(rateLimiter, metricsCollector);

        DefaultCircuitBreaker circuitBreaker = new DefaultCircuitBreaker("multi-test", 2, 30000, 1);
        setMetricsCollector(circuitBreaker, metricsCollector);

        // 执行多个操作
        loadBalancer.selectInstance(instances, "127.0.0.1", "rerank");
        
        RateLimitContext context = new RateLimitContext(
            ModelServiceRegistry.ServiceType.rerank, 
            "test-model", 
            "127.0.0.1", 
            1, 
            "instance1", 
            "http://service1.example.com"
        );
        rateLimiter.tryAcquire(context);
        
        circuitBreaker.onSuccess();

        // 验证所有组件的指标都被记录
        verify(metricsCollector, times(1)).recordLoadBalancer(eq("rerank"), eq("random"), anyString());
        verify(metricsCollector, times(1)).recordRateLimit(eq("rerank"), eq("token_bucket"), anyBoolean());
        verify(metricsCollector, atLeastOnce()).recordCircuitBreaker(eq("multi-test"), anyString(), anyString());
    }

    /**
     * 使用反射设置私有字段 metricsCollector
     */
    private void setMetricsCollector(Object target, MetricsCollector metricsCollector) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField("metricsCollector");
            field.setAccessible(true);
            field.set(target, metricsCollector);
        } catch (Exception e) {
            fail("Failed to set metricsCollector field: " + e.getMessage());
        }
    }
}
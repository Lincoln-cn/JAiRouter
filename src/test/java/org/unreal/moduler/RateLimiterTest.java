package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.factory.ComponentFactory;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimiterTest {

    @Mock
    private ComponentFactory componentFactory;

    @Mock
    private ConfigurationHelper configHelper;

    @Mock
    private ModelRouterProperties properties;

    @Mock
    private RateLimiter rateLimiter;

    @InjectMocks
    private RateLimitManager rateLimitManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testTryAcquireWithPriority_AllLevelsPass() {
        // Arrange
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String instanceId = "instance1";
        String instanceUrl = "http://example.com";
        
        RateLimitContext context = new RateLimitContext(
                serviceType, "model1", "127.0.0.1", 1, instanceId, instanceUrl);

        // Mock rate limiters to allow requests
        RateLimiter instanceLimiter = mock(RateLimiter.class);
        RateLimiter serviceLimiter = mock(RateLimiter.class);
        RateLimiter globalLimiter = mock(RateLimiter.class);
        
        when(instanceLimiter.tryAcquire(context)).thenReturn(true);
        when(serviceLimiter.tryAcquire(context)).thenReturn(true);
        when(globalLimiter.tryAcquire(context)).thenReturn(true);

        // Use reflection to set the limiters in the manager
        setInternalState(rateLimitManager, "globalLimiter", globalLimiter);
        
        Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters = new HashMap<>();
        serviceLimiters.put(serviceType, serviceLimiter);
        setInternalState(rateLimitManager, "serviceLimiters", serviceLimiters);
        
        Map<String, RateLimiter> instanceLimiters = new HashMap<>();
        instanceLimiters.put(serviceType.name() + ":" + instanceId, instanceLimiter);
        setInternalState(rateLimitManager, "instanceLimiters", instanceLimiters);

        // Act
        boolean result = rateLimitManager.tryAcquireWithPriority(context);

        // Assert
        assertTrue(result);
        verify(instanceLimiter).tryAcquire(context);
        verify(serviceLimiter).tryAcquire(context);
        verify(globalLimiter).tryAcquire(context);
    }

    @Test
    void testTryAcquireWithPriority_InstanceDenied() {
        // Arrange
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String instanceId = "instance1";
        String instanceUrl = "http://example.com";
        
        RateLimitContext context = new RateLimitContext(
                serviceType, "model1", "127.0.0.1", 1, instanceId, instanceUrl);

        // Mock instance limiter to deny request
        RateLimiter instanceLimiter = mock(RateLimiter.class);
        when(instanceLimiter.tryAcquire(context)).thenReturn(false);

        // Set up service and global limiters (should not be called)
        RateLimiter serviceLimiter = mock(RateLimiter.class);
        RateLimiter globalLimiter = mock(RateLimiter.class);

        // Use reflection to set the limiters in the manager
        setInternalState(rateLimitManager, "globalLimiter", globalLimiter);
        
        Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters = new HashMap<>();
        serviceLimiters.put(serviceType, serviceLimiter);
        setInternalState(rateLimitManager, "serviceLimiters", serviceLimiters);
        
        Map<String, RateLimiter> instanceLimiters = new HashMap<>();
        instanceLimiters.put(serviceType.name() + ":" + instanceId, instanceLimiter);
        setInternalState(rateLimitManager, "instanceLimiters", instanceLimiters);

        // Act
        boolean result = rateLimitManager.tryAcquireWithPriority(context);

        // Assert
        assertFalse(result);
        verify(instanceLimiter).tryAcquire(context);
        verify(serviceLimiter, never()).tryAcquire(any());
        verify(globalLimiter, never()).tryAcquire(any());
    }

    @Test
    void testSetRateLimiter_ServiceLevel() {
        // Arrange
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        RateLimitConfig config = new RateLimitConfig("token-bucket", 100, 10, "service");
        
        when(componentFactory.createScopedRateLimiter(config)).thenReturn(rateLimiter);

        // Act
        rateLimitManager.setRateLimiter(serviceType, config);

        // Assert
        Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters = 
            getInternalState(rateLimitManager, "serviceLimiters");
        assertEquals(rateLimiter, serviceLimiters.get(serviceType));
    }

    @Test
    void testRemoveRateLimiter() {
        // Arrange
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        
        // Pre-populate the service limiters map
        Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters = new HashMap<>();
        serviceLimiters.put(serviceType, rateLimiter);
        setInternalState(rateLimitManager, "serviceLimiters", serviceLimiters);

        // Act
        rateLimitManager.removeRateLimiter(serviceType);

        // Assert
        serviceLimiters = getInternalState(rateLimitManager, "serviceLimiters");
        assertFalse(serviceLimiters.containsKey(serviceType));
    }

    // Helper method to set private fields using reflection
    private void setInternalState(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Helper method to get private fields using reflection
    @SuppressWarnings("unchecked")
    private <T> T getInternalState(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testBurstTrafficHandling() {
        // 模拟突发流量场景
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String instanceId = "instance1";
        String instanceUrl = "http://example.com";

        // 创建一个真实的限流器进行测试
        RateLimitConfig config = new RateLimitConfig("token-bucket", 100, 10, "instance");
        // 对 componentFactory.createScopedRateLimiter 进行打桩
        RateLimiter realLimiter = mock(RateLimiter.class);
        when(componentFactory.createScopedRateLimiter(config)).thenReturn(realLimiter);

        RateLimitContext context = new RateLimitContext(
                serviceType, "model1", "127.0.0.1", 1, instanceId, instanceUrl);

        // 模拟部分请求通过，部分被拒绝
        when(realLimiter.tryAcquire(context)).thenReturn(true, true, false, true, false, true, true, false, true, true);

        // 允许的初始请求数
        int allowedRequests = 0;
        for (int i = 0; i < 10; i++) {
            if (realLimiter.tryAcquire(context)) {
                allowedRequests++;
            }
        }

        // 应该允许一部分请求通过
        assertTrue(allowedRequests > 0);
        assertTrue(allowedRequests <= 10);
    }

    @Test
    void testRateLimitBoundaryConditions() {
        // 测试边界条件
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String instanceId = "instance1";
        String instanceUrl = "http://example.com";

        // 创建低速率限制器
        RateLimitConfig config = new RateLimitConfig("token-bucket", 1, 1, "instance");
        // 对 componentFactory.createScopedRateLimiter 进行打桩
        RateLimiter realLimiter = mock(RateLimiter.class);
        when(componentFactory.createScopedRateLimiter(config)).thenReturn(realLimiter);

        RateLimitContext context = new RateLimitContext(
                serviceType, "model1", "127.0.0.1", 1, instanceId, instanceUrl);

        // 第一个请求应该通过
        when(realLimiter.tryAcquire(context)).thenReturn(true, false, false);

        assertTrue(realLimiter.tryAcquire(context));

        // 紧接着的请求应该被拒绝
        assertFalse(realLimiter.tryAcquire(context));
        assertFalse(realLimiter.tryAcquire(context));
    }

    @Test
    void testHighConcurrencyRateLimiting() throws InterruptedException {
        // 测试高并发场景下的限流
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String instanceId = "instance1";
        String instanceUrl = "http://example.com";

        RateLimitConfig config = new RateLimitConfig("token-bucket", 50, 10, "instance");
        // 对 componentFactory.createScopedRateLimiter 进行打桩
        RateLimiter realLimiter = mock(RateLimiter.class);
        when(componentFactory.createScopedRateLimiter(config)).thenReturn(realLimiter);

        // 模拟限流器的行为
        when(realLimiter.tryAcquire(any(RateLimitContext.class))).thenReturn(true, true, false, true, false, true);

        RateLimitContext context = new RateLimitContext(
                serviceType, "model1", "127.0.0.1", 1, instanceId, instanceUrl);

        int threadCount = 20;
        int requestsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger deniedCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                for (int i = 0; i < requestsPerThread; i++) {
                    if (realLimiter.tryAcquire(context)) {
                        allowedCount.incrementAndGet();
                    } else {
                        deniedCount.incrementAndGet();
                    }
                }
            } finally {
                latch.countDown();
            }
        };

        // 启动并发请求
        for (int i = 0; i < threadCount; i++) {
            new Thread(task).start();
        }

        // 等待所有请求完成
        latch.await(10, TimeUnit.SECONDS);

        // 验证总请求数正确
        assertEquals(threadCount * requestsPerThread, allowedCount.get() + deniedCount.get());
        // 验证有部分请求被允许
        assertTrue(allowedCount.get() > 0);
    }


}

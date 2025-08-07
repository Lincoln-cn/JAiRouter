package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ModelServiceRegistryTest {

    @Mock
    private ModelRouterProperties properties;

    @Mock
    private ServerChecker serverChecker;

    @Mock
    private RateLimitManager rateLimitManager;

    @Mock
    private LoadBalancerManager loadBalancerManager;

    @Mock
    private CircuitBreakerManager circuitBreakerManager;

    @Mock
    private LoadBalancer loadBalancer;

    private ModelServiceRegistry modelServiceRegistry;

    @BeforeEach
    void setUp() {
        // 默认配置
        when(properties.getAdapter()).thenReturn("normal");
        when(properties.getServices()).thenReturn(new HashMap<>());

        modelServiceRegistry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );
    }

    @Test
    void testConstructorWithNullServices() {
        when(properties.getServices()).thenReturn(null);

        assertDoesNotThrow(() -> {
            new ModelServiceRegistry(
                    properties,
                    serverChecker,
                    rateLimitManager,
                    loadBalancerManager,
                    circuitBreakerManager
            );
        });
    }

    @Test
    void testGetServiceAdapter() {
        // 设置服务配置
        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setAdapter("ollama");
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        assertEquals("ollama", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.chat));
        assertEquals("normal", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.embedding)); // 默认适配器
    }

    @Test
    void testGetAvailableServiceTypes() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        Set<ModelServiceRegistry.ServiceType> availableTypes = registry.getAvailableServiceTypes();
        assertTrue(availableTypes.contains(ModelServiceRegistry.ServiceType.chat));
        assertEquals(1, availableTypes.size());
    }

    @Test
    void testGetAvailableModels() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        Set<String> availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.chat);
        assertTrue(availableModels.contains("test-model"));
        assertEquals(1, availableModels.size());
    }

    @Test
    void testSelectInstanceWithNoInstances() {
        // 不再需要为这个测试设置stub，因为方法会在没有实例时直接抛出异常
        assertThrows(ResponseStatusException.class, () -> {
            modelServiceRegistry.selectInstance(
                    ModelServiceRegistry.ServiceType.chat,
                    "non-existent-model",
                    "127.0.0.1"
            );
        });
    }

    @Test
    void testSelectInstanceWithNoHealthyInstances() {
        // 创建实例
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        // 配置服务
        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serverChecker.isInstanceHealthy(anyString(), any())).thenReturn(false); // 所有实例都不健康

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        assertThrows(ResponseStatusException.class, () -> {
            registry.selectInstance(
                    ModelServiceRegistry.ServiceType.chat,
                    "test-model",
                    "127.0.0.1"
            );
        });
    }

    @Test
    void testSelectInstanceWithCircuitBreakerOpen() {
        // 创建实例
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        // 配置服务
        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serverChecker.isInstanceHealthy(anyString(), any())).thenReturn(true); // 实例健康
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(false); // 但熔断器打开

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        assertThrows(ResponseStatusException.class, () -> {
            registry.selectInstance(
                    ModelServiceRegistry.ServiceType.chat,
                    "test-model",
                    "127.0.0.1"
            );
        });
    }

    @Test
    void testSelectInstanceWithServiceRateLimitExceeded() {
        // 创建实例
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        // 配置服务
        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serverChecker.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(true);
        when(rateLimitManager.tryAcquire(any())).thenReturn(false); // 服务级限流超限

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        assertThrows(ResponseStatusException.class, () -> {
            registry.selectInstance(
                    ModelServiceRegistry.ServiceType.chat,
                    "test-model",
                    "127.0.0.1"
            );
        });
    }

    @Test
    void testSelectInstanceWithInstanceRateLimitExceeded() {
        // 创建实例
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        // 配置服务
        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serverChecker.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(true);
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(false); // 实例级限流超限
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(loadBalancer);
        when(loadBalancer.selectInstance(anyList(), anyString())).thenReturn(instance);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        assertThrows(ResponseStatusException.class, () -> {
            registry.selectInstance(
                    ModelServiceRegistry.ServiceType.chat,
                    "test-model",
                    "127.0.0.1"
            );
        });
    }

    @Test
    void testGetModelPathWithNonExistentModel() {
        assertThrows(ResponseStatusException.class, () -> {
            modelServiceRegistry.getModelPath(
                    ModelServiceRegistry.ServiceType.chat,
                    "non-existent-model"
            );
        });
    }

    @Test
    void testGetModelPath() {
        // 创建实例
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");
        instance.setPath("/v1/chat/completions");

        // 配置服务
        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        String path = registry.getModelPath(ModelServiceRegistry.ServiceType.chat, "test-model");
        assertEquals("/v1/chat/completions", path);
    }

    @Test
    void testRecordCallComplete() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        assertDoesNotThrow(() -> {
            modelServiceRegistry.recordCallComplete(ModelServiceRegistry.ServiceType.chat, instance);
        });

        // 验证调用了熔断器管理器的recordSuccess方法
        verify(circuitBreakerManager).recordSuccess(anyString(), anyString());
    }

    @Test
 void testRecordCallFailure() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        assertDoesNotThrow(() -> {
            modelServiceRegistry.recordCallFailure(ModelServiceRegistry.ServiceType.chat, instance);
        });

        // 验证调用了熔断器管理器的recordFailure方法
        verify(circuitBreakerManager).recordFailure(anyString(), anyString());
    }

    @Test
    void testGetAllInstances() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig chatConfig = new ModelRouterProperties.ServiceConfig();
        chatConfig.setInstances(Arrays.asList(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serverChecker,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager
        );

        Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> allInstances = registry.getAllInstances();
        assertTrue(allInstances.containsKey(ModelServiceRegistry.ServiceType.chat));
        assertEquals(1, allInstances.get(ModelServiceRegistry.ServiceType.chat).size());
        assertEquals("test-model", allInstances.get(ModelServiceRegistry.ServiceType.chat).get(0).getName());
    }

    @Test
    void testGetInstanceId() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-model");
        instance.setBaseUrl("http://test.com");

        assertEquals("test-model@http://test.com", instance.getInstanceId());
    }
}

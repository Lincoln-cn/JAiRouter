package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.config.ConfigMergeService;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.fallback.FallbackManager;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ModelServiceRegistryTest {

    @Mock
    private ModelRouterProperties properties;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private RateLimitManager rateLimitManager;

    @Mock
    private LoadBalancerManager loadBalancerManager;

    @Mock
    private CircuitBreakerManager circuitBreakerManager;
    
    @Mock
    private FallbackManager fallbackManager;
    
    @Mock
    private ConfigMergeService configMergeService;
    
    @Mock
    private ConfigurationHelper configurationHelper;

    @Mock
    private LoadBalancer loadBalancer;

    private ModelServiceRegistry modelServiceRegistry;

    @BeforeEach
    void setUp() {
        // 默认配置
        when(properties.getAdapter()).thenReturn("normal");
        when(properties.getServices()).thenReturn(new HashMap<>());
        when(configMergeService.getPersistedConfig()).thenReturn(new HashMap<>());
        when(configurationHelper.getServiceConfigKey(any())).thenCallRealMethod();
        when(configurationHelper.parseServiceType(anyString())).thenCallRealMethod();

        modelServiceRegistry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
        );
    }

    @Test
    void testConstructorWithNullServices() {
        when(properties.getServices()).thenReturn(null);

        assertDoesNotThrow(() -> {
            new ModelServiceRegistry(
                    properties,
                    serviceStateManager,
                    rateLimitManager,
                    loadBalancerManager,
                    circuitBreakerManager,
                    fallbackManager,
                    configMergeService,
                    configurationHelper
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
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");
        when(configurationHelper.parseServiceType(anyString())).thenReturn(ModelServiceRegistry.ServiceType.chat);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(false); // 所有实例都不健康
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true); // 实例健康
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(false); // 但熔断器打开
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(true);
        when(rateLimitManager.tryAcquire(any())).thenReturn(false); // 服务级限流超限
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(true);
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(false); // 实例级限流超限
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(loadBalancer);
        when(loadBalancer.selectInstance(anyList(), anyString())).thenReturn(instance);
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
        chatConfig.setInstances(List.of(instance));
        services.put("chat", chatConfig);

        when(properties.getServices()).thenReturn(services);
        when(configMergeService.mergeConfigurations()).thenReturn(createMergedConfig(services));
        when(configurationHelper.getServiceConfigKey(any())).thenReturn("chat");
        when(configurationHelper.parseServiceType(anyString())).thenReturn(ModelServiceRegistry.ServiceType.chat);

        ModelServiceRegistry registry = new ModelServiceRegistry(
                properties,
                serviceStateManager,
                rateLimitManager,
                loadBalancerManager,
                circuitBreakerManager,
                fallbackManager,
                configMergeService,
                configurationHelper
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
    
    /**
     * 创建模拟的合并配置
     */
    private Map<String, Object> createMergedConfig(Map<String, ModelRouterProperties.ServiceConfig> services) {
        Map<String, Object> mergedConfig = new HashMap<>();
        Map<String, Object> servicesMap = new HashMap<>();
        
        for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry : services.entrySet()) {
            Map<String, Object> serviceMap = new HashMap<>();
            serviceMap.put("instances", entry.getValue().getInstances());
            serviceMap.put("adapter", entry.getValue().getAdapter());
            servicesMap.put(entry.getKey(), serviceMap);
        }
        
        mergedConfig.put("services", servicesMap);
        return mergedConfig;
    }
}

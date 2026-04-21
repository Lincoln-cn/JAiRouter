package org.unreal.modelrouter.config.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.ConfigMergeService;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.config.dto.*;
import org.unreal.modelrouter.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.store.StoreManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ServiceConfigManager 单元测试 - v2.1.0 重构版
 * 使用强类型 DTO 进行测试
 * 
 * @author JAiRouter Team
 * @since v2.1.0
 */
@ExtendWith(MockitoExtension.class)
class ServiceConfigManagerTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ConfigurationHelper configurationHelper;

    @Mock
    private ConfigMergeService configMergeService;

    @InjectMocks
    private ServiceConfigManager serviceConfigManager;

    private Map<String, Object> testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new HashMap<>();
        Map<String, Object> services = new HashMap<>();
        testConfig.put("services", services);
        
        // 使用 thenAnswer 动态返回配置，模拟 saveConfig 后的配置变化
        // 需要返回配置的深拷贝，避免 clear() 影响后续读取
        lenient().when(storeManager.getConfig("model-router-config")).thenAnswer(invocation -> {
            Map<String, Object> copy = new HashMap<>();
            copy.put("services", new HashMap<>((Map<String, Object>) testConfig.get("services")));
            return copy;
        });
        lenient().doAnswer(invocation -> {
            Map<String, Object> newConfig = invocation.getArgument(1);
            testConfig.clear();
            testConfig.putAll(newConfig);
            return null;
        }).when(storeManager).saveConfig(eq("model-router-config"), anyMap());
    }

    @Test
    void testGetAvailableServiceTypes_EmptyConfig() {
        Set<String> result = serviceConfigManager.getAvailableServiceTypes();
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAvailableServiceTypes_WithServices() {
        Map<String, Object> services = new HashMap<>();
        services.put("chat", ServiceConfiguration.defaultConfig().toMap());
        services.put("embedding", ServiceConfiguration.defaultConfig().toMap());
        testConfig.put("services", services);
        
        Set<String> result = serviceConfigManager.getAvailableServiceTypes();
        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("chat"));
        assertTrue(result.contains("embedding"));
    }

    @Test
    void testGetServiceConfiguration_Existing() {
        ServiceConfiguration expectedConfig = new ServiceConfiguration(
            "openai",
            List.of(),
            LoadBalanceConfiguration.defaultConfig(),
            RateLimitConfiguration.defaultConfig(),
            CircuitBreakerConfiguration.defaultConfig(),
            FallbackConfiguration.defaultConfig()
        );
        
        Map<String, Object> services = new HashMap<>();
        services.put("chat", expectedConfig.toMap());
        testConfig.put("services", services);
        
        ServiceConfiguration result = serviceConfigManager.getServiceConfiguration("chat");
        
        assertNotNull(result);
        assertEquals("openai", result.adapter());
    }

    @Test
    void testGetServiceConfiguration_NotFound() {
        ServiceConfiguration result = serviceConfigManager.getServiceConfiguration("nonexistent");
        
        assertNull(result);
    }

    @Test
    void testGetAllServiceConfigurations() {
        Map<String, Object> services = new HashMap<>();
        services.put("chat", ServiceConfiguration.defaultConfig().toMap());
        services.put("embedding", ServiceConfiguration.defaultConfig().toMap());
        testConfig.put("services", services);
        
        Map<String, ServiceConfiguration> result = serviceConfigManager.getAllServiceConfigurations();
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testCreateService_Success() {
        ServiceConfiguration newConfig = new ServiceConfiguration(
            "anthropic",
            List.of(),
            LoadBalanceConfiguration.defaultConfig(),
            RateLimitConfiguration.defaultConfig(),
            CircuitBreakerConfiguration.defaultConfig(),
            FallbackConfiguration.defaultConfig()
        );
        
        serviceConfigManager.createService("chat", newConfig);
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证配置已保存
        ServiceConfiguration saved = serviceConfigManager.getServiceConfiguration("chat");
        assertNotNull(saved);
        assertEquals("anthropic", saved.adapter());
    }

    @Test
    void testCreateService_DuplicateThrows() {
        Map<String, Object> services = new HashMap<>();
        services.put("chat", ServiceConfiguration.defaultConfig().toMap());
        testConfig.put("services", services);
        
        ServiceConfiguration newConfig = ServiceConfiguration.defaultConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            serviceConfigManager.createService("chat", newConfig);
        });
    }

    @Test
    void testCreateService_InvalidServiceType() {
        ServiceConfiguration newConfig = ServiceConfiguration.defaultConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            serviceConfigManager.createService("", newConfig);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            serviceConfigManager.createService(null, newConfig);
        });
    }

    @Test
    void testCreateService_NullConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            serviceConfigManager.createService("chat", null);
        });
    }

    @Test
    void testUpdateServiceConfig_Success() {
        // 先创建服务
        ServiceConfiguration existingConfig = ServiceConfiguration.defaultConfig();
        Map<String, Object> services = new HashMap<>();
        services.put("chat", existingConfig.toMap());
        testConfig.put("services", services);
        
        // 更新配置
        ServiceConfiguration updateConfig = new ServiceConfiguration(
            "anthropic",
            List.of(),
            new LoadBalanceConfiguration("weighted", "murmur3"),
            RateLimitConfiguration.defaultConfig(),
            CircuitBreakerConfiguration.defaultConfig(),
            FallbackConfiguration.defaultConfig()
        );
        
        ServiceConfiguration result = serviceConfigManager.updateServiceConfig("chat", updateConfig);
        
        assertNotNull(result);
        assertEquals("anthropic", result.adapter());
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
    }

    @Test
    void testUpdateServiceConfig_PreservesInstances() {
        // 创建带实例的配置
        List<ModelInstanceConfiguration> instances = List.of(
            new ModelInstanceConfiguration("instance-1", "http://localhost:8001", null, null, 1, "active", null, null, null, Map.of(), null)
        );
        ServiceConfiguration existingConfig = new ServiceConfiguration(
            "openai",
            instances,
            LoadBalanceConfiguration.defaultConfig(),
            RateLimitConfiguration.defaultConfig(),
            CircuitBreakerConfiguration.defaultConfig(),
            FallbackConfiguration.defaultConfig()
        );
        
        Map<String, Object> services = new HashMap<>();
        services.put("chat", existingConfig.toMap());
        testConfig.put("services", services);
        
        // 更新配置（不传 instances）
        ServiceConfiguration updateConfig = new ServiceConfiguration(
            "anthropic",
            List.of(), // 空列表，但应该保留原有 instances
            LoadBalanceConfiguration.defaultConfig(),
            RateLimitConfiguration.defaultConfig(),
            CircuitBreakerConfiguration.defaultConfig(),
            FallbackConfiguration.defaultConfig()
        );
        
        serviceConfigManager.updateServiceConfig("chat", updateConfig);
        
        // 验证 instances 被保留
        ServiceConfiguration saved = serviceConfigManager.getServiceConfiguration("chat");
        assertNotNull(saved);
        assertEquals(1, saved.instances().size());
    }

    @Test
    void testUpdateServiceConfig_NotFound() {
        ServiceConfiguration updateConfig = ServiceConfiguration.defaultConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            serviceConfigManager.updateServiceConfig("nonexistent", updateConfig);
        });
    }

    @Test
    void testUpdateServiceConfigDto_Success() {
        // 先创建服务
        Map<String, Object> services = new HashMap<>();
        services.put("chat", ServiceConfiguration.defaultConfig().toMap());
        testConfig.put("services", services);
        
        // 创建 DTO 请求
        UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
            .adapter("anthropic")
            .build();
        
        serviceConfigManager.updateServiceConfigDto("chat", request);
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证配置已更新
        ServiceConfiguration saved = serviceConfigManager.getServiceConfiguration("chat");
        assertNotNull(saved);
        assertEquals("anthropic", saved.adapter());
    }

    @Test
    void testUpdateServiceConfigDto_NullRequest() {
        assertThrows(IllegalArgumentException.class, () -> {
            serviceConfigManager.updateServiceConfigDto("chat", null);
        });
    }

    @Test
    void testDeleteService_Success() {
        Map<String, Object> services = new HashMap<>();
        services.put("chat", ServiceConfiguration.defaultConfig().toMap());
        testConfig.put("services", services);
        
        serviceConfigManager.deleteService("chat");
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证配置已删除
        ServiceConfiguration saved = serviceConfigManager.getServiceConfiguration("chat");
        assertNull(saved);
    }

    @Test
    void testDeleteService_NotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            serviceConfigManager.deleteService("nonexistent");
        });
    }
}

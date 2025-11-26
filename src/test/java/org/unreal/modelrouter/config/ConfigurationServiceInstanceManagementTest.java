package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 配置服务实例管理测试
 * 测试实例添加操作的智能版本控制
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceInstanceManagementTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ConfigurationHelper configurationHelper;

    @Mock
    private ConfigMergeService configMergeService;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private SamplingConfigurationValidator samplingValidator;

    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new ConfigurationService(
                storeManager, configurationHelper, configMergeService,
                serviceStateManager, samplingValidator);
    }

    @Test
    void testAddServiceInstance_DuplicateInstance_ShouldThrowException() {
        // 准备测试数据
        String serviceType = "chat";
        ModelRouterProperties.ModelInstance instance = createTestInstance("test-model", "http://localhost:8000");

        // 模拟当前配置（已包含要添加的实例）
        Map<String, Object> instanceMap = createTestInstanceMap("test-model", "http://localhost:8000");
        Map<String, Object> configWithInstance = createTestConfigWithInstance(serviceType, instanceMap);

        when(storeManager.exists(anyString())).thenReturn(true);
        when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
        when(storeManager.getConfigByVersion(anyString(), eq(1))).thenReturn(configWithInstance);

        // 尝试添加已存在的实例 - 应该抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            configurationService.addServiceInstance(serviceType, instance);
        });

        assertTrue(exception.getMessage().contains("实例已存在"));
    }

    @Test
    void testAddServiceInstance_UsesIntelligentVersionControl() {
        // 准备测试数据
        String serviceType = "chat";
        ModelRouterProperties.ModelInstance instance = createTestInstance("model1", "http://localhost:8000");

        // 模拟当前配置（空的chat服务）
        Map<String, Object> currentConfig = createTestConfig();
        when(storeManager.exists(anyString())).thenReturn(true);
        when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
        when(storeManager.getConfigByVersion(anyString(), eq(1))).thenReturn(currentConfig);

        // 添加实例 - 应该使用智能版本控制
        assertDoesNotThrow(() -> {
            configurationService.addServiceInstance(serviceType, instance);
        });

        // 验证调用了智能版本控制相关的方法
        verify(storeManager, atLeastOnce()).getConfigVersions(anyString());
        verify(storeManager, atLeastOnce()).getConfigByVersion(anyString(), anyInt());
    }

    @Test
    void testUpdateServiceInstance_WithForceFlag() {
        // 准备测试数据
        String serviceType = "chat";
        String instanceId = "test-model@http://localhost:8000";
        ModelRouterProperties.ModelInstance instance = createTestInstance("test-model", "http://localhost:8000");

        // 模拟当前配置
        Map<String, Object> currentConfig = createTestConfig();
        when(storeManager.exists(anyString())).thenReturn(true);
        when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
        when(storeManager.getConfigByVersion(anyString(), eq(1))).thenReturn(currentConfig);

        // 测试更新实例
        assertDoesNotThrow(() -> {
            configurationService.updateServiceInstance(serviceType, instanceId, instance);
        });

        // 验证调用了相关方法
        verify(storeManager, atLeastOnce()).getConfigVersions(anyString());
        verify(storeManager, atLeastOnce()).getConfigByVersion(anyString(), anyInt());
    }

    // 辅助方法
    private ModelRouterProperties.ModelInstance createTestInstance(String name, String baseUrl) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl(baseUrl);
        instance.setWeight(1);
        instance.setStatus("active");
        return instance;
    }

    private Map<String, Object> createTestInstanceMap(String name, String baseUrl) {
        Map<String, Object> instanceMap = new HashMap<>();
        instanceMap.put("name", name);
        instanceMap.put("baseUrl", baseUrl);
        instanceMap.put("weight", 1);
        instanceMap.put("status", "active");
        instanceMap.put("instanceId", name + "@" + baseUrl);
        return instanceMap;
    }

    private Map<String, Object> createTestConfig() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> services = new HashMap<>();

        // 添加一个默认的chat服务
        Map<String, Object> chatService = new HashMap<>();
        chatService.put("instances", new ArrayList<>());
        services.put("chat", chatService);

        config.put("services", services);
        return config;
    }

    private Map<String, Object> createTestConfigWithInstance(String serviceType, Map<String, Object> instanceMap) {
        Map<String, Object> config = createTestConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) config.get("services");

        if (!services.containsKey(serviceType)) {
            Map<String, Object> serviceConfig = new HashMap<>();
            serviceConfig.put("instances", new ArrayList<>());
            services.put(serviceType, serviceConfig);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.get("instances");
        instances.add(instanceMap);

        return config;
    }
}
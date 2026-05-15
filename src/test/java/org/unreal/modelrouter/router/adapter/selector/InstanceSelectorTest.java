package org.unreal.modelrouter.router.adapter.selector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * InstanceSelector 单元测试 - v2.7.1
 * 
 * 测试目标：
 * - 验证使用 ServiceStateManager 缓存状态
 * - 验证健康检查非阻塞
 */
@DisplayName("InstanceSelector v2.7.1 测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstanceSelectorTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    private InstanceSelector instanceSelector;

    @BeforeEach
    void setUp() {
        instanceSelector = new InstanceSelector(registry, serviceStateManager);
    }

    @Test
    @DisplayName("测试 1: selectInstance 应调用 registry")
    void selectInstance_shouldCallRegistry() {
        ModelRouterProperties.ModelInstance mockInstance = createMockInstance("instance-1", "http://localhost:8080");
        when(registry.selectInstance(any(), anyString(), anyString())).thenReturn(mockInstance);

        ModelRouterProperties.ModelInstance result = instanceSelector.selectInstance(
                ModelServiceRegistry.ServiceType.chat, "model-1", "192.168.1.1");

        assertNotNull(result);
        verify(registry).selectInstance(ModelServiceRegistry.ServiceType.chat, "model-1", "192.168.1.1");
    }

    @Test
    @DisplayName("测试 2: isInstanceHealthy 应使用 ServiceStateManager 缓存")
    void isInstanceHealthy_shouldUseStateManagerCache() {
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1", "http://localhost:8080");
        when(serviceStateManager.isInstanceHealthyByKey(anyString())).thenReturn(true);

        boolean result = instanceSelector.isInstanceHealthy(ModelServiceRegistry.ServiceType.chat, instance);

        assertTrue(result);
        verify(serviceStateManager).isInstanceHealthyByKey("chat:instance-1");
    }

    @Test
    @DisplayName("测试 3: isInstanceHealthy 对 null 实例应返回 false")
    void isInstanceHealthy_nullInstance_shouldReturnFalse() {
        boolean result = instanceSelector.isInstanceHealthy(ModelServiceRegistry.ServiceType.chat, null);

        assertFalse(result);
        verify(serviceStateManager, never()).isInstanceHealthyByKey(anyString());
    }

    @Test
    @DisplayName("测试 4: isInstanceHealthy 应正确传递 instanceKey")
    void isInstanceHealthy_shouldPassCorrectInstanceKey() {
        ModelRouterProperties.ModelInstance instance = createMockInstance("test-instance-id", "http://localhost:9090");
        when(serviceStateManager.isInstanceHealthyByKey("chat:test-instance-id")).thenReturn(false);

        boolean result = instanceSelector.isInstanceHealthy(ModelServiceRegistry.ServiceType.chat, instance);

        assertFalse(result);
        verify(serviceStateManager).isInstanceHealthyByKey("chat:test-instance-id");
    }

    @Test
    @DisplayName("测试 5: getModelPath 应调用 registry")
    void getModelPath_shouldCallRegistry() {
        when(registry.getModelPath(any(), anyString())).thenReturn("/v1/chat/completions");

        String path = instanceSelector.getModelPath(ModelServiceRegistry.ServiceType.chat, "model-1");

        assertEquals("/v1/chat/completions", path);
        verify(registry).getModelPath(ModelServiceRegistry.ServiceType.chat, "model-1");
    }

    @Test
    @DisplayName("测试 6: recordInstanceCallComplete 应调用 registry")
    void recordInstanceCallComplete_shouldCallRegistry() {
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1", "http://localhost:8080");

        instanceSelector.recordInstanceCallComplete(ModelServiceRegistry.ServiceType.chat, instance);

        verify(registry).recordCallComplete(ModelServiceRegistry.ServiceType.chat, instance);
    }

    @Test
    @DisplayName("测试 7: recordInstanceCallFailure 应调用 registry")
    void recordInstanceCallFailure_shouldCallRegistry() {
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1", "http://localhost:8080");

        instanceSelector.recordInstanceCallFailure(ModelServiceRegistry.ServiceType.chat, instance);

        verify(registry).recordCallFailure(ModelServiceRegistry.ServiceType.chat, instance);
    }

    @Test
    @DisplayName("测试 8: 不同服务类型应生成不同的 instanceKey")
    void isInstanceHealthy_differentServiceTypes_shouldGenerateDifferentKeys() {
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1", "http://localhost:8080");
        
        when(serviceStateManager.isInstanceHealthyByKey("chat:instance-1")).thenReturn(true);
        when(serviceStateManager.isInstanceHealthyByKey("embedding:instance-1")).thenReturn(false);

        boolean chatResult = instanceSelector.isInstanceHealthy(ModelServiceRegistry.ServiceType.chat, instance);
        boolean embeddingResult = instanceSelector.isInstanceHealthy(ModelServiceRegistry.ServiceType.embedding, instance);

        assertTrue(chatResult);
        assertFalse(embeddingResult);
        verify(serviceStateManager).isInstanceHealthyByKey("chat:instance-1");
        verify(serviceStateManager).isInstanceHealthyByKey("embedding:instance-1");
    }

    /**
     * 创建 Mock 实例
     */
    private ModelRouterProperties.ModelInstance createMockInstance(String instanceId, String baseUrl) {
        ModelRouterProperties.ModelInstance instance = mock(ModelRouterProperties.ModelInstance.class);
        when(instance.getInstanceId()).thenReturn(instanceId);
        when(instance.getBaseUrl()).thenReturn(baseUrl);
        when(instance.getName()).thenReturn("test-model");
        when(instance.getWeight()).thenReturn(100);
        return instance;
    }
}

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
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InstanceManager 单元测试 - v2.1.0 重构版
 * 使用强类型 DTO 进行测试
 * 
 * @author JAiRouter Team
 * @since v2.1.0
 */
@ExtendWith(MockitoExtension.class)
class InstanceManagerTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ConfigurationHelper configurationHelper;

    @Mock
    private ConfigMergeService configMergeService;

    @InjectMocks
    private InstanceManager instanceManager;

    private Map<String, Object> testConfig;
    private List<ModelInstanceConfiguration> testInstances;

    @BeforeEach
    void setUp() {
        testConfig = new HashMap<>();
        Map<String, Object> services = new HashMap<>();
        Map<String, Object> chatService = new HashMap<>();
        testInstances = new ArrayList<>();
        
        ModelInstanceConfiguration instance1 = new ModelInstanceConfiguration(
            "instance-1",
            "http://localhost:8001",
            "/v1/chat/completions",
            null,
            1,
            "active",
            null, null, null,
            Map.of("Authorization", "Bearer token"),
            "instance-1"
        );
        testInstances.add(instance1);
        
        chatService.put("instances", ModelInstanceConfiguration.toMapList(testInstances));
        services.put("chat", chatService);
        testConfig.put("services", services);
        
        // 使用 thenAnswer 动态返回配置，模拟 saveConfig 后的配置变化
        // 需要返回配置的深拷贝，避免 clear() 影响后续读取
        lenient().when(storeManager.getConfig("model-router-config")).thenAnswer(invocation -> {
            Map<String, Object> copy = new HashMap<>();
            Map<String, Object> servicesCopy = new HashMap<>();
            Map<String, Object> servicesFromConfig = (Map<String, Object>) testConfig.get("services");
            if (servicesFromConfig != null) {
                for (Map.Entry<String, Object> entry : servicesFromConfig.entrySet()) {
                    Map<String, Object> service = (Map<String, Object>) entry.getValue();
                    Map<String, Object> serviceCopy = new HashMap<>(service);
                    servicesCopy.put(entry.getKey(), serviceCopy);
                }
            }
            copy.put("services", servicesCopy);
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
    void testGetServiceInstances_Success() {
        List<ModelInstanceConfiguration> result = instanceManager.getServiceInstances("chat");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("instance-1", result.get(0).name());
    }

    @Test
    void testGetServiceInstances_NotFound() {
        List<ModelInstanceConfiguration> result = instanceManager.getServiceInstances("nonexistent");
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetServiceInstancesAsMap_Success() {
        List<Map<String, Object>> result = instanceManager.getServiceInstancesAsMap("chat");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("instance-1", result.get(0).get("name"));
    }

    @Test
    void testGetServiceInstance_Success() {
        ModelInstanceConfiguration result = instanceManager.getServiceInstance("chat", "instance-1");
        
        assertNotNull(result);
        assertEquals("instance-1", result.name());
        assertEquals("http://localhost:8001", result.baseUrl());
    }

    @Test
    void testGetServiceInstance_NotFound() {
        ModelInstanceConfiguration result = instanceManager.getServiceInstance("chat", "nonexistent");
        
        assertNull(result);
    }

    @Test
    void testUpdateServiceInstance_WithDto_Success() {
        ModelInstanceConfiguration newInstance = new ModelInstanceConfiguration(
            "instance-1",
            "http://localhost:8001",
            "/v1/chat/completions",
            null,
            2,
            "inactive",
            null, null, null,
            Map.of("Authorization", "Bearer new-token"),
            "instance-1"
        );
        
        instanceManager.updateServiceInstance("chat", "instance-1", newInstance);
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证配置已更新
        ModelInstanceConfiguration saved = instanceManager.getServiceInstance("chat", "instance-1");
        assertNotNull(saved);
        assertEquals("inactive", saved.status());
    }

    @Test
    void testUpdateServiceInstance_WithModelInstance_Success() {
        ModelRouterProperties.ModelInstance modelInstance = new ModelRouterProperties.ModelInstance();
        modelInstance.setName("instance-1");
        modelInstance.setBaseUrl("http://localhost:8001");
        modelInstance.setStatus("inactive");
        modelInstance.setWeight(2);
        
        instanceManager.updateServiceInstance("chat", "instance-1", modelInstance);
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证配置已更新
        ModelInstanceConfiguration saved = instanceManager.getServiceInstance("chat", "instance-1");
        assertNotNull(saved);
        assertEquals("inactive", saved.status());
    }

    @Test
    void testUpdateServiceInstance_NotFound() {
        ModelInstanceConfiguration newInstance = ModelInstanceConfiguration.defaultConfig("nonexistent", "http://localhost");
        
        assertThrows(IllegalArgumentException.class, () -> {
            instanceManager.updateServiceInstance("chat", "nonexistent", newInstance);
        });
    }

    @Test
    void testUpdateServiceInstance_NullConfig() {
        assertThrows(IllegalArgumentException.class, () -> {
            instanceManager.updateServiceInstance("chat", "instance-1", (ModelInstanceConfiguration) null);
        });
    }

    @Test
    void testDeleteServiceInstance_Success() {
        instanceManager.deleteServiceInstance("chat", "instance-1");
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证实例已删除
        List<ModelInstanceConfiguration> instances = instanceManager.getServiceInstances("chat");
        assertTrue(instances.isEmpty());
    }

    @Test
    void testDeleteServiceInstance_NotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            instanceManager.deleteServiceInstance("chat", "nonexistent");
        });
    }

    @Test
    void testDeleteServiceInstance_InvalidServiceType() {
        assertThrows(IllegalArgumentException.class, () -> {
            instanceManager.deleteServiceInstance("", "instance-1");
        });
    }

    @Test
    void testBatchUpdateServiceInstances_DeleteOperation() {
        List<InstanceManager.InstanceOperation> operations = new ArrayList<>();
        operations.add(new InstanceManager.InstanceOperation(
            InstanceManager.OperationType.DELETE, "instance-1"));
        
        instanceManager.batchUpdateServiceInstances("chat", operations);
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证实例已删除
        List<ModelInstanceConfiguration> instances = instanceManager.getServiceInstances("chat");
        assertTrue(instances.isEmpty());
    }

    @Test
    void testBatchUpdateServiceInstances_AddOperation() {
        List<InstanceManager.InstanceOperation> operations = new ArrayList<>();
        ModelInstanceConfiguration newInstance = ModelInstanceConfiguration.defaultConfig("instance-2", "http://localhost:8002");
        operations.add(new InstanceManager.InstanceOperation(
            InstanceManager.OperationType.ADD, "instance-2", newInstance));
        
        instanceManager.batchUpdateServiceInstances("chat", operations);
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证实例已添加
        List<ModelInstanceConfiguration> instances = instanceManager.getServiceInstances("chat");
        assertEquals(2, instances.size());
    }

    @Test
    void testBatchUpdateServiceInstances_UpdateOperation() {
        List<InstanceManager.InstanceOperation> operations = new ArrayList<>();
        ModelInstanceConfiguration updateInstance = new ModelInstanceConfiguration(
            "instance-1",
            "http://localhost:8001",
            "/v1/chat/completions",
            null,
            2,
            "inactive",
            null, null, null,
            Map.of(),
            "instance-1"
        );
        operations.add(new InstanceManager.InstanceOperation(
            InstanceManager.OperationType.UPDATE, "instance-1", updateInstance));
        
        instanceManager.batchUpdateServiceInstances("chat", operations);
        
        verify(storeManager).saveConfig(eq("model-router-config"), anyMap());
        
        // 验证实例已更新
        ModelInstanceConfiguration saved = instanceManager.getServiceInstance("chat", "instance-1");
        assertNotNull(saved);
        assertEquals("inactive", saved.status());
    }

    @Test
    void testBatchUpdateServiceInstances_InvalidServiceType() {
        assertThrows(IllegalArgumentException.class, () -> {
            instanceManager.batchUpdateServiceInstances("", new ArrayList<>());
        });
    }

    @Test
    void testBatchUpdateServiceInstances_NullOperations() {
        // 传入 null 会抛出 NullPointerException 而不是 IllegalArgumentException
        assertThrows(NullPointerException.class, () -> {
            instanceManager.batchUpdateServiceInstances("chat", null);
        });
    }

    @Test
    void testBatchUpdateServiceInstances_EmptyOperations() {
        assertThrows(IllegalArgumentException.class, () -> {
            instanceManager.batchUpdateServiceInstances("chat", new ArrayList<>());
        });
    }

    @Test
    void testRequestDeduplication() throws InterruptedException {
        ModelInstanceConfiguration newInstance = new ModelInstanceConfiguration(
            "instance-1",
            "http://localhost:8001",
            null, null, 1, "active",
            null, null, null,
            Map.of(),
            "instance-1"
        );
        
        // 第一次调用
        instanceManager.updateServiceInstance("chat", "instance-1", newInstance);
        
        // 立即第二次调用（应该被去重）
        instanceManager.updateServiceInstance("chat", "instance-1", newInstance);
        
        // 验证只保存了一次
        verify(storeManager, times(1)).saveConfig(eq("model-router-config"), anyMap());
    }

    @Test
    void testGetServiceInstances_EmptyInstances() {
        Map<String, Object> chatService = new HashMap<>();
        chatService.put("instances", new ArrayList<>());
        Map<String, Object> services = new HashMap<>();
        services.put("chat", chatService);
        testConfig.put("services", services);
        
        List<ModelInstanceConfiguration> result = instanceManager.getServiceInstances("chat");
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetServiceInstances_NoInstancesKey() {
        Map<String, Object> chatService = new HashMap<>();
        Map<String, Object> services = new HashMap<>();
        services.put("chat", chatService);
        testConfig.put("services", services);
        
        List<ModelInstanceConfiguration> result = instanceManager.getServiceInstances("chat");
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMergeInstanceConfig_NestedMaps() {
        Map<String, Object> existing = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer old-token");
        existing.put("headers", headers);
        
        ModelInstanceConfiguration updates = new ModelInstanceConfiguration(
            "instance-1",
            "http://localhost:8001",
            null, null, 2, "inactive",
            null, null, null,
            Map.of("Authorization", "Bearer new-token", "Content-Type", "application/json"),
            "instance-1"
        );
        
        // 通过反射测试私有方法
        try {
            java.lang.reflect.Method method = InstanceManager.class.getDeclaredMethod(
                "mergeInstanceConfig", Map.class, Map.class);
            method.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) method.invoke(
                instanceManager, existing, updates.toMap());
            
            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultHeaders = (Map<String, Object>) result.get("headers");
            assertEquals("Bearer new-token", resultHeaders.get("Authorization"));
            assertEquals("application/json", resultHeaders.get("Content-Type"));
        } catch (Exception e) {
            fail("反射调用失败：" + e.getMessage());
        }
    }

    @Test
    void testInstanceOperation_Constructor() {
        InstanceManager.InstanceOperation op = new InstanceManager.InstanceOperation(
            InstanceManager.OperationType.UPDATE, "instance-1");
        
        assertEquals(InstanceManager.OperationType.UPDATE, op.getType());
        assertEquals("instance-1", op.getInstanceId());
        assertNull(op.getInstanceConfig());
    }

    @Test
    void testInstanceOperation_ConstructorWithConfig() {
        ModelInstanceConfiguration config = ModelInstanceConfiguration.defaultConfig("test", "http://test");
        InstanceManager.InstanceOperation op = new InstanceManager.InstanceOperation(
            InstanceManager.OperationType.ADD, "test", config);
        
        assertEquals(InstanceManager.OperationType.ADD, op.getType());
        assertEquals("test", op.getInstanceId());
        assertNotNull(op.getInstanceConfig());
    }

    @Test
    void testOperationType_Values() {
        InstanceManager.OperationType[] types = InstanceManager.OperationType.values();
        
        assertEquals(3, types.length);
        assertTrue(Arrays.asList(types).contains(InstanceManager.OperationType.UPDATE));
        assertTrue(Arrays.asList(types).contains(InstanceManager.OperationType.DELETE));
        assertTrue(Arrays.asList(types).contains(InstanceManager.OperationType.ADD));
    }
}

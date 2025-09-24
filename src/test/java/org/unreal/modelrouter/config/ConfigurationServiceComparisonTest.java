package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 配置服务智能比较机制测试
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceComparisonTest {

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
                storeManager,
                configurationHelper,
                configMergeService,
                serviceStateManager,
                samplingValidator
        );
    }

    @Test
    void testIsConfigurationChanged_IdenticalConfigs_ReturnsFalse() throws Exception {
        // 准备测试数据
        Map<String, Object> config1 = createTestConfig();
        Map<String, Object> config2 = createTestConfig();

        // 使用反射调用私有方法
        Method method = ConfigurationService.class.getDeclaredMethod("isConfigurationChanged", Map.class, Map.class);
        method.setAccessible(true);

        // 执行测试
        boolean result = (boolean) method.invoke(configurationService, config1, config2);

        // 验证结果
        assertFalse(result, "相同的配置应该返回false");
    }

    @Test
    void testIsConfigurationChanged_DifferentConfigs_ReturnsTrue() throws Exception {
        // 准备测试数据
        Map<String, Object> config1 = createTestConfig();
        Map<String, Object> config2 = createTestConfig();

        // 修改config2中的一个实例
        @SuppressWarnings("unchecked")
        Map<String, Object> services2 = (Map<String, Object>) config2.get("services");
        @SuppressWarnings("unchecked")
        Map<String, Object> openaiService2 = (Map<String, Object>) services2.get("openai");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instances2 = (List<Map<String, Object>>) openaiService2.get("instances");
        instances2.get(0).put("weight", 2); // 修改权重

        // 使用反射调用私有方法
        Method method = ConfigurationService.class.getDeclaredMethod("isConfigurationChanged", Map.class, Map.class);
        method.setAccessible(true);

        // 执行测试
        boolean result = (boolean) method.invoke(configurationService, config1, config2);

        // 验证结果
        assertTrue(result, "不同的配置应该返回true");
    }

    @Test
    void testIsConfigurationChanged_OnlyMetadataDifferent_ReturnsFalse() throws Exception {
        // 准备测试数据
        Map<String, Object> config1 = createTestConfig();
        Map<String, Object> config2 = createTestConfig();

        // 只修改元数据字段
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("timestamp", System.currentTimeMillis());
        metadata1.put("operation", "test1");
        config1.put("_metadata", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("timestamp", System.currentTimeMillis() + 1000);
        metadata2.put("operation", "test2");
        config2.put("_metadata", metadata2);

        // 使用反射调用私有方法
        Method method = ConfigurationService.class.getDeclaredMethod("isConfigurationChanged", Map.class, Map.class);
        method.setAccessible(true);

        // 执行测试
        boolean result = (boolean) method.invoke(configurationService, config1, config2);

        // 验证结果
        assertFalse(result, "只有元数据不同的配置应该返回false");
    }

    @Test
    void testNormalizeConfigForComparison_RemovesMetadataFields() throws Exception {
        // 准备测试数据
        Map<String, Object> config = createTestConfig();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        config.put("_metadata", metadata);
        config.put("timestamp", System.currentTimeMillis());
        config.put("lastModified", System.currentTimeMillis());

        // 使用反射调用私有方法
        Method method = ConfigurationService.class.getDeclaredMethod("normalizeConfigForComparison", Map.class);
        method.setAccessible(true);

        // 执行测试
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(configurationService, config);

        // 验证结果
        assertFalse(result.containsKey("_metadata"), "应该移除_metadata字段");
        assertFalse(result.containsKey("timestamp"), "应该移除timestamp字段");
        assertFalse(result.containsKey("lastModified"), "应该移除lastModified字段");
        assertTrue(result.containsKey("services"), "应该保留services字段");
    }

    @Test
    void testDeepEquals_IdenticalMaps_ReturnsTrue() throws Exception {
        // 准备测试数据
        Map<String, Object> map1 = new HashMap<>();
        map1.put("key1", "value1");
        Map<String, Object> nestedMap1 = new HashMap<>();
        nestedMap1.put("nested", "value");
        map1.put("key2", nestedMap1);
        List<String> list1 = new ArrayList<>();
        list1.add("item1");
        list1.add("item2");
        map1.put("key3", list1);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("key1", "value1");
        Map<String, Object> nestedMap2 = new HashMap<>();
        nestedMap2.put("nested", "value");
        map2.put("key2", nestedMap2);
        List<String> list2 = new ArrayList<>();
        list2.add("item1");
        list2.add("item2");
        map2.put("key3", list2);

        // 使用反射调用私有方法
        Method method = ConfigurationService.class.getDeclaredMethod("deepEquals", Object.class, Object.class);
        method.setAccessible(true);

        // 执行测试
        boolean result = (boolean) method.invoke(configurationService, map1, map2);

        // 验证结果
        assertTrue(result, "相同的Map应该返回true");
    }

    @Test
    void testDeepEquals_DifferentMaps_ReturnsFalse() throws Exception {
        // 准备测试数据
        Map<String, Object> map1 = new HashMap<>();
        map1.put("key1", "value1");
        Map<String, Object> map2 = new HashMap<>();
        map2.put("key1", "value2");

        // 使用反射调用私有方法
        Method method = ConfigurationService.class.getDeclaredMethod("deepEquals", Object.class, Object.class);
        method.setAccessible(true);
        // 执行测试
        boolean result = (boolean) method.invoke(configurationService, map1, map2);
        // 验证结果
        assertFalse(result, "不同的Map应该返回false");
    }

    @Test
    void testSaveAsNewVersionIfChanged_NoChanges_ReturnsCurrentVersion() {
        // 准备测试数据
        Map<String, Object> config = createTestConfig();
        // Mock当前配置
        when(storeManager.exists("model-router-config")).thenReturn(true);
        when(storeManager.getConfig("model-router-config")).thenReturn(config);
        when(storeManager.getConfigVersions("model-router-config")).thenReturn(new ArrayList<>());
        // 执行测试
        int result = configurationService.saveAsNewVersionIfChanged(config, "测试描述", "testUser");

        // 验证结果
        assertEquals(0, result, "没有变化时应该返回当前版本号");
        verify(storeManager, never()).saveConfigVersion(anyString(), any(), anyInt());
    }

    @Test
    void testSaveAsNewVersionIfChanged_HasChanges_CreatesNewVersion() {
        // 准备测试数据
        Map<String, Object> currentConfig = createTestConfig();
        Map<String, Object> newConfig = createTestConfig();

        // 修改新配置
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) newConfig.get("services");
        @SuppressWarnings("unchecked")
        Map<String, Object> openaiService = (Map<String, Object>) services.get("openai");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instances = (List<Map<String, Object>>) openaiService.get("instances");
        instances.get(0).put("weight", 2); // 修改权重

        // Mock当前配置
        when(storeManager.exists("model-router-config")).thenReturn(true);
        when(storeManager.getConfig("model-router-config")).thenReturn(currentConfig);
        when(storeManager.getConfigVersions("model-router-config")).thenReturn(new ArrayList<>());
        // 执行测试
        int result = configurationService.saveAsNewVersionIfChanged(newConfig, "测试描述", "testUser");

        // 验证结果
        assertEquals(1, result, "有变化时应该创建新版本");
        verify(storeManager).saveConfigVersion(eq("model-router-config"), eq(newConfig), eq(1));
    }

    /**
     * 创建测试用的配置数据
     */
    private Map<String, Object> createTestConfig() {
        Map<String, Object> config = new HashMap<>();

        // 创建服务配置
        Map<String, Object> services = new HashMap<>();

        // 创建OpenAI服务配置
        Map<String, Object> openaiService = new HashMap<>();
        List<Map<String, Object>> openaiInstances = new ArrayList<>();

        Map<String, Object> instance1 = new HashMap<>();
        instance1.put("name", "gpt-4");
        instance1.put("baseUrl", "https://api.openai.com");
        instance1.put("instanceId", "gpt-4@https://api.openai.com");
        instance1.put("weight", 1);
        instance1.put("status", "active");
        instance1.put("health", true);
        openaiInstances.add(instance1);

        openaiService.put("instances", openaiInstances);
        services.put("openai", openaiService);

        config.put("services", services);

        return config;
    }
}
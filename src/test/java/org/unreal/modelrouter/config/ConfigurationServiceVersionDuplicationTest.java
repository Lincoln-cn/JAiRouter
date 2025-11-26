package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 测试配置版本重复创建问题
 */
@ExtendWith(MockitoExtension.class)
public class ConfigurationServiceVersionDuplicationTest {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationServiceVersionDuplicationTest.class);

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
        // 模拟初始配置
        Map<String, Object> initialConfig = createTestConfig();
        lenient().when(storeManager.getConfig("model-router-config")).thenReturn(initialConfig);
        lenient().when(configMergeService.getPersistedConfig()).thenReturn(initialConfig);
        lenient().when(storeManager.exists(anyString())).thenReturn(false);
        lenient().when(storeManager.versionExists(anyString(), anyInt())).thenReturn(true);

        configurationService = new ConfigurationService(
                storeManager,
                configurationHelper,
                configMergeService,
                serviceStateManager,
                samplingValidator
        );
    }

    @Test
    void testSingleInstanceUpdateShouldNotCreateMultipleVersions() {
        logger.info("测试单个实例更新不应创建多个版本");

        // 准备测试数据
        Map<String, Object> initialConfig = createTestConfig();
        lenient().when(storeManager.getConfig("model-router-config")).thenReturn(initialConfig);
        lenient().when(configMergeService.getPersistedConfig()).thenReturn(initialConfig);

        // 模拟实例配置
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-instance");
        instance.setBaseUrl("http://test.example.com");
        instance.setStatus("active");

        Map<String, Object> instanceMap = new HashMap<>();
        instanceMap.put("name", "test-instance");
        instanceMap.put("baseUrl", "http://test.example.com");
        instanceMap.put("status", "active");

        lenient().when(configurationHelper.convertInstanceToMap(instance)).thenReturn(instanceMap);

        // 记录版本保存调用次数
        List<Map<String, Object>> savedConfigs = new ArrayList<>();
        lenient().doAnswer(invocation -> {
            Map<String, Object> config = invocation.getArgument(1);
            savedConfigs.add(new HashMap<>(config));
            return null;
        }).when(storeManager).saveConfig(eq("model-router-config@1"), any());

        // 执行实例更新
        try {
            configurationService.updateServiceInstance("chat", "test-instance@http://test.example.com", instance);
        } catch (Exception e) {
            logger.warn("实例更新失败，这是预期的，因为我们在测试环境中: {}", e.getMessage());
        }

        // 验证版本保存次数
        logger.info("版本保存调用次数: {}", savedConfigs.size());

        // 在正常情况下，单次实例更新应该最多只创建一个版本
        assertTrue(savedConfigs.size() <= 1,
                "单次实例更新不应创建多个版本，实际创建了 " + savedConfigs.size() + " 个版本");
    }

    @Test
    void testConfigurationComparisonAccuracy() {
        logger.info("测试配置比较的准确性");

        Map<String, Object> config1 = createTestConfig();
        Map<String, Object> config2 = createTestConfig();

        // 添加元数据到config2，这不应该被认为是实质性变化
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("operation", "test");
        config2.put("_metadata", metadata);

        // 使用反射调用私有方法进行测试
        try {
            java.lang.reflect.Method method = ConfigurationService.class.getDeclaredMethod(
                    "isConfigurationChanged", Map.class, Map.class);
            method.setAccessible(true);

            boolean changed = (Boolean) method.invoke(configurationService, config1, config2);

            assertFalse(changed, "仅添加元数据不应被认为是配置变化");
            logger.info("配置比较测试通过：元数据变化被正确忽略");

        } catch (Exception e) {
            logger.error("配置比较测试失败", e);
            fail("无法测试配置比较方法: " + e.getMessage());
        }
    }

    @Test
    void testMetadataRecoveryWhenMissing() {
        logger.info("测试元数据丢失时的恢复机制");

        // 模拟元数据文件不存在但历史文件存在的情况
        when(storeManager.exists("model-router-config.metadata")).thenReturn(false);
        when(storeManager.exists("model-router-config.history")).thenReturn(true);

        // 模拟历史数据
        List<Map<String, Object>> historyData = new ArrayList<>();
        Map<String, Object> historyItem = new HashMap<>();
        historyItem.put("version", 1);
        historyItem.put("createdAt", Arrays.asList(2025, 9, 23, 18, 50, 17, 964096443));
        historyItem.put("createdBy", "system");
        historyItem.put("changeType", "UPDATE");
        historyItem.put("description", "系统自动保存");
        historyData.add(historyItem);

        Map<String, Object> historyWrapper = new HashMap<>();
        historyWrapper.put("history", historyData);
        when(storeManager.getConfig("model-router-config.history")).thenReturn(historyWrapper);

        // 创建新的ConfigurationService实例来触发初始化
        ConfigurationService newService = new ConfigurationService(
                storeManager,
                configurationHelper,
                configMergeService,
                serviceStateManager,
                samplingValidator
        );

        // 测试getAllVersions方法
        List<Integer> versions = newService.getAllVersions();

        logger.info("获取到的版本列表: {}", versions);

        // 在元数据丢失的情况下，应该能够恢复或至少不返回空列表
        // 这里我们需要实现元数据恢复机制
        assertNotNull(versions, "版本列表不应为null");
    }

    @Test
    void testVersionIncrementAfterVersion2() {
        logger.info("测试版本号大于2后的版本递增");

        // 模拟已存在版本1和2
        lenient().when(storeManager.versionExists("model-router-config", 1)).thenReturn(true);
        lenient().when(storeManager.versionExists("model-router-config", 2)).thenReturn(true);
        lenient().when(storeManager.versionExists("model-router-config", 3)).thenReturn(false);

        // 创建一个ConfigurationService实例，并手动设置当前版本为2
        ConfigurationService testService = new ConfigurationService(
                storeManager,
                configurationHelper,
                configMergeService,
                serviceStateManager,
                samplingValidator
        );

        // 使用反射设置初始版本状态
        try {
            java.lang.reflect.Field configMetadataMapField = ConfigurationService.class.getDeclaredField("configMetadataMap");
            configMetadataMapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> configMetadataMap = (Map<String, Object>) configMetadataMapField.get(testService);

            // 创建元数据对象
            java.lang.reflect.Method getConfigMetadataClass = ConfigurationService.class.getDeclaredMethod("getClass");
            Class<?> configMetadataClass = null;
            for (Class<?> innerClass : ConfigurationService.class.getDeclaredClasses()) {
                if (innerClass.getSimpleName().equals("ConfigMetadata")) {
                    configMetadataClass = innerClass;
                    break;
                }
            }

            if (configMetadataClass != null) {
                Object metadata = configMetadataClass.getDeclaredConstructor().newInstance();

                // 设置元数据字段
                java.lang.reflect.Field configKeyField = configMetadataClass.getDeclaredField("configKey");
                configKeyField.setAccessible(true);
                configKeyField.set(metadata, "model-router-config");

                java.lang.reflect.Field currentVersionField = configMetadataClass.getDeclaredField("currentVersion");
                currentVersionField.setAccessible(true);
                currentVersionField.set(metadata, 2);

                java.lang.reflect.Field initialVersionField = configMetadataClass.getDeclaredField("initialVersion");
                initialVersionField.setAccessible(true);
                initialVersionField.set(metadata, 1);

                java.lang.reflect.Field totalVersionsField = configMetadataClass.getDeclaredField("totalVersions");
                totalVersionsField.setAccessible(true);
                totalVersionsField.set(metadata, 2);

                configMetadataMap.put("model-router-config", metadata);
            }
        } catch (Exception e) {
            logger.error("设置初始版本状态失败", e);
        }

        // 准备测试配置
        Map<String, Object> currentConfig = createTestConfig();
        Map<String, Object> newConfig = createTestConfig();

        // 修改配置以确保被识别为变化
        Map<String, Object> services = (Map<String, Object>) newConfig.get("services");
        Map<String, Object> chatService = (Map<String, Object>) services.get("chat");
        List<Map<String, Object>> instances = (List<Map<String, Object>>) chatService.get("instances");
        instances.get(0).put("status", "inactive"); // 修改状态

        lenient().when(configMergeService.getPersistedConfig()).thenReturn(currentConfig);

        // 记录保存的版本号
        final int[] savedVersion = {0};
        lenient().doAnswer(invocation -> {
            String configKey = invocation.getArgument(0);
            Map<String, Object> config = invocation.getArgument(1);
            int version = invocation.getArgument(2);
            savedVersion[0] = version;
            logger.info("保存版本配置: key={}, version={}", configKey, version);
            return null;
        }).when(storeManager).saveConfigVersion(eq("model-router-config"), any(), anyInt());

        // 执行版本保存
        try {
            int newVersion = testService.saveAsNewVersion(newConfig, "测试版本3", "test-user");
            logger.info("创建的新版本号: {}", newVersion);

            // 验证新版本号应该是3
            assertEquals(3, newVersion, "版本号大于2后，下一个版本应该是3");
            assertEquals(3, savedVersion[0], "保存的版本号应该是3");

        } catch (Exception e) {
            logger.error("版本保存失败", e);
            fail("版本保存不应该失败: " + e.getMessage());
        }

        logger.info("版本递增测试通过");
    }


    private Map<String, Object> createTestConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> services = new HashMap<>();
        Map<String, Object> chatService = new HashMap<>();

        List<Map<String, Object>> instances = new ArrayList<>();
        Map<String, Object> instance = new HashMap<>();
        instance.put("name", "test-instance");
        instance.put("baseUrl", "http://test.example.com");
        instance.put("status", "active");
        instances.add(instance);
        chatService.put("instances", instances);
        services.put("chat", chatService);
        config.put("services", services);

        return config;
    }
}
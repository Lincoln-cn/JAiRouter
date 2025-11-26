package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试时间戳字段不影响配置比较
 */
@ExtendWith(MockitoExtension.class)
public class ConfigurationServiceTimestampTest {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationServiceTimestampTest.class);

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
    void testTimestampFieldsDoNotAffectComparison() {
        logger.info("测试时间戳字段不影响配置比较");

        // 创建两个相同的配置，但包含不同的时间戳
        Map<String, Object> config1 = createTestConfigWithTimestamps(1000L);
        Map<String, Object> config2 = createTestConfigWithTimestamps(2000L);

        // 使用反射调用私有方法进行测试
        try {
            java.lang.reflect.Method method = ConfigurationService.class.getDeclaredMethod(
                    "isConfigurationChanged", Map.class, Map.class);
            method.setAccessible(true);

            boolean changed = (Boolean) method.invoke(configurationService, config1, config2);

            assertFalse(changed, "相同配置但不同时间戳不应被认为是配置变化");
            logger.info("时间戳比较测试通过：时间戳差异被正确忽略");

        } catch (Exception e) {
            logger.error("时间戳比较测试失败", e);
            fail("无法测试配置比较方法: " + e.getMessage());
        }
    }

    @Test
    void testMetadataFieldsDoNotAffectComparison() {
        logger.info("测试元数据字段不影响配置比较");

        Map<String, Object> config1 = createTestConfig();
        Map<String, Object> config2 = createTestConfig();

        // 添加不同的元数据
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("operation", "updateInstance");
        metadata1.put("timestamp", 1000L);
        metadata1.put("operationDetail", "更新实例1");
        config1.put("_metadata", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("operation", "updateInstance");
        metadata2.put("timestamp", 2000L);
        metadata2.put("operationDetail", "更新实例2");
        config2.put("_metadata", metadata2);

        try {
            java.lang.reflect.Method method = ConfigurationService.class.getDeclaredMethod(
                    "isConfigurationChanged", Map.class, Map.class);
            method.setAccessible(true);

            boolean changed = (Boolean) method.invoke(configurationService, config1, config2);

            assertFalse(changed, "相同配置但不同元数据不应被认为是配置变化");
            logger.info("元数据比较测试通过：元数据差异被正确忽略");

        } catch (Exception e) {
            logger.error("元数据比较测试失败", e);
            fail("无法测试配置比较方法: " + e.getMessage());
        }
    }

    @Test
    void testRealConfigurationChangeDetected() {
        logger.info("测试真实配置变化能被正确检测");

        Map<String, Object> config1 = createTestConfig();
        Map<String, Object> config2 = createTestConfig();

        // 修改实际配置内容
        Map<String, Object> services2 = (Map<String, Object>) config2.get("services");
        Map<String, Object> chatService2 = (Map<String, Object>) services2.get("chat");
        List<Map<String, Object>> instances2 = (List<Map<String, Object>>) chatService2.get("instances");
        instances2.get(0).put("status", "inactive"); // 修改状态

        try {
            java.lang.reflect.Method method = ConfigurationService.class.getDeclaredMethod(
                    "isConfigurationChanged", Map.class, Map.class);
            method.setAccessible(true);

            boolean changed = (Boolean) method.invoke(configurationService, config1, config2);

            assertTrue(changed, "真实配置变化应该被检测到");
            logger.info("真实配置变化检测测试通过");

        } catch (Exception e) {
            logger.error("真实配置变化检测测试失败", e);
            fail("无法测试配置比较方法: " + e.getMessage());
        }
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
        instance.put("weight", 1);
        instances.add(instance);

        chatService.put("instances", instances);
        services.put("chat", chatService);
        config.put("services", services);

        return config;
    }

    private Map<String, Object> createTestConfigWithTimestamps(long timestamp) {
        Map<String, Object> config = createTestConfig();

        // 添加各种时间戳字段
        config.put("timestamp", timestamp);
        config.put("lastModified", timestamp);
        config.put("createdAt", timestamp);
        config.put("lastUpdated", timestamp);
        config.put("saveTime", timestamp);

        // 在服务级别添加时间戳
        Map<String, Object> services = (Map<String, Object>) config.get("services");
        Map<String, Object> chatService = (Map<String, Object>) services.get("chat");
        chatService.put("lastModified", timestamp);

        // 在实例级别添加时间戳
        List<Map<String, Object>> instances = (List<Map<String, Object>>) chatService.get("instances");
        Map<String, Object> instance = instances.get(0);
        instance.put("lastHealthCheck", timestamp);
        instance.put("lastError", timestamp);
        instance.put("timestamp", timestamp);

        return config;
    }
}
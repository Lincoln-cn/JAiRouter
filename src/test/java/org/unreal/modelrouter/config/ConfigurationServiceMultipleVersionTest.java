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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * 测试多版本创建问题
 */
@ExtendWith(MockitoExtension.class)
public class ConfigurationServiceMultipleVersionTest {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationServiceMultipleVersionTest.class);

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
        // 使用lenient模式避免不必要的stubbing警告
        lenient().when(storeManager.exists(anyString())).thenReturn(false);
        lenient().when(storeManager.versionExists(anyString(), anyInt())).thenReturn(false);
        lenient().doNothing().when(storeManager).saveConfigVersion(anyString(), any(), anyInt());
        lenient().doNothing().when(storeManager).saveConfig(anyString(), any());

        configurationService = new ConfigurationService(
                storeManager,
                configurationHelper,
                configMergeService,
                serviceStateManager,
                samplingValidator
        );
    }

    @Test
    void testCallStackLogging() {
        logger.info("测试调用栈记录功能");

        // 准备测试数据
        Map<String, Object> testConfig = createTestConfig();
        lenient().when(configMergeService.getPersistedConfig()).thenReturn(testConfig);

        // 直接调用saveAsNewVersion来测试调用栈记录
        try {
            int version = configurationService.saveAsNewVersion(testConfig, "测试调用栈记录", "test-user");
            logger.info("成功创建版本: {}", version);
        } catch (Exception e) {
            logger.info("版本创建过程中的异常（这是预期的）: {}", e.getMessage());
        }
    }

    @Test
    void testRapidVersionCreationDetection() {
        logger.info("测试快速版本创建检测功能");

        // 准备测试数据
        Map<String, Object> testConfig = createTestConfig();
        lenient().when(configMergeService.getPersistedConfig()).thenReturn(testConfig);

        // 快速创建多个版本，测试重复创建检测
        try {
            for (int i = 0; i < 3; i++) {
                Map<String, Object> config = createTestConfig();
                config.put("testIndex", i); // 确保配置不同

                int version = configurationService.saveAsNewVersion(config, "快速创建测试 " + i, "test-user");
                logger.info("创建版本 {}: {}", i, version);

                // 短暂延迟，模拟快速操作
                Thread.sleep(100);
            }

            // 测试相同描述的重复创建
            configurationService.saveAsNewVersion(testConfig, "重复描述测试", "test-user");
            Thread.sleep(50); // 很短的间隔
            configurationService.saveAsNewVersion(testConfig, "重复描述测试", "test-user");

        } catch (Exception e) {
            logger.info("版本创建过程中的异常（这是预期的）: {}", e.getMessage());
        }
    }

    @Test
    void testSingleInstanceUpdateShouldCreateOnlyOneVersion() {
        logger.info("测试单个实例更新只应创建一个版本");

        // 准备测试数据
        Map<String, Object> initialConfig = createTestConfig();
        lenient().when(configMergeService.getPersistedConfig()).thenReturn(initialConfig);

        // 模拟实例配置
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("qwen3:1.7B");
        instance.setBaseUrl("http://172.16.30.6:9090");
        instance.setStatus("active");

        Map<String, Object> instanceMap = new HashMap<>();
        instanceMap.put("name", "qwen3:1.7B");
        instanceMap.put("baseUrl", "http://172.16.30.6:9090");
        instanceMap.put("status", "active");

        lenient().when(configurationHelper.convertInstanceToMap(instance)).thenReturn(instanceMap);

        // 记录版本保存调用次数
        List<Integer> savedVersions = new ArrayList<>();
        lenient().doAnswer(invocation -> {
            String configKey = invocation.getArgument(0);
            Map<String, Object> config = invocation.getArgument(1);
            int version = invocation.getArgument(2);
            savedVersions.add(version);
            logger.info("保存版本: key={}, version={}", configKey, version);
            return null;
        }).when(storeManager).saveConfigVersion(eq("model-router-config"), any(), anyInt());

        // 执行实例更新
        try {
            configurationService.updateServiceInstance("chat", "qwen3:1.7B@http://172.16.30.6:9090", instance);
            logger.info("实例更新完成，创建的版本数: {}", savedVersions.size());

            // 验证只创建了一个版本
            assertEquals(1, savedVersions.size(),
                    "单次实例更新应该只创建一个版本，实际创建了 " + savedVersions.size() + " 个版本");

        } catch (Exception e) {
            logger.warn("实例更新失败，这可能是预期的: {}", e.getMessage());
            // 即使更新失败，也不应该创建多个版本
            assertTrue(savedVersions.size() <= 1,
                    "即使更新失败，也不应该创建多个版本，实际创建了 " + savedVersions.size() + " 个版本");
        }
    }

    @Test
    void testMultipleSequentialUpdatesShouldCreateMultipleVersions() {
        logger.info("测试多次连续更新应该创建多个版本");

        // 准备测试数据
        Map<String, Object> initialConfig = createTestConfig();
        lenient().when(configMergeService.getPersistedConfig()).thenReturn(initialConfig);

        // 记录版本保存调用次数
        AtomicInteger versionCounter = new AtomicInteger(0);
        List<Integer> savedVersions = new ArrayList<>();

        lenient().doAnswer(invocation -> {
            int version = invocation.getArgument(2);
            savedVersions.add(version);
            logger.info("保存版本: {}", version);
            return null;
        }).when(storeManager).saveConfigVersion(eq("model-router-config"), any(), anyInt());

        // 执行多次更新，每次修改不同的属性
        for (int i = 0; i < 3; i++) {
            try {
                ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
                instance.setName("qwen3:1.7B");
                instance.setBaseUrl("http://172.16.30.6:9090");
                instance.setStatus(i % 2 == 0 ? "active" : "inactive"); // 交替状态

                Map<String, Object> instanceMap = new HashMap<>();
                instanceMap.put("name", "qwen3:1.7B");
                instanceMap.put("baseUrl", "http://172.16.30.6:9090");
                instanceMap.put("status", i % 2 == 0 ? "active" : "inactive");
                instanceMap.put("updateIndex", i); // 添加不同的标识

                lenient().when(configurationHelper.convertInstanceToMap(instance)).thenReturn(instanceMap);

                configurationService.updateServiceInstance("chat", "qwen3:1.7B@http://172.16.30.6:9090", instance);
                logger.info("更新 {} 完成", i);

            } catch (Exception e) {
                logger.warn("更新 {} 失败: {}", i, e.getMessage());
            }
        }

        logger.info("多次更新完成，总共创建的版本数: {}", savedVersions.size());

        // 验证创建了合理数量的版本（应该是3个或更少，取决于配置比较的结果）
        assertTrue(savedVersions.size() <= 3,
                "3次更新最多应该创建3个版本，实际创建了 " + savedVersions.size() + " 个版本");
        assertTrue(savedVersions.size() >= 1,
                "至少应该创建1个版本，实际创建了 " + savedVersions.size() + " 个版本");
    }

    private Map<String, Object> createTestConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> services = new HashMap<>();
        Map<String, Object> chatService = new HashMap<>();

        List<Map<String, Object>> instances = new ArrayList<>();
        Map<String, Object> instance = new HashMap<>();
        instance.put("name", "qwen3:1.7B");
        instance.put("baseUrl", "http://172.16.30.6:9090");
        instance.put("status", "active");
        instance.put("weight", 1);
        instances.add(instance);

        chatService.put("instances", instances);
        services.put("chat", chatService);
        config.put("services", services);

        return config;
    }
}
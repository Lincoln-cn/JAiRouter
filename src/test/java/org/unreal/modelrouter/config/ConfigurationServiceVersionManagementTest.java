package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 配置版本管理功能测试
 * 测试版本应用和删除功能的改进
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceVersionManagementTest {

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
        // 设置初始化时需要的模拟
        setupInitialMocks();

        configurationService = new ConfigurationService(
                storeManager,
                configurationHelper,
                configMergeService,
                serviceStateManager,
                samplingValidator
        );
    }

    private void setupInitialMocks() {
        // 模拟初始化时的元数据和历史记录不存在
        when(storeManager.exists("model-router-config.metadata")).thenReturn(false);
        when(storeManager.exists("model-router-config.history")).thenReturn(false);
    }

    @Test
    void testApplyVersion_InvalidVersion() {
        // 测试无效版本号（负数和0）
        IllegalArgumentException exception1 = assertThrows(
                IllegalArgumentException.class,
                () -> configurationService.applyVersion(-1)
        );
        assertTrue(exception1.getMessage().contains("不存在"));

        IllegalArgumentException exception2 = assertThrows(
                IllegalArgumentException.class,
                () -> configurationService.applyVersion(0)
        );
        assertTrue(exception2.getMessage().contains("不存在"));
    }

    @Test
    void testDeleteConfigVersion_InvalidVersion() {
        // 测试无效版本号（负数和0）
        IllegalArgumentException exception1 = assertThrows(
                IllegalArgumentException.class,
                () -> configurationService.deleteConfigVersion(-1)
        );
        assertTrue(exception1.getMessage().contains("不存在"));

        IllegalArgumentException exception2 = assertThrows(
                IllegalArgumentException.class,
                () -> configurationService.deleteConfigVersion(0)
        );
        assertTrue(exception2.getMessage().contains("不存在"));
    }

    @Test
    void testVersionExists_ValidCases() {
        // 由于versionExists是私有方法，我们通过公共方法间接测试
        // 测试版本不存在的情况
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> configurationService.applyVersion(999)
        );
        assertTrue(exception.getMessage().contains("版本 999 不存在"));
    }

    @Test
    void testErrorHandling_ApplyVersionWithStoreManagerException() {
        // 创建一个有效的配置来通过版本存在性检查
        Map<String, Object> validConfig = createTestConfig();

        // 首先创建一个版本，这样版本存在性检查会通过
        configurationService.saveAsNewVersion(validConfig, "Test version", "test-user");

        // 然后模拟StoreManager在保存时抛出异常
        doThrow(new RuntimeException("Storage error"))
                .when(storeManager).saveConfig(eq("model-router-config"), any());

        // 模拟getConfigByVersion返回有效配置
        when(storeManager.getConfigByVersion("model-router-config", 1))
                .thenReturn(validConfig);

        // 执行测试并验证异常处理
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> configurationService.applyVersion(1)
        );

        assertTrue(exception.getMessage().contains("应用配置版本 1 失败"));
    }

    @Test
    void testErrorHandling_DeleteVersionWithStoreManagerException() {
        // 创建两个版本，这样删除一个版本时不会触发"最后一个版本"的检查
        Map<String, Object> validConfig = createTestConfig();
        configurationService.saveAsNewVersion(validConfig, "Test version 1", "test-user");
        configurationService.saveAsNewVersion(validConfig, "Test version 2", "test-user");

        // 模拟StoreManager在删除时抛出异常
        doThrow(new RuntimeException("Storage error"))
                .when(storeManager).deleteConfigVersion("model-router-config", 1);

        // 执行测试并验证异常处理
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> configurationService.deleteConfigVersion(1)
        );

        assertTrue(exception.getMessage().contains("删除配置版本 1 失败"));
    }


    /**
     * 创建测试配置
     */
    private Map<String, Object> createTestConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> services = new HashMap<>();
        Map<String, Object> openaiService = new HashMap<>();

        List<Map<String, Object>> instances = new ArrayList<>();
        Map<String, Object> instance = new HashMap<>();
        instance.put("name", "gpt-4");
        instance.put("baseUrl", "https://api.openai.com/v1");
        instance.put("weight", 1);
        instance.put("status", "active");
        instances.add(instance);

        openaiService.put("instances", instances);
        services.put("openai", openaiService);
        config.put("services", services);

        return config;
    }
}
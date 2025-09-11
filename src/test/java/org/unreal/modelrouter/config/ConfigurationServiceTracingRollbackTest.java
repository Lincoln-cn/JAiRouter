package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationServiceTracingRollbackTest {
    
    @Mock
    private StoreManager storeManager;
    
    @Mock
    private ConfigurationHelper configurationHelper;
    
    @Mock
    private ConfigMergeService configMergeService;
    
    @Mock
    private SamplingConfigurationValidator samplingValidator;
    
    private ConfigurationService configurationService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configurationService = new ConfigurationService(storeManager, configurationHelper, 
                                                       configMergeService, samplingValidator);
    }
    
    @Test
    void testRollbackTracingSamplingConfigSuccess() {
        // 准备测试数据
        int targetVersion = 5;
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.7);
        
        Map<String, Object> tracingConfig = new HashMap<>();
        tracingConfig.put("sampling", samplingConfig);
        
        Map<String, Object> versionConfig = new HashMap<>();
        versionConfig.put("tracing", tracingConfig);
        
        Map<String, Object> currentConfig = new HashMap<>();
        
        when(storeManager.getConfigByVersion("model-router-config", targetVersion))
                .thenReturn(versionConfig);
        when(configMergeService.getPersistedConfig()).thenReturn(currentConfig);
        when(storeManager.getConfigVersions("model-router-config"))
                .thenReturn(Arrays.asList(1, 2, 3, 4, 5));
        
        // 模拟验证成功
        SamplingConfigurationValidator.ValidationResult validResult = 
                new SamplingConfigurationValidator.ValidationResult(true, new ArrayList<>(), new ArrayList<>());
        when(samplingValidator.validateSamplingConfig(any())).thenReturn(validResult);
        
        // 执行测试
        Map<String, Object> result = configurationService.rollbackTracingSamplingConfig(targetVersion);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(0.7, result.get("ratio"));
        
        // 验证调用
        verify(storeManager, times(1)).getConfigByVersion("model-router-config", targetVersion);
        verify(storeManager, times(1)).saveConfig(eq("model-router-config"), any());
    }
    
    @Test
    void testRollbackTracingSamplingConfigVersionNotExists() {
        // 准备测试数据
        int targetVersion = 999;
        
        when(storeManager.getConfigByVersion("model-router-config", targetVersion))
                .thenReturn(null);
        
        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            configurationService.rollbackTracingSamplingConfig(targetVersion);
        });
        
        assertTrue(exception.getMessage().contains("目标版本不存在"));
    }
    
    @Test
    void testRollbackTracingSamplingConfigNoSamplingConfig() {
        // 准备测试数据 - 版本配置中没有采样配置
        int targetVersion = 3;
        Map<String, Object> versionConfig = new HashMap<>();
        // 没有tracing配置
        
        Map<String, Object> currentConfig = new HashMap<>();
        
        when(storeManager.getConfigByVersion("model-router-config", targetVersion))
                .thenReturn(versionConfig);
        when(configMergeService.getPersistedConfig()).thenReturn(currentConfig);
        when(storeManager.getConfigVersions("model-router-config"))
                .thenReturn(Arrays.asList(1, 2, 3));
        
        // 模拟验证成功
        SamplingConfigurationValidator.ValidationResult validResult = 
                new SamplingConfigurationValidator.ValidationResult(true, new ArrayList<>(), new ArrayList<>());
        when(samplingValidator.validateSamplingConfig(any())).thenReturn(validResult);
        
        // 执行测试
        Map<String, Object> result = configurationService.rollbackTracingSamplingConfig(targetVersion);
        
        // 验证结果 - 应该返回默认配置
        assertNotNull(result);
        assertEquals(1.0, result.get("ratio")); // 默认值
        assertTrue(result.containsKey("serviceRatios"));
        
        // 验证调用
        verify(storeManager, times(1)).saveConfig(eq("model-router-config"), any());
    }
    
    @Test
    void testRollbackTracingSamplingConfigValidationFails() {
        // 准备测试数据
        int targetVersion = 4;
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 2.0); // 无效值
        
        Map<String, Object> tracingConfig = new HashMap<>();
        tracingConfig.put("sampling", samplingConfig);
        
        Map<String, Object> versionConfig = new HashMap<>();
        versionConfig.put("tracing", tracingConfig);
        
        when(storeManager.getConfigByVersion("model-router-config", targetVersion))
                .thenReturn(versionConfig);
        
        // 模拟验证失败
        SamplingConfigurationValidator.ValidationResult invalidResult = 
                new SamplingConfigurationValidator.ValidationResult(false, 
                        Arrays.asList("采样率无效"), new ArrayList<>());
        when(samplingValidator.validateSamplingConfig(any())).thenReturn(invalidResult);
        
        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            configurationService.rollbackTracingSamplingConfig(targetVersion);
        });
        
        assertTrue(exception.getMessage().contains("目标版本的采样配置无效"));
    }
    
    @Test
    void testRollbackTracingSamplingConfigWithWarnings() {
        // 准备测试数据
        int targetVersion = 2;
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.5);
        
        Map<String, Object> tracingConfig = new HashMap<>();
        tracingConfig.put("sampling", samplingConfig);
        
        Map<String, Object> versionConfig = new HashMap<>();
        versionConfig.put("tracing", tracingConfig);
        
        Map<String, Object> currentConfig = new HashMap<>();
        
        when(storeManager.getConfigByVersion("model-router-config", targetVersion))
                .thenReturn(versionConfig);
        when(configMergeService.getPersistedConfig()).thenReturn(currentConfig);
        when(storeManager.getConfigVersions("model-router-config"))
                .thenReturn(Arrays.asList(1, 2));
        
        // 模拟验证成功但有警告
        SamplingConfigurationValidator.ValidationResult warningResult = 
                new SamplingConfigurationValidator.ValidationResult(true, new ArrayList<>(), 
                        Arrays.asList("配置有警告"));
        when(samplingValidator.validateSamplingConfig(any())).thenReturn(warningResult);
        
        // 执行测试
        Map<String, Object> result = configurationService.rollbackTracingSamplingConfig(targetVersion);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(0.5, result.get("ratio"));
        
        // 验证调用
        verify(storeManager, times(1)).saveConfig(eq("model-router-config"), any());
    }
    
    @Test
    void testUpdateTracingSamplingConfigWithValidation() {
        // 准备测试数据
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.8);
        
        Map<String, Object> currentConfig = new HashMap<>();
        
        when(configMergeService.getPersistedConfig()).thenReturn(currentConfig);
        
        // 模拟验证成功
        SamplingConfigurationValidator.ValidationResult validResult = 
                new SamplingConfigurationValidator.ValidationResult(true, new ArrayList<>(), new ArrayList<>());
        when(samplingValidator.validateSamplingConfig(any())).thenReturn(validResult);
        
        // 执行测试
        configurationService.updateTracingSamplingConfig(samplingConfig, false);
        
        // 验证调用
        verify(samplingValidator, times(1)).validateSamplingConfig(any());
        verify(storeManager, times(1)).saveConfig(eq("model-router-config"), any());
    }
    
    @Test
    void testUpdateTracingSamplingConfigValidationException() {
        // 准备测试数据
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", -1.0); // 无效值
        
        Map<String, Object> currentConfig = new HashMap<>();
        
        when(configMergeService.getPersistedConfig()).thenReturn(currentConfig);
        
        // 模拟验证失败
        SamplingConfigurationValidator.ValidationResult invalidResult = 
                new SamplingConfigurationValidator.ValidationResult(false, 
                        Arrays.asList("采样率必须在0.0-1.0之间"), new ArrayList<>());
        when(samplingValidator.validateSamplingConfig(any())).thenReturn(invalidResult);
        
        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            configurationService.updateTracingSamplingConfig(samplingConfig, false);
        });
        
        assertTrue(exception.getMessage().contains("采样配置验证失败"));
    }
}
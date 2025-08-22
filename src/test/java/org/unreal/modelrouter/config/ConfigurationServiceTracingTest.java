package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationServiceTracingTest {
    
    @Mock
    private StoreManager storeManager;
    
    @Mock
    private ConfigurationHelper configurationHelper;
    
    @Mock
    private ConfigMergeService configMergeService;
    
    @Mock
    private ModelRouterProperties modelRouterProperties;
    
    private ConfigurationService configurationService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configurationService = new ConfigurationService(storeManager, configurationHelper, configMergeService);
    }
    
    @Test
    void testGetTracingSamplingConfig() {
        // 准备测试数据
        Map<String, Object> tracingConfig = new HashMap<>();
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.5);
        tracingConfig.put("sampling", samplingConfig);
        
        Map<String, Object> config = new HashMap<>();
        config.put("tracing", tracingConfig);
        
        when(configMergeService.getPersistedConfig()).thenReturn(config);
        
        // 执行测试
        Map<String, Object> result = configurationService.getTracingSamplingConfig();
        
        // 验证结果
        assertNotNull(result);
        assertEquals(0.5, result.get("ratio"));
        assertTrue(result.containsKey("serviceRatios"));
        assertTrue(result.containsKey("alwaysSample"));
        assertTrue(result.containsKey("neverSample"));
        assertTrue(result.containsKey("rules"));
    }
    
    @Test
    void testGetTracingSamplingConfigWithNoTracingConfig() {
        // 准备测试数据 - 没有追踪配置
        Map<String, Object> config = new HashMap<>();
        
        when(configMergeService.getPersistedConfig()).thenReturn(config);
        
        // 执行测试
        Map<String, Object> result = configurationService.getTracingSamplingConfig();
        
        // 验证结果 - 应该返回默认配置
        assertNotNull(result);
        assertEquals(1.0, result.get("ratio"));
        assertTrue(result.containsKey("serviceRatios"));
        assertTrue(result.containsKey("alwaysSample"));
        assertTrue(result.containsKey("neverSample"));
        assertTrue(result.containsKey("rules"));
    }
    
    @Test
    void testUpdateTracingSamplingConfigWithNewVersion() {
        // 准备测试数据
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.8);
        
        Map<String, Object> tracingConfig = new HashMap<>();
        tracingConfig.put("sampling", samplingConfig);
        
        Map<String, Object> config = new HashMap<>();
        config.put("tracing", tracingConfig);
        
        when(configMergeService.getPersistedConfig()).thenReturn(new HashMap<>());
        
        // 执行测试
        configurationService.updateTracingSamplingConfig(samplingConfig, true);
        
        // 验证调用
        verify(storeManager, times(1)).saveConfigVersion(anyString(), anyMap(), anyInt());
    }
    
    @Test
    void testUpdateTracingSamplingConfigWithoutNewVersion() {
        // 准备测试数据
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.8);
        
        when(configMergeService.getPersistedConfig()).thenReturn(new HashMap<>());
        
        // 执行测试
        configurationService.updateTracingSamplingConfig(samplingConfig, false);
        
        // 验证调用
        verify(storeManager, times(1)).saveConfig(anyString(), anyMap());
    }
}
package org.unreal.modelrouter.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.config.ConfigurationService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * TracingSamplingController回滚功能集成测试
 */
class TracingSamplingControllerRollbackTest {
    
    @Mock
    private ConfigurationService configurationService;
    
    private WebTestClient webTestClient;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        TracingSamplingController controller = new TracingSamplingController(configurationService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }
    
    @Test
    void testRollbackTracingSamplingConfigSuccess() {
        // 准备测试数据
        int targetVersion = 5;
        Map<String, Object> rolledBackConfig = new HashMap<>();
        rolledBackConfig.put("ratio", 0.7);
        rolledBackConfig.put("serviceRatios", new HashMap<>());
        
        when(configurationService.rollbackTracingSamplingConfig(targetVersion))
                .thenReturn(rolledBackConfig);
        
        // 执行测试
        webTestClient.post()
                .uri("/api/config/tracing/sampling/rollback/{targetVersion}", targetVersion)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置回滚成功")
                .jsonPath("$.data.ratio").isEqualTo(0.7);
        
        // 验证调用
        verify(configurationService, times(1)).rollbackTracingSamplingConfig(targetVersion);
    }
    
    @Test
    void testRollbackTracingSamplingConfigVersionNotExists() {
        // 准备测试数据
        int targetVersion = 999;
        
        when(configurationService.rollbackTracingSamplingConfig(targetVersion))
                .thenThrow(new IllegalArgumentException("目标版本不存在: " + targetVersion));
        
        // 执行测试
        webTestClient.post()
                .uri("/api/config/tracing/sampling/rollback/{targetVersion}", targetVersion)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("目标版本不存在"));
        
        // 验证调用
        verify(configurationService, times(1)).rollbackTracingSamplingConfig(targetVersion);
    }
    
    @Test
    void testRollbackTracingSamplingConfigInternalError() {
        // 准备测试数据
        int targetVersion = 3;
        
        when(configurationService.rollbackTracingSamplingConfig(targetVersion))
                .thenThrow(new RuntimeException("内部服务错误"));
        
        // 执行测试
        webTestClient.post()
                .uri("/api/config/tracing/sampling/rollback/{targetVersion}", targetVersion)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("内部服务错误"));
        
        // 验证调用
        verify(configurationService, times(1)).rollbackTracingSamplingConfig(targetVersion);
    }
    
    @Test
    void testGetTracingSamplingConfig() {
        // 准备测试数据
        Map<String, Object> config = new HashMap<>();
        config.put("ratio", 0.5);
        config.put("serviceRatios", new HashMap<>());
        
        when(configurationService.getTracingSamplingConfig()).thenReturn(config);
        
        // 执行测试
        webTestClient.get()
                .uri("/api/config/tracing/sampling")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ratio").isEqualTo(0.5);
        
        // 验证调用
        verify(configurationService, times(1)).getTracingSamplingConfig();
    }
    
    @Test
    void testUpdateTracingSamplingConfigWithValidation() {
        // 准备测试数据
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.8);
        samplingConfig.put("serviceRatios", new HashMap<>());
        
        doNothing().when(configurationService).updateTracingSamplingConfig(anyMap(), eq(true));
        
        // 执行测试
        webTestClient.put()
                .uri("/api/config/tracing/sampling?createNewVersion=true")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(samplingConfig)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置更新成功");
        
        // 验证调用
        verify(configurationService, times(1)).updateTracingSamplingConfig(anyMap(), eq(true));
    }
    
    @Test
    void testUpdateTracingSamplingConfigValidationFailure() {
        // 准备测试数据
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 2.0); // 无效值
        
        doThrow(new IllegalArgumentException("采样配置验证失败: 采样率必须在0.0-1.0之间"))
                .when(configurationService).updateTracingSamplingConfig(anyMap(), eq(false));
        
        // 执行测试
        webTestClient.put()
                .uri("/api/config/tracing/sampling?createNewVersion=false")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(samplingConfig)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("采样配置验证失败"));
        
        // 验证调用
        verify(configurationService, times(1)).updateTracingSamplingConfig(anyMap(), eq(false));
    }
    
    @Test
    void testResetTracingSamplingConfig() {
        // 准备测试数据
        doNothing().when(configurationService).updateTracingSamplingConfig(anyMap(), eq(true));
        
        // 执行测试
        webTestClient.post()
                .uri("/api/config/tracing/sampling/reset?createNewVersion=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置重置成功");
        
        // 验证调用
        verify(configurationService, times(1)).updateTracingSamplingConfig(anyMap(), eq(true));
    }
    
    @Test
    void testResetTracingSamplingConfigWithoutNewVersion() {
        // 准备测试数据
        doNothing().when(configurationService).updateTracingSamplingConfig(anyMap(), eq(false));
        
        // 执行测试
        webTestClient.post()
                .uri("/api/config/tracing/sampling/reset?createNewVersion=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置重置成功");
        
        // 验证调用
        verify(configurationService, times(1)).updateTracingSamplingConfig(anyMap(), eq(false));
    }
    
    @Test
    void testInvalidRollbackVersionPath() {
        // 测试无效的版本号路径参数
        webTestClient.post()
                .uri("/api/config/tracing/sampling/rollback/invalid")
                .exchange()
                .expectStatus().isBadRequest();
        
        // 验证没有调用服务
        verify(configurationService, never()).rollbackTracingSamplingConfig(anyInt());
    }
}
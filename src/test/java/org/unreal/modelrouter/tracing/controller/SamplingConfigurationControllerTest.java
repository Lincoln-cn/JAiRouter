package org.unreal.modelrouter.tracing.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.sampler.SamplingStrategyManager;

import static org.mockito.Mockito.*;

/**
 * 采样配置控制器测试类
 */
class SamplingConfigurationControllerTest {
    
    @Mock
    private TracingConfiguration tracingConfig;
    
    @Mock
    private SamplingStrategyManager samplingStrategyManager;
    
    @Mock
    private TracingConfiguration.SamplingConfig samplingConfig;
    
    private WebTestClient webTestClient;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tracingConfig.getSampling()).thenReturn(samplingConfig);
        
        SamplingConfigurationController controller = new SamplingConfigurationController(
                tracingConfig, samplingStrategyManager);
        webTestClient = WebTestClient.bindToController(controller).build();
    }
    
    @Test
    void testGetSamplingConfiguration() {
        webTestClient.get()
                .uri("/api/tracing/sampling/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ratio").exists();
    }
    
    @Test
    void testUpdateSamplingConfiguration() {
        when(tracingConfig.getSampling()).thenReturn(samplingConfig);
        
        webTestClient.put()
                .uri("/api/tracing/sampling/config")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(samplingConfig)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("采样配置更新成功");
    }
    
    @Test
    void testResetSamplingConfiguration() {
        webTestClient.delete()
                .uri("/api/tracing/sampling/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("采样配置已重置为默认值");
    }
    
    @Test
    void testGetSamplingStatistics() {
        // 模拟返回一个非空的采样策略
        io.opentelemetry.sdk.trace.samplers.Sampler mockSampler = mock(io.opentelemetry.sdk.trace.samplers.Sampler.class);
        when(mockSampler.getDescription()).thenReturn("Mock Sampler");
        when(samplingStrategyManager.getCurrentStrategy()).thenReturn(mockSampler);
        
        webTestClient.get()
                .uri("/api/tracing/sampling/statistics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").exists();
    }
}
package org.unreal.modelrouter.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.config.TraceConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * 追踪采样控制器测试类
 */
class TracingSamplingControllerTest {

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
    void testGetSamplingConfiguration() {
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.5);
        samplingConfig.put("serviceRatios", new HashMap<String, Double>());
        samplingConfig.put("alwaysSample", Collections.emptyList());
        when(configurationService.getTracingSamplingConfig()).thenReturn(samplingConfig);

        webTestClient.get()
                .uri("/api/config/tracing/sampling")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ratio").isEqualTo(0.5);
    }

    @Test
    void testUpdateSamplingConfiguration() {
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.8);

        doNothing().when(configurationService).updateTracingSamplingConfig(anyMap(), eq(false));

        webTestClient.put()
                .uri("/api/config/tracing/sampling?createNewVersion=false")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(samplingConfig)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置更新成功");
    }

    @Test
    void testUpdateSamplingConfigurationWithVersion() {
        Map<String, Object> samplingConfig = new HashMap<>();
        samplingConfig.put("ratio", 0.6);

        doNothing().when(configurationService).updateTracingSamplingConfig(anyMap(), eq(true));

        webTestClient.put()
                .uri("/api/config/tracing/sampling?createNewVersion=true")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(samplingConfig)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置更新成功");
    }

    @Test
    void testPartialUpdateSamplingConfiguration() {
        Map<String, Object> partialConfig = Collections.singletonMap("ratio", 0.3);

        doNothing().when(configurationService).updateTracingSamplingConfig(anyMap(), eq(false));

        webTestClient.put()
                .uri("/api/config/tracing/sampling?createNewVersion=false")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(partialConfig)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置更新成功");
    }

    @Test
    void testResetSamplingConfiguration() {
        doNothing().when(configurationService).updateTracingSamplingConfig(anyMap(), eq(false));

        webTestClient.post()
                .uri("/api/config/tracing/sampling/reset?createNewVersion=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("追踪采样配置重置成功");
    }
}
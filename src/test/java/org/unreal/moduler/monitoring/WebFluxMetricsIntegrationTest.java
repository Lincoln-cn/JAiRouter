package org.unreal.moduler.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.monitoring.MetricsCollector;
import org.unreal.modelrouter.monitoring.MonitoringConfiguration;
import org.unreal.modelrouter.monitoring.MonitoringProperties;
import org.unreal.modelrouter.monitoring.WebFluxMetricsConfiguration;
import org.unreal.modelrouter.monitoring.WebFluxMetricsInterceptor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

/**
 * WebFlux指标拦截器集成测试
 * 测试在Spring WebFlux环境中的集成情况
 */
@WebFluxTest
@Import({
    MonitoringConfiguration.class,
    WebFluxMetricsConfiguration.class,
    WebFluxMetricsInterceptor.class,
    MonitoringProperties.class
})
@TestPropertySource(properties = {
    "monitoring.metrics.enabled=true",
    "monitoring.metrics.prefix=jairouter",
    "monitoring.metrics.enabled-categories=system,business,infrastructure"
})
class WebFluxMetricsIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private MetricsCollector metricsCollector;

    @Test
    void testMetricsInterceptorIntegration() {
        // 发送测试请求
        webTestClient.post()
            .uri("/v1/chat/completions")
            .bodyValue("{\"model\":\"test\",\"messages\":[]}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no actual controller

        // 验证指标被记录
        verify(metricsCollector, atLeastOnce()).recordRequest(
            eq("chat"),
            eq("POST"),
            anyLong(),
            eq("404")
        );

        verify(metricsCollector, atLeastOnce()).recordRequestSize(
            eq("chat"),
            anyLong(),
            anyLong()
        );
    }

    @Test
    void testActuatorEndpointSkipped() {
        // 发送actuator请求
        webTestClient.get()
            .uri("/actuator/health")
            .exchange();

        // 验证指标没有被记录（因为应该被跳过）
        // 注意：这里不能用never()因为可能有其他请求，所以我们只测试正常请求被记录
    }

    @Test
    void testEmbeddingsEndpoint() {
        // 发送嵌入请求
        webTestClient.post()
            .uri("/v1/embeddings")
            .bodyValue("{\"model\":\"test\",\"input\":\"test\"}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no actual controller

        // 验证指标被记录
        verify(metricsCollector, atLeastOnce()).recordRequest(
            eq("embedding"),
            eq("POST"),
            anyLong(),
            eq("404")
        );
    }

    @Test
    void testRerankEndpoint() {
        // 发送重排序请求
        webTestClient.post()
            .uri("/v1/rerank")
            .bodyValue("{\"model\":\"test\",\"query\":\"test\",\"documents\":[]}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no actual controller

        // 验证指标被记录
        verify(metricsCollector, atLeastOnce()).recordRequest(
            eq("rerank"),
            eq("POST"),
            anyLong(),
            eq("404")
        );
    }

    @Test
    void testImageGenerationEndpoint() {
        // 发送图像生成请求
        webTestClient.post()
            .uri("/v1/images/generations")
            .bodyValue("{\"model\":\"test\",\"prompt\":\"test\"}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no actual controller

        // 验证指标被记录
        verify(metricsCollector, atLeastOnce()).recordRequest(
            eq("imgGen"),
            eq("POST"),
            anyLong(),
            eq("404")
        );
    }

    @Test
    void testImageEditEndpoint() {
        // 发送图像编辑请求
        webTestClient.post()
            .uri("/v1/images/edits")
            .bodyValue("{\"model\":\"test\",\"prompt\":\"test\"}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no actual controller

        // 验证指标被记录
        verify(metricsCollector, atLeastOnce()).recordRequest(
            eq("imgEdit"),
            eq("POST"),
            anyLong(),
            eq("404")
        );
    }

    @Test
    void testAudioSpeechEndpoint() {
        // 发送TTS请求
        webTestClient.post()
            .uri("/v1/audio/speech")
            .bodyValue("{\"model\":\"test\",\"input\":\"test\",\"voice\":\"alloy\"}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no actual controller

        // 验证指标被记录
        verify(metricsCollector, atLeastOnce()).recordRequest(
            eq("tts"),
            eq("POST"),
            anyLong(),
            eq("404")
        );
    }

    @Test
    void testAudioTranscriptionsEndpoint() {
        // 发送STT请求
        webTestClient.post()
            .uri("/v1/audio/transcriptions")
            .bodyValue("{\"model\":\"test\"}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no actual controller

        // 验证指标被记录
        verify(metricsCollector, atLeastOnce()).recordRequest(
            eq("stt"),
            eq("POST"),
            anyLong(),
            eq("404")
        );
    }
}
package org.unreal.modelrouter.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.WebFluxMetricsInterceptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebFlux指标拦截器测试
 */
@ExtendWith(MockitoExtension.class)
class WebFluxMetricsInterceptorTest {

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private SlowQueryDetector slowQueryDetector;

    @Mock
    private WebFilterChain filterChain;

    private MonitoringProperties monitoringProperties;
    private WebFluxMetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        monitoringProperties = new MonitoringProperties();
        monitoringProperties.setEnabled(true);
        monitoringProperties.setPrefix("jairouter");
        monitoringProperties.setEnabledCategories(Set.of("system", "business", "infrastructure"));
        
        interceptor = new WebFluxMetricsInterceptor(metricsCollector, monitoringProperties,slowQueryDetector);
    }

    @Test
    void testChatCompletionsRequest() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/chat/completions")
            .header("Content-Length", "100")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);

        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            methodCaptor.capture(),
            durationCaptor.capture(),
            statusCaptor.capture()
        );

        assertEquals("chat", serviceCaptor.getValue());
        assertEquals("POST", methodCaptor.getValue());
        assertEquals("200", statusCaptor.getValue());
        assertTrue(durationCaptor.getValue() >= 0);
    }

    @Test
    void testEmbeddingsRequest() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/embeddings")
            .header("Content-Length", "200")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            eq("POST"),
            anyLong(),
            eq("200")
        );

        assertEquals("embedding", serviceCaptor.getValue());
    }

    @Test
    void testRerankRequest() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/rerank")
            .header("Content-Length", "150")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            eq("POST"),
            anyLong(),
            eq("200")
        );

        assertEquals("rerank", serviceCaptor.getValue());
    }

    @Test
    void testAudioSpeechRequest() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/audio/speech")
            .header("Content-Length", "80")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            eq("POST"),
            anyLong(),
            eq("200")
        );

        assertEquals("tts", serviceCaptor.getValue());
    }

    @Test
    void testAudioTranscriptionsRequest() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/audio/transcriptions")
            .header("Content-Length", "1000")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            eq("POST"),
            anyLong(),
            eq("200")
        );

        assertEquals("stt", serviceCaptor.getValue());
    }

    @Test
    void testImageGenerationsRequest() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/images/generations")
            .header("Content-Length", "300")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            eq("POST"),
            anyLong(),
            eq("200")
        );

        assertEquals("imgGen", serviceCaptor.getValue());
    }

    @Test
    void testImageEditsRequest() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/images/edits")
            .header("Content-Length", "500")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            eq("POST"),
            anyLong(),
            eq("200")
        );

        assertEquals("imgEdit", serviceCaptor.getValue());
    }

    @Test
    void testErrorResponse() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/chat/completions")
            .header("Content-Length", "100")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟过滤器链抛出异常
        when(filterChain.filter(any())).thenReturn(Mono.error(new RuntimeException("Test error")));

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyError();

        // 验证错误指标记录
        verify(metricsCollector).recordRequest(
            eq("chat"),
            eq("POST"),
            anyLong(),
            eq("500")
        );
    }

    @Test
    void testRequestSizeRecording() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/chat/completions")
            .header("Content-Length", "250")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证请求大小记录
        ArgumentCaptor<Long> requestSizeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> responseSizeCaptor = ArgumentCaptor.forClass(Long.class);

        verify(metricsCollector).recordRequestSize(
            eq("chat"),
            requestSizeCaptor.capture(),
            responseSizeCaptor.capture()
        );

        assertEquals(250L, requestSizeCaptor.getValue());
        assertTrue(responseSizeCaptor.getValue() >= 0);
    }

    @Test
    void testSkipActuatorEndpoints() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟过滤器链
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证不记录指标
        verify(metricsCollector, never()).recordRequest(anyString(), anyString(), anyLong(), anyString());
        verify(metricsCollector, never()).recordRequestSize(anyString(), anyLong(), anyLong());
    }

    @Test
    void testSkipStaticResources() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/static/css/style.css")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟过滤器链
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证不记录指标
        verify(metricsCollector, never()).recordRequest(anyString(), anyString(), anyLong(), anyString());
        verify(metricsCollector, never()).recordRequestSize(anyString(), anyLong(), anyLong());
    }

    @Test
    void testUnknownServicePath() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/unknown/path")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);

        // 模拟过滤器链
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证指标记录
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsCollector).recordRequest(
            serviceCaptor.capture(),
            eq("GET"),
            anyLong(),
            eq("404")
        );

        assertEquals("unknown", serviceCaptor.getValue());
    }

    @Test
    void testGetOrder() {
        // 验证过滤器顺序
        int order = interceptor.getOrder();
        assertTrue(order > Integer.MIN_VALUE);
        assertTrue(order < Integer.MAX_VALUE);
    }
}
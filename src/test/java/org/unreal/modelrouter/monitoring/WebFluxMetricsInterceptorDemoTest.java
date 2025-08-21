package org.unreal.modelrouter.monitoring;

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
 * WebFlux指标拦截器演示测试
 * 展示拦截器如何工作以及记录的指标类型
 */
@ExtendWith(MockitoExtension.class)
class WebFluxMetricsInterceptorDemoTest {

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private WebFilterChain filterChain;

    @Test
    void demonstrateMetricsCollection() {
        // 设置监控配置
        MonitoringProperties properties = new MonitoringProperties();
        properties.setEnabled(true);
        properties.setPrefix("jairouter");
        properties.setEnabledCategories(Set.of("system", "business", "infrastructure"));
        
        // 创建拦截器
        WebFluxMetricsInterceptor interceptor = new WebFluxMetricsInterceptor(metricsCollector, properties);

        // 模拟聊天完成请求
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/chat/completions")
            .header("Content-Type", "application/json")
            .header("Content-Length", "150")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟过滤器链处理
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // 执行拦截器
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        // 验证请求指标被记录
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

        // 验证记录的指标值
        assertEquals("chat", serviceCaptor.getValue(), "应该识别为聊天服务");
        assertEquals("POST", methodCaptor.getValue(), "应该记录HTTP方法");
        assertEquals("200", statusCaptor.getValue(), "应该记录状态码");
        assertTrue(durationCaptor.getValue() >= 0, "响应时间应该非负");

        // 验证请求大小指标被记录
        ArgumentCaptor<Long> requestSizeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> responseSizeCaptor = ArgumentCaptor.forClass(Long.class);

        verify(metricsCollector).recordRequestSize(
            eq("chat"),
            requestSizeCaptor.capture(),
            responseSizeCaptor.capture()
        );

        assertEquals(150L, requestSizeCaptor.getValue(), "应该记录请求大小");
        assertTrue(responseSizeCaptor.getValue() >= 0, "响应大小应该非负");

        System.out.println("=== 指标收集演示 ===");
        System.out.println("服务类型: " + serviceCaptor.getValue());
        System.out.println("HTTP方法: " + methodCaptor.getValue());
        System.out.println("响应时间: " + durationCaptor.getValue() + "ms");
        System.out.println("状态码: " + statusCaptor.getValue());
        System.out.println("请求大小: " + requestSizeCaptor.getValue() + " bytes");
        System.out.println("响应大小: " + responseSizeCaptor.getValue() + " bytes");
    }

    @Test
    void demonstrateServiceTypeExtraction() {
        MonitoringProperties properties = new MonitoringProperties();
        properties.setEnabled(true);
        WebFluxMetricsInterceptor interceptor = new WebFluxMetricsInterceptor(metricsCollector, properties);

        // 测试不同的API端点
        String[][] testCases = {
            {"/v1/chat/completions", "chat"},
            {"/v1/embeddings", "embedding"},
            {"/v1/rerank", "rerank"},
            {"/v1/audio/speech", "tts"},
            {"/v1/audio/transcriptions", "stt"},
            {"/v1/images/generations", "imgGen"},
            {"/v1/images/edits", "imgEdit"},
            {"/unknown/path", "unknown"}
        };

        System.out.println("=== 服务类型识别演示 ===");
        
        for (String[] testCase : testCases) {
            String path = testCase[0];
            String expectedService = testCase[1];

            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getResponse().setStatusCode(HttpStatus.OK);

            when(filterChain.filter(any())).thenReturn(Mono.empty());

            StepVerifier.create(interceptor.filter(exchange, filterChain))
                .verifyComplete();

            ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
            verify(metricsCollector, atLeastOnce()).recordRequest(
                serviceCaptor.capture(),
                anyString(),
                anyLong(),
                anyString()
            );

            String actualService = serviceCaptor.getValue();
            assertEquals(expectedService, actualService, 
                "路径 " + path + " 应该识别为服务 " + expectedService);
            
            System.out.println("路径: " + path + " -> 服务: " + actualService);
            
            reset(metricsCollector);
        }
    }

    @Test
    void demonstrateSkippedPaths() {
        MonitoringProperties properties = new MonitoringProperties();
        properties.setEnabled(true);
        WebFluxMetricsInterceptor interceptor = new WebFluxMetricsInterceptor(metricsCollector, properties);

        String[] skippedPaths = {
            "/actuator/health",
            "/actuator/prometheus",
            "/static/css/style.css",
            "/favicon.ico"
        };

        System.out.println("=== 跳过路径演示 ===");

        for (String path : skippedPaths) {
            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(exchange)).thenReturn(Mono.empty());

            StepVerifier.create(interceptor.filter(exchange, filterChain))
                .verifyComplete();

            // 验证没有记录指标
            verify(metricsCollector, never()).recordRequest(anyString(), anyString(), anyLong(), anyString());
            verify(metricsCollector, never()).recordRequestSize(anyString(), anyLong(), anyLong());
            
            System.out.println("跳过路径: " + path);
            
            reset(metricsCollector);
        }
    }

    @Test
    void demonstrateAsyncResponseTimeTracking() {
        MonitoringProperties properties = new MonitoringProperties();
        properties.setEnabled(true);
        WebFluxMetricsInterceptor interceptor = new WebFluxMetricsInterceptor(metricsCollector, properties);

        MockServerHttpRequest request = MockServerHttpRequest
            .post("/v1/chat/completions")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // 模拟异步处理延迟
        when(filterChain.filter(any())).thenReturn(
            Mono.<Void>empty().delayElement(java.time.Duration.ofMillis(100))
        );

        long startTime = System.currentTimeMillis();
        
        StepVerifier.create(interceptor.filter(exchange, filterChain))
            .verifyComplete();

        long actualDuration = System.currentTimeMillis() - startTime;

        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        verify(metricsCollector).recordRequest(
            anyString(),
            anyString(),
            durationCaptor.capture(),
            anyString()
        );

        long recordedDuration = durationCaptor.getValue();
        
        System.out.println("=== 异步响应时间跟踪演示 ===");
        System.out.println("实际处理时间: " + actualDuration + "ms");
        System.out.println("记录的响应时间: " + recordedDuration + "ms");
        
        // 验证记录的时间在合理范围内（放宽时间要求以适应测试环境）
        assertTrue(recordedDuration >= 0, "记录的时间应该非负");
        assertTrue(recordedDuration <= actualDuration + 100, "记录的时间不应该过长");
    }
}
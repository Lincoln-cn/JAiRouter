package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.monitoring.collector.DefaultMetricsCollector;
import org.unreal.modelrouter.monitoring.AsyncMetricsProcessor;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整监控数据流的端到端测试
 * 验证从请求接收到Prometheus指标暴露的完整数据流
 */
@SpringBootTest(
    classes = org.unreal.modelrouter.ModelRouterApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
    "monitoring.metrics.enabled=true",
    "monitoring.metrics.prefix=jairouter",
    "monitoring.metrics.performance.async-processing=true",
    "monitoring.metrics.performance.batch-size=10",
    "monitoring.metrics.performance.buffer-size=100",
    "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
    "management.endpoint.prometheus.cache.time-to-live=1s",
    "management.endpoints.web.base-path=/actuator",
    "management.endpoints.web.cors.allowed-origins=*",
    "management.endpoints.web.cors.allowed-methods=GET,POST",
    "management.endpoint.health.show-details=always",
    "management.endpoint.prometheus.enabled=true",
    "management.security.enabled=false",
    "management.endpoints.web.exposure.include=*",
    "management.endpoints.enabled-by-default=true",
    "management.endpoint.info.enabled=true",
    "management.endpoint.health.enabled=true",
    "management.endpoint.metrics.enabled=true",
    "management.endpoint.prometheus.sensitive=false",
    "management.endpoints.web.path-mapping.prometheus=prometheus",
    "jairouter.security.jwt.secret=x0TP1rE6RQWX7Zefq9Vqd7FGfgYdTJg4"
})
public class MonitoringDataFlowEndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DefaultMetricsCollector metricsCollector;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private AsyncMetricsProcessor asyncMetricsProcessor;

    private WebClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    @Test
    void testCompleteRequestToPrometheusFlow() throws InterruptedException {
        // 1. 模拟HTTP请求处理流程
        String serviceName = "end_to_end_test";
        String method = "POST";
        long duration = 150;
        String status = "200";

        // 2. 记录请求指标（模拟WebFlux拦截器行为）
        metricsCollector.recordRequest(serviceName, method, duration, status);
        metricsCollector.recordRequestSize(serviceName, 1024, 2048);

        // 3. 记录后端调用指标（模拟适配器调用）
        metricsCollector.recordBackendCall("test_adapter", "instance_1", 100, true);
        metricsCollector.recordBackendCall("test_adapter", "instance_2", 120, false);

        // 4. 记录基础设施指标
        metricsCollector.recordRateLimit(serviceName, "token-bucket", true);
        metricsCollector.recordCircuitBreaker(serviceName, "CLOSED", "SUCCESS");
        metricsCollector.recordLoadBalancer(serviceName, "round-robin", "instance_1");

        // 5. 等待异步处理完成
        if (asyncMetricsProcessor != null) {
            Thread.sleep(500); // 等待异步处理
        } else {
            Thread.sleep(100); // 同步处理只需短暂等待
        }

        // 6. 验证指标在MeterRegistry中正确注册
        verifyMetricsInRegistry(serviceName, method, status);

        // 7. 验证指标在Prometheus端点中正确暴露
        verifyMetricsInPrometheusEndpoint(serviceName, method, status);
    }

    private void verifyMetricsInRegistry(String serviceName, String method, String status) {
        // 验证请求计数器
        Counter requestCounter = meterRegistry.find("jairouter_requests_total")
            .tag("service", serviceName)
            .tag("method", method)
            .tag("status", status)
            .counter();
        assertNotNull(requestCounter, "Request counter should exist in registry");
        assertTrue(requestCounter.count() > 0, "Request counter should have been incremented");

        // 验证请求时长直方图
        Timer requestTimer = meterRegistry.find("jairouter_request_duration_seconds")
            .tag("service", serviceName)
            .tag("method", method)
            .timer();
        assertNotNull(requestTimer, "Request timer should exist in registry");
        assertTrue(requestTimer.count() > 0, "Request timer should have recorded values");

        // 验证后端调用指标 - 修正标签匹配逻辑
        Counter backendSuccessCounter = meterRegistry.find("jairouter_backend_calls_total")
            .tag("adapter", "test_adapter")
            .tag("status", "success")
            .counter();
        Counter backendFailureCounter = meterRegistry.find("jairouter_backend_calls_total")
            .tag("adapter", "test_adapter")
            .tag("status", "failure")
            .counter();
        assertNotNull(backendSuccessCounter, "Backend success call counter should exist");
        assertNotNull(backendFailureCounter, "Backend failure call counter should exist");
        assertTrue(backendSuccessCounter.count() > 0, "Backend success call counter should have been incremented");
        assertTrue(backendFailureCounter.count() > 0, "Backend failure call counter should have been incremented");

        // 验证基础设施指标 - 修正标签匹配逻辑
        Counter rateLimitAllowedCounter = meterRegistry.find("jairouter_rate_limit_events_total")
            .tag("service", serviceName)
            .tag("algorithm", "token-bucket")
            .tag("result", "allowed")
            .counter();
        assertNotNull(rateLimitAllowedCounter, "Rate limit allowed counter should exist");
        assertTrue(rateLimitAllowedCounter.count() > 0, "Rate limit allowed counter should have been incremented");
    }

    private void verifyMetricsInPrometheusEndpoint(String serviceName, String method, String status) {
        String response = webClient.get()
            .uri("/actuator/prometheus")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5));

        assertNotNull(response, "Prometheus response should not be null");

        // 验证请求指标
        assertTrue(response.contains("jairouter_requests_total{"), 
            "Should contain request counter metrics");
        assertTrue(response.contains("service=\"" + serviceName + "\""), 
            "Should contain service label");
        assertTrue(response.contains("method=\"" + method + "\""), 
            "Should contain method label");
        assertTrue(response.contains("status=\"" + status + "\""), 
            "Should contain status label");

        // 验证直方图指标
        assertTrue(response.contains("jairouter_request_duration_seconds_bucket"), 
            "Should contain request duration histogram buckets");
        assertTrue(response.contains("jairouter_request_duration_seconds_count"), 
            "Should contain request duration count");
        assertTrue(response.contains("jairouter_request_duration_seconds_sum"), 
            "Should contain request duration sum");

        // 验证后端调用指标
        assertTrue(response.contains("jairouter_backend_calls_total{"), 
            "Should contain backend call metrics");
        assertTrue(response.contains("adapter=\"test_adapter\""), 
            "Should contain adapter label");

        // 验证基础设施指标
        assertTrue(response.contains("jairouter_rate_limit_events_total{"), 
            "Should contain rate limit metrics");
        assertTrue(response.contains("jairouter_circuit_breaker_events_total{"), 
            "Should contain circuit breaker metrics");
        assertTrue(response.contains("jairouter_loadbalancer_selections_total{"), 
            "Should contain load balancer metrics");
    }

    @Test
    void testAsyncProcessingDataFlow() throws InterruptedException {
        if (asyncMetricsProcessor == null) {
            // 如果没有异步处理器，跳过此测试
            return;
        }

        // 快速生成大量指标数据
        for (int i = 0; i < 50; i++) {
            metricsCollector.recordRequest("async_test", "GET", 100 + i, "200");
            metricsCollector.recordBackendCall("async_adapter", "instance_" + (i % 3), 50 + i, true);
        }

        // 等待异步处理完成
        Thread.sleep(1000);

        // 验证所有指标都被正确处理
        Counter requestCounter = meterRegistry.find("jairouter_requests_total")
            .tag("service", "async_test")
            .counter();
        assertNotNull(requestCounter, "Async request counter should exist");
        assertEquals(50.0, requestCounter.count(), 0.1, "All async requests should be counted");

        // 验证在Prometheus端点中可见
        String response = webClient.get()
            .uri("/actuator/prometheus")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5));

        assertTrue(response.contains("service=\"async_test\""), 
            "Async metrics should be visible in Prometheus endpoint");
    }

    @Test
    void testErrorHandlingInDataFlow() throws InterruptedException {
        // 模拟各种错误场景
        metricsCollector.recordRequest("error_test", "POST", 5000, "500");
        metricsCollector.recordRequest("error_test", "POST", 1000, "404");
        metricsCollector.recordRequest("error_test", "POST", 30000, "503");
        
        metricsCollector.recordBackendCall("error_adapter", "failing_instance", 10000, false);
        metricsCollector.recordCircuitBreaker("error_test", "OPEN", "FAILURE");

        Thread.sleep(200);

        // 验证错误指标被正确记录
        String response = webClient.get()
            .uri("/actuator/prometheus")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5));

        assertTrue(response.contains("status=\"500\""), "Should contain 500 error status");
        assertTrue(response.contains("status=\"404\""), "Should contain 404 error status");
        assertTrue(response.contains("status=\"503\""), "Should contain 503 error status");
        assertTrue(response.contains("status=\"failure\"") || response.contains("result=\"failure\""), 
            "Should contain backend failure status or result tag");
        assertTrue(response.contains("event=\"FAILURE\""), "Should contain circuit breaker failure event");
    }

    @Test
    void testMetricsDataConsistency() throws InterruptedException {
        String testService = "consistency_test";
        int requestCount = 20;

        // 记录固定数量的请求
        for (int i = 0; i < requestCount; i++) {
            metricsCollector.recordRequest(testService, "GET", 100, "200");
        }

        Thread.sleep(300);

        // 验证MeterRegistry中的计数
        Counter registryCounter = meterRegistry.find("jairouter_requests_total")
            .tag("service", testService)
            .counter();
        assertNotNull(registryCounter);
        assertEquals(requestCount, registryCounter.count(), 0.1, 
            "Registry counter should match recorded requests");

        // 验证Prometheus端点中的计数
        String response = webClient.get()
            .uri("/actuator/prometheus")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5));

        // 从Prometheus响应中提取计数值
        String[] lines = response.split("\n");
        double prometheusCount = -1;
        for (String line : lines) {
            if (line.contains("jairouter_requests_total{") && 
                line.contains("service=\"" + testService + "\"")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    prometheusCount = Double.parseDouble(parts[parts.length - 1]);
                    break;
                }
            }
        }

        assertTrue(prometheusCount >= 0, "Should find prometheus counter value");
        assertEquals(requestCount, prometheusCount, 0.1, 
            "Prometheus counter should match registry counter");
    }

    @Test
    void testConcurrentDataFlowIntegrity() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger totalRequests = new AtomicInteger(0);
        int threadsCount = 10;
        int requestsPerThread = 20;

        // 并发生成指标数据
        CompletableFuture<?>[] futures = new CompletableFuture[threadsCount];
        for (int t = 0; t < threadsCount; t++) {
            final int threadId = t;
            futures[t] = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < requestsPerThread; i++) {
                    metricsCollector.recordRequest("concurrent_test", "POST", 
                        100 + threadId * 10 + i, "200");
                    totalRequests.incrementAndGet();
                }
            }, executor);
        }

        // 等待所有线程完成
        try {
            CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException("Concurrent task execution failed", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Concurrent task execution timeout", e);
        }
        Thread.sleep(500); // 等待异步处理完成

        // 验证数据完整性
        Counter counter = meterRegistry.find("jairouter_requests_total")
            .tag("service", "concurrent_test")
            .counter();
        assertNotNull(counter);
        assertEquals(totalRequests.get(), counter.count(), 0.1, 
            "Concurrent requests should all be counted correctly");

        // 验证Prometheus端点数据一致性
        String response = webClient.get()
            .uri("/actuator/prometheus")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5));

        assertTrue(response.contains("service=\"concurrent_test\""), 
            "Concurrent test metrics should be visible in Prometheus");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), 
            "Executor should terminate cleanly");
    }

    @Test
    void testMetricsLabelsAndTagsPropagation() throws InterruptedException {
        // 测试复杂的标签传播
        metricsCollector.recordRequest("label_test", "PUT", 200, "201");
        metricsCollector.recordBackendCall("complex_adapter", "labeled_instance", 150, true);
        metricsCollector.recordLoadBalancer("label_test", "weighted-round-robin", "labeled_instance");

        Thread.sleep(200);

        String response = webClient.get()
            .uri("/actuator/prometheus")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5));

        // 验证所有标签都正确传播到Prometheus输出
        assertTrue(response.contains("service=\"label_test\""), "Service label should propagate");
        assertTrue(response.contains("method=\"PUT\""), "Method label should propagate");
        assertTrue(response.contains("status=\"201\""), "Status label should propagate");
        assertTrue(response.contains("adapter=\"complex_adapter\""), "Adapter label should propagate");
        assertTrue(response.contains("instance=\"labeled_instance\""), "Instance label should propagate");
        assertTrue(response.contains("strategy=\"weighted-round-robin\""), "Strategy label should propagate");
        
        // 验证全局标签
        assertTrue(response.contains("application=\"jairouter\""), "Global application tag should be present");
    }
}
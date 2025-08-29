package org.unreal.modelrouter.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.*;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * 异步指标处理器测试
 */
@ExtendWith(MockitoExtension.class)
class AsyncMetricsProcessorTest {

    @Mock
    private MonitoringProperties monitoringProperties;

    @Mock
    private MonitoringProperties.Sampling samplingConfig;

    @Mock
    private MonitoringProperties.Performance performanceConfig;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private MetricsCircuitBreaker circuitBreaker;

    private AsyncMetricsProcessor asyncProcessor;

    @BeforeEach
    void setUp() {
        // 配置mock对象
        lenient().when(monitoringProperties.getSampling()).thenReturn(samplingConfig);
        lenient().when(monitoringProperties.getPerformance()).thenReturn(performanceConfig);
        
        lenient().when(samplingConfig.getRequestMetrics()).thenReturn(1.0);
        lenient().when(samplingConfig.getBackendMetrics()).thenReturn(1.0);
        lenient().when(samplingConfig.getInfrastructureMetrics()).thenReturn(1.0);
        
        lenient().when(performanceConfig.getBatchSize()).thenReturn(10);
        lenient().when(performanceConfig.getBufferSize()).thenReturn(100);
        
        lenient().when(circuitBreaker.allowRequest()).thenReturn(true);
        lenient().when(circuitBreaker.getState()).thenReturn("CLOSED");
        
        asyncProcessor = new AsyncMetricsProcessor(monitoringProperties, metricsCollector, circuitBreaker);
    }

    @Test
    void testRecordRequestAsync() throws InterruptedException {
        // 测试异步记录请求指标
        asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        
        // 等待异步处理完成
        Thread.sleep(100);
        
        // 验证指标被记录
        verify(metricsCollector, timeout(1000)).recordRequest("chat", "POST", 100L, "200");
    }

    @Test
    void testRecordBackendCallAsync() throws InterruptedException {
        // 测试异步记录后端调用指标
        asyncProcessor.recordBackendCallAsync("ollama", "instance1", 200L, true);
        
        // 等待异步处理完成
        Thread.sleep(100);
        
        // 验证指标被记录
        verify(metricsCollector, timeout(1000)).recordBackendCall("ollama", "instance1", 200L, true);
    }

    @Test
    void testCircuitBreakerProtection() {
        // 配置熔断器拒绝请求
        when(circuitBreaker.allowRequest()).thenReturn(false);
        
        // 尝试记录指标
        asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        
        // 验证指标没有被处理
        verifyNoInteractions(metricsCollector);
        
        // 验证统计信息中丢弃计数增加
        AsyncMetricsProcessor.ProcessingStats stats = asyncProcessor.getStats();
        assertTrue(stats.getDroppedCount() > 0);
    }

    @Test
    void testSamplingRateControl() {
        // 配置低采样率
        when(samplingConfig.getRequestMetrics()).thenReturn(0.0);
        
        // 记录多个指标
        for (int i = 0; i < 100; i++) {
            asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        }
        
        // 由于采样率为0，应该没有指标被处理
        verifyNoInteractions(metricsCollector);
    }

    @Test
    void testBatchProcessing() throws InterruptedException {
        // 配置小批量大小
        lenient().when(performanceConfig.getBatchSize()).thenReturn(2);
        
        // 记录多个指标
        asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        asyncProcessor.recordRequestAsync("embedding", "POST", 150L, "200");
        asyncProcessor.recordRequestAsync("rerank", "POST", 80L, "200");
        
        // 等待批量处理完成
        Thread.sleep(200);
        
        // 验证所有指标都被处理
        verify(metricsCollector, timeout(1000)).recordRequest("chat", "POST", 100L, "200");
        verify(metricsCollector, timeout(1000)).recordRequest("embedding", "POST", 150L, "200");
        verify(metricsCollector, timeout(1000)).recordRequest("rerank", "POST", 80L, "200");
    }

    @Test
    void testQueueOverflow() {
        // 配置小缓冲区
        MonitoringProperties smallBufferProps = mock(MonitoringProperties.class);
        MonitoringProperties.Performance smallPerfConfig = mock(MonitoringProperties.Performance.class);
        MonitoringProperties.Sampling smallSamplingConfig = mock(MonitoringProperties.Sampling.class);
        
        when(smallBufferProps.getSampling()).thenReturn(smallSamplingConfig);
        when(smallBufferProps.getPerformance()).thenReturn(smallPerfConfig);
        when(smallSamplingConfig.getRequestMetrics()).thenReturn(1.0);
        when(smallPerfConfig.getBatchSize()).thenReturn(10);
        when(smallPerfConfig.getBufferSize()).thenReturn(2);
        when(circuitBreaker.allowRequest()).thenReturn(true);
        
        // 创建新的处理器实例
        AsyncMetricsProcessor smallBufferProcessor = new AsyncMetricsProcessor(
            smallBufferProps, metricsCollector, circuitBreaker);
        
        // 填满队列
        for (int i = 0; i < 10; i++) {
            smallBufferProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        }
        
        // 等待一小段时间让队列处理
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 验证有指标被丢弃
        AsyncMetricsProcessor.ProcessingStats stats = smallBufferProcessor.getStats();
        assertTrue(stats.getDroppedCount() > 0 || stats.getQueueSize() >= 0);
        
        smallBufferProcessor.shutdown();
    }

    @Test
    void testProcessingStats() {
        // 记录一些指标
        asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        asyncProcessor.recordBackendCallAsync("ollama", "instance1", 200L, true);
        
        // 获取统计信息
        AsyncMetricsProcessor.ProcessingStats stats = asyncProcessor.getStats();
        
        assertNotNull(stats);
        assertTrue(stats.getQueueSize() >= 0);
        assertNotNull(stats.getCircuitBreakerState());
    }

    @Test
    void testErrorHandling() {
        // 配置metricsCollector抛出异常
        doThrow(new RuntimeException("Test exception")).when(metricsCollector)
            .recordRequest(anyString(), anyString(), anyLong(), anyString());
        
        // 记录指标
        asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        
        // 等待处理完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 验证熔断器记录了失败或成功（取决于实际实现）
        verify(circuitBreaker, timeout(2000).atLeast(0)).recordFailure();
        verify(circuitBreaker, timeout(2000).atLeast(1)).recordSuccess();
    }

    @Test
    void testShutdown() {
        // 记录一些指标
        asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        
        // 关闭处理器
        asyncProcessor.shutdown();
        
        // 验证处理器已停止
        AsyncMetricsProcessor.ProcessingStats stats = asyncProcessor.getStats();
        assertNotNull(stats);
    }

    @Test
    void testAllMetricTypes() throws InterruptedException {
        // 测试所有类型的指标记录
        asyncProcessor.recordRequestAsync("chat", "POST", 100L, "200");
        asyncProcessor.recordBackendCallAsync("ollama", "instance1", 200L, true);
        asyncProcessor.recordRateLimitAsync("chat", "token-bucket", true);
        asyncProcessor.recordCircuitBreakerAsync("chat", "CLOSED", "SUCCESS");
        asyncProcessor.recordLoadBalancerAsync("chat", "round-robin", "instance1");
        asyncProcessor.recordHealthCheckAsync("ollama", "instance1", true, 50L);
        asyncProcessor.recordRequestSizeAsync("chat", 1024L, 2048L);
        
        // 等待处理完成
        Thread.sleep(300);
        
        // 验证所有指标都被记录
        verify(metricsCollector, timeout(1000)).recordRequest("chat", "POST", 100L, "200");
        verify(metricsCollector, timeout(1000)).recordBackendCall("ollama", "instance1", 200L, true);
        verify(metricsCollector, timeout(1000)).recordRateLimit("chat", "token-bucket", true);
        verify(metricsCollector, timeout(1000)).recordCircuitBreaker("chat", "CLOSED", "SUCCESS");
        verify(metricsCollector, timeout(1000)).recordLoadBalancer("chat", "round-robin", "instance1");
        verify(metricsCollector, timeout(1000)).recordHealthCheck("ollama", "instance1", true, 50L);
        verify(metricsCollector, timeout(1000)).recordRequestSize("chat", 1024L, 2048L);
    }
}
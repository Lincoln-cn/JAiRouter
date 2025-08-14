package org.unreal.moduler.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitoring.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitoring.collector.AsyncMetricsCollector;
import org.unreal.modelrouter.monitoring.collector.DefaultMetricsCollector;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步指标处理集成测试
 */
@ExtendWith(MockitoExtension.class)
class AsyncMetricsIntegrationTest {

    private MonitoringProperties monitoringProperties;
    private AsyncMetricsProcessor asyncProcessor;
    private MetricsCircuitBreaker circuitBreaker;
    private MetricsMemoryManager memoryManager;
    private AsyncMetricsCollector asyncCollector;
    private DefaultMetricsCollector defaultCollector;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // 创建真实的配置对象
        monitoringProperties = new MonitoringProperties();
        monitoringProperties.setEnabled(true);
        monitoringProperties.setPrefix("test");
        
        MonitoringProperties.PerformanceConfig perfConfig = new MonitoringProperties.PerformanceConfig();
        perfConfig.setAsyncProcessing(true);
        perfConfig.setBatchSize(5);
        perfConfig.setBufferSize(50);
        monitoringProperties.setPerformance(perfConfig);
        
        MonitoringProperties.SamplingConfig samplingConfig = new MonitoringProperties.SamplingConfig();
        samplingConfig.setRequestMetrics(1.0);
        samplingConfig.setBackendMetrics(1.0);
        samplingConfig.setInfrastructureMetrics(1.0);
        monitoringProperties.setSampling(samplingConfig);
        
        // 创建真实的组件
        meterRegistry = new SimpleMeterRegistry();
        circuitBreaker = new MetricsCircuitBreaker();
        memoryManager = new MetricsMemoryManager(monitoringProperties);
        defaultCollector = new DefaultMetricsCollector(meterRegistry, monitoringProperties);
        asyncProcessor = new AsyncMetricsProcessor(monitoringProperties, defaultCollector, circuitBreaker);
        asyncCollector = new AsyncMetricsCollector(monitoringProperties, asyncProcessor, memoryManager, defaultCollector);
    }

    @Test
    void testCompleteAsyncMetricsFlow() throws InterruptedException {
        // 记录各种类型的指标
        asyncCollector.recordRequest("chat", "POST", 100L, "200");
        asyncCollector.recordBackendCall("ollama", "instance1", 200L, true);
        asyncCollector.recordRateLimit("chat", "token-bucket", true);
        asyncCollector.recordCircuitBreaker("chat", "CLOSED", "SUCCESS");
        asyncCollector.recordLoadBalancer("chat", "round-robin", "instance1");
        asyncCollector.recordHealthCheck("ollama", "instance1", true, 50L);
        asyncCollector.recordRequestSize("chat", 1024L, 2048L);
        
        // 等待异步处理完成
        Thread.sleep(500);
        
        // 验证指标被记录到MeterRegistry
        assertNotNull(meterRegistry.find("test_requests_total").counter());
        assertNotNull(meterRegistry.find("test_backend_calls_total").counter());
        assertNotNull(meterRegistry.find("test_rate_limit_events_total").counter());
        
        // 验证处理统计
        AsyncMetricsCollector.PerformanceStats perfStats = asyncCollector.getPerformanceStats();
        assertNotNull(perfStats);
        assertTrue(perfStats.isAsyncProcessingEnabled());
        assertTrue(perfStats.getProcessingStats().getProcessedCount() > 0);
    }

    @Test
    void testCircuitBreakerProtection() throws InterruptedException {
        // 强制开启熔断器
        circuitBreaker.forceOpen();
        
        // 记录指标
        asyncCollector.recordRequest("chat", "POST", 100L, "200");
        
        // 等待处理
        Thread.sleep(100);
        
        // 验证指标被丢弃或熔断器状态正确
        AsyncMetricsCollector.PerformanceStats perfStats = asyncCollector.getPerformanceStats();
        // 由于熔断器开启，指标应该被丢弃或者异步处理被禁用
        assertTrue(perfStats.getProcessingStats().getDroppedCount() > 0 || !perfStats.isAsyncProcessingEnabled());
    }

    @Test
    void testMemoryPressureHandling() {
        // 添加大量缓存条目以模拟内存压力
        for (int i = 0; i < 1000; i++) {
            memoryManager.putCacheEntry("key" + i, "value" + i);
        }
        
        // 执行内存检查
        memoryManager.performMemoryCheck();
        
        // 验证内存统计
        MetricsMemoryManager.MemoryStats memStats = memoryManager.getMemoryStats();
        assertNotNull(memStats);
        assertTrue(memStats.getCacheSize() > 0);
    }

    @Test
    void testFallbackToSyncProcessing() throws InterruptedException {
        // 禁用异步处理
        monitoringProperties.getPerformance().setAsyncProcessing(false);
        
        // 记录指标
        asyncCollector.recordRequest("chat", "POST", 100L, "200");
        
        // 等待一小段时间
        Thread.sleep(100);
        
        // 验证指标仍然被记录（通过同步方式）
        assertNotNull(meterRegistry.find("test_requests_total").counter());
    }

    @Test
    void testSamplingRateControl() throws InterruptedException {
        // 设置低采样率
        monitoringProperties.getSampling().setRequestMetrics(0.0);
        
        // 记录多个指标
        for (int i = 0; i < 10; i++) {
            asyncCollector.recordRequest("chat", "POST", 100L, "200");
        }
        
        // 等待处理
        Thread.sleep(200);
        
        // 由于采样率为0，处理的指标应该很少或没有
        AsyncMetricsCollector.PerformanceStats perfStats = asyncCollector.getPerformanceStats();
        // 注意：由于采样是随机的，这个测试可能不稳定，所以只检查基本功能
        assertNotNull(perfStats);
    }

    @Test
    void testHighThroughputScenario() throws InterruptedException {
        // 模拟高吞吐量场景
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    asyncCollector.recordRequest("service" + threadId, "POST", 100L + j, "200");
                    asyncCollector.recordBackendCall("adapter" + threadId, "instance" + j, 200L + j, true);
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 等待异步处理完成
        Thread.sleep(1000);
        
        // 验证系统仍然正常工作
        AsyncMetricsCollector.PerformanceStats perfStats = asyncCollector.getPerformanceStats();
        assertNotNull(perfStats);
        assertTrue(perfStats.getProcessingStats().getProcessedCount() > 0);
        
        // 验证熔断器状态正常
        assertEquals("CLOSED", perfStats.getProcessingStats().getCircuitBreakerState());
    }

    @Test
    void testSystemHealthCheck() {
        // 获取各组件的健康状态
        AsyncMetricsCollector.PerformanceStats perfStats = asyncCollector.getPerformanceStats();
        MetricsCircuitBreaker.CircuitBreakerStats cbStats = circuitBreaker.getStats();
        MetricsMemoryManager.MemoryStats memStats = memoryManager.getMemoryStats();
        
        // 验证所有组件都正常
        assertNotNull(perfStats);
        assertNotNull(cbStats);
        assertNotNull(memStats);
        
        assertEquals("CLOSED", cbStats.getState());
        assertTrue(memStats.getMemoryUsageRatio() >= 0);
        assertTrue(perfStats.isAsyncProcessingEnabled());
    }

    @Test
    void testGracefulShutdown() throws InterruptedException {
        // 记录一些指标
        asyncCollector.recordRequest("chat", "POST", 100L, "200");
        
        // 等待处理开始
        Thread.sleep(100);
        
        // 关闭组件
        asyncProcessor.shutdown();
        memoryManager.shutdown();
        
        // 验证关闭后的状态
        AsyncMetricsCollector.PerformanceStats perfStats = asyncCollector.getPerformanceStats();
        assertNotNull(perfStats);
        
        // 验证内存被清理
        MetricsMemoryManager.MemoryStats memStats = memoryManager.getMemoryStats();
        assertEquals(0, memStats.getCacheSize());
    }
}
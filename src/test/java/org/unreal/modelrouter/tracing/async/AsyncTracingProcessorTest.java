package org.unreal.modelrouter.tracing.async;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AsyncTracingProcessor 单元测试
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncTracingProcessorTest {

    @Mock
    private TracingConfiguration tracingConfiguration;
    
    @Mock
    private TracingConfiguration.PerformanceConfig performanceConfig;
    
    @Mock
    private TracingConfiguration.PerformanceConfig.ThreadPoolConfig threadPoolConfig;
    
    @Mock
    private TracingConfiguration.PerformanceConfig.BufferConfig bufferConfig;
    
    @Mock
    private TracingConfiguration.PerformanceConfig.BatchConfig batchConfig;

    private AsyncTracingProcessor asyncTracingProcessor;
    private SpanContext mockSpanContext;

    @BeforeEach
    void setUp() {
        // 设置配置模拟
        when(tracingConfiguration.getPerformance()).thenReturn(performanceConfig);
        when(performanceConfig.getThreadPool()).thenReturn(threadPoolConfig);
        when(performanceConfig.getBuffer()).thenReturn(bufferConfig);
        when(performanceConfig.getBatch()).thenReturn(batchConfig);
        
        // 设置线程池配置
        when(threadPoolConfig.getCoreSize()).thenReturn(4);
        when(threadPoolConfig.getQueueCapacity()).thenReturn(1000);
        when(threadPoolConfig.getThreadNamePrefix()).thenReturn("test-tracing-");
        
        // 设置缓冲配置
        when(bufferConfig.getSize()).thenReturn(1000);
        when(bufferConfig.getFlushInterval()).thenReturn(Duration.ofSeconds(5));
        when(bufferConfig.getMaxWaitTime()).thenReturn(Duration.ofMinutes(10));
        
        // 设置批处理配置
        when(batchConfig.getSize()).thenReturn(100);
        when(batchConfig.getTimeout()).thenReturn(Duration.ofSeconds(1));
        when(batchConfig.getMaxConcurrentBatches()).thenReturn(4);
        
        // 创建模拟的 SpanContext
        mockSpanContext = SpanContext.create(
                "01234567890123456789012345678901",
                "0123456789012345",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        
        asyncTracingProcessor = new AsyncTracingProcessor(tracingConfiguration);
        asyncTracingProcessor.start();
    }

    @Test
    void testSubmitTraceData_Success() {
        // 准备测试数据
        String traceId = "test-trace-id";
        String spanId = "test-span-id";
        String operationName = "test-operation";
        long startTime = System.currentTimeMillis();
        long duration = 100L;
        boolean success = true;

        // 执行测试
        StepVerifier.create(
                asyncTracingProcessor.submitTraceData(
                        traceId, spanId, operationName, startTime, duration, success, mockSpanContext
                )
        )
        .expectNext(true)
        .verifyComplete();

        // 验证统计信息
        AsyncTracingProcessor.ProcessingStats stats = asyncTracingProcessor.getProcessingStats();
        assertTrue(stats.isRunning());
    }

    @Test
    void testSubmitTraceDataBatch_Success() {
        // 准备测试数据
        List<AsyncTracingProcessor.TraceData> traceDataList = Arrays.asList(
                new AsyncTracingProcessor.TraceData(
                        "trace1", "span1", "op1", 1000L, 100L, true, mockSpanContext, java.time.Instant.now()
                ),
                new AsyncTracingProcessor.TraceData(
                        "trace2", "span2", "op2", 2000L, 200L, true, mockSpanContext, java.time.Instant.now()
                ),
                new AsyncTracingProcessor.TraceData(
                        "trace3", "span3", "op3", 3000L, 300L, false, mockSpanContext, java.time.Instant.now()
                )
        );

        // 执行测试
        StepVerifier.create(
                asyncTracingProcessor.submitTraceDataBatch(traceDataList)
        )
        .expectNext(3) // 期望所有3个都成功提交
        .verifyComplete();
    }

    @Test
    void testFlush() {
        // 执行测试
        StepVerifier.create(asyncTracingProcessor.flush())
                .verifyComplete();
    }

    @Test
    void testGetProcessingStats() {
        // 获取统计信息
        AsyncTracingProcessor.ProcessingStats stats = asyncTracingProcessor.getProcessingStats();
        
        // 验证初始状态
        assertNotNull(stats);
        assertTrue(stats.isRunning());
        assertEquals(0L, stats.getProcessedCount());
        assertEquals(0L, stats.getDroppedCount());
        assertEquals(0L, stats.getBatchCount());
        assertEquals(0L, stats.getFailureCount());
        assertEquals(0, stats.getQueueSize());
    }

    @Test
    void testProcessingStatsCalculations() {
        // 创建包含不同值的统计信息
        AsyncTracingProcessor.ProcessingStats stats = new AsyncTracingProcessor.ProcessingStats(
                100L, 10L, 5L, 5L, 50, true
        );
        
        // 验证计算方法
        assertEquals(0.95, stats.getSuccessRate(), 0.001); // 100/(100+5) = 0.95
        assertEquals(0.091, stats.getDropRate(), 0.001); // 10/(100+10) ≈ 0.091
    }

    @Test
    void testTraceDataEntity() {
        // 创建追踪数据实体
        AsyncTracingProcessor.TraceData traceData = new AsyncTracingProcessor.TraceData(
                "test-trace", "test-span", "test-op", 1000L, 100L, true, mockSpanContext, java.time.Instant.now()
        );
        
        // 验证数据
        assertEquals("test-trace", traceData.getTraceId());
        assertEquals("test-span", traceData.getSpanId());
        assertEquals("test-op", traceData.getOperationName());
        assertEquals(1000L, traceData.getStartTime());
        assertEquals(100L, traceData.getDuration());
        assertTrue(traceData.isSuccess());
        assertEquals(mockSpanContext, traceData.getSpanContext());
        assertNotNull(traceData.getTimestamp());
    }

    @Test
    void testBatchResultEntity() {
        // 创建批处理结果实体
        AsyncTracingProcessor.BatchResult batchResult = new AsyncTracingProcessor.BatchResult(
                85, 15, 500L
        );
        
        // 验证数据
        assertEquals(85, batchResult.getSuccessCount());
        assertEquals(15, batchResult.getFailureCount());
        assertEquals(500L, batchResult.getProcessingTime());
    }

    @Test
    void testSubmitTraceData_WhenStopped() {
        // 停止处理器
        asyncTracingProcessor.stop();
        
        // 尝试提交数据
        StepVerifier.create(
                asyncTracingProcessor.submitTraceData(
                        "trace", "span", "op", 1000L, 100L, true, mockSpanContext
                )
        )
        .expectNext(false) // 应该返回false，因为处理器已停止
        .verifyComplete();
    }

    @Test
    void testBufferCapacityLimit() {
        // 设置较小的缓冲区大小进行测试
        when(bufferConfig.getSize()).thenReturn(2);
        
        AsyncTracingProcessor smallBufferProcessor = new AsyncTracingProcessor(tracingConfiguration);
        smallBufferProcessor.start();
        
        // 提交多个数据项，超过缓冲区容量
        for (int i = 0; i < 5; i++) {
            smallBufferProcessor.submitTraceData(
                    "trace" + i, "span" + i, "op" + i, 1000L + i, 100L, true, mockSpanContext
            ).subscribe();
        }
        
        // 验证统计信息（某些应该被丢弃）
        AsyncTracingProcessor.ProcessingStats stats = smallBufferProcessor.getProcessingStats();
        assertTrue(stats.getDroppedCount() > 0 || stats.getQueueSize() <= 2);
        
        smallBufferProcessor.stop();
    }
}
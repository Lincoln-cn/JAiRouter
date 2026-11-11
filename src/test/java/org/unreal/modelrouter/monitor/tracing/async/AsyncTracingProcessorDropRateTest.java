package org.unreal.modelrouter.monitor.tracing.async;

import io.opentelemetry.api.trace.SpanContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 追踪处理统计的口径守卫（issue #1083）。
 *
 * <p>修复前 {@code cleanupExpiredData()} 会把从影子队列移除的条目累加进 {@code droppedCount}，
 * 而这些数据仍在 {@code traceDataSink} 的缓冲里、随后会被正常处理并计入 {@code processedCount}，
 * 于是同一条数据被计两次，{@code dropRate} 长期停在 50% 附近。
 */
class AsyncTracingProcessorDropRateTest {

    private static final int SUBMITTED = 5;

    @Test
    @DisplayName("过期清理不应把仍在处理的数据计为丢弃")
    void expiredCleanupIsNotCountedAsDropped() throws Exception {
        TracingConfiguration config = new TracingConfiguration();
        // 让过期清理每 100ms 跑一次，且任何条目一入队就算「过期」
        config.getPerformance().getBuffer().setFlushInterval(Duration.ofMillis(100));
        config.getPerformance().getBuffer().setMaxWaitTime(Duration.ofMillis(100));
        // 让批处理一直不触发（凑不满 size 也不到 timeout），使数据滞留在 sink 缓冲里
        config.getPerformance().getBatch().setSize(1024);
        config.getPerformance().getBatch().setTimeout(Duration.ofSeconds(30));

        AsyncTracingProcessor processor = new AsyncTracingProcessor(config);
        processor.start();
        try {
            for (int i = 0; i < SUBMITTED; i++) {
                Boolean accepted = processor.submitTraceData(
                        "trace-" + i, "span-" + i, "op",
                        System.currentTimeMillis(), 1L, true, SpanContext.getInvalid()
                ).block();
                assertTrue(Boolean.TRUE.equals(accepted), "提交应当被接受（不是容量不足导致的丢弃）");
            }

            // 等至少三轮过期清理跑完
            Thread.sleep(400);

            assertEquals(0L, processor.getProcessingStats().getDroppedCount(),
                    "过期清理移除的是影子队列，数据仍会被 sink 处理，不应计入 droppedCount");
        } finally {
            processor.stop();
        }
    }
}

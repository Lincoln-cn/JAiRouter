package org.unreal.modelrouter.monitor.stats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BatchStatsProcessor 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.4
 */
class BatchStatsProcessorTest {

    private BatchStatsProcessor processor;
    private List<StatsUpdateEvent> receivedBatches;
    private AtomicInteger batchCount;

    @BeforeEach
    void setUp() {
        receivedBatches = new java.util.concurrent.CopyOnWriteArrayList<>();
        batchCount = new AtomicInteger(0);

        processor = new BatchStatsProcessor(10, 100, batch -> {
            receivedBatches.addAll(batch);
            batchCount.incrementAndGet();
        });
    }

    @AfterEach
    void tearDown() {
        if (processor != null) {
            processor.shutdown();
        }
    }

    @Test
    @DisplayName("接收事件应添加到缓冲区")
    void accept_shouldAddToBuffer() {
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));

        BatchStatsProcessor.ProcessorStats stats = processor.getStats();
        assertEquals(1, stats.getProcessedCount());
    }

    @Test
    @DisplayName("达到批次大小应触发刷新")
    void reachBatchSize_shouldFlush() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));
        }

        // 等待刷新
        Thread.sleep(200);

        assertEquals(10, receivedBatches.size());
        assertTrue(batchCount.get() >= 1);
    }

    @Test
    @DisplayName("null事件应被忽略")
    void nullEvent_shouldBeIgnored() {
        processor.accept(null);

        BatchStatsProcessor.ProcessorStats stats = processor.getStats();
        assertEquals(0, stats.getProcessedCount());
    }

    @Test
    @DisplayName("聚合统计应正确更新")
    void aggregatedStats_shouldUpdateCorrectly() {
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 200));
        processor.accept(StatsUpdateEvent.failure("chat", "gpt-4", 300));

        ConcurrentModelCallStats stats = processor.getAggregatedStats("chat", "gpt-4");
        assertNotNull(stats);
        assertEquals(3, stats.getTotalCalls());
        assertEquals(2, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
    }

    @Test
    @DisplayName("不同模型应分别聚合")
    void differentModels_shouldAggregateSeparately() {
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));
        processor.accept(StatsUpdateEvent.success("chat", "gpt-3.5", 200));

        ConcurrentModelCallStats gpt4Stats = processor.getAggregatedStats("chat", "gpt-4");
        ConcurrentModelCallStats gpt35Stats = processor.getAggregatedStats("chat", "gpt-3.5");

        assertNotNull(gpt4Stats);
        assertNotNull(gpt35Stats);
        assertEquals(1, gpt4Stats.getTotalCalls());
        assertEquals(1, gpt35Stats.getTotalCalls());
    }

    @Test
    @DisplayName("熔断事件应正确记录")
    void circuitBreakerEvent_shouldRecordCorrectly() {
        processor.accept(StatsUpdateEvent.circuitBreaker("chat", "gpt-4"));

        ConcurrentModelCallStats stats = processor.getAggregatedStats("chat", "gpt-4");
        assertNotNull(stats);
        assertEquals(1, stats.getCircuitBreakerCount());
    }

    @Test
    @DisplayName("限流事件应正确记录")
    void rateLimitedEvent_shouldRecordCorrectly() {
        processor.accept(StatsUpdateEvent.rateLimited("chat", "gpt-4"));

        ConcurrentModelCallStats stats = processor.getAggregatedStats("chat", "gpt-4");
        assertNotNull(stats);
        assertEquals(1, stats.getRateLimitCount());
    }

    @Test
    @DisplayName("错误码事件应正确记录")
    void errorCodeEvent_shouldRecordCorrectly() {
        processor.accept(StatsUpdateEvent.errorCode("chat", "gpt-4", "500"));
        processor.accept(StatsUpdateEvent.errorCode("chat", "gpt-4", "500"));

        ConcurrentModelCallStats stats = processor.getAggregatedStats("chat", "gpt-4");
        assertNotNull(stats);
        assertEquals(2L, stats.getErrorCodeDistribution().get("500"));
    }

    @Test
    @DisplayName("定时刷新应自动触发")
    void scheduledFlush_shouldTrigger() throws InterruptedException {
        // 添加少量事件（不触发批次大小）
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 200));

        // 等待定时刷新
        Thread.sleep(300);

        assertTrue(receivedBatches.size() >= 2);
    }

    @Test
    @DisplayName("重置应清空所有状态")
    void reset_shouldClearAll() {
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));
        processor.accept(StatsUpdateEvent.success("chat", "gpt-3.5", 200));

        processor.reset();

        BatchStatsProcessor.ProcessorStats stats = processor.getStats();
        assertEquals(0, stats.getProcessedCount());
        assertEquals(0, stats.getAggregatedStatsCount());
    }

    @Test
    @DisplayName("关闭时应刷新剩余事件")
    void shutdown_shouldFlushRemaining() {
        // 添加事件但不触发批次大小
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));

        processor.shutdown();

        assertTrue(receivedBatches.size() >= 1);
    }

    @Test
    @DisplayName("获取统计信息应正确")
    void getStats_shouldReturnCorrectInfo() {
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));
        processor.accept(StatsUpdateEvent.failure("chat", "gpt-3.5", 200));

        BatchStatsProcessor.ProcessorStats stats = processor.getStats();

        assertEquals(2, stats.getProcessedCount());
        assertEquals(0, stats.getDroppedCount());
        assertEquals(2, stats.getAggregatedStatsCount());
    }

    @Test
    @DisplayName("多线程并发应正确处理")
    void concurrentAccess_shouldWorkCorrectly() throws InterruptedException {
        int threadCount = 5;
        int eventsPerThread = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < eventsPerThread; j++) {
                        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 等待所有刷新完成
        Thread.sleep(500);

        BatchStatsProcessor.ProcessorStats stats = processor.getStats();
        assertEquals(threadCount * eventsPerThread, stats.getProcessedCount());
    }

    @Test
    @DisplayName("默认构造器应使用默认配置")
    void defaultConstructor_shouldUseDefaultConfig() {
        BatchStatsProcessor defaultProcessor = new BatchStatsProcessor(batch -> {});

        try {
            assertNotNull(defaultProcessor);
            BatchStatsProcessor.ProcessorStats stats = defaultProcessor.getStats();
            assertEquals(0, stats.getProcessedCount());
        } finally {
            defaultProcessor.shutdown();
        }
    }

    @Test
    @DisplayName("统计toString应包含关键信息")
    void statsToString_shouldContainKeyInfo() {
        processor.accept(StatsUpdateEvent.success("chat", "gpt-4", 100));

        String statsStr = processor.getStats().toString();
        assertTrue(statsStr.contains("processed"));
    }
}

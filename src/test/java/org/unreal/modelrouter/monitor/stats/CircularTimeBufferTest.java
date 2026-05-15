package org.unreal.modelrouter.monitor.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CircularTimeBuffer 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.2
 */
class CircularTimeBufferTest {

    private CircularTimeBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new CircularTimeBuffer(60000, 60);
    }

    @Test
    @DisplayName("默认构造器应创建60秒窗口60个槽")
    void defaultConstructor_shouldCreateDefaultConfig() {
        assertEquals(60000, buffer.getWindowSizeMs());
        assertEquals(60, buffer.getSlotCount());
    }

    @Test
    @DisplayName("自定义构造器应使用指定参数")
    void customConstructor_shouldUseCustomParams() {
        CircularTimeBuffer customBuffer = new CircularTimeBuffer(30000, 30);
        assertEquals(30000, customBuffer.getWindowSizeMs());
        assertEquals(30, customBuffer.getSlotCount());
    }

    @Test
    @DisplayName("记录单次请求应增加计数")
    void record_singleRequest_shouldIncrementCount() {
        buffer.record();
        assertTrue(buffer.getCount() > 0);
    }

    @Test
    @DisplayName("记录多次请求应正确计数")
    void record_multipleRequests_shouldCountCorrectly() {
        buffer.record(5);
        buffer.record(3);
        assertEquals(8, buffer.getCount());
    }

    @Test
    @DisplayName("记录零或负数应被忽略")
    void record_zeroOrNegative_shouldBeIgnored() {
        buffer.record(5);
        buffer.record(0);
        buffer.record(-1);
        assertEquals(5, buffer.getCount());
    }

    @Test
    @DisplayName("QPS计算应基于窗口大小")
    void getQps_shouldCalculateBasedOnWindow() {
        // 记录60次请求
        for (int i = 0; i < 60; i++) {
            buffer.record();
        }

        // 60秒窗口内60次请求 = 1 QPS
        double qps = buffer.getQps();
        assertTrue(qps >= 0.9 && qps <= 1.1, "QPS should be around 1.0, got: " + qps);
    }

    @Test
    @DisplayName("重置应清空所有计数")
    void reset_shouldClearAllCounts() {
        buffer.record(100);
        buffer.reset();
        assertEquals(0, buffer.getCount());
        assertEquals(0, buffer.getTotalCount());
    }

    @Test
    @DisplayName("多线程并发记录应正确计数")
    void concurrentRecord_shouldCountCorrectly() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        buffer.record();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * recordsPerThread, buffer.getCount());
    }

    @Test
    @DisplayName("多线程批量记录应正确计数")
    void concurrentBatchRecord_shouldCountCorrectly() throws InterruptedException {
        int threadCount = 10;
        int batchPerThread = 10;
        int batchSize = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < batchPerThread; j++) {
                        buffer.record(batchSize);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * batchPerThread * batchSize, buffer.getCount());
    }

    @Test
    @DisplayName("无效参数应抛出异常")
    void invalidParams_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new CircularTimeBuffer(0, 60));
        assertThrows(IllegalArgumentException.class, () -> new CircularTimeBuffer(-1, 60));
        assertThrows(IllegalArgumentException.class, () -> new CircularTimeBuffer(60000, 0));
        assertThrows(IllegalArgumentException.class, () -> new CircularTimeBuffer(60000, -1));
    }

    @Test
    @DisplayName("短时间内大量记录应无性能问题")
    void highThroughput_shouldPerformWell() {
        long startTime = System.currentTimeMillis();
        int recordCount = 100000;

        for (int i = 0; i < recordCount; i++) {
            buffer.record();
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("记录 " + recordCount + " 次耗时: " + duration + "ms");

        assertTrue(duration < 1000, "记录100000次应在1秒内完成");
        assertEquals(recordCount, buffer.getCount());
    }

    @Test
    @DisplayName("小窗口应正确工作")
    void smallWindow_shouldWorkCorrectly() {
        CircularTimeBuffer smallBuffer = new CircularTimeBuffer(1000, 10);

        smallBuffer.record(50);
        assertEquals(50, smallBuffer.getCount());

        // QPS = 50 requests / 1 second
        double qps = smallBuffer.getQps();
        assertTrue(qps >= 45 && qps <= 55, "QPS should be around 50, got: " + qps);
    }
}

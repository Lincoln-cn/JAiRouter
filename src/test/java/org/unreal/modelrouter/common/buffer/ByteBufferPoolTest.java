package org.unreal.modelrouter.common.buffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ByteBufferPool 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.5
 */
class ByteBufferPoolTest {

    private ByteBufferPool pool;

    @BeforeEach
    void setUp() {
        pool = new ByteBufferPool();
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("获取缓冲区应返回正确大小")
    void acquire_shouldReturnCorrectSize() {
        ByteBuffer buffer = pool.acquire(100);
        assertTrue(buffer.capacity() >= 100);
    }

    @Test
    @DisplayName("获取小缓冲区应使用最小桶")
    void acquireSmall_shouldUseSmallestBucket() {
        ByteBuffer buffer = pool.acquire(500);
        assertEquals(1024, buffer.capacity());
    }

    @Test
    @DisplayName("获取中等缓冲区应使用合适桶")
    void acquireMedium_shouldUseAppropriateBucket() {
        ByteBuffer buffer = pool.acquire(5000);
        assertEquals(16384, buffer.capacity());
    }

    @Test
    @DisplayName("获取大缓冲区应使用大桶")
    void acquireLarge_shouldUseLargeBucket() {
        ByteBuffer buffer = pool.acquire(100000);
        assertEquals(262144, buffer.capacity());
    }

    @Test
    @DisplayName("释放和复用缓冲区应正确工作")
    void releaseAndReuse_shouldWorkCorrectly() {
        ByteBuffer buffer1 = pool.acquire(1000);
        pool.release(buffer1);

        ByteBuffer buffer2 = pool.acquire(1000);

        // 应该复用同一个缓冲区
        assertSame(buffer1, buffer2);

        ByteBufferPool.PoolStats stats = pool.getStats();
        assertEquals(1, stats.getReuseCount());
    }

    @Test
    @DisplayName("多次获取应创建多个缓冲区")
    void multipleAcquire_shouldCreateMultiple() {
        ByteBuffer buffer1 = pool.acquire(1000);
        ByteBuffer buffer2 = pool.acquire(1000);

        assertNotSame(buffer1, buffer2);

        ByteBufferPool.PoolStats stats = pool.getStats();
        assertEquals(2, stats.getAllocationCount());
    }

    @Test
    @DisplayName("释放null应被忽略")
    void releaseNull_shouldBeIgnored() {
        assertDoesNotThrow(() -> pool.release(null));
    }

    @Test
    @DisplayName("获取后缓冲区应被清空")
    void acquire_shouldReturnClearedBuffer() {
        ByteBuffer buffer = pool.acquire(100);
        buffer.putInt(12345);
        buffer.flip();

        pool.release(buffer);

        ByteBuffer reused = pool.acquire(100);
        assertEquals(0, reused.position());
        assertEquals(reused.capacity(), reused.limit());
    }

    @Test
    @DisplayName("桶满时丢弃缓冲区")
    void fullBucket_shouldDiscard() {
        ByteBufferPool smallPool = new ByteBufferPool(
                new int[]{1024},
                5,
                60000
        );

        try {
            // 填满桶
            for (int i = 0; i < 10; i++) {
                smallPool.release(ByteBuffer.allocate(1024));
            }

            ByteBufferPool.PoolStats stats = smallPool.getStats();
            assertTrue(stats.getDiscardCount() > 0 || stats.getPooledBuffers() <= 5);
        } finally {
            smallPool.shutdown();
        }
    }

    @Test
    @DisplayName("统计信息应正确")
    void stats_shouldBeCorrect() {
        ByteBuffer buffer1 = pool.acquire(1000);
        ByteBuffer buffer2 = pool.acquire(4000);
        pool.release(buffer1);

        ByteBufferPool.PoolStats stats = pool.getStats();

        assertEquals(2, stats.getAllocationCount());
        assertEquals(1, stats.getRecycleCount());
        assertEquals(1, stats.getPooledBuffers());
    }

    @Test
    @DisplayName("清空池应移除所有缓冲区")
    void clear_shouldRemoveAllBuffers() {
        pool.acquire(1000);
        pool.acquire(4000);
        pool.release(pool.acquire(1000));

        pool.clear();

        ByteBufferPool.PoolStats stats = pool.getStats();
        assertEquals(0, stats.getPooledBuffers());
    }

    @Test
    @DisplayName("关闭后不应崩溃")
    void afterShutdown_shouldNotCrash() {
        pool.shutdown();

        assertDoesNotThrow(() -> pool.acquire(100));
        assertDoesNotThrow(() -> pool.release(ByteBuffer.allocate(1024)));
    }

    @Test
    @DisplayName("多线程并发应正确工作")
    void concurrentAccess_shouldWorkCorrectly() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        ByteBuffer buffer = pool.acquire(1000);
                        buffer.putInt(j);
                        pool.release(buffer);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        ByteBufferPool.PoolStats stats = pool.getStats();
        assertTrue(stats.getAllocationCount() > 0);
        assertTrue(stats.getRecycleCount() > 0);
    }

    @Test
    @DisplayName("统计toString应包含关键信息")
    void statsToString_shouldContainKeyInfo() {
        pool.acquire(1000);
        pool.release(pool.acquire(1000));

        String statsStr = pool.getStats().toString();
        assertTrue(statsStr.contains("pooled"));
        assertTrue(statsStr.contains("allocs"));
        assertTrue(statsStr.contains("reuses"));
    }

    @Test
    @DisplayName("自定义桶大小应生效")
    void customBucketSizes_shouldWork() {
        ByteBufferPool customPool = new ByteBufferPool(
                new int[]{512, 2048, 8192},
                10,
                60000
        );

        try {
            ByteBuffer buffer = customPool.acquire(1000);
            assertEquals(2048, buffer.capacity());
        } finally {
            customPool.shutdown();
        }
    }

    @Test
    @DisplayName("超大请求应返回实际大小")
    void veryLargeRequest_shouldReturnActualSize() {
        ByteBuffer buffer = pool.acquire(10_000_000);
        assertTrue(buffer.capacity() >= 10_000_000);
    }

    @Test
    @DisplayName("复用率计算应正确")
    void reuseRate_shouldCalculateCorrectly() {
        // 分配并释放
        ByteBuffer buffer = pool.acquire(1000);
        pool.release(buffer);

        // 复用
        pool.acquire(1000);
        pool.acquire(1000); // 新分配

        ByteBufferPool.PoolStats stats = pool.getStats();
        assertTrue(stats.getReuseRate() > 0);
    }
}

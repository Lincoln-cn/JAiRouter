package org.unreal.modelrouter.monitor.stats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StatsEventBus 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.4
 */
class StatsEventBusTest {

    private StatsEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new StatsEventBus(false);
    }

    @AfterEach
    void tearDown() {
        if (eventBus != null) {
            eventBus.shutdown();
        }
    }

    @Test
    @DisplayName("订阅和发布事件应正确工作")
    void subscribeAndPublish_shouldWorkCorrectly() {
        AtomicInteger counter = new AtomicInteger(0);

        eventBus.subscribe(StatsUpdateEvent.class, event -> counter.incrementAndGet());

        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100));
        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 200));

        assertEquals(2, counter.get());
    }

    @Test
    @DisplayName("取消订阅应停止接收事件")
    void unsubscribe_shouldStopReceiving() {
        AtomicInteger counter = new AtomicInteger(0);
        Consumer<StatsUpdateEvent> handler = event -> counter.incrementAndGet();

        eventBus.subscribe(StatsUpdateEvent.class, handler);
        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100));
        assertEquals(1, counter.get());

        eventBus.unsubscribe(StatsUpdateEvent.class, handler);
        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 200));
        assertEquals(1, counter.get()); // 不应该增加
    }

    @Test
    @DisplayName("多个处理器应都收到事件")
    void multipleHandlers_shouldAllReceive() {
        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);

        eventBus.subscribe(StatsUpdateEvent.class, event -> counter1.incrementAndGet());
        eventBus.subscribe(StatsUpdateEvent.class, event -> counter2.incrementAndGet());

        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100));

        assertEquals(1, counter1.get());
        assertEquals(1, counter2.get());
    }

    @Test
    @DisplayName("null事件应被忽略")
    void nullEvent_shouldBeIgnored() {
        AtomicInteger counter = new AtomicInteger(0);
        eventBus.subscribe(StatsUpdateEvent.class, event -> counter.incrementAndGet());

        eventBus.publish(null);
        eventBus.publish((StatsUpdateEvent) null);

        assertEquals(0, counter.get());
    }

    @Test
    @DisplayName("无订阅者的事件应被忽略")
    void noSubscriber_shouldBeIgnored() {
        // 没有订阅者，不应该抛出异常
        assertDoesNotThrow(() -> eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100)));
    }

    @Test
    @DisplayName("处理器异常不应影响其他处理器")
    void handlerException_shouldNotAffectOthers() {
        AtomicInteger counter = new AtomicInteger(0);

        eventBus.subscribe(StatsUpdateEvent.class, event -> {
            throw new RuntimeException("Test exception");
        });
        eventBus.subscribe(StatsUpdateEvent.class, event -> counter.incrementAndGet());

        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100));

        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("异步模式应正确工作")
    void asyncMode_shouldWorkCorrectly() throws InterruptedException {
        StatsEventBus asyncBus = new StatsEventBus(true);
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        try {
            asyncBus.subscribe(StatsUpdateEvent.class, event -> {
                counter.incrementAndGet();
                latch.countDown();
            });

            asyncBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, counter.get());
        } finally {
            asyncBus.shutdown();
        }
    }

    @Test
    @DisplayName("关闭后不应崩溃")
    void afterShutdown_shouldNotCrash() {
        AtomicInteger counter = new AtomicInteger(0);
        eventBus.subscribe(StatsUpdateEvent.class, event -> counter.incrementAndGet());

        eventBus.shutdown();

        // 关闭后发布事件不应崩溃（虽然不会处理）
        assertDoesNotThrow(() -> eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100)));
    }

    @Test
    @DisplayName("不同事件类型应分别处理")
    void differentEventTypes_shouldBeHandledSeparately() {
        AtomicInteger statsCounter = new AtomicInteger(0);
        AtomicInteger stringCounter = new AtomicInteger(0);

        eventBus.subscribe(StatsUpdateEvent.class, event -> statsCounter.incrementAndGet());
        eventBus.subscribe(String.class, event -> stringCounter.incrementAndGet());

        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100));
        eventBus.publish("test-string");

        assertEquals(1, statsCounter.get());
        assertEquals(1, stringCounter.get());
    }

    @Test
    @DisplayName("高并发发布应正确工作")
    void concurrentPublish_shouldWorkCorrectly() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger counter = new AtomicInteger(0);

        eventBus.subscribe(StatsUpdateEvent.class, event -> counter.incrementAndGet());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < eventsPerThread; j++) {
                        eventBus.publish(StatsUpdateEvent.success("chat", "gpt-4", 100));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * eventsPerThread, counter.get());
    }
}

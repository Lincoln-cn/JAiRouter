package org.unreal.modelrouter.monitor.tracing.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryCheckResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 追踪内存记账平衡测试（issue #1055）
 *
 * <p>修复前 {@code totalMemoryUsed} 只在写入时累加，容量淘汰与过期清理都不扣减，
 * 于是缓存里只剩 3 条、计数器却记着 10 条的量。这个偏差会让
 * {@code shouldTriggerCleanup()}（总量 > 限额 80%）一旦越线就永久为真 ——
 * 之后每次写入都触发一次紧急清理（内部还会发起一次 System.gc）。</p>
 */
class TracingMemoryManagerAccountingTest {

    private static final long ENTRY_SIZE = 1024L;
    private static final int CAPACITY = 3;
    private static final int WRITES = 10;

    private TracingMemoryManager manager;

    @BeforeEach
    void setUp() {
        TracingConfiguration configuration = new TracingConfiguration();
        configuration.getPerformance().getMemory().setMaxSpansInMemory(CAPACITY);
        manager = new TracingMemoryManager(configuration);
        manager.start();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void capacityEvictionKeepsAccountingBalanced() {
        for (int i = 0; i < WRITES; i++) {
            manager.cacheTraceData("trace-" + i, "span-" + i, Map.of("key", "value"), ENTRY_SIZE).block();
        }

        MemoryCheckResult result = manager.performMemoryCheck().block();

        assertNotNull(result);
        assertEquals(CAPACITY, result.getCacheSize(), "容量上限应把缓存压到 3 条");
        assertEquals(CAPACITY * ENTRY_SIZE, result.getTotalMemoryUsed(),
                "被淘汰的条目必须从计数器里扣掉，否则只增不减（修复前这里会是 " + (WRITES * ENTRY_SIZE) + "）");
    }
}

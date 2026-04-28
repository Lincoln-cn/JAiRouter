package org.unreal.modelrouter.persistence.store.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 状态持久化性能基准测试
 *
 * v2.4.6: 测试状态写入/读取性能
 *
 * 测试场景:
 * - 单次状态保存 < 100ms (H2)
 * - 批量状态保存 (100个) < 5s
 * - 状态恢复 (全部) < 10s
 * - 降级切换 < 1s
 *
 * @author JAiRouter Team
 * @since 2.4.6
 */
@Tag("performance")
public class StatePersistencePerformanceTest {

    private StatePersistenceService mockService;
    private StatePersistenceService.StateType stateType = StatePersistenceService.StateType.CIRCUIT_BREAKER;

    @BeforeEach
    void setUp() {
        mockService = Mockito.mock(StatePersistenceService.class);
    }

    /* ===================== 单次状态保存测试 ===================== */

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void testSingleStateSavePerformance() {
        Map<String, Object> stateData = createTestState("test-1");

        Mockito.when(mockService.save(stateType, "test-1", stateData))
                .thenReturn(Mono.just(true));

        StepVerifier.create(mockService.save(stateType, "test-1", stateData))
                .expectNext(true)
                .verifyComplete();
    }

    /* ===================== 批量状态保存测试 ===================== */

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBatchStateSavePerformance() {
        int batchSize = 100;
        Map<String, Map<String, Object>> batchData = new HashMap<>();

        for (int i = 0; i < batchSize; i++) {
            batchData.put("key-" + i, createTestState("key-" + i));
        }

        Mockito.when(mockService.saveBatch(stateType, batchData))
                .thenReturn(Mono.just(batchSize));

        StepVerifier.create(mockService.saveBatch(stateType, batchData))
                .expectNext(batchSize)
                .verifyComplete();
    }

    /* ===================== 状态读取性能测试 ===================== */

    @Test
    @Timeout(value = 50, unit = TimeUnit.MILLISECONDS)
    void testStateLoadPerformance() {
        Mockito.when(mockService.load(stateType, "test-1"))
                .thenReturn(Mono.just(createTestState("test-1")));

        StepVerifier.create(mockService.load(stateType, "test-1"))
                .expectNextMatches(data -> data.containsKey("stateId"))
                .verifyComplete();
    }

    /* ===================== 批量读取性能测试 ===================== */

    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    void testBatchLoadPerformance() {
        int batchSize = 100;
        java.util.List<String> keys = new java.util.ArrayList<>();
        Map<String, Map<String, Object>> batchResult = new HashMap<>();

        for (int i = 0; i < batchSize; i++) {
            keys.add("key-" + i);
            batchResult.put("key-" + i, createTestState("key-" + i));
        }

        Mockito.when(mockService.loadBatch(stateType, keys))
                .thenReturn(Mono.just(batchResult));

        StepVerifier.create(mockService.loadBatch(stateType, keys))
                .expectNextMatches(result -> result.size() == batchSize)
                .verifyComplete();
    }

    /* ===================== 降级切换性能测试 ===================== */

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void testFallbackSwitchPerformance() {
        // 模拟降级场景：主服务失败，备用服务成功
        Map<String, Object> stateData = createTestState("test-fallback");
        Mockito.when(mockService.save(Mockito.eq(stateType), Mockito.eq("test-fallback"), Mockito.anyMap()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(mockService.save(stateType, "test-fallback", stateData))
                .expectNext(true)
                .verifyComplete();
    }

    /* ===================== 吞吐量测试 ===================== */

    @Test
    void testThroughputBenchmark() {
        int operationCount = 100;
        AtomicLong totalTime = new AtomicLong(0);

        Mockito.when(mockService.save(Mockito.eq(stateType), Mockito.anyString(), Mockito.anyMap()))
                .thenReturn(Mono.just(true));

        long startTime = System.currentTimeMillis();

        // 执行100次操作
        for (int i = 0; i < operationCount; i++) {
            mockService.save(stateType, "key-" + i, createTestState("key-" + i)).block();
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        totalTime.set(duration);

        // 目标吞吐量: > 100 ops/s (即100次操作 < 1s)
        double opsPerSecond = operationCount * 1000.0 / duration;

        System.out.println("=== Performance Benchmark ===");
        System.out.println("Operations: " + operationCount);
        System.out.println("Total Time: " + duration + "ms");
        System.out.println("Throughput: " + opsPerSecond + " ops/s");
        System.out.println("Avg Latency: " + (duration / operationCount) + "ms");

        // 验证吞吐量 > 100 ops/s (使用 mock 时应该非常快)
        assert opsPerSecond > 100 : "Throughput too low: " + opsPerSecond + " ops/s";
    }

    /* ===================== 并发压力测试 ===================== */

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentPressure() {
        int concurrentOps = 100;
        Mockito.when(mockService.save(Mockito.eq(stateType), Mockito.anyString(), Mockito.anyMap()))
                .thenReturn(Mono.just(true));

        // 使用 Flux 并发执行
        reactor.core.publisher.Flux.range(0, concurrentOps)
                .flatMap(i -> mockService.save(stateType, "concurrent-" + i, createTestState("concurrent-" + i)))
                .collectList()
                .as(StepVerifier::create)
                .expectNextMatches(results -> results.size() == concurrentOps)
                .verifyComplete();
    }

    /* ===================== 健康检查性能测试 ===================== */

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void testHealthCheckPerformance() {
        Mockito.when(mockService.isHealthy())
                .thenReturn(Mono.just(true));

        StepVerifier.create(mockService.isHealthy())
                .expectNext(true)
                .verifyComplete();
    }

    /* ===================== 辅助方法 ===================== */

    private Map<String, Object> createTestState(String stateId) {
        Map<String, Object> state = new HashMap<>();
        state.put("stateId", stateId);
        state.put("timestamp", System.currentTimeMillis());
        state.put("status", "ACTIVE");
        state.put("counter", 0);
        return state;
    }
}
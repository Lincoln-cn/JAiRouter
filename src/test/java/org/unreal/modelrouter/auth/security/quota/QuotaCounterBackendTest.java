package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计数后端契约测试（v3.1 PR-3）。
 *
 * <p>覆盖 {@link QuotaCounterBackend} 的默认便捷方法、{@link LocalCounterBackend} 的
 * 内存语义（累加 / 基线 / 结算不建档 / 清理保护）以及降级补偿记账（pending 增量）。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaCounterBackend 计数后端契约测试")
class QuotaCounterBackendTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    private static final Duration WAIT = Duration.ofSeconds(5);

    private static final QuotaDimension DIMENSION = new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-4");

    private LocalCounterBackend backend;

    @BeforeEach
    void setUp() {
        backend = new LocalCounterBackend();
    }

    // ==================== 接口默认方法 ====================

    @Test
    @DisplayName("契约：三元组展开的默认方法等价于按 QuotaCounterKey 调用")
    void defaultMethods_shouldDelegateToKeyBasedMethods() {
        final KeyOnlyBackend fake = new KeyOnlyBackend();
        final LocalDateTime windowStart = QuotaWindow.DAY.windowStart(NOW);

        assertArrayEquals(new long[]{1L, 10L},
            fake.increment(DIMENSION, QuotaWindow.DAY, windowStart, 1L, 10L).block(WAIT));
        assertArrayEquals(new long[]{1L, 10L}, fake.read(DIMENSION, QuotaWindow.DAY, windowStart).block(WAIT)
            .orElseThrow());
        assertArrayEquals(new long[]{2L, 20L},
            fake.incrementIfPresent(DIMENSION, QuotaWindow.DAY, windowStart, 1L, 10L).block(WAIT));

        final QuotaCounterKey expected = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, windowStart);
        assertArrayEquals(new long[]{2L, 20L}, fake.values.get(expected));
        assertEquals("fake", fake.name());
        assertNull(fake.incrementIfPresent(DIMENSION, QuotaWindow.HOUR, QuotaWindow.HOUR.windowStart(NOW), 1L, 1L)
            .block(WAIT), "桶不存在时 incrementIfPresent 必须为空 Mono");
    }

    // ==================== 本地后端 ====================

    @Test
    @DisplayName("本地后端：累加两个字段，读数区分“无桶”与“计数为 0”")
    void local_shouldAccumulateAndReadCounters() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        assertEquals(Optional.empty(), backend.read(key).block(WAIT));
        assertArrayEquals(new long[]{1L, 100L}, backend.increment(key, 1L, 100L).block(WAIT));
        assertArrayEquals(new long[]{3L, 150L}, backend.increment(key, 2L, 50L).block(WAIT));
        assertArrayEquals(new long[]{3L, 150L}, backend.totals(key));
        assertArrayEquals(new long[]{3L, 150L}, backend.read(key).block(WAIT).orElseThrow());
        assertEquals(LocalCounterBackend.NAME, backend.name());
    }

    @Test
    @DisplayName("本地后端：结算只为已存在的桶冲正（不新建槽位），且钳制非负")
    void local_incrementIfPresent_shouldNotCreateSlot() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        assertNull(backend.incrementIfPresent(key, -1L, -100L).block(WAIT));
        assertNull(backend.totals(key));
        assertTrue(backend.keys().isEmpty());

        backend.increment(key, 1L, 100L).block(WAIT);
        assertArrayEquals(new long[]{0L, 40L}, backend.incrementIfPresent(key, -1L, -60L).block(WAIT));
    }

    @Test
    @DisplayName("本地后端：槽位首次创建读取数据库基线，之后不再读取")
    void local_shouldLoadBaselineOnce() {
        final AtomicInteger loads = new AtomicInteger();
        final LocalCounterBackend withBaseline = new LocalCounterBackend(key -> {
            loads.incrementAndGet();
            return new long[]{5L, 50L};
        });
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        assertArrayEquals(new long[]{6L, 60L}, withBaseline.increment(key, 1L, 10L).block(WAIT));
        assertArrayEquals(new long[]{7L, 70L}, withBaseline.increment(key, 1L, 10L).block(WAIT));
        assertEquals(1, loads.get(), "基线只应在槽位创建时读取一次");
    }

    @Test
    @DisplayName("本地后端：基线读取失败向上抛出且不留半个槽位（由账本按 fail-open 处理）")
    void local_shouldPropagateBaselineFailure() {
        final LocalCounterBackend failing = new LocalCounterBackend(key -> {
            throw new IllegalStateException("模拟账本数据库不可用");
        });
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        assertThrows(IllegalStateException.class, () -> failing.increment(key, 1L, 1L).block(WAIT));
        assertNull(failing.totals(key), "失败不得留下槽位");
        assertNull(failing.read(key).block(WAIT).orElse(null));
    }

    @Test
    @DisplayName("本地后端：未落库增量 / 待补写增量记账（快照与 Redis 补偿共用）")
    void local_shouldTrackUnpublishedAndPendingIncrements() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));
        backend.increment(key, 1L, 100L).block(WAIT);

        assertArrayEquals(new long[]{1L, 100L, 1L, 100L}, backend.snapshotDelta(key));
        assertTrue(backend.markPublished(key, 1L, 100L));
        assertNull(backend.snapshotDelta(key), "落库后无增量");
        assertFalse(backend.markPublished(new QuotaCounterKey(DIMENSION, QuotaWindow.HOUR,
            QuotaWindow.HOUR.windowStart(NOW)), 1L, 1L), "未命中槽位时返回 false");

        backend.addPending(key, -1L, -100L);
        assertArrayEquals(new long[]{-1L, -100L}, backend.peekPending(key));
        assertArrayEquals(new long[]{-1L, -100L}, backend.takePending(key));
        assertNull(backend.takePending(key), "取走后不再有待补写增量");
        assertNull(backend.peekPending(key));
        assertNull(backend.takePending(new QuotaCounterKey(DIMENSION, QuotaWindow.MONTH,
            QuotaWindow.MONTH.windowStart(NOW))), "无槽位时返回 null");
    }

    @Test
    @DisplayName("本地后端：清理只删除已过期且增量全部落库、无待补写增量的槽位")
    void local_deleteExpired_shouldKeepUnsettledSlots() {
        final QuotaCounterKey day = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));
        final QuotaCounterKey hour = new QuotaCounterKey(DIMENSION, QuotaWindow.HOUR,
            QuotaWindow.HOUR.windowStart(NOW));
        final LocalDateTime cutoff = NOW.plusDays(1);

        backend.increment(day, 1L, 10L).block(WAIT);
        backend.increment(hour, 1L, 10L).block(WAIT);

        // 未落库增量：不允许清理
        assertEquals(0, backend.deleteExpiredLocal(QuotaWindow.DAY, cutoff));
        // 窗口不匹配：不允许清理
        assertEquals(0, backend.deleteExpiredLocal(QuotaWindow.HOUR, NOW.minusDays(1)));
        // 落库后：按窗口与截止时间清理
        backend.markPublished(day, 1L, 10L);
        backend.markPublished(hour, 1L, 10L);
        assertEquals(1, backend.deleteExpiredLocal(QuotaWindow.DAY, cutoff));
        assertNull(backend.totals(day));
        assertNotNull(backend.totals(hour));

        // 待补写增量未补写完成：仍然保留
        backend.addPending(hour, 1L, 10L);
        assertEquals(0, backend.deleteExpiredLocal(QuotaWindow.HOUR, cutoff));
        backend.takePending(hour);
        assertEquals(1, backend.deleteExpiredLocal(QuotaWindow.HOUR, cutoff));
    }

    @Test
    @DisplayName("本地后端：reset 按 API Key 清理槽位，keys/clear 支持遍历与关闭清空")
    void local_resetAndClear() {
        final QuotaDimension first = QuotaDimension.ofApiKey("key-1");
        final QuotaDimension second = QuotaDimension.ofApiKey("key-2");
        final LocalDateTime dayStart = QuotaWindow.DAY.windowStart(NOW);
        backend.increment(new QuotaCounterKey(first, QuotaWindow.DAY, dayStart), 1L, 1L).block(WAIT);
        backend.increment(new QuotaCounterKey(first, QuotaWindow.HOUR, QuotaWindow.HOUR.windowStart(NOW)),
            1L, 1L).block(WAIT);
        backend.increment(new QuotaCounterKey(second, QuotaWindow.DAY, dayStart), 1L, 1L).block(WAIT);
        assertEquals(3, backend.keys().size());

        assertEquals(2L, backend.reset("key-1").block(WAIT).longValue());
        assertEquals(1, backend.keys().size());
        assertEquals(0, backend.resetLocal(null));
        assertEquals(0, backend.resetLocal(""));

        backend.clear();
        assertTrue(backend.keys().isEmpty());
    }

    /**
     * 只实现按 {@link QuotaCounterKey} 方法的最小后端，用于验证接口默认方法。
     */
    private static final class KeyOnlyBackend implements QuotaCounterBackend {

        private final Map<QuotaCounterKey, long[]> values = new LinkedHashMap<>();

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public Mono<long[]> increment(final QuotaCounterKey key, final long requests, final long tokens) {
            values.put(key, new long[]{requests, tokens});
            return Mono.just(values.get(key));
        }

        @Override
        public Mono<long[]> incrementIfPresent(final QuotaCounterKey key, final long requests, final long tokens) {
            final long[] current = values.get(key);
            if (current == null) {
                return Mono.empty();
            }
            final long[] next = new long[]{current[0] + requests, current[1] + tokens};
            values.put(key, next);
            return Mono.just(next);
        }

        @Override
        public Mono<Optional<long[]>> read(final QuotaCounterKey key) {
            return Mono.just(Optional.ofNullable(values.get(key)));
        }

        @Override
        public Mono<Long> reset(final String apiKeyId) {
            return Mono.just(0L);
        }

        @Override
        public Mono<Long> deleteExpired(final QuotaWindow window, final LocalDateTime cutoff) {
            return Mono.just(0L);
        }
    }
}

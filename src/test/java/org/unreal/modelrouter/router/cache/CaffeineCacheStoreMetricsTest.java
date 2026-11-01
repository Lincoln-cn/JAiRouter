package org.unreal.modelrouter.router.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #947：响应缓存条目数的 gauge。
 *
 * <p>缓存「满了没有」在 {@code /actuator/prometheus} 上此前看不到：命中率可以从日志推算，
 * 但容量只有在压测里靠猜。本类守住 gauge 真的注册在 Caffeine 实例上、且随写入变化。</p>
 *
 * @since 3.5.10
 */
class CaffeineCacheStoreMetricsTest {

    private static CaffeineCacheStore newStore(final SimpleMeterRegistry registry) {
        final ResponseCacheProperties properties = new ResponseCacheProperties();
        properties.setMaxSize(100);
        final CaffeineCacheStore store = new CaffeineCacheStore(properties);
        if (registry != null) {
            ReflectionTestUtils.setField(store, "meterRegistry", registry);
            store.registerSizeMetric();
        }
        return store;
    }

    @Test
    @DisplayName("注册后能在注册表里按指标名查到，且随写入增长")
    void registersAndReflectsEntries() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final CaffeineCacheStore store = newStore(registry);

        assertThat(registry.find(CaffeineCacheStore.SIZE_METRIC).gauge())
                .as("gauge 应已注册")
                .isNotNull();
        assertThat(registry.get(CaffeineCacheStore.SIZE_METRIC).gauge().value())
                .as("空缓存应为 0")
                .isZero();

        store.put("k1", "v1", Duration.ofMinutes(5));
        store.put("k2", "v2", Duration.ofMinutes(5));

        assertThat(registry.get(CaffeineCacheStore.SIZE_METRIC).gauge().value())
                .as("写入两条后应为 2")
                .isEqualTo(2.0d);
    }

    @Test
    @DisplayName("删除后 gauge 跟随下降（不是只增不减的计数器）")
    void tracksDeletion() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final CaffeineCacheStore store = newStore(registry);
        store.put("k1", "v1", Duration.ofMinutes(5));
        store.put("k2", "v2", Duration.ofMinutes(5));

        store.delete("k1");

        assertThat(registry.get(CaffeineCacheStore.SIZE_METRIC).gauge().value())
                .as("删除一条后应为 1")
                .isEqualTo(1.0d);
    }

    @Test
    @DisplayName("未装配 MeterRegistry 时不注册、也不抛异常（单元测试直接 new 的形态）")
    void noMetricsWithoutRegistry() {
        final CaffeineCacheStore store = newStore(null);
        store.registerSizeMetric();

        assertThat(store.size()).as("缓存本身应照常工作").isZero();
    }
}

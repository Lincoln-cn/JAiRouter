package org.unreal.modelrouter.monitor.monitoring.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.MonitoringProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.9.0: DefaultMetricsCollector 缓存指标单元测试
 */
@DisplayName("DefaultMetricsCollector 缓存指标测试")
class CacheMetricsTest {

    private DefaultMetricsCollector collector;
    private MeterRegistry meterRegistry;
    private MonitoringProperties monitoringProperties;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitoringProperties = new MonitoringProperties();
        monitoringProperties.setPrefix("jairouter");
        collector = new DefaultMetricsCollector(meterRegistry, monitoringProperties);
    }

    @Test
    @DisplayName("记录 KV 缓存命中 token 计数器")
    void recordCacheHitTokens() {
        collector.recordCacheTokenUsage("vllm", "instance-1", 500, 200);

        assertNotNull(meterRegistry.find("jairouter_cache_hit_tokens_total")
                .tag("adapter", "vllm")
                .tag("instance", "instance-1")
                .counter());
        assertEquals(500.0,
                meterRegistry.find("jairouter_cache_hit_tokens_total")
                        .tag("adapter", "vllm")
                        .tag("instance", "instance-1")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("记录 KV 缓存未命中 token 计数器")
    void recordCacheMissTokens() {
        collector.recordCacheTokenUsage("deepseek", "instance-2", 100, 800);

        assertNotNull(meterRegistry.find("jairouter_cache_miss_tokens_total")
                .tag("adapter", "deepseek")
                .tag("instance", "instance-2")
                .counter());
        assertEquals(800.0,
                meterRegistry.find("jairouter_cache_miss_tokens_total")
                        .tag("adapter", "deepseek")
                        .tag("instance", "instance-2")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("记录缓存命中率 Gauge")
    void recordCacheHitRatio() {
        collector.recordCacheTokenUsage("vllm", "inst-1", 750, 250);

        assertNotNull(meterRegistry.find("jairouter_cache_hit_ratio")
                .tag("adapter", "vllm")
                .tag("instance", "inst-1")
                .gauge());
        // hit ratio = 750 / (750+250) = 0.75
        assertEquals(0.75,
                meterRegistry.find("jairouter_cache_hit_ratio")
                        .tag("adapter", "vllm")
                        .tag("instance", "inst-1")
                        .gauge()
                        .value(),
                0.001);
    }

    @Test
    @DisplayName("缓存 hit=0 时只记录 miss 计数器")
    void recordOnlyMissTokens() {
        collector.recordCacheTokenUsage("openai", "inst-1", 0, 1000);

        assertNull(meterRegistry.find("jairouter_cache_hit_tokens_total")
                .tag("adapter", "openai")
                .tag("instance", "inst-1")
                .counter());
        assertNotNull(meterRegistry.find("jairouter_cache_miss_tokens_total")
                .tag("adapter", "openai")
                .tag("instance", "inst-1")
                .counter());
    }

    @Test
    @DisplayName("缓存 miss=0 时只记录 hit 计数器")
    void recordOnlyHitTokens() {
        collector.recordCacheTokenUsage("vllm", "inst-1", 1000, 0);

        assertNotNull(meterRegistry.find("jairouter_cache_hit_tokens_total")
                .tag("adapter", "vllm")
                .tag("instance", "inst-1")
                .counter());
        assertNull(meterRegistry.find("jairouter_cache_miss_tokens_total")
                .tag("adapter", "vllm")
                .tag("instance", "inst-1")
                .counter());
    }

    @Test
    @DisplayName("多次调用累加缓存命中计数器")
    void accumulateCacheHitTokens() {
        collector.recordCacheTokenUsage("vllm", "inst-1", 100, 50);
        collector.recordCacheTokenUsage("vllm", "inst-1", 200, 100);

        assertEquals(300.0,
                meterRegistry.find("jairouter_cache_hit_tokens_total")
                        .tag("adapter", "vllm")
                        .tag("instance", "inst-1")
                        .counter()
                        .count());
        assertEquals(150.0,
                meterRegistry.find("jairouter_cache_miss_tokens_total")
                        .tag("adapter", "vllm")
                        .tag("instance", "inst-1")
                        .counter()
                        .count());
    }
}

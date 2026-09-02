package org.unreal.modelrouter.monitor.monitoring.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.MonitoringProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * v2.9.9: 响应缓存 hit/miss 指标单元测试
 */
@DisplayName("响应缓存指标测试")
class ResponseCacheMetricsTest {

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
    @DisplayName("记录响应缓存命中计数器")
    void recordResponseCacheHit() {
        collector.recordResponseCacheHit("chat", "gpt-4");

        assertNotNull(meterRegistry.find("jairouter_response_cache_hits_total")
                .tag("service", "chat")
                .tag("model", "gpt-4")
                .counter());
        assertEquals(1.0,
                meterRegistry.find("jairouter_response_cache_hits_total")
                        .tag("service", "chat")
                        .tag("model", "gpt-4")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("记录响应缓存未命中计数器")
    void recordResponseCacheMiss() {
        collector.recordResponseCacheMiss("embedding", "text-embedding");

        assertNotNull(meterRegistry.find("jairouter_response_cache_misses_total")
                .tag("service", "embedding")
                .tag("model", "text-embedding")
                .counter());
        assertEquals(1.0,
                meterRegistry.find("jairouter_response_cache_misses_total")
                        .tag("service", "embedding")
                        .tag("model", "text-embedding")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("hit+miss 后更新命中率 Gauge")
    void recordHitRatioGauge() {
        collector.recordResponseCacheHit("rerank", "rerank-model");
        collector.recordResponseCacheMiss("rerank", "rerank-model");

        assertNotNull(meterRegistry.find("jairouter_response_cache_hit_ratio")
                .tag("service", "rerank")
                .tag("model", "rerank-model")
                .gauge());
        assertEquals(0.5,
                meterRegistry.find("jairouter_response_cache_hit_ratio")
                        .tag("service", "rerank")
                        .tag("model", "rerank-model")
                        .gauge()
                        .value(),
                0.001);
    }
}

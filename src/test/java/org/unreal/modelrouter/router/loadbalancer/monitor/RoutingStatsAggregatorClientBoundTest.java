package org.unreal.modelrouter.router.loadbalancer.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #877：路由统计里的客户端映射必须有上限，不能随客户端 IP 无限增长。
 */
@DisplayName("#877 路由统计的客户端映射上限")
class RoutingStatsAggregatorClientBoundTest {

    @Test
    @DisplayName("877-001 超过上限后淘汰最早的客户端，映射与计数都不再增长")
    void oldestClientsAreEvicted() {
        final RoutingStatsAggregator.ServiceRoutingStats stats =
                new RoutingStatsAggregator.ServiceRoutingStats("chat", 3);

        record(stats, "10.0.0.1");
        record(stats, "10.0.0.2");
        record(stats, "10.0.0.3");
        assertEquals(3, stats.getTrackedClientCount());
        assertTrue(stats.getClientToInstanceMapping(10).containsKey("10.0.0.1"));

        record(stats, "10.0.0.4");

        assertEquals(3, stats.getTrackedClientCount(), "跟踪的客户端数量必须停在配置上限");
        assertFalse(stats.getClientToInstanceMapping(10).containsKey("10.0.0.1"),
                "最早的客户端应被淘汰");
        assertTrue(stats.getClientToInstanceMapping(10).containsKey("10.0.0.4"),
                "新客户端应被记录");
    }

    @Test
    @DisplayName("877-002 同一客户端重复请求不会占用新的名额")
    void repeatedClientDoesNotGrow() {
        final RoutingStatsAggregator.ServiceRoutingStats stats =
                new RoutingStatsAggregator.ServiceRoutingStats("chat", 2);

        for (int i = 0; i < 50; i++) {
            record(stats, "10.0.0.9");
        }

        assertEquals(1, stats.getTrackedClientCount());
        assertEquals(1, stats.getClientToInstanceMapping(10).size(), "映射里只有一条客户端记录");
        assertEquals(50L, stats.getTotalSelections(), "实例计数应累计到 50");
    }

    @Test
    @DisplayName("877-003 默认上限为正数，避免构造时把上限配成 0 变成「不记录」")
    void defaultLimitIsPositive() {
        final RoutingStatsAggregator.ServiceRoutingStats stats =
                new RoutingStatsAggregator.ServiceRoutingStats("chat");
        record(stats, "203.0.113.5");

        assertNotNull(stats.getClientToInstanceMapping(10));
        assertEquals(1, stats.getTrackedClientCount());
        assertTrue(RoutingStatsAggregator.ServiceRoutingStats.DEFAULT_MAX_TRACKED_CLIENTS > 0);
    }

    private static void record(final RoutingStatsAggregator.ServiceRoutingStats stats, final String clientId) {
        stats.recordSelection("instance-a", clientId, Instant.now(), "round-robin", "qwen3:4b");
    }
}

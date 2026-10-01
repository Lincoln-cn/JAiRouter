package org.unreal.modelrouter.common.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.unreal.modelrouter.common.util.InstanceIdentity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集群事件广播单元测试（#164）。
 *
 * <p>重点覆盖两处容易出错、又不易用集成测试覆盖的语义：<b>回环过滤</b>（写错会让每个客户端
 * 收到重复事件）与<b>Redis 不可用时的降级</b>（可观测性能力不应阻断服务启动）。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
@DisplayName("集群事件广播")
class ClusterEventBusTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ReactiveRedisConnectionFactory> absentConnectionFactory() {
        ObjectProvider<ReactiveRedisConnectionFactory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private static RedisClusterEventBus redisBusWithoutConnection() {
        return new RedisClusterEventBus(absentConnectionFactory(), new ObjectMapper());
    }

    private static String envelope(final String source, final String payload) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("source", source, "payload", payload));
    }

    @Test
    @DisplayName("单副本实现不广播、不订阅、发布不抛异常")
    void localBusIsInert() {
        ClusterEventBus bus = new LocalClusterEventBus();

        assertFalse(bus.isClusterWide());
        bus.publish("any-channel", "{\"a\":1}");
        assertEquals(0, bus.subscribe("any-channel").collectList().block().size());
    }

    @Test
    @DisplayName("Redis 不可用时降级为仅本副本可见，发布与订阅都不抛")
    void redisBusDegradesWithoutConnectionFactory() {
        ClusterEventBus bus = redisBusWithoutConnection();

        assertFalse(bus.isClusterWide(), "拿不到连接工厂时不应自称跨副本");
        bus.publish("c", "payload");
        assertTrue(bus.subscribe("c").collectList().block().isEmpty());
    }

    @Test
    @DisplayName("回环过滤：本副本自己发出的事件不再向下分发")
    void selfEmittedEventsAreFilteredOut() throws Exception {
        RedisClusterEventBus bus = redisBusWithoutConnection();

        String mine = envelope(InstanceIdentity.id(), "my-event");

        assertTrue(bus.unwrap(mine).blockOptional().isEmpty(),
                "自己发的事件必须过滤——本地 Sink 已经分发过一次，否则客户端收到重复事件");
    }

    @Test
    @DisplayName("其它副本的事件透传原始载荷")
    void eventsFromOtherReplicasArePassedThrough() throws Exception {
        RedisClusterEventBus bus = redisBusWithoutConnection();

        String theirs = envelope("replica-b", "their-event");

        assertEquals("their-event", bus.unwrap(theirs).block());
    }

    @Test
    @DisplayName("无法解析的消息被忽略，而不是让订阅流报错")
    void malformedMessagesAreIgnored() {
        RedisClusterEventBus bus = redisBusWithoutConnection();

        assertTrue(bus.unwrap("not-json").blockOptional().isEmpty());
        assertTrue(bus.unwrap("").blockOptional().isEmpty());
    }
}

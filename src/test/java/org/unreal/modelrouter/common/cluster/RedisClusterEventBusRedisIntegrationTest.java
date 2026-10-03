package org.unreal.modelrouter.common.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 跨副本事件广播的真 Redis 双实例集成测试（#181，补 #164 遗留的端到端验证）。
 *
 * <p><b>默认跳过</b>：仅当 {@code REDIS_TEST=true} 时执行，连接参数可用 {@code REDIS_TEST_HOST}
 * / {@code REDIS_TEST_PORT} 覆盖；CI 已提供 redis service。</p>
 *
 * <p>为什么需要真 Redis：单元测试只能验证信封解析与回环过滤的纯逻辑。真正要证明的是
 * 「一个副本发出的事件能被另一个副本的订阅流收到」——那需要真实的 pub/sub 往返，
 * 而 {@code selfId} 的可注入（见 {@code RedisClusterEventBus} 的三参构造器）正是为了在同一个
 * JVM 里模拟两个副本。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.7
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST", matches = "true")
@DisplayName("跨副本事件广播：真 Redis 双实例（REDIS_TEST=true 时执行）")
class RedisClusterEventBusRedisIntegrationTest {

    /** 订阅连上 Redis 需要一点时间；留出这个窗口再发布，避免竞态带来的假失败。 */
    private static final Duration SUBSCRIBE_SETTLE = Duration.ofMillis(600);

    private LettuceConnectionFactory factory;
    private String channel;

    @BeforeEach
    void setUp() {
        final String host = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
        final int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
        factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        channel = "dual-instance-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ReactiveRedisConnectionFactory> redisProvider() {
        final ObjectProvider<ReactiveRedisConnectionFactory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(factory);
        return provider;
    }

    private RedisClusterEventBus bus(final String replicaId) {
        return new RedisClusterEventBus(redisProvider(), new ObjectMapper(), replicaId);
    }

    @Test
    @DisplayName("一个副本发布的事件能被另一个副本的订阅流收到")
    void eventPublishedByOneReplicaIsVisibleToAnother() throws Exception {
        final RedisClusterEventBus replicaA = bus("replica-a");
        final RedisClusterEventBus replicaB = bus("replica-b");

        final CompletableFuture<List<String>> receivedByB =
                replicaB.subscribe(channel).take(1).collectList().toFuture();
        Thread.sleep(SUBSCRIBE_SETTLE.toMillis());

        replicaA.publish(channel, "routing-event-from-a");

        assertEquals(List.of("routing-event-from-a"), receivedByB.get(10, TimeUnit.SECONDS),
                "B 应收到 A 广播的事件——这正是多副本下监控面板不再漏事件的前提");
    }

    @Test
    @DisplayName("本副本发出的事件不回环：自己订阅不到自己的事件")
    void ownEventsAreNotDeliveredBackToThePublisher() throws Exception {
        final RedisClusterEventBus replicaA = bus("replica-a");
        final RedisClusterEventBus replicaB = bus("replica-b");

        // B 的订阅用于证明这次发布确实到达了 Redis，而不是 A 压根没发出去
        final CompletableFuture<List<String>> receivedByB =
                replicaB.subscribe(channel).take(1).collectList().toFuture();
        final CompletableFuture<List<String>> receivedByA =
                replicaA.subscribe(channel).take(1).collectList().toFuture();
        Thread.sleep(SUBSCRIBE_SETTLE.toMillis());

        replicaA.publish(channel, "only-for-others");

        assertEquals(List.of("only-for-others"), receivedByB.get(10, TimeUnit.SECONDS),
                "前提校验：事件确实经由 Redis 广播出去了");

        // 超时即「未收到」。注意 CompletableFuture.get(timeout) 在未完成时直接抛 TimeoutException，
        // 只有 future 本身异常完成才会包成 ExecutionException。
        assertThrows(TimeoutException.class, () -> receivedByA.get(3, TimeUnit.SECONDS),
                "A 不应收到自己发出的事件——本地 Sink 已经分发过一次，回环会让客户端看到重复事件");
    }
}

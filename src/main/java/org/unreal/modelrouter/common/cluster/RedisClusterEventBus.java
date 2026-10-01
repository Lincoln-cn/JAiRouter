package org.unreal.modelrouter.common.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.util.InstanceIdentity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis pub/sub 的跨副本事件广播（#164）。
 *
 * <p>本副本产生的事件在分发到本地 Sink 的同时 publish 到 Redis 通道；各副本 subscribe
 * 该通道并把收到的事件并入自己的输出流，从而让任一客户端看到全集群的事件。</p>
 *
 * <p>三条边界语义：</p>
 * <ul>
 *   <li><b>回环过滤</b>：消息带来源副本标识，本副本发出的事件不再向下分发——否则
 *       每个客户端会收到重复事件（本地 Sink 已经发过一次）。</li>
 *   <li><b>降级</b>：{@link ReactiveRedisConnectionFactory} 不可用时只告警一次并退化为
 *       「仅本副本可见」，不阻断启动——可观测性能力不应让服务起不来。</li>
 *   <li><b>自愈</b>：订阅中断（Redis 重启/网络抖动）时按退避重试，Redis 恢复后自动续订，
 *       不需要重启应用。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "jairouter.cluster.events.enabled",
        havingValue = "true")
public class RedisClusterEventBus implements ClusterEventBus {

    /** Redis 通道前缀，避免与其它用途的通道冲突。 */
    private static final String CHANNEL_PREFIX = "jairouter:events:";

    /**
     * 消息信封：携带来源副本标识，供订阅侧做回环过滤。
     *
     * @param source  发布该事件的副本标识
     * @param payload 事件载荷（调用方已序列化）
     */
    private record Envelope(String source, String payload) {
    }

    private final ReactiveRedisTemplate<String, String> template;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;
    private final String selfId = InstanceIdentity.id();

    /** 每个逻辑通道共享一个 Redis 订阅，避免每个客户端各建一条。 */
    private final Map<String, Flux<String>> subscriptions = new ConcurrentHashMap<>();

    public RedisClusterEventBus(final ObjectProvider<ReactiveRedisConnectionFactory> connectionFactoryProvider,
                                final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        final ReactiveRedisConnectionFactory factory = connectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            log.warn("jairouter.cluster.events.enabled=true 但 RedisConnectionFactory 不可用，"
                    + "集群事件广播降级为「仅本副本可见」");
            this.template = null;
            this.listenerContainer = null;
        } else {
            this.template = new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
            this.listenerContainer = new ReactiveRedisMessageListenerContainer(factory);
            log.info("集群事件广播已启用：副本标识={}", selfId);
        }
    }

    @Override
    public void publish(final String channel, final String payload) {
        if (template == null || channel == null || payload == null) {
            return;
        }
        try {
            final String envelope = objectMapper.writeValueAsString(new Envelope(selfId, payload));
            template.convertAndSend(CHANNEL_PREFIX + channel, envelope)
                    .subscribe(
                            sent -> { },
                            error -> log.warn("集群事件广播失败，该事件仅本副本可见: channel={}, error={}",
                                    channel, error.getMessage()));
        } catch (Exception e) {
            // 广播失败不能影响本地事件分发，故只记录
            log.warn("集群事件序列化失败，已丢弃广播: channel={}, error={}", channel, e.getMessage());
        }
    }

    @Override
    public Flux<String> subscribe(final String channel) {
        if (listenerContainer == null || channel == null) {
            return Flux.empty();
        }
        return subscriptions.computeIfAbsent(channel, this::createSubscription);
    }

    @Override
    public boolean isClusterWide() {
        return listenerContainer != null;
    }

    private Flux<String> createSubscription(final String channel) {
        final String redisChannel = CHANNEL_PREFIX + channel;
        return listenerContainer.receive(ChannelTopic.of(redisChannel))
                .map(message -> message.getMessage())
                .flatMap(this::unwrap)
                .doOnSubscribe(subscription -> log.info("已订阅集群事件通道: {}", redisChannel))
                .doOnError(error -> log.warn("集群事件订阅中断，将按退避重试: channel={}, error={}",
                        redisChannel, error.getMessage()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30)))
                // 多客户端共享同一条 Redis 订阅
                .share();
    }

    /**
     * 拆信封并过滤本副本自己发出的事件。
     *
     * <p>包级可见以便直接单元测试——回环过滤写错会让每个客户端收到重复事件，
     * 而这在没有真实 Redis 的环境里不容易通过集成测试覆盖。</p>
     *
     * @param raw 原始消息
     * @return 其它副本的事件载荷；本副本发出的或无法解析的返回空
     */
    Mono<String> unwrap(final String raw) {
        try {
            final Envelope envelope = objectMapper.readValue(raw, Envelope.class);
            if (selfId.equals(envelope.source())) {
                return Mono.empty();
            }
            return Mono.justOrEmpty(envelope.payload());
        } catch (Exception e) {
            log.debug("忽略无法解析的集群事件: {}", e.getMessage());
            return Mono.empty();
        }
    }
}

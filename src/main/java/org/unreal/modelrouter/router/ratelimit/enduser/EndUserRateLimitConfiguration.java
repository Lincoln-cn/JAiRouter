package org.unreal.modelrouter.router.ratelimit.enduser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import java.time.Duration;

/**
 * 终端用户级限流装配（v3.5.2 PR-2）.
 *
 * <p>仅在 {@code jairouter.ratelimit.end-user.enabled=true} 时注册
 * {@link EndUserRateLimitCounter} 与 {@link EndUserRateLimitService}；默认关闭时上下文里
 * 没有这两个 Bean，{@code ServiceRequestHandler} 的可选注入为空、整段跳过——请求行为与
 * v3.5.1 完全一致。</p>
 *
 * <p><b>计数后端选择</b>：{@code jairouter.ratelimit.distributed.enabled=true} 且上下文里存在
 * {@link ReactiveRedisConnectionFactory} 时用 {@link RedisEndUserRateLimitCounter}（跨副本共享），
 * 否则用 {@link LocalEndUserRateLimitCounter}（本机有界 Caffeine）。</p>
 *
 * <p>与配额层的 {@code QuotaRedisConfiguration} 同取向：<b>不做启动期连接探测</b>；Redis
 * 运行期不可达时由计数器在热路径按 fail-open 放行并打 WARN，而不是让服务起不来。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "jairouter.ratelimit.end-user", name = "enabled", havingValue = "true")
public class EndUserRateLimitConfiguration {

    /**
     * 装配终端用户限流计数器（本地或 Redis，二选一）。
     *
     * @param properties                端用户限流配置
     * @param connectionFactoryProvider Redis 连接工厂（可选：未引入 Redis 时为空）
     * @param distributedEnabled        是否要求跨副本共享计数
     * @param timeoutMillis             单次 Redis 调用超时（毫秒）
     * @return 计数器
     */
    @Bean
    public EndUserRateLimitCounter endUserRateLimitCounter(
            final EndUserRateLimitProperties properties,
            final ObjectProvider<ReactiveRedisConnectionFactory> connectionFactoryProvider,
            @Value("${jairouter.ratelimit.distributed.enabled:false}") final boolean distributedEnabled,
            @Value("${jairouter.ratelimit.distributed.timeout-ms:50}") final long timeoutMillis) {
        if (distributedEnabled) {
            final ReactiveRedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
            if (connectionFactory != null) {
                log.info("终端用户限流计数器已装配: backend=redis, timeoutMs={}", timeoutMillis);
                return new RedisEndUserRateLimitCounter(
                        new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string()),
                        Duration.ofMillis(timeoutMillis));
            }
            log.warn("jairouter.ratelimit.distributed.enabled=true 但上下文没有 Redis 连接工厂，"
                    + "终端用户限流回落本地计数（多副本下放行量约为配置值 × 副本数）");
        }
        log.info("终端用户限流计数器已装配: backend=local, localCacheSize={}",
                properties.effectiveLocalCacheSize());
        return new LocalEndUserRateLimitCounter(properties.effectiveLocalCacheSize());
    }

    /**
     * 装配终端用户限流门面。
     *
     * @param properties                端用户限流配置
     * @param counter                   计数后端
     * @param metricsCollectorProvider  指标收集器（可选：监控未装配时为空）
     * @return 门面
     */
    @Bean
    public EndUserRateLimitService endUserRateLimitService(
            final EndUserRateLimitProperties properties,
            final EndUserRateLimitCounter counter,
            final ObjectProvider<MetricsCollector> metricsCollectorProvider) {
        return new EndUserRateLimitService(properties, counter, metricsCollectorProvider.getIfAvailable());
    }
}

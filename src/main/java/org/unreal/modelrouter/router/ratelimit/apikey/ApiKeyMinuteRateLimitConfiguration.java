package org.unreal.modelrouter.router.ratelimit.apikey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitCounter;
import org.unreal.modelrouter.router.ratelimit.enduser.LocalEndUserRateLimitCounter;
import org.unreal.modelrouter.router.ratelimit.enduser.RedisEndUserRateLimitCounter;

import java.time.Duration;

/**
 * API Key 级每分钟限流装配（{@code jairouter.ratelimit.api-key.enabled}，默认开启）。
 *
 * <p><b>兼容性约束</b>：本类<b>不</b>把计数器注册成独立 Bean。{@code EndUserRateLimitConfiguration}
 * 的 {@code endUserRateLimitService(...)} 以 <em>类型</em>（参数名 {@code counter}）注入
 * {@link EndUserRateLimitCounter}，同类型再多一个 Bean 会让那条装配在启动期抛
 * {@code NoUniqueBeanDefinitionException}。因此这里在 {@code @Bean} 方法内部直接
 * {@code new} 计数器并交给本层门面持有——两者各自独立、互不干扰。</p>
 *
 * <p><b>后端选择与终端用户级限流同构</b>：{@code jairouter.ratelimit.distributed.enabled=true}
 * 且上下文里有 {@link ReactiveRedisConnectionFactory} 时用
 * {@link RedisEndUserRateLimitCounter}（跨副本共享，前缀换成 {@link #REDIS_KEY_PREFIX}），
 * 否则用 {@link LocalEndUserRateLimitCounter}。不做启动期连接探测：Redis 运行期不可达时
 * 由计数器在热路径 fail-open 放行并打 WARN，与既有取向一致。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "jairouter.ratelimit.api-key", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ApiKeyMinuteRateLimitConfiguration {

    /**
     * API Key 级限流在 Redis 里的 key 前缀。
     *
     * <p>与终端用户级的 {@code jairouter:ratelimit:end-user:} 并列在同一命名空间下，运维可用
     * {@code SCAN jairouter:ratelimit:* } 一次看清「谁在限流」，也便于按前缀分别统计。</p>
     */
    public static final String REDIS_KEY_PREFIX = "jairouter:ratelimit:api-key:";

    /**
     * 装配 API Key 级每分钟限流门面。
     *
     * @param properties                本层配置
     * @param apiKeyServiceProvider     API Key 服务（可选：解析限额用，缺失时退化为不判定）
     * @param connectionFactoryProvider Redis 连接工厂（可选：未引入 Redis 时为空）
     * @param metricsCollectorProvider  指标收集器（可选：监控未装配时为空）
     * @param distributedEnabled        是否要求跨副本共享计数
     * @param timeoutMillis             单次 Redis 调用超时（毫秒）
     * @return 门面
     */
    @Bean
    public ApiKeyMinuteRateLimitService apiKeyMinuteRateLimitService(
            final ApiKeyMinuteRateLimitProperties properties,
            final ObjectProvider<ApiKeyService> apiKeyServiceProvider,
            final ObjectProvider<ReactiveRedisConnectionFactory> connectionFactoryProvider,
            final ObjectProvider<MetricsCollector> metricsCollectorProvider,
            @Value("${jairouter.ratelimit.distributed.enabled:false}") final boolean distributedEnabled,
            @Value("${jairouter.ratelimit.distributed.timeout-ms:50}") final long timeoutMillis) {
        final EndUserRateLimitCounter counter =
                buildCounter(properties, connectionFactoryProvider, distributedEnabled, timeoutMillis);
        if (apiKeyServiceProvider.getIfAvailable() == null) {
            log.warn("apiKeyMinuteRateLimitService 装配时没有 ApiKeyService，"
                    + "API Key 级每分钟限流将不生效（每次判定都会因取不到限额而放行）");
        }
        return new ApiKeyMinuteRateLimitService(properties, counter,
                apiKeyServiceProvider.getIfAvailable(),
                metricsCollectorProvider.getIfAvailable());
    }

    /**
     * 构造计数后端（本地或 Redis，二选一）。
     *
     * @param properties                本层配置
     * @param connectionFactoryProvider Redis 连接工厂
     * @param distributedEnabled        是否要求跨副本共享计数
     * @param timeoutMillis             单次 Redis 调用超时（毫秒）
     * @return 计数后端
     */
    private static EndUserRateLimitCounter buildCounter(
            final ApiKeyMinuteRateLimitProperties properties,
            final ObjectProvider<ReactiveRedisConnectionFactory> connectionFactoryProvider,
            final boolean distributedEnabled,
            final long timeoutMillis) {
        if (distributedEnabled) {
            final ReactiveRedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
            if (connectionFactory != null) {
                log.info("API Key 级每分钟限流计数器已装配: backend=redis, prefix={}, timeoutMs={}",
                        REDIS_KEY_PREFIX, timeoutMillis);
                return new RedisEndUserRateLimitCounter(
                        new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string()),
                        Duration.ofMillis(timeoutMillis),
                        REDIS_KEY_PREFIX);
            }
            log.warn("jairouter.ratelimit.distributed.enabled=true 但上下文没有 Redis 连接工厂，"
                    + "API Key 级每分钟限流回落本地计数（多副本下放行量约为配置值 × 副本数）");
        }
        log.info("API Key 级每分钟限流计数器已装配: backend=local, localCacheSize={}",
                properties.effectiveLocalCacheSize());
        return new LocalEndUserRateLimitCounter(properties.effectiveLocalCacheSize());
    }
}

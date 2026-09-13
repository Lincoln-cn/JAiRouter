package org.unreal.modelrouter.auth.security.quota;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * 配额分布式计数（Redis）装配（v3.1 PR-3）。
 *
 * <p>仅在 {@code jairouter.quota.distributed.enabled=true} 时创建
 * {@link QuotaCounterBackend} Bean（{@link RedisCounterBackend}）；默认关闭时不注册任何 Bean，
 * 应用上下文与 v3.1 PR-1/PR-2 完全一致。</p>
 *
 * <p>Redis 客户端沿用 Spring Boot 自动装配的 {@link ReactiveRedisConnectionFactory}
 * （{@code spring-boot-starter-data-redis-reactive} 提供，或由既有 Redis 配置提供），
 * 不做连接探测：Redis 进程不可达时不在启动期失败，而是在热路径按
 * {@code jairouter.quota.distributed.degrade-to-local} 降级回本地计数并打告警日志。</p>
 *
 * <p>注意：开启该开关后若上下文中不存在 {@link ReactiveRedisConnectionFactory}
 * （既未引入 Redis starter 也未自行提供），Spring 会在启动期报缺依赖错误——这属于配置错误，
 * 与“Redis 运行期不可用”是两种不同的场景，前者应当立即暴露。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "jairouter.quota.distributed", name = "enabled", havingValue = "true")
public class QuotaRedisConfiguration {

    /**
     * 装配 Redis 计数后端。
     *
     * @param connectionFactory Redis 连接工厂（自动装配或既有 Redis 配置提供）
     * @param properties        账本配置（key 前缀与保留期 → TTL）
     * @return Redis 计数后端
     */
    @Bean
    public QuotaCounterBackend quotaRedisCounterBackend(final ReactiveRedisConnectionFactory connectionFactory,
                                                        final QuotaProperties properties) {
        final ReactiveRedisTemplate<String, String> template =
            new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
        log.info("配额分布式计数后端已装配: backend={}, keyPrefix={}", RedisCounterBackend.NAME,
            properties.distributedKeyPrefix());
        return new RedisCounterBackend(template, properties);
    }
}

package org.unreal.modelrouter.config.health;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT Redis 健康探针的开关（{@code jairouter.security.jwt.redis-health.*}）。
 *
 * <p>与 {@link HealthProbeProperties} 同属健康探针配置，但键前缀挂在
 * {@code jairouter.security.jwt} 下，与既有的 {@code persistence.redis} / {@code blacklist.redis}
 * 开关相邻，便于运维在一个配置块里看清「JWT 的 Redis 相关开关」。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.security.jwt.redis-health")
public class JwtRedisHealthProperties {

    /**
     * Redis 不可用时是否让 {@code jwtRedisHealthIndicator} 判定为 DOWN。
     *
     * <p>默认 {@code true} 保持既有行为。置为 {@code false} 后，Redis 不可用只体现为
     * 组件 details（{@code redis=unavailable} + 影响面说明），整体 {@code /actuator/health}
     * 不再因此返回 503，从而避免编排层把仍在正常服务的副本摘流或重启。</p>
     */
    private boolean failHealth = true;
}

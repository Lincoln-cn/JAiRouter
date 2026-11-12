package org.unreal.modelrouter.router.ratelimit.apikey;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API Key 级每分钟限流配置（{@code jairouter.ratelimit.api-key.*}）。
 *
 * <p>本层是「同一个 API Key 在全部副本上的每分钟上限」的实现，替代此前分散在两处的判定：
 * 认证侧的进程内滑动窗口（{@code ApiKeyValidator#validateRateLimit}）与配额账本的
 * {@code MINUTE} 窗口（{@code QuotaEnforcementService}）。三者并存时同一份请求会被计数两次
 * 且两套口径（进程内 / 跨副本、固定窗口 / 滑动窗口）会互相矛盾，故收敛到本层一处。</p>
 *
 * <p><b>默认开启</b>（{@code enabled=true}）：删除上述两条旧路径后，若本层默认关闭，每分钟
 * 限额会完全失效，因此默认值只能是开。关闭后{@code rateLimitPerMinute} 在单副本内仍无作用，
 * 属于运维显式选择的「不限流」姿态，日志会打 WARN 提示。</p>
 *
 * <p><b>窗口语义</b>：按自然分钟对齐的固定窗口，与终端用户级限流
 * （{@code jairouter.ratelimit.end-user.*}）严格同构——两者共用
 * {@code EndUserRateLimitCounter} 的计数实现与 fail-open 语义，只是 Redis key 前缀与
 * 桶键不同。固定窗口的代价是窗口交界处允许接近 2 倍突发，换来的是能精确算出
 * {@code Retry-After}（到下一个整分钟）且多副本天然一致。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.ratelimit.api-key")
public class ApiKeyMinuteRateLimitProperties {

    /** 默认本地计数缓存条目上限（与终端用户级限流同量级）。 */
    public static final int DEFAULT_LOCAL_CACHE_SIZE = 50000;

    /** 本地缓存条目下界。 */
    private static final int MIN_LOCAL_CACHE_SIZE = 1;

    /** 本地缓存条目上界（内存保护）。 */
    private static final int MAX_LOCAL_CACHE_SIZE = 1_000_000;

    /**
     * 是否启用 API Key 级每分钟限流（默认 true：旧的两条判定已删除，关闭即等于不限流）
     */
    private boolean enabled = true;

    /**
     * 本地计数缓存条目上限（Caffeine maximumSize，钳制在 1 ~ 1,000,000）
     *
     * <p>只在「未要求跨副本共享」或「要求了但没有 Redis 连接工厂」时生效。</p>
     */
    private int localCacheSize = DEFAULT_LOCAL_CACHE_SIZE;

    /**
     * 生效的本地缓存条目上限。
     *
     * @return 钳制在 1 ~ 1,000,000 的取值
     */
    public int effectiveLocalCacheSize() {
        return Math.max(MIN_LOCAL_CACHE_SIZE, Math.min(localCacheSize, MAX_LOCAL_CACHE_SIZE));
    }
}

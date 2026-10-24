package org.unreal.modelrouter.router.ratelimit.enduser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 终端用户级限流配置（{@code jairouter.ratelimit.end-user.*}）。
 *
 * <p>默认 {@link #isEnabled() enabled=false}，即完全不启用：不解析身份、不计数、不写响应头，
 * 行为与未引入本模块时完全一致（零行为变更）。</p>
 *
 * <p><b>语义边界</b>：本能力是<b>公平性手段，不是安全边界</b>。终端用户标识由调用方自报
 * （请求头 {@value #HEADER_NAME} 或请求体 {@code user} / Anthropic {@code metadata.user_id}），
 * 客户端可以留空、也可以伪造；它只用于「同一个 API Key 被多个终端用户共享时按用户分配配额」，
 * 不能用于鉴定调用者身份。</p>
 *
 * <p>数值型配置统一在访问器里钳制，而不是靠 {@code @Validated} 抛错：限流参数写错不应该让
 * 进程起不来（与 {@code QuotaProperties} 对 {@code distributed.timeout} 的钳制同取向）。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.ratelimit.end-user")
public class EndUserRateLimitProperties {

    /** 终端用户标识请求头名（仅当 {@link #isHeaderEnabled()} 为 {@code true} 时读取）。 */
    public static final String HEADER_NAME = "X-End-User-Id";

    /** 默认每分钟请求上限。 */
    public static final int DEFAULT_REQUESTS_PER_MINUTE = 60;

    /** 默认本地计数缓存条目上限。 */
    public static final int DEFAULT_LOCAL_CACHE_SIZE = 50000;

    /** 默认摘要长度（hex 字符数）。 */
    public static final int DEFAULT_FINGERPRINT_LENGTH = 16;

    /** 每分钟上限的下界（0 或负数会把该用户永久拒绝，按 1 处理）。 */
    private static final int MIN_REQUESTS_PER_MINUTE = 1;

    /** 每分钟上限的上界（防误配成天文数字后失去限流意义）。 */
    private static final int MAX_REQUESTS_PER_MINUTE = 1_000_000;

    /** 本地缓存条目下界。 */
    private static final int MIN_LOCAL_CACHE_SIZE = 1;

    /** 本地缓存条目上界（内存保护）。 */
    private static final int MAX_LOCAL_CACHE_SIZE = 1_000_000;

    /** 摘要长度下界。 */
    private static final int MIN_FINGERPRINT_LENGTH = 8;

    /** 摘要长度上界。 */
    private static final int MAX_FINGERPRINT_LENGTH = 32;

    /**
     * 是否启用终端用户级限流（默认 false：零行为变更）
     */
    private boolean enabled = false;

    /**
     * 每用户每分钟请求上限（钳制在 1 ~ 1,000,000）
     */
    private int requestsPerMinute = DEFAULT_REQUESTS_PER_MINUTE;

    /**
     * 是否读取请求头 {@value #HEADER_NAME}（默认 true）。
     *
     * <p>把请求头当作<b>优先</b>来源：网关 / 调用方显式注入时最准确；若部署环境中该请求头
     * 可能由客户端直传且不可信，可关掉它，只保留请求体 {@code user}。</p>
     */
    private boolean headerEnabled = true;

    /**
     * 终端用户标识缺失时的处置（默认 {@link MissingIdentity#SKIP}）
     */
    private MissingIdentity missingIdentity = MissingIdentity.SKIP;

    /**
     * 本地计数缓存条目上限（Caffeine maximumSize，钳制在 1 ~ 1,000,000）
     */
    private int localCacheSize = DEFAULT_LOCAL_CACHE_SIZE;

    /**
     * 摘要长度（hex 字符数，钳制在 8 ~ 32）：用于日志与 Redis key 里的不可逆标识
     */
    private int fingerprintLength = DEFAULT_FINGERPRINT_LENGTH;

    /**
     * 标识缺失时的处置策略。
     */
    public enum MissingIdentity {

        /** 跳过该请求（不限流），只记指标。适合灰度期与「客户端还没接入」的过渡。 */
        SKIP,

        /** 拒绝该请求（429），迫使调用方必须带上终端用户标识。 */
        REJECT
    }

    /**
     * 生效的每分钟上限。
     *
     * @return 钳制在 1 ~ 1,000,000 的取值
     */
    public int effectiveRequestsPerMinute() {
        return clamp(requestsPerMinute, MIN_REQUESTS_PER_MINUTE, MAX_REQUESTS_PER_MINUTE);
    }

    /**
     * 生效的本地缓存条目上限。
     *
     * @return 钳制在 1 ~ 1,000,000 的取值
     */
    public int effectiveLocalCacheSize() {
        return clamp(localCacheSize, MIN_LOCAL_CACHE_SIZE, MAX_LOCAL_CACHE_SIZE);
    }

    /**
     * 生效的摘要长度。
     *
     * @return 钳制在 8 ~ 32 的取值
     */
    public int effectiveFingerprintLength() {
        return clamp(fingerprintLength, MIN_FINGERPRINT_LENGTH, MAX_FINGERPRINT_LENGTH);
    }

    private static int clamp(final int value, final int min, final int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}

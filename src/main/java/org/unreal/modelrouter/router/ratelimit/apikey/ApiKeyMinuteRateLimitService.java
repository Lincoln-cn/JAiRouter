package org.unreal.modelrouter.router.ratelimit.apikey;

import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitCounter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;

/**
 * API Key 级每分钟限流门面：解析限额 → 计算桶键 → 计数判定 → 记录指标。
 *
 * <p><b>为什么要有这一层</b>：同一个 {@code rateLimitPerMinute} 此前被两处独立判定——认证侧的
 * 进程内滑动窗口（{@code ApiKeyValidator#validateRateLimit}）与配额账本的 {@code MINUTE} 窗口。
 * 多副本下前者各算各的（上限被放大到「配置值 × 副本数」），后者虽然跨副本却按自然分钟固定窗口，
 * 两套口径叠加时又出现「同一分钟被两个计数器各扣一次」。本类把它收敛成唯一实现：</p>
 * <ul>
 *   <li><b>单一计数器</b>：复用 {@link EndUserRateLimitCounter} 的实现（Redis 跨副本 / 本地内存），
 *       只换 key 前缀与桶键，因此与终端用户级限流共享同一套窗口语义与 fail-open 策略；</li>
 *   <li><b>单一阈值来源</b>：只用 {@link ApiKey#getRateLimitPerMinute()}，不引入新配置字段；</li>
 *   <li><b>单一判定点</b>：主链路在 {@code selectInstance} 之前调用一次，认证侧不再判定。</li>
 * </ul>
 *
 * <p>{@link Optional} 语义与 {@code EndUserRateLimitService} 一致：{@code empty} 表示本次不做判定
 * （未启用 / 控制台 JWT 调用方 / 找不到 Key 配置 / 限额为 0 或负数），调用方应直接放行。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Slf4j
public class ApiKeyMinuteRateLimitService {

    /** 指标 / 日志里的算法标识。 */
    private static final String ALGORITHM = "api-key-fixed-window";

    /** 降级时的算法标识后缀。 */
    private static final String DEGRADED_SUFFIX = "-degraded";

    /** 日志里展示的摘要长度（只露前 8 位，与终端用户级限流一致）。 */
    private static final int LOG_FINGERPRINT_LENGTH = 8;

    /** 桶键长度（hex 字符数）。 */
    private static final int BUCKET_KEY_LENGTH = 16;

    private final ApiKeyMinuteRateLimitProperties properties;
    private final EndUserRateLimitCounter counter;
    private final ApiKeyService apiKeyService;
    private final MetricsCollector metricsCollector;

    /**
     * 构造门面。
     *
     * @param properties       配置，不可为 {@code null}
     * @param counter          计数后端，不可为 {@code null}
     * @param apiKeyService    API Key 服务（用于解析限额），可为 {@code null}
     * @param metricsCollector 指标收集器，可为 {@code null}（不影响判定）
     */
    public ApiKeyMinuteRateLimitService(final ApiKeyMinuteRateLimitProperties properties,
                                        final EndUserRateLimitCounter counter,
                                        final ApiKeyService apiKeyService,
                                        final MetricsCollector metricsCollector) {
        this.properties = properties;
        this.counter = counter;
        this.apiKeyService = apiKeyService;
        this.metricsCollector = metricsCollector;
    }

    /**
     * 判定一次请求。
     *
     * @param apiKeyId    调用方 API Key ID，可为 {@code null}
     * @param serviceType 服务类型（仅用于指标低基数标签），可为 {@code null}
     * @param jwtCaller   是否为控制台 JWT 调用方（是则跳过：控制台登录态不按 API Key 限额）
     * @return 判定结果；不需要判定时为 {@link Optional#empty()}，绝不返回 {@code null}
     */
    public Optional<EndUserRateLimitCounter.Decision> evaluate(final String apiKeyId,
                                                               final ServiceType serviceType,
                                                               final boolean jwtCaller) {
        if (properties == null || !properties.isEnabled()) {
            return Optional.empty();
        }
        if (jwtCaller || isBlank(apiKeyId)) {
            return Optional.empty();
        }

        final ApiKey apiKey = resolveApiKey(apiKeyId);
        if (apiKey == null) {
            return Optional.empty();
        }
        final int limit = apiKey.getRateLimitPerMinute();
        if (limit <= 0) {
            // 0 / 负数 = 显式不限流，保持与 ApiKeyValidator 既有语义一致
            return Optional.empty();
        }

        final long now = System.currentTimeMillis() / 1000L;
        final long windowStart = now / EndUserRateLimitCounter.WINDOW_SECONDS
                * EndUserRateLimitCounter.WINDOW_SECONDS;
        final EndUserRateLimitCounter.Decision decision = counter.record(bucketKey(apiKeyId), windowStart, limit, now);

        if (!decision.allowed()) {
            log.warn("API Key 每分钟限流拒绝: keyFp={} service={} limit={}/min used={}",
                    prefix(fingerprint(apiKeyId)), serviceType, decision.limit(), decision.used());
        }
        recordMetrics(serviceType, decision);
        return Optional.of(decision);
    }

    /**
     * 读取本窗口已用量（管理面展示「当前分钟已用」的唯一数据源）。
     *
     * @param apiKeyId API Key ID，可为 {@code null}
     * @return 已用量；未启用或无法解析时返回 {@code 0}，绝不返回负数
     */
    public long currentUsage(final String apiKeyId) {
        if (properties == null || !properties.isEnabled() || isBlank(apiKeyId)) {
            return 0L;
        }
        final long now = System.currentTimeMillis() / 1000L;
        final long windowStart = now / EndUserRateLimitCounter.WINDOW_SECONDS
                * EndUserRateLimitCounter.WINDOW_SECONDS;
        return counter.currentUsage(bucketKey(apiKeyId), windowStart);
    }

    /**
     * 清零指定 API Key 的当前窗口计数（管理面「重置配额」用）。
     *
     * <p>只影响<b>当前</b>窗口：上一窗口的计数本来就会随 key TTL 过期消失。</p>
     *
     * <p>先确认 Key 存在再清零：对不存在的 Key 返回 {@code false}，避免管理面把「没找到这个
     * Key」误报成「已重置」。清零本身是幂等的 —— 已经为 0 的桶再清一次仍返回 {@code true}。</p>
     *
     * @param apiKeyId API Key ID，可为 {@code null}
     * @return 是否已清零；未启用、无 ID 或 Key 不存在时返回 {@code false}
     */
    public boolean reset(final String apiKeyId) {
        if (properties == null || !properties.isEnabled() || isBlank(apiKeyId)) {
            return false;
        }
        if (resolveApiKey(apiKeyId) == null) {
            log.debug("重置 API Key 每分钟限流：Key 不存在，忽略: keyFp={}", prefix(fingerprint(apiKeyId)));
            return false;
        }
        final long now = System.currentTimeMillis() / 1000L;
        final long windowStart = now / EndUserRateLimitCounter.WINDOW_SECONDS
                * EndUserRateLimitCounter.WINDOW_SECONDS;
        return counter.reset(bucketKey(apiKeyId), windowStart);
    }

    /**
     * 清零全部计数（管理面「重置全部配额」用）。
     *
     * <p>返回值是<b>实际清零的桶数</b>。分布式后端不支持全清时返回 {@code 0}，
     * 调用方应如实提示，不要把它当成成功。</p>
     *
     * @return 实际清零的桶数
     */
    public int resetAll() {
        if (properties == null || !properties.isEnabled()) {
            return 0;
        }
        return counter.resetAll();
    }

    /**
     * 当前是否启用（供调用方省掉无谓的判定）。
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return properties != null && properties.isEnabled();
    }

    /**
     * 按 API Key ID 解析限额配置（沿用 {@code QuotaEnforcementService} 的索引方式）。
     *
     * @param apiKeyId API Key ID
     * @return 限额配置；索引缺失或读取异常时返回 {@code null}（调用方按不判定放行）
     */
    private ApiKey resolveApiKey(final String apiKeyId) {
        if (apiKeyService == null) {
            return null;
        }
        try {
            final String keyHash = apiKeyService.getKeyIdIndex().get(apiKeyId);
            if (keyHash == null) {
                return null;
            }
            return apiKeyService.getApiKeyCache().get(keyHash);
        } catch (RuntimeException e) {
            log.warn("读取 API Key 限额配置失败，按不判定放行: keyFp={}, error={}",
                    prefix(fingerprint(apiKeyId)), e.toString());
            return null;
        }
    }

    /**
     * 记录指标（低基数标签：服务类型 + 算法 + 是否放行）。
     *
     * @param serviceType 服务类型
     * @param decision    判定结果
     */
    private void recordMetrics(final ServiceType serviceType, final EndUserRateLimitCounter.Decision decision) {
        if (metricsCollector == null || serviceType == null) {
            return;
        }
        try {
            final String algorithm = decision.degraded() ? ALGORITHM + DEGRADED_SUFFIX : ALGORITHM;
            metricsCollector.recordRateLimit(serviceType.name().toLowerCase(Locale.ROOT),
                    algorithm, decision.allowed());
        } catch (RuntimeException e) {
            // 指标记录异常不影响业务判定；只吞运行时异常，错误（Error）照常冒泡
        }
    }

    /**
     * 计算桶键：API Key ID 的 SHA-256 前 16 位 hex。
     *
     * <p>不直接把 Key ID 写进 Redis key：与终端用户级限流保持同一姿态（日志与 key 里只出现不可逆
     * 摘要），运维在 {@code KEYS}/{@code SCAN} 的输出里也不会看到明文标识。</p>
     *
     * @param apiKeyId API Key ID
     * @return 16 位 hex 摘要
     */
    private static String bucketKey(final String apiKeyId) {
        return fingerprint(apiKeyId).substring(0, BUCKET_KEY_LENGTH);
    }

    /**
     * 计算 SHA-256 摘要（hex）。
     *
     * @param value 原值
     * @return 64 位 hex 摘要
     */
    private static String fingerprint(final String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0x0F, 16));
                sb.append(Character.forDigit(b & 0x0F, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JVM 必备算法；走到这里说明运行环境异常，用长度作退化摘要
            return "len" + value.length();
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String prefix(final String fingerprint) {
        if (fingerprint == null || fingerprint.length() <= LOG_FINGERPRINT_LENGTH) {
            return fingerprint;
        }
        return fingerprint.substring(0, LOG_FINGERPRINT_LENGTH);
    }
}

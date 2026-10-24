package org.unreal.modelrouter.router.ratelimit.enduser;

import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.Locale;
import java.util.Optional;

/**
 * 终端用户级限流门面：解析身份 → 计算桶键 → 计数判定 → 记录指标。
 *
 * <p>本类<b>不接触 HTTP</b>：调用方（主链路的 {@code ServiceRequestHandler}）把已解析好的
 * 入参传进来，返回 {@link Optional} 语义：</p>
 * <ul>
 *   <li>{@code Optional.empty()} = 本次不做判定（功能未启用 / 控制台 JWT 调用方 / 不是 v1 覆盖的
 *       服务类型 / 标识缺失且策略为 {@code skip}），调用方应直接放行；</li>
 *   <li>{@code Optional.of(decision)} = 已判定，{@code allowed=false} 时调用方应返回 429 并带上
 *       {@code Retry-After} 与 {@code X-RateLimit-*} 头。</li>
 * </ul>
 *
 * <p><b>只在 v1 路由的服务类型上生效</b>：只有 Chat / Embedding / 图片生成 / 图片编辑的请求体
 * 带 {@code user} 字段（rerank / tts / stt 没有），对它们做「用户级」限流只能依赖请求头，
 * 语义不完整，故直接跳过而不是猜。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
@Slf4j
public class EndUserRateLimitService {

    /** 指标 / 日志里的算法标识。 */
    private static final String ALGORITHM = "end-user-fixed-window";

    /** 降级时的算法标识后缀。 */
    private static final String DEGRADED_SUFFIX = "-degraded";

    /** 日志里展示的摘要长度（只露前 8 位）。 */
    private static final int LOG_FINGERPRINT_LENGTH = 8;

    private final EndUserRateLimitProperties properties;
    private final EndUserRateLimitCounter counter;
    private final MetricsCollector metricsCollector;

    /**
     * 构造门面（无指标收集器）。
     *
     * @param properties 配置，不可为 {@code null}
     * @param counter    计数器，不可为 {@code null}
     */
    public EndUserRateLimitService(final EndUserRateLimitProperties properties,
                                   final EndUserRateLimitCounter counter) {
        this(properties, counter, null);
    }

    /**
     * 构造门面。
     *
     * @param properties       配置，不可为 {@code null}
     * @param counter          计数器，不可为 {@code null}
     * @param metricsCollector 指标收集器，可为 {@code null}（不影响判定）
     */
    public EndUserRateLimitService(final EndUserRateLimitProperties properties,
                                   final EndUserRateLimitCounter counter,
                                   final MetricsCollector metricsCollector) {
        this.properties = properties;
        this.counter = counter;
        this.metricsCollector = metricsCollector;
    }

    /**
     * 判定一次请求。
     *
     * @param apiKeyId    调用方 API Key ID（由主链路从认证主体取），可为 {@code null}
     * @param requestDto  已反序列化的请求体（{@code JAIR_REQUEST_DTO}），可为 {@code null}
     * @param headerValue 请求头 {@value EndUserRateLimitProperties#HEADER_NAME} 的取值，可为 {@code null}
     * @param serviceType 服务类型，可为 {@code null}
     * @param jwtCaller   是否为控制台 JWT 调用方（是则跳过：控制台用户走限流管理面，不走本层）
     * @return 判定结果；不需要判定时为 {@link Optional#empty()}，绝不返回 {@code null}
     */
    public Optional<EndUserRateLimitCounter.Decision> evaluate(final String apiKeyId,
                                                               final Object requestDto,
                                                               final String headerValue,
                                                               final ServiceType serviceType,
                                                               final boolean jwtCaller) {
        if (properties == null || !properties.isEnabled()) {
            return Optional.empty();
        }
        if (jwtCaller || isBlank(apiKeyId) || !supported(serviceType)) {
            return Optional.empty();
        }

        final long now = System.currentTimeMillis() / 1000L;
        final long windowStart = now / EndUserRateLimitCounter.WINDOW_SECONDS
                * EndUserRateLimitCounter.WINDOW_SECONDS;
        final int limit = properties.effectiveRequestsPerMinute();

        final EndUserIdentityResolver.Resolution identity = EndUserIdentityResolver.resolve(
                headerValue, requestDto, properties.isHeaderEnabled(), properties.effectiveFingerprintLength());

        if (!identity.present()) {
            return handleMissingIdentity(serviceType, windowStart, now, limit);
        }

        final String bucketKey = EndUserIdentityResolver.bucketKey(apiKeyId, identity.identity());
        final EndUserRateLimitCounter.Decision decision = counter.record(bucketKey, windowStart, limit, now);

        if (!decision.allowed()) {
            log.warn("终端用户限流拒绝: keyFp={} userFp={} service={} limit={}/min used={}",
                    EndUserIdentityResolver.fingerprint(apiKeyId, LOG_FINGERPRINT_LENGTH),
                    prefix(identity.fingerprint()),
                    serviceType,
                    decision.limit(),
                    decision.used());
        }
        recordMetrics(serviceType, decision);
        return Optional.of(decision);
    }

    /**
     * 标识缺失时的处置。
     *
     * @param serviceType 服务类型
     * @param windowStart 窗口起点
     * @param now         当前 epoch 秒
     * @param limit       本窗口上限
     * @return {@code skip} 时为空；{@code reject} 时为拒绝判定
     */
    private Optional<EndUserRateLimitCounter.Decision> handleMissingIdentity(final ServiceType serviceType,
                                                                            final long windowStart,
                                                                            final long now,
                                                                            final int limit) {
        if (properties.getMissingIdentity() != EndUserRateLimitProperties.MissingIdentity.REJECT) {
            return Optional.empty();
        }
        final long retryAfter = EndUserRateLimitCounter.retryAfterSeconds(windowStart, now);
        log.warn("终端用户标识缺失且 missing-identity=reject: service={} limit={}/min", serviceType, limit);
        recordMetrics(serviceType, new EndUserRateLimitCounter.Decision(false, limit, limit, retryAfter, false));
        return Optional.of(new EndUserRateLimitCounter.Decision(false, limit, limit, retryAfter, false));
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
     * 是否为本层覆盖的服务类型。
     *
     * @param serviceType 服务类型，可为 {@code null}
     * @return 是否为 chat / embedding / imgGen / imgEdit
     */
    private static boolean supported(final ServiceType serviceType) {
        if (serviceType == null) {
            return false;
        }
        return switch (serviceType) {
            case chat, embedding, imgGen, imgEdit -> true;
            default -> false;
        };
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

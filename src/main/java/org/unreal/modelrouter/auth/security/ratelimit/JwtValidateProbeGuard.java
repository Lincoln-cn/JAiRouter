package org.unreal.modelrouter.auth.security.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.common.dto.AuditEventType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code POST /api/auth/jwt/validate} 的失败计数与熔断（issue #1248）。
 *
 * <p>该端点必须保持匿名可用 —— 前端的 {@code stores/user.ts} 在用户还没登录时也用它
 * 判断本地缓存的令牌是否还能用，一旦要求认证，这条路就断了。代价是它天然是一个
 * 「令牌有效性探针」：任何人都能拿它批量试令牌，且过去既不计次也不留痕。</p>
 *
 * <p>这里的收紧不改变契约（仍然匿名、仍然返回 200 + {@code valid=false}），
 * 只做两件事：</p>
 * <ol>
 *   <li>按来源 IP 统计失败次数，超过阈值后在窗口期内直接拒绝（429），
 *       让批量试探的成本从「免费」变成「有限」；</li>
 *   <li>首次越过阈值时写一条 {@link AuditEventType#BRUTE_FORCE_ATTEMPT} 审计，
 *       让运维能在台账里看到「有人在扫令牌」，而不是只能翻 debug 日志。</li>
 * </ol>
 *
 * <p>为什么按 IP 而不是按令牌：被试探的令牌本来就是攻击者伪造的，按令牌聚合毫无意义；
 * 真实攻击的形态是「同一个出口打很多个令牌」。</p>
 *
 * @since v3.10.0
 */
@Component
@ConditionalOnProperty(name = "jairouter.auth.jwt-validate-guard.enabled",
        havingValue = "true", matchIfMissing = true)
public class JwtValidateProbeGuard {

    private static final Logger log = LoggerFactory.getLogger(JwtValidateProbeGuard.class);

    /** 同一 IP 在窗口期内允许的验证失败次数 */
    @Value("${jairouter.auth.jwt-validate-guard.max-failures-per-ip:20}")
    private volatile int maxFailuresPerIp = 20;

    /** 失败计数的滑动窗口（分钟），同时也是触发后的拒绝时长 */
    @Value("${jairouter.auth.jwt-validate-guard.window-minutes:5}")
    private volatile int windowMinutes = 5;

    private static final long MINUTE_MS = 60_000L;

    /** 每个来源 IP 一个桶；桶里只放两个数字，够用且不必引第三方缓存 */
    private final Map<String, FailureBucket> buckets = new ConcurrentHashMap<>();

    private final AtomicLong lastCleanupMs = new AtomicLong(System.currentTimeMillis());

    @Autowired(required = false)
    private SecurityAuditService securityAuditService;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /**
     * 判断该来源 IP 是否已被熔断。
     *
     * @param clientIp 来源 IP，可为 null（null 视为不设限，避免把「取不到 IP」变成拒绝服务）
     * @return {@code true} 表示应直接拒绝
     */
    public boolean isBlocked(final String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        cleanupIfNeeded();
        final FailureBucket bucket = buckets.get(clientIp);
        if (bucket == null) {
            return false;
        }
        final long now = System.currentTimeMillis();
        return bucket.failures.get() >= maxFailuresPerIp
                && now - bucket.windowStartedAt.get() < windowMinutes * MINUTE_MS;
    }

    /**
     * 记一次验证失败。
     *
     * @param clientIp 来源 IP，可为 null
     * @param reason   失败原因（只进日志与审计，不回显给调用方）
     */
    public void recordFailure(final String clientIp, final String reason) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        cleanupIfNeeded();
        final long now = System.currentTimeMillis();
        final long windowMs = windowMinutes * MINUTE_MS;
        final FailureBucket bucket = buckets.computeIfAbsent(clientIp, ip -> new FailureBucket(now));
        // 窗口过期就重置，避免「很久以前的失败」把好 IP 长期挡在外面
        if (now - bucket.windowStartedAt.get() >= windowMs) {
            bucket.windowStartedAt.set(now);
            bucket.failures.set(0);
            bucket.audited.set(false);
        }
        final int failures = bucket.failures.incrementAndGet();
        if (failures == maxFailuresPerIp) {
            log.warn("JWT 验证失败次数达到阈值（ip={}, 窗口 {} 分钟, 阈值 {}），后续请求将被拒绝",
                    clientIp, windowMinutes, maxFailuresPerIp);
        }
        if (failures >= maxFailuresPerIp && bucket.audited.compareAndSet(false, true)) {
            auditProbe(clientIp, failures, reason);
        }
    }

    /**
     * 清空计数（测试与配置重置用）。
     */
    public void reset() {
        buckets.clear();
    }

    /**
     * 当前该 IP 的失败计数（观测/测试用）。
     *
     * @param clientIp 来源 IP
     * @return 窗口内的失败次数，未知 IP 返回 0
     */
    public int failureCount(final String clientIp) {
        final FailureBucket bucket = clientIp == null ? null : buckets.get(clientIp);
        return bucket == null ? 0 : bucket.failures.get();
    }

    private void auditProbe(final String clientIp, final int failures, final String reason) {
        if (meterRegistry != null) {
            meterRegistry.counter("jairouter.auth.jwt_validate.probe_blocked").increment();
        }
        if (securityAuditService == null) {
            return;
        }
        final SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType(AuditEventType.BRUTE_FORCE_ATTEMPT.name())
                .userId("anonymous")
                .resource("jwt")
                .action("VALIDATE")
                .success(false)
                .clientIp(clientIp)
                .timestamp(LocalDateTime.now())
                .additionalData(Map.of(
                        "ip", clientIp,
                        "failures", failures,
                        "windowMinutes", windowMinutes,
                        "lastReason", reason == null ? "" : reason))
                .build();
        securityAuditService.recordEvent(event)
                .subscribe(null, err -> log.warn("JWT 探测审计写入失败: {}", err.getMessage()));
    }

    /**
     * 每 5 分钟清一次过期桶，避免长时间运行后按 IP 无限增长。
     */
    private void cleanupIfNeeded() {
        final long now = System.currentTimeMillis();
        final long last = lastCleanupMs.get();
        if (now - last < 5 * MINUTE_MS || !lastCleanupMs.compareAndSet(last, now)) {
            return;
        }
        final long ttl = windowMinutes * MINUTE_MS;
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStartedAt.get() > ttl);
    }

    /** 单个 IP 的失败窗口 */
    private static final class FailureBucket {

        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicLong windowStartedAt;
        private final java.util.concurrent.atomic.AtomicBoolean audited =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        private FailureBucket(final long startedAt) {
            this.windowStartedAt = new AtomicLong(startedAt);
        }
    }
}

package org.unreal.modelrouter.auth.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.ApiException;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 登录失败频控 + 暴力破解审计（issue #697）。
 *
 * <p>背景：{@code POST /api/auth/jwt/login} 此前既不限速、也不累计失败次数 ——
 * {@link AdminApiRateLimiter} 只覆盖 {@code /api/auth/api-keys} 前缀，全仓没有锁定或指数退避实现，
 * 而 {@link AuditEventType#BRUTE_FORCE_ATTEMPT} 在生产代码里没有任何生产者（唯一发出点是生成测试
 * 审计数据的管理端点）。结果是可无限次撞库且不产生任何告警。</p>
 *
 * <p>两个计数维度，都按「窗口内失败次数」判定，达到阈值即锁定，锁定期间的登录请求直接 429：</p>
 * <ul>
 *   <li><b>IP + 用户名</b>（默认 5 次 / 15 分钟窗口 / 锁定 15 分钟）：锁定单个账号的撞库；</li>
 *   <li><b>仅 IP</b>（默认 30 次 / 15 分钟窗口）：防「换用户名继续撞」。阈值刻意高于按账号的，
 *       因为共享出口（NAT / 办公网）下多个用户名的失败会叠加到同一个 IP 计数上。</li>
 * </ul>
 *
 * <p>阈值、窗口与锁定时长可经 {@code jairouter.auth.login-bruteforce.*} 覆盖（见字段默认值）。
 * 单测直接 {@code new LoginBruteForceGuard(mock)} 再用 {@code ReflectionTestUtils} 改阈值。</p>
 *
 * <p><b>状态是单实例内存</b>（与 {@link AdminApiRateLimiter} 同款形态）：多副本部署时每个副本各自计数，
 * 攻击者把尝试分摊到多个副本可降低被锁概率。跨副本共享计数需要 Redis 原子计数，本次不做。</p>
 *
 * <p>审计事件只在「刚跨过阈值」的那一刻发一次：锁定期内的后续请求在 {@link #checkNotLocked} 就被拒、
 * 不会进入 {@link #recordFailure}，因此不会刷屏。</p>
 *
 * @since 2026-10（issue #697）
 */
@Slf4j
@Component
public class LoginBruteForceGuard {

    /** 同一来源 IP + 同一用户名的失败阈值（达到即锁定）。 */
    @Value("${jairouter.auth.login-bruteforce.max-failures:5}")
    private int maxFailuresPerPrincipal = 5;

    /** 同一来源 IP 跨用户名的失败阈值（防「换用户名继续撞库」）。 */
    @Value("${jairouter.auth.login-bruteforce.max-failures-per-ip:30}")
    private int maxFailuresPerIp = 30;

    /** 失败计数窗口（分钟）：窗口之前的失败不计入。 */
    @Value("${jairouter.auth.login-bruteforce.window-minutes:15}")
    private int windowMinutes = 15;

    /** 达到阈值后的锁定时长（分钟）。 */
    @Value("${jairouter.auth.login-bruteforce.lock-minutes:15}")
    private int lockMinutes = 15;

    private static final long MINUTE_MS = 60_000L;
    private static final long CLEANUP_INTERVAL_MS = 5 * MINUTE_MS;
    private static final long RETENTION_MS = 60 * MINUTE_MS;
    private static final String PRINCIPAL_PREFIX = "principal:";
    private static final String IP_PREFIX = "ip:";
    private static final String KEY_SEPARATOR = "|";

    private final ExtendedSecurityAuditService auditService;

    private final Map<String, FailureState> states = new ConcurrentHashMap<>();

    private final AtomicLong lastCleanupMs = new AtomicLong(System.currentTimeMillis());

    public LoginBruteForceGuard(final ExtendedSecurityAuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 检查该主体此刻是否仍处于锁定窗口内。
     *
     * @param username 登录请求里的用户名（可为 null）
     * @param clientIp 客户端 IP（null 归一为空串）
     * @return 未锁定时为空完成；锁定时以 {@link ErrorCode#RATE_LIMITED_LOGIN} 的 {@link ApiException}
     *         （HTTP 429）终止
     */
    public Mono<Void> checkNotLocked(final String username, final String clientIp) {
        final long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        if (isLocked(principalKey(username, clientIp), now) || isLocked(ipKey(clientIp), now)) {
            log.warn("登录请求被拒绝：仍在锁定窗口内, username={}, ip={}", username, clientIp);
            return Mono.error(ApiException.of(ErrorCode.RATE_LIMITED_LOGIN.code(),
                    "登录尝试过于频繁，请稍后再试"));
        }
        return Mono.empty();
    }

    /**
     * 记录一次登录失败；刚好跨过阈值时发出 {@code BRUTE_FORCE_ATTEMPT} 审计事件并开始锁定。
     *
     * @param username 登录请求里的用户名（可为 null）
     * @param clientIp 客户端 IP（null 归一为空串）
     */
    public void recordFailure(final String username, final String clientIp) {
        final long now = System.currentTimeMillis();
        if (crossedThreshold(principalKey(username, clientIp), maxFailuresPerPrincipal, now)) {
            auditBruteForce(username, clientIp, "account", maxFailuresPerPrincipal);
        }
        if (crossedThreshold(ipKey(clientIp), maxFailuresPerIp, now)) {
            auditBruteForce(username, clientIp, "ip", maxFailuresPerIp);
        }
    }

    /**
     * 登录成功：清零该 (IP, 用户名) 的失败计数。
     *
     * <p>只清账号维度，不清 IP 维度 —— 否则攻击者只要持有一个有效账号，就能用「失败若干次 + 成功一次」
     * 的节奏把跨用户名的 IP 计数一直归零。</p>
     *
     * @param username 登录请求里的用户名（可为 null）
     * @param clientIp 客户端 IP（null 归一为空串）
     */
    public void recordSuccess(final String username, final String clientIp) {
        final FailureState state = states.get(principalKey(username, clientIp));
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.failures.set(0);
            state.windowStartMs = System.currentTimeMillis();
        }
    }

    private boolean isLocked(final String key, final long now) {
        final FailureState state = states.get(key);
        return state != null && state.lockedUntilMs > now;
    }

    private boolean crossedThreshold(final String key, final int threshold, final long now) {
        final FailureState state = states.computeIfAbsent(key, k -> new FailureState(now));
        synchronized (state) {
            if (state.lockedUntilMs > now) {
                return false;
            }
            if (now - state.windowStartMs >= windowMs()) {
                state.windowStartMs = now;
                state.failures.set(0);
            }
            state.lastFailureMs = now;
            final int failures = state.failures.incrementAndGet();
            if (failures < threshold) {
                return false;
            }
            state.failures.set(0);
            state.windowStartMs = now;
            state.lockedUntilMs = now + lockMs();
            return true;
        }
    }

    private long windowMs() {
        return Math.max(0L, windowMinutes) * MINUTE_MS;
    }

    private long lockMs() {
        return Math.max(0L, lockMinutes) * MINUTE_MS;
    }

    private void auditBruteForce(final String username, final String clientIp,
            final String scope, final int threshold) {
        final String details = String.format(
                "登录失败次数达到阈值：scope=%s, threshold=%d, windowMinutes=%d, lockMinutes=%d",
                scope, threshold, windowMinutes, lockMinutes);
        log.warn("检测到登录暴力破解尝试: username={}, ip={}, {}", username, clientIp, details);
        try {
            auditService.auditSecurityEvent(AuditEventType.BRUTE_FORCE_ATTEMPT.name(), details, username, clientIp)
                    .onErrorResume(ex -> {
                        log.warn("记录暴力破解审计事件失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (RuntimeException ex) {
            // 审计写入不得影响登录链：审计面异常只记日志
            log.warn("记录暴力破解审计事件失败: {}", ex.getMessage());
        }
    }

    private void cleanupIfNeeded(final long now) {
        final long last = lastCleanupMs.get();
        if (now - last < CLEANUP_INTERVAL_MS) {
            return;
        }
        if (!lastCleanupMs.compareAndSet(last, now)) {
            return;
        }
        final long cutoff = now - RETENTION_MS;
        states.entrySet().removeIf(entry -> entry.getValue().lastFailureMs < cutoff
                && entry.getValue().lockedUntilMs < cutoff);
        log.debug("清理过期的登录失败计数器，剩余: {}", states.size());
    }

    private static String principalKey(final String username, final String clientIp) {
        return PRINCIPAL_PREFIX + normalize(clientIp) + KEY_SEPARATOR + normalize(username);
    }

    private static String ipKey(final String clientIp) {
        return IP_PREFIX + normalize(clientIp);
    }

    /** 归一化：null 视为空串；用户名大小写不敏感（与登录时的账号查找口径无关，仅用于计数归组）。 */
    private static String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 单个计数键的窗口状态；锁与读都在 {@code synchronized(state)} 内进行。 */
    private static final class FailureState {

        private final AtomicInteger failures = new AtomicInteger(0);

        private volatile long windowStartMs;

        private volatile long lockedUntilMs;

        private volatile long lastFailureMs;

        private FailureState(final long now) {
            this.windowStartMs = now;
            this.lastFailureMs = now;
        }
    }
}

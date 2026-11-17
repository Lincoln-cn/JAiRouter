package org.unreal.modelrouter.auth.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.ApiException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * <p><b>参数可热改（issue #1176）</b>：四个阈值以 {@link LoginGuardSettings} 整体生效，启动时由
 * {@code @Value} 从 YAML 取值，随后 {@code LoginGuardConfigService} 用数据库里那一行覆盖；
 * 控制台改完立即调 {@link #applySettings(LoginGuardSettings)}，不清计数、不需要重启。
 * {@link #snapshotLocked()} 与 {@link #unlock(String, String)} 供「登录保护」页展示与解锁。</p>
 *
 * <p>单测直接 {@code new LoginBruteForceGuard(mock)} 再用 {@code ReflectionTestUtils} 改阈值
 * （历史写法，本轮保留字段级覆盖能力）。</p>
 *
 * <p><b>状态是单实例内存</b>（与 {@link AdminApiRateLimiter} 同款形态）：多副本部署时每个副本各自计数，
 * 攻击者把尝试分摊到多个副本可降低被锁概率。跨副本共享计数需要 Redis 原子计数，本次不做。</p>
 *
 * <p>审计事件只在「刚跨过阈值」的那一刻发一次：锁定期内的后续请求在 {@link #checkNotLocked} 就被拒、
 * 不会进入 {@link #recordFailure}，因此不会刷屏。</p>
 *
 * @since 2026-10（issue #697）；2026-11 参数热改与解锁（issue #1176）
 */
@Slf4j
@Component
public class LoginBruteForceGuard {

    /** 同一来源 IP + 同一用户名的失败阈值（达到即锁定）。 */
    @Value("${jairouter.auth.login-bruteforce.max-failures:5}")
    private volatile int maxFailuresPerPrincipal = 5;

    /** 同一来源 IP 跨用户名的失败阈值（防「换用户名继续撞库」）。 */
    @Value("${jairouter.auth.login-bruteforce.max-failures-per-ip:30}")
    private volatile int maxFailuresPerIp = 30;

    /** 失败计数窗口（分钟）：窗口之前的失败不计入。 */
    @Value("${jairouter.auth.login-bruteforce.window-minutes:15}")
    private volatile int windowMinutes = 15;

    /** 达到阈值后的锁定时长（分钟）。 */
    @Value("${jairouter.auth.login-bruteforce.lock-minutes:15}")
    private volatile int lockMinutes = 15;

    private static final long MINUTE_MS = 60_000L;
    private static final long SECOND_MS = 1_000L;
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
     * 当前生效参数快照。
     *
     * @return 四个阈值的不可变视图
     */
    public LoginGuardSettings settings() {
        return new LoginGuardSettings(maxFailuresPerPrincipal, maxFailuresPerIp, windowMinutes, lockMinutes);
    }

    /**
     * 整体替换生效参数（热改入口）。
     *
     * <p>不清计数：放宽阈值后原本被锁的主体仍按旧锁到期时间解锁 —— 想让它们立刻恢复请调
     * {@link #unlock(String, String)} 或 {@link #unlockAll()}，两件事分开做，避免「改阈值」被误当成「放行」。</p>
     *
     * @param settings 新参数（构造时已校验）
     */
    public void applySettings(final LoginGuardSettings settings) {
        this.maxFailuresPerPrincipal = settings.maxFailuresPerPrincipal();
        this.maxFailuresPerIp = settings.maxFailuresPerIp();
        this.windowMinutes = settings.windowMinutes();
        this.lockMinutes = settings.lockMinutes();
    }

    /**
     * 检查该主体此刻是否仍处于锁定窗口内。
     *
     * @param username 登录请求里的用户名（可为 null）
     * @param clientIp 客户端 IP（null 归一为空串）
     * @return 未锁定时为空完成；锁定时以 {@link ErrorCode#RATE_LIMITED_LOGIN} 的 {@link ApiException}
     *         （HTTP 429）终止，文案里带剩余等待时间
     */
    public Mono<Void> checkNotLocked(final String username, final String clientIp) {
        final long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        final FailureState principalState = states.get(principalKey(username, clientIp));
        final FailureState ipState = states.get(ipKey(clientIp));
        final long lockedUntil = Math.max(lockedUntilOf(principalState), lockedUntilOf(ipState));
        if (lockedUntil > now) {
            final long remainingMs = lockedUntil - now;
            log.warn("登录请求被拒绝：仍在锁定窗口内, username={}, ip={}, 剩余 {} 秒",
                    username, clientIp, remainingMs / SECOND_MS);
            return Mono.error(ApiException.of(ErrorCode.RATE_LIMITED_LOGIN.code(),
                    "登录尝试过于频繁，请在 " + formatRemaining(remainingMs) + "后重试"));
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

    /**
     * 列出当前仍在锁定窗口内的主体（按解锁时间升序）。
     *
     * <p>只返回「正在被锁」的条目：计数未达阈值的不展示，避免把控制台变成撞库观测台。</p>
     *
     * @return 被锁主体快照；无锁定时为空列表
     */
    public List<LockedPrincipal> snapshotLocked() {
        final long now = System.currentTimeMillis();
        final List<LockedPrincipal> result = new ArrayList<>();
        states.forEach((key, state) -> {
            final long lockedUntil = state.lockedUntilMs;
            if (lockedUntil <= now) {
                return;
            }
            result.add(toLockedPrincipal(key, lockedUntil, (lockedUntil - now) / SECOND_MS));
        });
        result.sort(Comparator.comparingLong(LockedPrincipal::remainingSeconds));
        return result;
    }

    /**
     * 解除锁定并清空计数。
     *
     * <p>参数组合语义：</p>
     * <ul>
     *   <li>{@code username} 与 {@code ip} 都非空 ⇒ 只解 (IP, 用户名) 这一条；</li>
     *   <li>只有 {@code username} ⇒ 解该用户名在所有 IP 上的条目；</li>
     *   <li>只有 {@code ip} ⇒ 解该 IP 的条目（含跨用户名的 IP 计数）；</li>
     *   <li>都为空 ⇒ 等同 {@link #unlockAll()}。</li>
     * </ul>
     *
     * @param username 用户名（可为 null）
     * @param ip       客户端 IP（可为 null）
     * @return 实际清除的计数键数量
     */
    public int unlock(final String username, final String ip) {
        final boolean hasUser = username != null && !username.trim().isEmpty();
        final boolean hasIp = ip != null && !ip.trim().isEmpty();
        if (!hasUser && !hasIp) {
            return unlockAll();
        }
        if (hasUser && hasIp) {
            return states.remove(principalKey(username, ip)) != null ? 1 : 0;
        }
        final String ipKey = hasIp ? ipKey(ip) : null;
        final String suffix = hasUser ? KEY_SEPARATOR + normalize(username) : null;
        final List<String> targets = new ArrayList<>();
        states.keySet().forEach(key -> {
            if (ipKey != null && key.equals(ipKey)) {
                targets.add(key);
            } else if (suffix != null && key.startsWith(PRINCIPAL_PREFIX) && key.endsWith(suffix)) {
                targets.add(key);
            }
        });
        targets.forEach(states::remove);
        return targets.size();
    }

    /**
     * 清空全部计数与锁定（「全部解锁」）。
     *
     * @return 清除的计数键数量
     */
    public int unlockAll() {
        final int size = states.size();
        states.clear();
        return size;
    }

    private long lockedUntilOf(final FailureState state) {
        return state == null ? 0L : state.lockedUntilMs;
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

    /** 把计数键还原成可读的锁定条目。 */
    private static LockedPrincipal toLockedPrincipal(final String key, final long lockedUntil, final long remaining) {
        if (key.startsWith(PRINCIPAL_PREFIX)) {
            final String rest = key.substring(PRINCIPAL_PREFIX.length());
            final int separator = rest.indexOf(KEY_SEPARATOR);
            final String ip = separator >= 0 ? rest.substring(0, separator) : rest;
            final String username = separator >= 0 ? rest.substring(separator + KEY_SEPARATOR.length()) : "";
            return new LockedPrincipal("account", username, ip, lockedUntil, remaining);
        }
        final String ip = key.startsWith(IP_PREFIX) ? key.substring(IP_PREFIX.length()) : key;
        return new LockedPrincipal("ip", "", ip, lockedUntil, remaining);
    }

    /** 剩余时间的人类可读形式（秒级；超过 1 分钟折成「N 分 M 秒」）。 */
    private static String formatRemaining(final long remainingMs) {
        final long seconds = Math.max(1L, (remainingMs + SECOND_MS - 1) / SECOND_MS);
        if (seconds < 60) {
            return seconds + " 秒";
        }
        return (seconds / 60) + " 分 " + (seconds % 60) + " 秒";
    }

    /**
     * 一条被锁主体的快照。
     *
     * @param scope            锁定维度：{@code account}（IP + 用户名）或 {@code ip}（仅 IP）
     * @param username         用户名（{@code scope=ip} 时为空串）
     * @param ip               客户端 IP
     * @param lockedUntilEpochMs 解锁时间（epoch 毫秒）
     * @param remainingSeconds 剩余秒数
     */
    public record LockedPrincipal(String scope, String username, String ip,
            long lockedUntilEpochMs, long remainingSeconds) {
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

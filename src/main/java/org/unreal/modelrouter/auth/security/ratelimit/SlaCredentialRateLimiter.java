package org.unreal.modelrouter.auth.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.util.ClientIpResolver;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SLA 凭据入口的速率限制（issue #1216）。
 *
 * <p>{@code /api/auth/sla/bootstrap} 与 {@code /api/auth/sla/refresh} 是 <strong>permitAll</strong> 的：
 * 它们靠「持有令牌」自证身份，不经过任何认证主体。而控制面既有的 {@code AdminApiRateLimiter}
 * 只认 {@code /api/auth/api-keys} 前缀，登录面另有 {@code LoginBruteForceGuard} —— 于是这两条
 * 入口此前完全没有限流，任何人都能按机器速度去猜接入令牌与刷新凭据。</p>
 *
 * <p>本过滤器按来源 IP 分桶，同时覆盖消费侧（{@code /api/auth/sla/}）与管理侧
 * （{@code /api/security/sla/}）。限流键取 {@link ClientIpResolver} 的解析结果（与审计、
 * 既有限流器同一套信任策略），默认每分钟 120 次、每小时 1000 次，可通过
 * {@code jairouter.auth.sla-rate-limit.*} 覆盖。</p>
 *
 * <p>超限返回 429 与 {@link ErrorCode#RATE_LIMIT_EXCEEDED}，形状与 {@code AdminApiRateLimiter}
 * 一致，客户端不需要为控制面记两套错误体。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true", matchIfMissing = true)
public class SlaCredentialRateLimiter implements WebFilter {

    /** 凭据消费面（bootstrap / refresh）。 */
    private static final String SLA_AUTH_PREFIX = "/api/auth/sla/";

    /** 凭据管理面（签发 / 吊销 / 启停）。 */
    private static final String SLA_ADMIN_PREFIX = "/api/security/sla/";

    /** 每分钟上限。 */
    @Value("${jairouter.auth.sla-rate-limit.per-minute:120}")
    private int limitPerMinute = 120;

    /** 每小时上限。 */
    @Value("${jairouter.auth.sla-rate-limit.per-hour:1000}")
    private int limitPerHour = 1000;

    private final Map<String, SlaRequestCounter> counters = new ConcurrentHashMap<>();

    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;

    private volatile long lastCleanupTime = System.currentTimeMillis();

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(SLA_AUTH_PREFIX) && !path.startsWith(SLA_ADMIN_PREFIX)) {
            return chain.filter(exchange);
        }

        final String clientIp = ClientIpResolver.resolve(exchange);
        cleanupIfNeeded();

        final SlaRequestCounter counter = counters.computeIfAbsent(clientIp, key -> new SlaRequestCounter());
        if (!counter.tryAcquire(limitPerMinute, limitPerHour)) {
            log.warn("SLA 凭据入口速率限制触发, IP: {}, 路径: {}", clientIp, path);
            return writeRateLimited(exchange);
        }
        return chain.filter(exchange);
    }

    private static Mono<Void> writeRateLimited(final ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        final String body = "{\"code\":\"" + ErrorCode.RATE_LIMIT_EXCEEDED.code()
                + "\",\"message\":\"请求过于频繁，请稍后再试\"}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private void cleanupIfNeeded() {
        final long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            final long hourAgo = now - Duration.ofHours(1).toMillis();
            counters.entrySet().removeIf(entry -> entry.getValue().lastAccessTime < hourAgo);
            log.debug("清理过期 SLA 限流计数器，剩余: {}", counters.size());
        }
    }

    /**
     * 单个来源 IP 的两个定长窗口计数器。
     *
     * <p>用定长窗口而不是滑动窗口：这里要防的是「机器速度猜凭据」，不是精确配额，
     * 定长窗口的边界双倍放行（最多 2 倍）对这类攻击足够。整个方法加锁 —— 单 IP 的
     * 计数开销远小于一次数据库查询，不值得为它做无锁设计。</p>
     */
    private static final class SlaRequestCounter {

        private long minuteWindowStart = System.currentTimeMillis();
        private int minuteCount;
        private long hourWindowStart = System.currentTimeMillis();
        private int hourCount;
        private volatile long lastAccessTime = System.currentTimeMillis();

        synchronized boolean tryAcquire(final int perMinute, final int perHour) {
            final long now = System.currentTimeMillis();
            lastAccessTime = now;

            if (now - minuteWindowStart >= Duration.ofMinutes(1).toMillis()) {
                minuteWindowStart = now;
                minuteCount = 0;
            }
            if (now - hourWindowStart >= Duration.ofHours(1).toMillis()) {
                hourWindowStart = now;
                hourCount = 0;
            }
            if (minuteCount >= perMinute || hourCount >= perHour) {
                return false;
            }
            minuteCount++;
            hourCount++;
            return true;
        }
    }
}

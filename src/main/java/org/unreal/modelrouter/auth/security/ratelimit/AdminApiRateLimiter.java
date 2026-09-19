package org.unreal.modelrouter.auth.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理接口速率限制过滤器。
 * 针对 {@code /api/auth/api-keys} 等敏感管理接口进行速率限制。
 *
 * <p>v3.2 UX：读写分桶。控制台列表页/刷新会高频 GET，若与创建/删除共用同一配额，
 * 管理员正常使用就会把自己限流掉。因此：</p>
 * <ul>
 *   <li>GET/HEAD（读）：默认 120 次/分钟、2000 次/小时</li>
 *   <li>POST/PUT/PATCH/DELETE（写）：默认 30 次/分钟、200 次/小时</li>
 *   <li>POST 创建：额外 10 次/小时（防误刷创建）</li>
 * </ul>
 *
 * <p>限额可通过 {@code jairouter.auth.admin-api-rate-limit.*} 配置覆盖；
 * 无 Spring 注入时（单测 {@code new AdminApiRateLimiter()}）使用字段默认值。</p>
 *
 * <p>v3.1 PR-4d.1：429 错误体（含中文提示）显式按 {@link StandardCharsets#UTF_8} 编码写出。</p>
 */
@Slf4j
@Component
public class AdminApiRateLimiter implements WebFilter {

    private static final String ADMIN_API_PREFIX = "/api/auth/api-keys";

    /** 读操作（GET/HEAD）每分钟上限 */
    @Value("${jairouter.auth.admin-api-rate-limit.get-per-minute:120}")
    private int getLimitPerMinute = 120;

    /** 读操作每小时上限 */
    @Value("${jairouter.auth.admin-api-rate-limit.get-per-hour:2000}")
    private int getLimitPerHour = 2000;

    /** 写操作（POST/PUT/PATCH/DELETE）每分钟上限 */
    @Value("${jairouter.auth.admin-api-rate-limit.mutate-per-minute:30}")
    private int mutateLimitPerMinute = 30;

    /** 写操作每小时上限 */
    @Value("${jairouter.auth.admin-api-rate-limit.mutate-per-hour:200}")
    private int mutateLimitPerHour = 200;

    /** 创建（POST）每小时上限 */
    @Value("${jairouter.auth.admin-api-rate-limit.create-per-hour:10}")
    private int createLimitPerHour = 10;

    private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();

    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;
    private volatile long lastCleanupTime = System.currentTimeMillis();

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(ADMIN_API_PREFIX)) {
            return chain.filter(exchange);
        }

        final String clientIp = getClientIp(exchange);
        cleanupIfNeeded();

        final RequestCounter counter = counters.computeIfAbsent(clientIp, k -> new RequestCounter());
        final String method = exchange.getRequest().getMethod().name();
        final boolean read = isReadMethod(method);

        if (!read && "POST".equals(method) && !counter.tryAcquireCreate(createLimitPerHour)) {
            log.warn("API Key创建速率限制触发, IP: {}, 路径: {}", clientIp, path);
            return writeRateLimited(exchange, "创建操作过于频繁，请稍后再试");
        }

        if (read) {
            if (!counter.tryAcquireRead(getLimitPerMinute, getLimitPerHour)) {
                log.warn("API Key管理接口读操作速率限制触发, IP: {}, 路径: {}", clientIp, path);
                return writeRateLimited(exchange, "请求过于频繁，请稍后再试");
            }
        } else {
            if (!counter.tryAcquireMutate(mutateLimitPerMinute, mutateLimitPerHour)) {
                log.warn("API Key管理接口写操作速率限制触发, IP: {}, 路径: {}", clientIp, path);
                return writeRateLimited(exchange, "请求过于频繁，请稍后再试");
            }
        }

        return chain.filter(exchange);
    }

    private static boolean isReadMethod(final String method) {
        final String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        return "GET".equals(m) || "HEAD".equals(m);
    }

    private static Mono<Void> writeRateLimited(final ServerWebExchange exchange, final String message) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        final String body = "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"" + message + "\"}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private String getClientIp(final ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            final int index = ip.indexOf(',');
            if (index != -1) {
                ip = ip.substring(0, index);
            }
            return ip.trim();
        }

        ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private void cleanupIfNeeded() {
        final long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            final long hourAgo = now - Duration.ofHours(1).toMillis();
            counters.entrySet().removeIf(entry -> entry.getValue().lastAccessTime < hourAgo);
            log.debug("清理过期速率限制计数器，剩余: {}", counters.size());
        }
    }

    /**
     * 读/写分桶计数器：控制台列表 GET 不再消耗写操作配额。
     */
    static final class RequestCounter {
        private static final long MINUTE_MS = 60 * 1000L;
        private static final long HOUR_MS = 60 * 60 * 1000L;

        private final AtomicLong readMinuteCount = new AtomicLong(0);
        private final AtomicLong readHourCount = new AtomicLong(0);
        private final AtomicLong mutateMinuteCount = new AtomicLong(0);
        private final AtomicLong mutateHourCount = new AtomicLong(0);
        private final AtomicLong createCount = new AtomicLong(0);

        private volatile long readMinuteStart = System.currentTimeMillis();
        private volatile long readHourStart = System.currentTimeMillis();
        private volatile long mutateMinuteStart = System.currentTimeMillis();
        private volatile long mutateHourStart = System.currentTimeMillis();
        private volatile long createStart = System.currentTimeMillis();
        private volatile long lastAccessTime = System.currentTimeMillis();

        boolean tryAcquireRead(final int minuteLimit, final int hourLimit) {
            lastAccessTime = System.currentTimeMillis();
            final long now = lastAccessTime;
            if (now - readMinuteStart >= MINUTE_MS) {
                readMinuteStart = now;
                readMinuteCount.set(0);
            }
            if (now - readHourStart >= HOUR_MS) {
                readHourStart = now;
                readHourCount.set(0);
            }
            return readMinuteCount.incrementAndGet() <= minuteLimit
                    && readHourCount.incrementAndGet() <= hourLimit;
        }

        boolean tryAcquireMutate(final int minuteLimit, final int hourLimit) {
            lastAccessTime = System.currentTimeMillis();
            final long now = lastAccessTime;
            if (now - mutateMinuteStart >= MINUTE_MS) {
                mutateMinuteStart = now;
                mutateMinuteCount.set(0);
            }
            if (now - mutateHourStart >= HOUR_MS) {
                mutateHourStart = now;
                mutateHourCount.set(0);
            }
            return mutateMinuteCount.incrementAndGet() <= minuteLimit
                    && mutateHourCount.incrementAndGet() <= hourLimit;
        }

        boolean tryAcquireCreate(final int hourLimit) {
            lastAccessTime = System.currentTimeMillis();
            final long now = lastAccessTime;
            if (now - createStart >= HOUR_MS) {
                createStart = now;
                createCount.set(0);
            }
            return createCount.incrementAndGet() <= hourLimit;
        }
    }
}

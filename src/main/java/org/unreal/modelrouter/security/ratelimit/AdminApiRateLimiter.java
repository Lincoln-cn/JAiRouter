package org.unreal.modelrouter.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理接口速率限制过滤器
 * 针对 /api/auth/api-keys 等敏感管理接口进行速率限制
 * 
 * 限制策略：
 * - 每个 IP 每分钟最多 30 次请求
 * - 每个 IP 每小时最多 100 次请求
 * - 创建操作每个 IP 每小时最多 10 次
 */
@Slf4j
@Component
public class AdminApiRateLimiter implements WebFilter {

    // 需要进行速率限制的路径前缀
    private static final String ADMIN_API_PREFIX = "/api/auth/api-keys";
    
    // 每分钟限制
    private static final int LIMIT_PER_MINUTE = 30;
    // 每小时限制
    private static final int LIMIT_PER_HOUR = 100;
    // 创建操作每小时限制
    private static final int CREATE_LIMIT_PER_HOUR = 10;
    
    // IP -> 请求计数器
    private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();
    
    // 清理间隔（5分钟）
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;
    private volatile long lastCleanupTime = System.currentTimeMillis();
    
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 只对管理 API 进行速率限制
        if (!path.startsWith(ADMIN_API_PREFIX)) {
            return chain.filter(exchange);
        }
        
        // 获取客户端 IP
        String clientIp = getClientIp(exchange);
        
        // 定期清理过期计数器
        cleanupIfNeeded();
        
        // 检查速率限制
        RequestCounter counter = counters.computeIfAbsent(clientIp, k -> new RequestCounter());
        
        String method = exchange.getRequest().getMethod().name();
        
        // 创建操作有更严格的限制
        if ("POST".equals(method)) {
            if (!counter.tryAcquireCreate()) {
                log.warn("API Key创建速率限制触发, IP: {}, 路径: {}", clientIp, path);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().writeWith(
                        Mono.just(exchange.getResponse().bufferFactory()
                                .wrap("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"创建操作过于频繁，请稍后再试\"}"
                                        .getBytes())));
            }
        }
        
        // 检查每分钟限制
        if (!counter.tryAcquireMinute()) {
            log.warn("API Key管理接口分钟速率限制触发, IP: {}, 路径: {}", clientIp, path);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory()
                            .wrap("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"请求过于频繁，请稍后再试\"}"
                                    .getBytes())));
        }
        
        // 检查每小时限制
        if (!counter.tryAcquireHour()) {
            log.warn("API Key管理接口小时速率限制触发, IP: {}, 路径: {}", clientIp, path);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory()
                            .wrap("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"每小时请求次数已达上限\"}"
                                    .getBytes())));
        }
        
        return chain.filter(exchange);
    }
    
    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(final ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多个代理时取第一个
            int index = ip.indexOf(',');
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
    
    /**
     * 定期清理过期的计数器
     */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            
            long hourAgo = now - Duration.ofHours(1).toMillis();
            counters.entrySet().removeIf(entry -> entry.getValue().lastAccessTime < hourAgo);
            
            log.debug("清理过期速率限制计数器，剩余: {}", counters.size());
        }
    }
    
    /**
     * 请求计数器
     */
    private static class RequestCounter {
        private final AtomicLong minuteCount = new AtomicLong(0);
        private final AtomicLong hourCount = new AtomicLong(0);
        private final AtomicLong createCount = new AtomicLong(0);
        private volatile long minuteStartTime = System.currentTimeMillis();
        private volatile long hourStartTime = System.currentTimeMillis();
        private volatile long createStartTime = System.currentTimeMillis();
        private volatile long lastAccessTime = System.currentTimeMillis();
        
        private static final long MINUTE_MS = 60 * 1000;
        private static final long HOUR_MS = 60 * 60 * 1000;
        
        boolean tryAcquireMinute() {
            lastAccessTime = System.currentTimeMillis();
            long now = lastAccessTime;
            
            // 检查是否需要重置分钟计数器
            if (now - minuteStartTime >= MINUTE_MS) {
                minuteStartTime = now;
                minuteCount.set(0);
            }
            
            return minuteCount.incrementAndGet() <= LIMIT_PER_MINUTE;
        }
        
        boolean tryAcquireHour() {
            lastAccessTime = System.currentTimeMillis();
            long now = lastAccessTime;
            
            // 检查是否需要重置小时计数器
            if (now - hourStartTime >= HOUR_MS) {
                hourStartTime = now;
                hourCount.set(0);
            }
            
            return hourCount.incrementAndGet() <= LIMIT_PER_HOUR;
        }
        
        boolean tryAcquireCreate() {
            lastAccessTime = System.currentTimeMillis();
            long now = lastAccessTime;
            
            // 检查是否需要重置创建计数器
            if (now - createStartTime >= HOUR_MS) {
                createStartTime = now;
                createCount.set(0);
            }
            
            return createCount.incrementAndGet() <= CREATE_LIMIT_PER_HOUR;
        }
    }
}
package org.unreal.modelrouter.auth.security.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT清理服务监控指标管理器
 * 负责初始化和记录Micrometer指标
 */
@Slf4j
@Getter
public class JwtCleanupMetrics {

    private final Timer cleanupTimer;
    private final Counter tokenCleanupCounter;
    private final Counter blacklistCleanupCounter;
    private final Counter cleanupFailureCounter;

    public JwtCleanupMetrics(final MeterRegistry meterRegistry) {
        this.cleanupTimer = Timer.builder("jwt.cleanup.duration")
            .description("JWT cleanup operation duration")
            .register(meterRegistry);

        this.tokenCleanupCounter = Counter.builder("jwt.cleanup.tokens.removed")
            .description("Number of tokens removed during cleanup")
            .register(meterRegistry);

        this.blacklistCleanupCounter = Counter.builder("jwt.cleanup.blacklist.removed")
            .description("Number of blacklist entries removed during cleanup")
            .register(meterRegistry);

        this.cleanupFailureCounter = Counter.builder("jwt.cleanup.failures")
            .description("Number of failed cleanup operations")
            .register(meterRegistry);

        log.debug("JwtCleanupMetrics initialized");
    }

    /**
     * 记录令牌清理数量
     * @param count 清理数量
     */
    public void recordTokenCleanup(final long count) {
        tokenCleanupCounter.increment(count);
    }

    /**
     * 记录黑名单清理数量
     * @param count 清理数量
     */
    public void recordBlacklistCleanup(final long count) {
        blacklistCleanupCounter.increment(count);
    }

    /**
     * 记录清理失败
     */
    public void recordFailure() {
        cleanupFailureCounter.increment();
    }

    /**
     * 获取总清理时间（毫秒）
     */
    public double getTotalCleanupTimeMs() {
        return cleanupTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 获取令牌清理计数
     */
    public double getTokenCleanupCount() {
        return tokenCleanupCounter.count();
    }

    /**
     * 获取黑名单清理计数
     */
    public double getBlacklistCleanupCount() {
        return blacklistCleanupCounter.count();
    }

    /**
     * 获取失败计数
     */
    public double getFailureCount() {
        return cleanupFailureCounter.count();
    }
}

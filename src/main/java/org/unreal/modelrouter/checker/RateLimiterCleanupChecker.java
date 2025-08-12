package org.unreal.modelrouter.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

/**
 * 限流器清理检查器
 * 定期清理不活跃的客户端IP限流器，防止内存泄漏
 */
@Component
public class RateLimiterCleanupChecker {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterCleanupChecker.class);

    private final RateLimitManager rateLimitManager;

    public RateLimiterCleanupChecker(RateLimitManager rateLimitManager) {
        this.rateLimitManager = rateLimitManager;
    }

    /**
     * 定时清理不活跃的客户端IP限流器
     * 每5分钟执行一次清理任务
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300,000毫秒
    public void cleanupInactiveRateLimiters() {
        log.debug("开始清理不活跃的客户端IP限流器");
        
        try {
            rateLimitManager.cleanupInactiveClientIpLimiters();
            log.debug("客户端IP限流器清理任务完成");
        } catch (Exception e) {
            log.error("清理客户端IP限流器时发生错误", e);
        }
    }
}
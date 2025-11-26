package org.unreal.modelrouter.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * JWT令牌过期状态自动更新调度器
 * 定期检查并更新过期令牌的状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.enabled", havingValue = "true")
public class JwtTokenExpirationScheduler {
    
    @Autowired(required = false)
    private JwtTokenLifecycleService jwtTokenLifecycleService;
    
    /**
     * 每小时执行一次过期令牌状态更新
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 3600000毫秒
    public void updateExpiredTokens() {
        if (jwtTokenLifecycleService == null) {
            log.debug("JWT令牌生命周期服务未启用，跳过过期令牌更新");
            return;
        }
        
        log.debug("开始执行过期令牌状态更新任务");
        
        jwtTokenLifecycleService.updateExpiredTokens()
            .subscribe(
                count -> {
                    if (count > 0) {
                        log.info("过期令牌状态更新完成，共更新{}个令牌", count);
                    } else {
                        log.debug("没有发现需要更新的过期令牌");
                    }
                },
                error -> log.error("过期令牌状态更新任务执行失败", error)
            );
    }
    
    /**
     * 每天凌晨2点执行一次完整的令牌状态检查
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyTokenStatusCheck() {
        if (jwtTokenLifecycleService == null) {
            log.debug("JWT令牌生命周期服务未启用，跳过每日令牌状态检查");
            return;
        }
        
        log.info("开始执行每日令牌状态检查任务");
        
        // 获取统计信息
        jwtTokenLifecycleService.getLifecycleStats()
            .subscribe(
                stats -> {
                    log.info("令牌状态统计 - 总计: {}, 活跃: {}, 已撤销: {}, 已过期: {}", 
                        stats.getTotalTokens(), 
                        stats.getActiveTokens(), 
                        stats.getRevokedTokens(), 
                        stats.getExpiredTokens());
                },
                error -> log.error("获取令牌状态统计失败", error)
            );
        
        // 更新过期令牌
        jwtTokenLifecycleService.updateExpiredTokens()
            .subscribe(
                count -> log.info("每日令牌状态检查完成，共更新{}个过期令牌", count),
                error -> log.error("每日令牌状态检查任务执行失败", error)
            );
    }
}
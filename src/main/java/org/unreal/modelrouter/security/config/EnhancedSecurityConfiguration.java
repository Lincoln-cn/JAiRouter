package org.unreal.modelrouter.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.unreal.modelrouter.security.service.EnhancedJwtBlacklistService;

/**
 * 增强安全配置类
 * 配置增强的JWT安全服务
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class EnhancedSecurityConfiguration {
    
    /**
     * 配置增强的JWT黑名单服务
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.security.jwt.blacklist-enabled", havingValue = "true", matchIfMissing = true)
    public EnhancedJwtBlacklistService enhancedJwtBlacklistService(ReactiveStringRedisTemplate redisTemplate) {
        log.info("配置增强的JWT黑名单服务");
        return new EnhancedJwtBlacklistService(redisTemplate);
    }
}
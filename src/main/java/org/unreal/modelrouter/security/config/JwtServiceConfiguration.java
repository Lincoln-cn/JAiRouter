package org.unreal.modelrouter.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.security.service.impl.JwtBlacklistServiceImpl;
import org.unreal.modelrouter.security.service.impl.JwtTokenPersistenceServiceImpl;
import org.unreal.modelrouter.security.service.impl.RedisJwtBlacklistServiceImpl;
import org.unreal.modelrouter.security.service.impl.RedisJwtTokenPersistenceServiceImpl;
import org.unreal.modelrouter.store.StoreManager;

/**
 * JWT服务配置类
 * 根据配置选择使用Redis或StoreManager实现
 */
@Slf4j
@Configuration
public class JwtServiceConfiguration {
    
    /**
     * 主要的JWT持久化服务 - Redis实现
     * 当Redis启用时使用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
    public JwtPersistenceService redisJwtPersistenceService(
            @Qualifier("jwtReactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            StoreManager storeManager) {
        
        log.info("Initializing Redis-based JWT persistence service");
        return new RedisJwtTokenPersistenceServiceImpl(redisTemplate, storeManager);
    }
    
    /**
     * 备用的JWT持久化服务 - StoreManager实现
     * 当Redis未启用时使用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "false", matchIfMissing = true)
    public JwtPersistenceService storeManagerJwtPersistenceService(
            StoreManager storeManager) {
        
        log.info("Initializing StoreManager-based JWT persistence service");
        return new JwtTokenPersistenceServiceImpl(storeManager);
    }
    
    /**
     * 主要的JWT黑名单服务 - Redis实现
     * 当Redis启用时使用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "jairouter.security.jwt.blacklist.redis.enabled", havingValue = "true")
    public JwtBlacklistService redisJwtBlacklistService(
            @Qualifier("jwtReactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            StoreManager storeManager) {
        
        log.info("Initializing Redis-based JWT blacklist service");
        return new RedisJwtBlacklistServiceImpl(redisTemplate, storeManager);
    }
    
    /**
     * 备用的JWT黑名单服务 - StoreManager实现
     * 当Redis未启用时使用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "jairouter.security.jwt.blacklist.redis.enabled", havingValue = "false", matchIfMissing = true)
    public JwtBlacklistService storeManagerJwtBlacklistService(StoreManager storeManager) {
        
        log.info("Initializing StoreManager-based JWT blacklist service");
        return new JwtBlacklistServiceImpl(storeManager);
    }
    
    /**
     * JWT服务健康检查器
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.enabled", havingValue = "true")
    public JwtServiceHealthChecker jwtServiceHealthChecker(
            JwtPersistenceService persistenceService,
            JwtBlacklistService blacklistService) {
        
        return new JwtServiceHealthChecker(persistenceService, blacklistService);
    }
    
    /**
     * JWT服务健康检查器实现
     */
    public static class JwtServiceHealthChecker {
        private final JwtPersistenceService persistenceService;
        private final JwtBlacklistService blacklistService;
        
        public JwtServiceHealthChecker(JwtPersistenceService persistenceService, 
                                     JwtBlacklistService blacklistService) {
            this.persistenceService = persistenceService;
            this.blacklistService = blacklistService;
        }
        
        /**
         * 检查JWT持久化服务是否健康
         */
        public boolean isPersistenceServiceHealthy() {
            try {
                // 尝试计数活跃令牌
                Long count = persistenceService.countActiveTokens().block();
                return count != null;
            } catch (Exception e) {
                log.warn("JWT persistence service health check failed: {}", e.getMessage());
                return false;
            }
        }
        
        /**
         * 检查JWT黑名单服务是否健康
         */
        public boolean isBlacklistServiceHealthy() {
            try {
                // 检查黑名单服务是否可用
                Boolean available = blacklistService.isServiceAvailable().block();
                return Boolean.TRUE.equals(available);
            } catch (Exception e) {
                log.warn("JWT blacklist service health check failed: {}", e.getMessage());
                return false;
            }
        }
        
        /**
         * 获取服务状态信息
         */
        public String getServiceStatus() {
            boolean persistenceHealthy = isPersistenceServiceHealthy();
            boolean blacklistHealthy = isBlacklistServiceHealthy();
            
            if (persistenceHealthy && blacklistHealthy) {
                return "All JWT services are healthy";
            } else if (persistenceHealthy) {
                return "JWT persistence service is healthy, blacklist service has issues";
            } else if (blacklistHealthy) {
                return "JWT blacklist service is healthy, persistence service has issues";
            } else {
                return "Both JWT services have issues";
            }
        }
    }
}
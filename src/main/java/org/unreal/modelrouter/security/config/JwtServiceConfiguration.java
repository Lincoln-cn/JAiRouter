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
    public JwtPersistenceService storeManagerJwtPersistenceService(StoreManager storeManager) {
        
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
        
        public JwtServiceHealthChecker(JwtPersistenceService persistenceService, JwtBlacklistService blacklistService) {
            this.persistenceService = persistenceService;
            this.blacklistService = blacklistService;
        }
        
        /**
         * 检查JWT服务是否健康
         */
        public boolean isHealthy() {
            try {
                // 检查持久化服务健康状态
                boolean persistenceHealthy = persistenceService != null;
                
                // 检查黑名单服务健康状态
                boolean blacklistHealthy = blacklistService != null;
                
                return persistenceHealthy && blacklistHealthy;
            } catch (Exception e) {
                log.error("JWT service health check failed: {}", e.getMessage(), e);
                return false;
            }
        }
    }
}
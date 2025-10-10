package org.unreal.modelrouter.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.store.StoreManagerFactory;

/**
 * JWT令牌持久化配置类
 * 配置JWT令牌持久化所需的StoreManager和相关组件
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.enabled", havingValue = "true")
public class JwtPersistenceConfiguration {
    
    /**
     * JWT持久化配置属性
     */
    @ConfigurationProperties(prefix = "jairouter.security.jwt.persistence")
    public static class JwtPersistenceProperties {
        private String primaryStorage = "memory";
        private String fallbackStorage = "memory";
        private String storagePath = "config-store/jwt-tokens";
        private Memory memory = new Memory();
        
        public static class Memory {
            private int maxTokens = 50000;
            private double cleanupThreshold = 0.8;
            private boolean lruEnabled = true;
            
            // Getters and Setters
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            
            public double getCleanupThreshold() { return cleanupThreshold; }
            public void setCleanupThreshold(double cleanupThreshold) { this.cleanupThreshold = cleanupThreshold; }
            
            public boolean isLruEnabled() { return lruEnabled; }
            public void setLruEnabled(boolean lruEnabled) { this.lruEnabled = lruEnabled; }
        }
        
        // Getters and Setters
        public String getPrimaryStorage() { return primaryStorage; }
        public void setPrimaryStorage(String primaryStorage) { this.primaryStorage = primaryStorage; }
        
        public String getFallbackStorage() { return fallbackStorage; }
        public void setFallbackStorage(String fallbackStorage) { this.fallbackStorage = fallbackStorage; }
        
        public String getStoragePath() { return storagePath; }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
        
        public Memory getMemory() { return memory; }
        public void setMemory(Memory memory) { this.memory = memory; }
    }
    
    /**
     * JWT黑名单配置属性
     */
    @ConfigurationProperties(prefix = "jairouter.security.jwt.blacklist.persistence")
    public static class JwtBlacklistProperties {
        private String primaryStorage = "memory";
        private String fallbackStorage = "memory";
        private String storagePath = "config-store/jwt-blacklist";
        private int maxMemorySize = 10000;
        private int cleanupInterval = 3600; // 1小时
        
        // Getters and Setters
        public String getPrimaryStorage() { return primaryStorage; }
        public void setPrimaryStorage(String primaryStorage) { this.primaryStorage = primaryStorage; }
        
        public String getFallbackStorage() { return fallbackStorage; }
        public void setFallbackStorage(String fallbackStorage) { this.fallbackStorage = fallbackStorage; }
        
        public String getStoragePath() { return storagePath; }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
        
        public int getMaxMemorySize() { return maxMemorySize; }
        public void setMaxMemorySize(int maxMemorySize) { this.maxMemorySize = maxMemorySize; }
        
        public int getCleanupInterval() { return cleanupInterval; }
        public void setCleanupInterval(int cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    }
    
    /**
     * 创建JWT令牌持久化用的StoreManager
     */
    @Bean("jwtTokenStoreManager")
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.enabled", havingValue = "true")
    public StoreManager jwtTokenStoreManager() {
        JwtPersistenceProperties properties = jwtPersistenceProperties();
        
        try {
            String storageType = properties.getPrimaryStorage();
            String storagePath = properties.getStoragePath();
            
            log.info("Initializing JWT token StoreManager with type: {} and path: {}", storageType, storagePath);
            
            StoreManager storeManager = StoreManagerFactory.createStoreManager(storageType, storagePath);
            
            log.info("Successfully initialized JWT token StoreManager");
            return storeManager;
            
        } catch (Exception e) {
            log.warn("Failed to initialize primary JWT token storage, falling back to memory storage: {}", e.getMessage());
            
            // 回退到内存存储
            return StoreManagerFactory.createMemoryStoreManager();
        }
    }
    
    /**
     * JWT持久化配置属性Bean
     */
    @Bean
    @ConfigurationProperties(prefix = "jairouter.security.jwt.persistence")
    public JwtPersistenceProperties jwtPersistenceProperties() {
        return new JwtPersistenceProperties();
    }
    
    /**
     * JWT黑名单配置属性Bean
     */
    @Bean
    @ConfigurationProperties(prefix = "jairouter.security.jwt.blacklist.persistence")
    public JwtBlacklistProperties jwtBlacklistProperties() {
        return new JwtBlacklistProperties();
    }
}
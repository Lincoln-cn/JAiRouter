package org.unreal.modelrouter.security.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT Redis配置属性
 * 统一管理JWT相关的Redis配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.security.jwt")
public class JwtRedisProperties {
    
    /**
     * JWT持久化配置
     */
    private Persistence persistence = new Persistence();
    
    /**
     * JWT黑名单配置
     */
    private Blacklist blacklist = new Blacklist();
    
    @Data
    public static class Persistence {
        /**
         * 是否启用持久化
         */
        private boolean enabled = false;
        
        /**
         * 主要存储类型
         */
        private String primaryStorage = "redis";
        
        /**
         * 备用存储类型
         */
        private String fallbackStorage = "memory";
        
        /**
         * Redis配置
         */
        private Redis redis = new Redis();
        
        /**
         * 清理配置
         */
        private Cleanup cleanup = new Cleanup();
        
        @Data
        public static class Redis {
            /**
             * 是否启用Redis
             */
            private boolean enabled = false;
            
            /**
             * Redis主机
             */
            private String host = "localhost";
            
            /**
             * Redis端口
             */
            private int port = 6379;
            
            /**
             * Redis密码
             */
            private String password;
            
            /**
             * Redis数据库
             */
            private int database = 0;
            
            /**
             * 键前缀
             */
            private String keyPrefix = "jwt:";
            
            /**
             * 默认TTL（秒）
             */
            private int defaultTtl = 3600;
            
            /**
             * 连接超时（毫秒）
             */
            private int connectionTimeout = 5000;
            
            /**
             * 重试次数
             */
            private int retryAttempts = 3;
            
            /**
             * 序列化格式
             */
            private String serializationFormat = "json";
            
            /**
             * 连接池配置
             */
            private Pool pool = new Pool();
            
            @Data
            public static class Pool {
                /**
                 * 最大活跃连接数
                 */
                private int maxActive = 8;
                
                /**
                 * 最大空闲连接数
                 */
                private int maxIdle = 8;
                
                /**
                 * 最小空闲连接数
                 */
                private int minIdle = 0;
                
                /**
                 * 最大等待时间（毫秒）
                 */
                private long maxWait = -1;
            }
        }
        
        @Data
        public static class Cleanup {
            /**
             * 是否启用清理
             */
            private boolean enabled = true;
            
            /**
             * 清理调度表达式
             */
            private String schedule = "0 0 2 * * ?";
            
            /**
             * 保留天数
             */
            private int retentionDays = 30;
            
            /**
             * 批处理大小
             */
            private int batchSize = 1000;
        }
    }
    
    @Data
    public static class Blacklist {
        /**
         * 持久化配置
         */
        private Persistence persistence = new Persistence();
        
        /**
         * Redis配置
         */
        private Redis redis = new Redis();
        
        @Data
        public static class Persistence {
            /**
             * 是否启用持久化
             */
            private boolean enabled = false;
            
            /**
             * 主要存储类型
             */
            private String primaryStorage = "redis";
            
            /**
             * 备用存储类型
             */
            private String fallbackStorage = "memory";
            
            /**
             * 最大内存大小
             */
            private int maxMemorySize = 10000;
            
            /**
             * 清理间隔（秒）
             */
            private int cleanupInterval = 3600;
        }
        
        @Data
        public static class Redis {
            /**
             * 是否启用Redis
             */
            private boolean enabled = false;
            
            /**
             * Redis主机
             */
            private String host = "localhost";
            
            /**
             * Redis端口
             */
            private int port = 6379;
            
            /**
             * Redis密码
             */
            private String password;
            
            /**
             * Redis数据库
             */
            private int database = 0;
            
            /**
             * 键前缀
             */
            private String keyPrefix = "jwt:blacklist:";
            
            /**
             * 默认TTL（秒）
             */
            private int defaultTtl = 86400;
            
            /**
             * 连接超时（毫秒）
             */
            private int connectionTimeout = 5000;
            
            /**
             * 重试次数
             */
            private int retryAttempts = 3;
            
            /**
             * 连接池配置
             */
            private Pool pool = new Pool();
            
            @Data
            public static class Pool {
                /**
                 * 最大活跃连接数
                 */
                private int maxActive = 8;
                
                /**
                 * 最大空闲连接数
                 */
                private int maxIdle = 8;
                
                /**
                 * 最小空闲连接数
                 */
                private int minIdle = 0;
                
                /**
                 * 最大等待时间（毫秒）
                 */
                private long maxWait = -1;
            }
        }
    }
    
    // 便利方法
    
    /**
     * 获取JWT持久化Redis配置
     */
    public Persistence.Redis getPersistenceRedis() {
        return persistence.getRedis();
    }
    
    /**
     * 获取JWT黑名单Redis配置
     */
    public Blacklist.Redis getBlacklistRedis() {
        return blacklist.getRedis();
    }
    
    /**
     * 检查JWT持久化是否启用Redis
     */
    public boolean isPersistenceRedisEnabled() {
        return persistence.isEnabled() && persistence.getRedis().isEnabled();
    }
    
    /**
     * 检查JWT黑名单是否启用Redis
     */
    public boolean isBlacklistRedisEnabled() {
        return blacklist.getPersistence().isEnabled() && blacklist.getRedis().isEnabled();
    }
    
    /**
     * 获取持久化Redis连接超时时间
     */
    public Duration getPersistenceRedisConnectionTimeout() {
        return Duration.ofMillis(persistence.getRedis().getConnectionTimeout());
    }
    
    /**
     * 获取黑名单Redis连接超时时间
     */
    public Duration getBlacklistRedisConnectionTimeout() {
        return Duration.ofMillis(blacklist.getRedis().getConnectionTimeout());
    }
    
    /**
     * 获取持久化Redis默认TTL
     */
    public Duration getPersistenceRedisDefaultTtl() {
        return Duration.ofSeconds(persistence.getRedis().getDefaultTtl());
    }
    
    /**
     * 获取黑名单Redis默认TTL
     */
    public Duration getBlacklistRedisDefaultTtl() {
        return Duration.ofSeconds(blacklist.getRedis().getDefaultTtl());
    }
    
    /**
     * 验证配置的有效性
     */
    public void validate() {
        if (isPersistenceRedisEnabled()) {
            validateRedisConfig(persistence.getRedis(), "JWT Persistence Redis");
        }
        
        if (isBlacklistRedisEnabled()) {
            validateRedisConfig(blacklist.getRedis(), "JWT Blacklist Redis");
        }
    }
    
    private void validateRedisConfig(Object redisConfig, String configName) {
        if (redisConfig instanceof Persistence.Redis) {
            Persistence.Redis config = (Persistence.Redis) redisConfig;
            if (config.getHost() == null || config.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException(configName + " host cannot be null or empty");
            }
            if (config.getPort() <= 0 || config.getPort() > 65535) {
                throw new IllegalArgumentException(configName + " port must be between 1 and 65535");
            }
            if (config.getDatabase() < 0) {
                throw new IllegalArgumentException(configName + " database must be non-negative");
            }
        } else if (redisConfig instanceof Blacklist.Redis) {
            Blacklist.Redis config = (Blacklist.Redis) redisConfig;
            if (config.getHost() == null || config.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException(configName + " host cannot be null or empty");
            }
            if (config.getPort() <= 0 || config.getPort() > 65535) {
                throw new IllegalArgumentException(configName + " port must be between 1 and 65535");
            }
            if (config.getDatabase() < 0) {
                throw new IllegalArgumentException(configName + " database must be non-negative");
            }
        }
    }
}
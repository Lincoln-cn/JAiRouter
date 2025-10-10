package org.unreal.modelrouter.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis JWT缓存配置类
 * 配置Redis连接和ReactiveRedisTemplate用于JWT令牌和黑名单缓存
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
public class RedisJwtCacheConfiguration {
    
    /**
     * Redis JWT缓存配置属性
     */
    @ConfigurationProperties(prefix = "jairouter.security.jwt.persistence.redis")
    public static class RedisJwtCacheProperties {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private int connectionTimeout = 5000;
        private int retryAttempts = 3;
        private String keyPrefix = "jwt:";
        private int defaultTtl = 3600;
        private String serializationFormat = "json";
        private Pool pool = new Pool();
        
        public static class Pool {
            private int maxActive = 8;
            private int maxIdle = 8;
            private int minIdle = 0;
            private long maxWait = -1;
            
            // Getters and Setters
            public int getMaxActive() { return maxActive; }
            public void setMaxActive(int maxActive) { this.maxActive = maxActive; }
            
            public int getMaxIdle() { return maxIdle; }
            public void setMaxIdle(int maxIdle) { this.maxIdle = maxIdle; }
            
            public int getMinIdle() { return minIdle; }
            public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
            
            public long getMaxWait() { return maxWait; }
            public void setMaxWait(long maxWait) { this.maxWait = maxWait; }
        }
        
        // Getters and Setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }
        
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        
        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
        
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
        
        public int getDefaultTtl() { return defaultTtl; }
        public void setDefaultTtl(int defaultTtl) { this.defaultTtl = defaultTtl; }
        
        public String getSerializationFormat() { return serializationFormat; }
        public void setSerializationFormat(String serializationFormat) { this.serializationFormat = serializationFormat; }
        
        public Pool getPool() { return pool; }
        public void setPool(Pool pool) { this.pool = pool; }
    }
    
    /**
     * Redis JWT缓存配置属性Bean
     */
    @Bean
    @ConfigurationProperties(prefix = "jairouter.security.jwt.persistence.redis")
    public RedisJwtCacheProperties redisJwtCacheProperties() {
        return new RedisJwtCacheProperties();
    }
    
    /**
     * JWT专用的Redis连接工厂
     */
    @Bean("jwtRedisConnectionFactory")
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
    public ReactiveRedisConnectionFactory jwtRedisConnectionFactory() {
        RedisJwtCacheProperties properties = redisJwtCacheProperties();
        
        try {
            log.info("Initializing JWT Redis connection factory with host: {}:{}, database: {}", 
                properties.getHost(), properties.getPort(), properties.getDatabase());
            
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(properties.getHost());
            config.setPort(properties.getPort());
            config.setDatabase(properties.getDatabase());
            
            if (properties.getPassword() != null && !properties.getPassword().trim().isEmpty()) {
                config.setPassword(properties.getPassword());
            }
            
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            
            log.info("Successfully initialized JWT Redis connection factory");
            return factory;
            
        } catch (Exception e) {
            log.error("Failed to initialize JWT Redis connection factory: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize JWT Redis connection factory", e);
        }
    }
    
    /**
     * JWT专用的ReactiveRedisTemplate
     */
    @Bean("jwtReactiveRedisTemplate")
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
    public ReactiveRedisTemplate<String, String> jwtReactiveRedisTemplate() {
        try {
            ReactiveRedisConnectionFactory connectionFactory = jwtRedisConnectionFactory();
            
            // 使用String序列化器
            StringRedisSerializer stringSerializer = new StringRedisSerializer();
            
            RedisSerializationContext<String, String> serializationContext = 
                RedisSerializationContext.<String, String>newSerializationContext()
                    .key(stringSerializer)
                    .value(stringSerializer)
                    .hashKey(stringSerializer)
                    .hashValue(stringSerializer)
                    .build();
            
            ReactiveRedisTemplate<String, String> template = 
                new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
            
            log.info("Successfully initialized JWT ReactiveRedisTemplate");
            return template;
            
        } catch (Exception e) {
            log.error("Failed to initialize JWT ReactiveRedisTemplate: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize JWT ReactiveRedisTemplate", e);
        }
    }
    
    /**
     * Redis健康检查Bean
     */
    @Bean("redisJwtHealthChecker")
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
    public RedisJwtHealthChecker redisJwtHealthChecker() {
        return new RedisJwtHealthChecker(jwtReactiveRedisTemplate());
    }
    
    /**
     * Redis JWT健康检查器
     */
    public static class RedisJwtHealthChecker {
        private final ReactiveRedisTemplate<String, String> redisTemplate;
        private static final String HEALTH_CHECK_KEY = "jwt:health_check";
        
        public RedisJwtHealthChecker(ReactiveRedisTemplate<String, String> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }
        
        /**
         * 检查Redis连接是否健康
         */
        public boolean isHealthy() {
            try {
                String testValue = "health_check_" + System.currentTimeMillis();
                
                // 尝试写入和读取测试值
                Boolean setResult = redisTemplate.opsForValue()
                    .set(HEALTH_CHECK_KEY, testValue, java.time.Duration.ofSeconds(10))
                    .block(java.time.Duration.ofSeconds(5));
                
                if (Boolean.TRUE.equals(setResult)) {
                    String getValue = redisTemplate.opsForValue()
                        .get(HEALTH_CHECK_KEY)
                        .block(java.time.Duration.ofSeconds(5));
                    
                    if (testValue.equals(getValue)) {
                        // 清理测试键
                        redisTemplate.delete(HEALTH_CHECK_KEY).subscribe();
                        return true;
                    }
                }
                
                return false;
                
            } catch (Exception e) {
                log.warn("Redis JWT health check failed: {}", e.getMessage());
                return false;
            }
        }
        
        /**
         * 获取Redis连接信息
         */
        public String getConnectionInfo() {
            try {
                return "Redis JWT Cache - Connection established";
            } catch (Exception e) {
                return "Redis JWT Cache - Connection failed: " + e.getMessage();
            }
        }
    }
}
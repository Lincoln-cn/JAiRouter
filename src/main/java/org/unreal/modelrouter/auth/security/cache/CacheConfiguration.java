package org.unreal.modelrouter.auth.security.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 缓存配置类
 * 配置Redis连接和序列化器
 */
@Slf4j
@Configuration
public class CacheConfiguration {
    
    /**
     * 配置Redis模板
     * 仅在启用Redis缓存时创建
     *
     * <p>{@code @Primary}（issue #196）：该开关打开时，应用里还会存在 Spring Boot 自动装配的
     * {@code reactiveStringRedisTemplate}，两者都可满足未限定的
     * {@code ReactiveRedisTemplate<String, String>} 注入。标为 primary 让解析确定，避免"候选不唯一"。
     * 两者用的是同一个连接工厂与 String 序列化，行为一致。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "jairouter.security.cache.redis.enabled", havingValue = "true")
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            final ReactiveRedisConnectionFactory connectionFactory) {
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(stringSerializer)
                .value(stringSerializer)
                .hashKey(stringSerializer)
                .hashValue(stringSerializer)
                .build();
        
        log.info("Initializing security cache Redis template");
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
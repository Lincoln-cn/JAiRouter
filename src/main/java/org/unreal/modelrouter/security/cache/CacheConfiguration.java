package org.unreal.modelrouter.security.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.security.cache.redis.enabled", havingValue = "true")
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(stringSerializer)
                .value(stringSerializer)
                .hashKey(stringSerializer)
                .hashValue(stringSerializer)
                .build();
        
        ReactiveRedisTemplate<String, String> template = new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
        log.info("Redis响应式模板配置完成");
        return template;
    }
    
    /**
     * 配置ObjectMapper用于序列化
     */
    @Bean
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
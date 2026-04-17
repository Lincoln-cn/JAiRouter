package org.unreal.modelrouter.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * ObjectMapper 全局配置
 * 提供单例的 ObjectMapper 实例，避免重复创建
 */
@Configuration
public class JacksonConfig {

    /**
     * 全局 ObjectMapper 单例
     * 配置：
     * - 忽略未知属性
     * - 序列化时不包含 null 值
     * - 支持 Java 8 日期时间类型
     * - 使用下划线命名策略
     */
    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                )
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .modules(new JavaTimeModule())
                .build();
    }
}

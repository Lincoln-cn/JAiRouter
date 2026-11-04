package org.unreal.modelrouter.config.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

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
     * - Java 8 日期时间类型由 Jackson 3 内置支持（不再需要注册 JavaTimeModule）
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 3 默认开启（3.x 默认关闭）：null 灌进 primitive 会抛异常，
                // 而本仓大量 DTO 用 primitive 字段且允许缺省，保持 3.x 行为。
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                // Jackson 3 用 DateTimeFeature 控制时间戳输出，默认即 ISO 字符串
                .changeDefaultPropertyInclusion(value -> JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
    }
}

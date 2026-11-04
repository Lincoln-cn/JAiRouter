package org.unreal.modelrouter.common.util;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.Serializers;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.io.IOException;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;

public class JacksonHelper {
    /** Private constructor to prevent instantiation. */
    private JacksonHelper() {}

    public static final String NORM_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String NORM_TIME_PATTERN = "HH:mm:ss";
    public static final String NORM_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 单例 ObjectMapper 实例
     * ObjectMapper 是线程安全的，可以全局复用
     * 
     * 性能优化 (v2.7.0): 
     * - 避免每次调用创建新实例 (~50-100ms 初始化开销)
     * - 减少对象创建和 GC 压力
     */
    private static final ObjectMapper INSTANCE = createObjectMapper();

    /**
     * 获取单例 ObjectMapper 实例
     * 推荐用于大多数场景，性能最优
     * 
     * @return 全局共享的 ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return INSTANCE;
    }

    /**
     * 创建新的 ObjectMapper 实例
     * 用于需要自定义配置的特殊场景
     * 
     * @return 新创建的 ObjectMapper 实例
     */
    public static ObjectMapper createNewObjectMapper() {
        return createObjectMapper();
    }

    /**
     * 创建并配置 ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        // Jackson 3 的 ObjectMapper 不可变，只能走 builder；java.time 已内建，
        // 因此没有 findAndRegisterModules 这一步，setDateFormat(SimpleDateFormat)
        // 也只影响 java.util.Date（java.time 的格式由下面的 module 决定）。
        return JsonMapper.builder()
                .defaultLocale(Locale.CHINA)
                .defaultTimeZone(TimeZone.getTimeZone(ZoneId.systemDefault()))
                .addModule(javaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 3 默认开启（3.x 默认关闭），保持旧行为
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public static LocalDateTime covertStringToLocalDateTime(final String date) {
        return LocalDateTime.parse(date, DateTimeFormatter.ofPattern(NORM_DATETIME_PATTERN));
    }

    public static LocalDateTime covertStringToLocalDateTime(final String date, final String pattern) {
        return LocalDateTime.parse(date, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 保留 3.5 以来的日期格式（yyyy-MM-dd HH:mm:ss）：JWT 黑名单等缓存条目以 JSON 存在 Redis，
     * 换格式会让旧数据读不出来。Jackson 3 没有 JavaTimeModule，改用 Serializers/Deserializers 工厂。
     */
    private static JacksonModule javaTimeModule() {
        final DateTimeFormatter dateTimeFmt = DateTimeFormatter.ofPattern(NORM_DATETIME_PATTERN);
        final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern(NORM_TIME_PATTERN);
        final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(NORM_DATE_PATTERN);
        return new JacksonModule() {
            @Override
            public String getModuleName() {
                return "jairouter-java-time";
            }

            @Override
            public Version version() {
                return Version.unknownVersion();
            }

            @Override
            public void setupModule(final SetupContext context) {
                context.addSerializers(new Serializers.Base() {
                    @Override
                    public ValueSerializer<?> findSerializer(final SerializationConfig config,
                            final JavaType type, final BeanDescription.Supplier beanDesc,
                            final JsonFormat.Value format) {
                        final Class<?> raw = type.getRawClass();
                        if (raw == LocalDateTime.class) {
                            return new LocalDateTimeSerializer(dateTimeFmt);
                        }
                        if (raw == LocalTime.class) {
                            return new LocalTimeSerializer(timeFmt);
                        }
                        if (raw == LocalDate.class) {
                            return new LocalDateSerializer(dateFmt);
                        }
                        return null;
                    }
                });
                context.addDeserializers(new Deserializers.Base() {
                    @Override
                    public boolean hasDeserializerFor(final DeserializationConfig config, final Class<?> rawType) {
                        return rawType == LocalDateTime.class || rawType == LocalDate.class || rawType == LocalTime.class;
                    }

                    @Override
                    public ValueDeserializer<?> findBeanDeserializer(final JavaType type,
                            final DeserializationConfig config, final BeanDescription.Supplier beanDesc) {
                        final Class<?> raw = type.getRawClass();
                        if (raw == LocalDateTime.class) {
                            return new FlexibleLocalDateTimeDeserializer();
                        }
                        if (raw == LocalDate.class) {
                            return new LocalDateDeserializer(dateFmt);
                        }
                        if (raw == LocalTime.class) {
                            return new LocalTimeDeserializer(timeFmt);
                        }
                        return null;
                    }
                });
            }
        };
    }

    /**
     * 灵活的LocalDateTime反序列化器，支持多种格式
     */
    private static class FlexibleLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(final JsonParser p, final DeserializationContext ctxt) {
            String value = p.getValueAsString();
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            
            try {
                // 首先尝试标准格式
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(NORM_DATETIME_PATTERN));
            } catch (Exception e1) {
                try {
                    // 尝试ISO格式 (toString()产生的格式)
                    return LocalDateTime.parse(value);
                } catch (Exception e2) {
                    try {
                        // 尝试其他常见格式
                        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (Exception e3) {
                        throw new IllegalArgumentException("Unable to parse LocalDateTime from: " + value, e3);
                    }
                }
            }
        }
    }
}
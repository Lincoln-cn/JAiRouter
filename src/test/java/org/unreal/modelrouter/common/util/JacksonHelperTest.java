package org.unreal.modelrouter.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JacksonHelper 单元测试 - v2.7.0
 * 
 * 测试目标：
 * - 验证单例模式正确性
 * - 验证 ObjectMapper 配置正确性
 * - 验证日期序列化/反序列化
 */
@DisplayName("JacksonHelper v2.7.0 单例测试")
class JacksonHelperTest {

    @Test
    @DisplayName("测试 1: getObjectMapper 应返回同一实例")
    void getObjectMapper_shouldReturnSameInstance() {
        ObjectMapper mapper1 = JacksonHelper.getObjectMapper();
        ObjectMapper mapper2 = JacksonHelper.getObjectMapper();
        
        assertSame(mapper1, mapper2, "多次调用应返回同一个 ObjectMapper 实例");
    }

    @Test
    @DisplayName("测试 2: createNewObjectMapper 应返回新实例")
    void createNewObjectMapper_shouldReturnNewInstance() {
        ObjectMapper mapper1 = JacksonHelper.createNewObjectMapper();
        ObjectMapper mapper2 = JacksonHelper.createNewObjectMapper();
        
        assertNotSame(mapper1, mapper2, "createNewObjectMapper 应返回新实例");
    }

    @Test
    @DisplayName("测试 3: 单例与新建实例应不同")
    void singleton_shouldDifferFromNewInstance() {
        ObjectMapper singleton = JacksonHelper.getObjectMapper();
        ObjectMapper newInstance = JacksonHelper.createNewObjectMapper();
        
        assertNotSame(singleton, newInstance, "单例应与新建实例不同");
    }

    @Test
    @DisplayName("测试 4: ObjectMapper 应配置忽略未知属性")
    void objectMapper_shouldIgnoreUnknownProperties() throws Exception {
        ObjectMapper mapper = JacksonHelper.getObjectMapper();

        // 使用一个只有 name 字段的简单类测试
        String json = "{\"name\":\"test\",\"trulyUnknownField\":\"value\"}";
        TestDto result = mapper.readValue(json, TestDto.class);

        assertEquals("test", result.getName(), "应正确读取 name 字段");
        // trulyUnknownField 在 TestDto 中不存在，由于禁用了 FAIL_ON_UNKNOWN_PROPERTIES，不会抛异常
    }

    @Test
    @DisplayName("测试 5: ObjectMapper 应禁用 FAIL_ON_UNKNOWN_PROPERTIES")
    void objectMapper_shouldDisableFailOnUnknownProperties() {
        ObjectMapper mapper = JacksonHelper.getObjectMapper();
        
        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                "应禁用 FAIL_ON_UNKNOWN_PROPERTIES");
    }

    @Test
    @DisplayName("测试 6: ObjectMapper 应正确序列化 LocalDateTime")
    void objectMapper_shouldSerializeLocalDateTime() throws Exception {
        ObjectMapper mapper = JacksonHelper.getObjectMapper();
        LocalDateTime dateTime = LocalDateTime.of(2026, 5, 15, 10, 30, 0);
        
        String json = mapper.writeValueAsString(dateTime);
        
        assertNotNull(json, "应成功序列化");
        assertTrue(json.contains("2026"), "应包含年份");
    }

    @Test
    @DisplayName("测试 7: ObjectMapper 应正确反序列化 LocalDateTime")
    void objectMapper_shouldDeserializeLocalDateTime() throws Exception {
        ObjectMapper mapper = JacksonHelper.getObjectMapper();
        String json = "\"2026-05-15 10:30:00\"";
        
        LocalDateTime result = mapper.readValue(json, LocalDateTime.class);
        
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
    }

    @Test
    @DisplayName("测试 8: covertStringToLocalDateTime 标准格式")
    void covertStringToLocalDateTime_standardFormat() {
        String dateStr = "2026-05-15 10:30:00";
        
        LocalDateTime result = JacksonHelper.covertStringToLocalDateTime(dateStr);
        
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
        assertEquals(10, result.getHour());
        assertEquals(30, result.getMinute());
    }

    @Test
    @DisplayName("测试 9: covertStringToLocalDateTime 自定义格式")
    void covertStringToLocalDateTime_customFormat() {
        String dateStr = "2026/05/15 10:30";
        String pattern = "yyyy/MM/dd HH:mm";
        
        LocalDateTime result = JacksonHelper.covertStringToLocalDateTime(dateStr, pattern);
        
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
    }

    @Test
    @DisplayName("测试 10: 常量格式应正确")
    void constants_shouldBeCorrect() {
        assertEquals("yyyy-MM-dd HH:mm:ss", JacksonHelper.NORM_DATE_PATTERN);
        assertEquals("HH:mm:ss", JacksonHelper.NORM_TIME_PATTERN);
        assertEquals("yyyy-MM-dd HH:mm:ss", JacksonHelper.NORM_DATETIME_PATTERN);
    }

    @Test
    @DisplayName("测试 11: 多线程并发获取单例应安全")
    void getObjectMapper_concurrentAccess_shouldBeThreadSafe() throws Exception {
        int threadCount = 100;
        ObjectMapper[] mappers = new ObjectMapper[threadCount];
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                mappers[index] = JacksonHelper.getObjectMapper();
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 所有线程应获取到同一个实例
        ObjectMapper first = mappers[0];
        for (int i = 1; i < threadCount; i++) {
            assertSame(first, mappers[i], "所有线程应获取同一实例");
        }
    }

    /**
     * 测试用 DTO
     */
    public static class TestDto {
        private String name;
        private String unknownField;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUnknownField() {
            return unknownField;
        }

        public void setUnknownField(String unknownField) {
            this.unknownField = unknownField;
        }
    }
}

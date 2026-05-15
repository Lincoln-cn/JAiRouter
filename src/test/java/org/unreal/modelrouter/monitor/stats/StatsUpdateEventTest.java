package org.unreal.modelrouter.monitor.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StatsUpdateEvent 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.4
 */
class StatsUpdateEventTest {

    @Test
    @DisplayName("成功事件应包含正确信息")
    void successEvent_shouldContainCorrectInfo() {
        StatsUpdateEvent event = StatsUpdateEvent.success("chat", "gpt-4", 150);

        assertEquals(StatsUpdateEvent.Type.CALL_SUCCESS, event.getType());
        assertEquals("chat", event.getServiceType());
        assertEquals("gpt-4", event.getModelName());
        assertEquals(150, event.getResponseTime());
        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    @DisplayName("失败事件应包含正确信息")
    void failureEvent_shouldContainCorrectInfo() {
        StatsUpdateEvent event = StatsUpdateEvent.failure("embedding", "text-embedding", 300);

        assertEquals(StatsUpdateEvent.Type.CALL_FAILURE, event.getType());
        assertEquals("embedding", event.getServiceType());
        assertEquals("text-embedding", event.getModelName());
        assertEquals(300, event.getResponseTime());
    }

    @Test
    @DisplayName("熔断事件应包含正确信息")
    void circuitBreakerEvent_shouldContainCorrectInfo() {
        StatsUpdateEvent event = StatsUpdateEvent.circuitBreaker("chat", "gpt-4");

        assertEquals(StatsUpdateEvent.Type.CIRCUIT_BREAKER, event.getType());
        assertEquals("chat", event.getServiceType());
        assertEquals("gpt-4", event.getModelName());
        assertEquals(0, event.getResponseTime()); // 熔断事件没有响应时间
    }

    @Test
    @DisplayName("限流事件应包含正确信息")
    void rateLimitedEvent_shouldContainCorrectInfo() {
        StatsUpdateEvent event = StatsUpdateEvent.rateLimited("chat", "gpt-3.5");

        assertEquals(StatsUpdateEvent.Type.RATE_LIMITED, event.getType());
        assertEquals("chat", event.getServiceType());
        assertEquals("gpt-3.5", event.getModelName());
    }

    @Test
    @DisplayName("错误码事件应包含正确信息")
    void errorCodeEvent_shouldContainCorrectInfo() {
        StatsUpdateEvent event = StatsUpdateEvent.errorCode("chat", "gpt-4", "500");

        assertEquals(StatsUpdateEvent.Type.ERROR_CODE, event.getType());
        assertEquals("chat", event.getServiceType());
        assertEquals("gpt-4", event.getModelName());
        assertEquals("500", event.getErrorCode());
    }

    @Test
    @DisplayName("Builder应正确构建事件")
    void builder_shouldBuildCorrectly() {
        StatsUpdateEvent event = StatsUpdateEvent.builder()
                .type(StatsUpdateEvent.Type.CALL_SUCCESS)
                .serviceType("rerank")
                .modelName("rerank-model")
                .responseTime(100)
                .build();

        assertEquals(StatsUpdateEvent.Type.CALL_SUCCESS, event.getType());
        assertEquals("rerank", event.getServiceType());
        assertEquals("rerank-model", event.getModelName());
        assertEquals(100, event.getResponseTime());
    }

    @Test
    @DisplayName("toString应包含关键信息")
    void toString_shouldContainKeyInfo() {
        StatsUpdateEvent event = StatsUpdateEvent.success("chat", "gpt-4", 150);

        String str = event.toString();
        assertTrue(str.contains("CALL_SUCCESS"));
        assertTrue(str.contains("chat"));
        assertTrue(str.contains("gpt-4"));
        assertTrue(str.contains("150"));
    }

    @Test
    @DisplayName("事件类型枚举应包含所有类型")
    void eventTypeEnum_shouldContainAllTypes() {
        StatsUpdateEvent.Type[] types = StatsUpdateEvent.Type.values();

        assertEquals(5, types.length);
        assertArrayEquals(
                new StatsUpdateEvent.Type[] {
                    StatsUpdateEvent.Type.CALL_SUCCESS,
                    StatsUpdateEvent.Type.CALL_FAILURE,
                    StatsUpdateEvent.Type.CIRCUIT_BREAKER,
                    StatsUpdateEvent.Type.RATE_LIMITED,
                    StatsUpdateEvent.Type.ERROR_CODE
                },
                types
        );
    }

    @Test
    @DisplayName("时间戳应在合理范围内")
    void timestamp_shouldBeInReasonableRange() {
        long before = System.currentTimeMillis();
        StatsUpdateEvent event = StatsUpdateEvent.success("chat", "gpt-4", 100);
        long after = System.currentTimeMillis();

        assertTrue(event.getTimestamp() >= before);
        assertTrue(event.getTimestamp() <= after);
    }
}

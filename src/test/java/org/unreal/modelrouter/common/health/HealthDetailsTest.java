package org.unreal.modelrouter.common.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 健康详情安全取值工具的行为。
 *
 * <p>该工具存在的理由只有一个：{@code Map.of} 与 {@code Health.Builder#withDetail}
 * 都拒绝 null，而异常 message 常常是 null（见 issue #1133）。</p>
 */
@DisplayName("健康详情安全取值")
class HealthDetailsTest {

    @Test
    @DisplayName("异常本身为 null 时返回固定占位，不抛空指针")
    void safeMessage_nullThrowable_returnsPlaceholder() {
        assertEquals("unknown", HealthDetails.safeMessage(null));
    }

    @Test
    @DisplayName("异常没有消息时回落到类名，保证可用作 Map 值")
    void safeMessage_withoutMessage_fallsBackToClassName() {
        assertEquals("IllegalStateException",
                HealthDetails.safeMessage(new IllegalStateException()));
    }

    @Test
    @DisplayName("消息为纯空白时同样回落到类名，不产出看起来为空的明细")
    void safeMessage_blankMessage_fallsBackToClassName() {
        assertEquals("IllegalStateException",
                HealthDetails.safeMessage(new IllegalStateException("   ")));
    }

    @Test
    @DisplayName("消息存在时原样保留，不吞掉排查信息")
    void safeMessage_withMessage_keepsOriginalMessage() {
        assertEquals("redis connection reset",
                HealthDetails.safeMessage(new IllegalStateException("redis connection reset")));
    }
}

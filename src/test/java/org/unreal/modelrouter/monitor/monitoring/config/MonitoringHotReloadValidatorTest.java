package org.unreal.modelrouter.monitor.monitoring.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.ApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MonitoringHotReloadValidator} 的边界测试（#1172）.
 *
 * <p>这些断言对准的是「两个写入口共用一套校验」这件事：控制台的
 * {@code PUT /api/monitoring/config/sampling|performance} 与运维面的
 * {@code POST /actuator/metrics/sampling|performance-config} 都走这里，
 * 所以只要这里的边界对了，两条链路的非法输入就都拒得掉。</p>
 */
@DisplayName("监控热改校验：取值边界")
class MonitoringHotReloadValidatorTest {

    @Nested
    @DisplayName("valueOr：请求值缺省回落到当前值")
    class ValueOrTests {

        @Test
        @DisplayName("传了请求值就用请求值")
        void usesRequestValueWhenPresent() {
            assertEquals(0.25, MonitoringHotReloadValidator.valueOr(0.25, 0.9));
        }

        @Test
        @DisplayName("没传就用当前值")
        void fallsBackWhenAbsent() {
            assertEquals(0.9, MonitoringHotReloadValidator.valueOr(null, 0.9));
        }

        @Test
        @DisplayName("传 0 是有效值，不能被当成缺省")
        void zeroIsAValidValue() {
            assertEquals(0.0, MonitoringHotReloadValidator.valueOr(0.0, 0.9));
        }
    }

    @Nested
    @DisplayName("requireSamplingRate：0~1 含边界")
    class SamplingRateTests {

        @Test
        @DisplayName("0 与 1 都在边界内")
        void acceptsBounds() {
            assertDoesNotThrow(() -> MonitoringHotReloadValidator.requireSamplingRate("request-metrics", 0.0));
            assertDoesNotThrow(() -> MonitoringHotReloadValidator.requireSamplingRate("request-metrics", 1.0));
            assertDoesNotThrow(() -> MonitoringHotReloadValidator.requireSamplingRate("request-metrics", 0.5));
        }

        @Test
        @DisplayName("负数被拒，且错误码是 INVALID_REQUEST")
        void rejectsNegative() {
            ApiException error = assertThrows(ApiException.class,
                    () -> MonitoringHotReloadValidator.requireSamplingRate("backend-metrics", -0.01));
            assertEquals(ErrorCode.INVALID_REQUEST.code(), error.getErrorCode());
        }

        @Test
        @DisplayName("大于 1 被拒（5 = 全量采集，指标量级失控）")
        void rejectsAboveOne() {
            ApiException error = assertThrows(ApiException.class,
                    () -> MonitoringHotReloadValidator.requireSamplingRate("trace-metrics", 1.01));
            assertEquals(ErrorCode.INVALID_REQUEST.code(), error.getErrorCode());
        }
    }

    @Nested
    @DisplayName("requirePerformanceBounds：批大小 ≥ 1 且缓冲区 ≥ 批大小")
    class PerformanceBoundsTests {

        @Test
        @DisplayName("等于边界时通过")
        void acceptsEqualBounds() {
            assertDoesNotThrow(() -> MonitoringHotReloadValidator.requirePerformanceBounds(1, 1));
            assertDoesNotThrow(() -> MonitoringHotReloadValidator.requirePerformanceBounds(100, 1000));
        }

        @Test
        @DisplayName("批大小为 0 被拒")
        void rejectsNonPositiveBatchSize() {
            ApiException error = assertThrows(ApiException.class,
                    () -> MonitoringHotReloadValidator.requirePerformanceBounds(0, 10));
            assertEquals(ErrorCode.INVALID_REQUEST.code(), error.getErrorCode());
        }

        @Test
        @DisplayName("缓冲区小于批大小被拒（异步处理会取不到整批）")
        void rejectsBufferSmallerThanBatch() {
            ApiException error = assertThrows(ApiException.class,
                    () -> MonitoringHotReloadValidator.requirePerformanceBounds(500, 499));
            assertEquals(ErrorCode.INVALID_REQUEST.code(), error.getErrorCode());
        }
    }
}

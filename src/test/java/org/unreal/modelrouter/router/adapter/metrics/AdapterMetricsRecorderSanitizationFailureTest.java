package org.unreal.modelrouter.router.adapter.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * issue #1058：SUMMARY 记录级别的脱敏链路失败时，不能把原始正文写进调用历史。
 *
 * <p>修复前的行为是「异常/超时/返回空 → 回退为 {@code raw}」，也就是在最该隐藏内容的时刻
 * 把未脱敏原文落库。现在统一改为占位标记。</p>
 */
@DisplayName("AdapterMetricsRecorder 脱敏失败处理（issue #1058）")
@ExtendWith(MockitoExtension.class)
class AdapterMetricsRecorderSanitizationFailureTest {

    private static final String RAW_BODY = "{\"prompt\":\"手机号 13800138000\"}";
    private static final String PLACEHOLDER = "[脱敏失败，内容未记录]";

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private ModelCallStatsRepository statsRepository;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private SanitizationService sanitizationService;

    @Test
    @DisplayName("脱敏抛异常时写占位标记，不写原文")
    void sanitizationFailureWritesPlaceholder() {
        when(sanitizationService.sanitizeForStorage(anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("sanitizer down")));
        AdapterMetricsRecorder recorder = newRecorder();

        assertEquals(PLACEHOLDER, recorder.sanitizeOptional(true, sanitizationService, RAW_BODY).block());
    }

    @Test
    @DisplayName("脱敏返回 null 时写占位标记，不写原文")
    void sanitizationNullResultWritesPlaceholder() {
        when(sanitizationService.sanitizeForStorage(anyString(), anyString()))
                .thenReturn(Mono.empty());
        AdapterMetricsRecorder recorder = newRecorder();

        assertEquals(PLACEHOLDER, recorder.sanitizeOptional(true, sanitizationService, RAW_BODY).block());
    }

    @Test
    @DisplayName("脱敏返回空串时写占位标记，不写原文")
    void sanitizationEmptyResultWritesPlaceholder() {
        when(sanitizationService.sanitizeForStorage(anyString(), anyString()))
                .thenReturn(Mono.just(""));
        AdapterMetricsRecorder recorder = newRecorder();

        assertEquals(PLACEHOLDER, recorder.sanitizeOptional(true, sanitizationService, RAW_BODY).block());
    }

    @Test
    @DisplayName("脱敏成功时使用脱敏后的内容")
    void sanitizationSuccessUsesSanitizedBody() {
        when(sanitizationService.sanitizeForStorage(anyString(), anyString()))
                .thenReturn(Mono.just("{\"prompt\":\"手机号 ***\"}"));
        AdapterMetricsRecorder recorder = newRecorder();

        assertEquals("{\"prompt\":\"手机号 ***\"}",
                recorder.sanitizeOptional(true, sanitizationService, RAW_BODY).block());
    }

    @Test
    @DisplayName("不需要脱敏时原样透传")
    void noSanitizationPassesRawThrough() {
        AdapterMetricsRecorder recorder = newRecorder();

        assertEquals(RAW_BODY, recorder.sanitizeOptional(false, sanitizationService, RAW_BODY).block());
    }

    @Test
    @DisplayName("正文为空时保持为空，不写占位标记")
    void emptyRawBodyStaysEmpty() {
        AdapterMetricsRecorder recorder = newRecorder();

        assertNull(recorder.sanitizeOptional(true, sanitizationService, null).block());
        assertEquals("", recorder.sanitizeOptional(true, sanitizationService, "").block());
    }

    private AdapterMetricsRecorder newRecorder() {
        return new AdapterMetricsRecorder(metricsCollector, statsRepository, registry);
    }
}

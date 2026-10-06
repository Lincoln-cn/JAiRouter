package org.unreal.modelrouter.monitor.tracing.exporter;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 健康状态去抖动（issue #224 批 B2）。
 *
 * <p>{@code monitoring.health.failure-threshold} / {@code recovery-threshold} 此前没有任何消费方 ——
 * 每次检查都直接翻转 {@code /actuator/health} 的状态。本用例钉住接线后的语义：
 * 连续失败达到 failure-threshold 才判不健康，连续成功达到 recovery-threshold 才判恢复，
 * 中途出现反向结果会把对应计数清零。</p>
 */
@DisplayName("导出器健康状态去抖动（issue #224 B2）")
class ExporterHealthCheckerThresholdTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final int RECOVERY_THRESHOLD = 2;

    private static TracingConfiguration configuration() {
        final TracingConfiguration configuration = new TracingConfiguration();
        configuration.getMonitoring().getHealth().setFailureThreshold(FAILURE_THRESHOLD);
        configuration.getMonitoring().getHealth().setRecoveryThreshold(RECOVERY_THRESHOLD);
        return configuration;
    }

    private static ExporterHealthChecker checkerFor(final SpanExporter exporter) {
        final SdkTracerProvider provider = SdkTracerProvider.builder().build();
        final Tracer tracer = provider.get("health-threshold-test");
        return new ExporterHealthChecker(exporter, configuration(), tracer);
    }

    private static void failing(final SpanExporter exporter) {
        // 用 doThrow/doReturn 而不是 when(...)：when(mock.export(...)) 会真的调用 mock，
        // 重新打桩时会被上一次的 thenThrow 直接抛出。
        doThrow(new RuntimeException("导出失败（用例注入）")).when(exporter).export(any());
    }

    private static void succeeding(final SpanExporter exporter) {
        doReturn(CompletableResultCode.ofSuccess()).when(exporter).export(any());
    }

    private static void assertUp(final ExporterHealthChecker checker) {
        assertTrue(Status.UP.equals(checker.health().getStatus()),
                "期望健康(UP)，实际 " + checker.health().getStatus());
    }

    private static void assertNotUp(final ExporterHealthChecker checker) {
        assertFalse(Status.UP.equals(checker.health().getStatus()),
                "期望不健康(非 UP)，实际 " + checker.health().getStatus());
    }

    @Test
    @DisplayName("连续失败达阈值才判不健康；连续成功达阈值才判恢复")
    void transitionsAreDebounced() {
        final SpanExporter exporter = mock(SpanExporter.class);
        failing(exporter);
        final ExporterHealthChecker checker = checkerFor(exporter);

        assertUp(checker);

        checker.performHealthCheck();
        assertUp(checker);
        checker.performHealthCheck();
        assertUp(checker);
        checker.performHealthCheck();
        assertNotUp(checker);

        succeeding(exporter);
        checker.performHealthCheck();
        assertNotUp(checker);
        checker.performHealthCheck();
        assertUp(checker);
    }

    @Test
    @DisplayName("计数不累计：失败之间夹一次成功会清零失败计数")
    void countersResetOnOppositeOutcome() {
        final SpanExporter exporter = mock(SpanExporter.class);
        final ExporterHealthChecker checker = checkerFor(exporter);

        failing(exporter);
        checker.performHealthCheck();
        checker.performHealthCheck();
        assertUp(checker);

        succeeding(exporter);
        checker.performHealthCheck();
        assertUp(checker);

        failing(exporter);
        checker.performHealthCheck();
        assertUp(checker, "失败计数已被成功清零，本次只是第 1 次失败");
    }

    private static void assertUp(final ExporterHealthChecker checker, final String message) {
        assertTrue(Status.UP.equals(checker.health().getStatus()),
                message + "（实际 " + checker.health().getStatus() + "）");
    }
}

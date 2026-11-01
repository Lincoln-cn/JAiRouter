package org.unreal.modelrouter.monitor.tracing.config;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Zipkin 导出器与新 SDK 的二进制兼容守卫（#940）。
 *
 * <p>自 {@code opentelemetry-bom} 1.65.0 起，Zipkin 导出器不再由 BOM 管理 —— 它最后一个发布版本是
 * 1.64.0。本仓保留 {@code tracing.exporter.type=zipkin} 这条路径，所以在 pom 里把它显式钉在 1.64.0，
 * 与 1.67 的 API / SDK 混用。</p>
 *
 * <p>混用是能编译的（编译期 classpath 只有 1.67，说明方法签名没变），但编译通过不等于运行时可用：
 * 若 1.64 的导出器在运行时调用了 1.67 里已改签名的 SDK 方法，会在真实导出时抛
 * {@code NoSuchMethodError} / {@code NoClassDefFoundError}，而这类错误只会在「恰好配置了 zipkin」
 * 的部署里出现。</p>
 *
 * <p>本测试把真实的 {@link ZipkinSpanExporter} 接上一个真实的 {@link SpanData} 走一遍
 * {@code export(...)}：端点故意指向一个不存在的地址，所以结果一定是「导出失败」，
 * 但这已经足够证明调用链能走到底 —— 失败原因是网络，不是链接错误。</p>
 *
 * @since 3.5.10
 */
class ZipkinExporterCompatibilityTest {

    /** 收集 end 掉的 span，避免依赖 sdk-testing（项目未引入）。 */
    private static final class CollectingProcessor implements SpanProcessor {

        private final List<SpanData> collected = new ArrayList<>();

        @Override
        public void onStart(final Context parentContext, final ReadWriteSpan span) {
            // 不需要
        }

        @Override
        public boolean isStartRequired() {
            return false;
        }

        @Override
        public void onEnd(final ReadableSpan span) {
            collected.add(span.toSpanData());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }
    }

    @Test
    @DisplayName("1.64 的 Zipkin 导出器能在 1.67 的 SDK 上跑完导出调用")
    void exportsSpanBuiltByNewSdk() {
        final CollectingProcessor processor = new CollectingProcessor();
        final SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(processor)
                .build();

        final Span span = provider.get("zipkin-compat")
                .spanBuilder("backend-call")
                .startSpan();
        span.setAttribute("test.attr", "value");
        span.end();

        assertThat(processor.collected)
                .as("应收集到一条 SpanData")
                .hasSize(1);

        final ZipkinSpanExporter exporter = ZipkinSpanExporter.builder()
                .setEndpoint("http://127.0.0.1:1/api/v2/spans")
                .build();

        final CompletableResultCode result = exporter.export(processor.collected);

        assertThat(result).as("导出调用应返回结果对象").isNotNull();
        assertThatCode(() -> result.join(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("端点不可达只允许以「失败」收场，不允许抛 NoSuchMethodError / NoClassDefFoundError")
                .doesNotThrowAnyException();
        assertThat(result.isSuccess())
                .as("端口 1 上没有 Zipkin，导出必然失败")
                .isFalse();
    }
}

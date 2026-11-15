package org.unreal.modelrouter.monitor.tracing.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code SpanRecord} 的 span 类型默认值（#1153）。
 *
 * <p>三个构造器承担三种调用场景：只有入口 span 用 9 参（默认 SERVER），
 * 已知父 span 但未声明类型用 10 参（默认 INTERNAL），明确声明类型用 11 参。
 * 三种默认值一旦写错，详情页的「网关自身耗时 / 上游调用耗时」会被算反。</p>
 */
@DisplayName("SpanRecord span 类型默认值（#1153）")
class SpanRecordKindTest {

    private static final Instant START = Instant.now();
    private static final Instant END = START.plusMillis(10);

    private static TraceQueryService.SpanRecord build9() {
        return new TraceQueryService.SpanRecord(
            "span-1", "trace-1", "POST /v1/chat/completions",
            START, END, 10.0, false, "200", new HashMap<>());
    }

    private static TraceQueryService.SpanRecord build10() {
        return new TraceQueryService.SpanRecord(
            "span-1", "trace-1", "InstanceSelection",
            START, END, 10.0, false, "OK", new HashMap<>(), "parent-1");
    }

    private static TraceQueryService.SpanRecord build11(String kind) {
        return new TraceQueryService.SpanRecord(
            "span-1", "trace-1", "Adapter.gpustack.chat",
            START, END, 10.0, false, "OK", new HashMap<>(), "parent-1", kind);
    }

    @Test
    @DisplayName("9 参构造（无父 span）默认按入口 span 处理")
    void nineArgConstructorDefaultsToServer() {
        assertEquals(TraceQueryService.SpanRecord.KIND_SERVER, build9().getSpanKind());
    }

    @Test
    @DisplayName("10 参构造（有父 span 但未声明类型）默认按网关内部处理")
    void tenArgConstructorDefaultsToInternal() {
        assertEquals(TraceQueryService.SpanRecord.KIND_INTERNAL, build10().getSpanKind());
    }

    @Test
    @DisplayName("11 参构造传 null 时回落为网关内部")
    void elevenArgConstructorWithNullFallsBackToInternal() {
        assertEquals(TraceQueryService.SpanRecord.KIND_INTERNAL, build11(null).getSpanKind());
    }

    @Test
    @DisplayName("11 参构造显式传 CLIENT 时保持原值")
    void elevenArgConstructorKeepsExplicitKind() {
        assertEquals(TraceQueryService.SpanRecord.KIND_CLIENT,
            build11(TraceQueryService.SpanRecord.KIND_CLIENT).getSpanKind());
    }
}

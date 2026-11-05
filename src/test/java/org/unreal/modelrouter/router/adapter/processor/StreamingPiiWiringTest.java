package org.unreal.modelrouter.router.adapter.processor;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.pii.PiiProperties;
import org.unreal.modelrouter.pii.ReversiblePiiService;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 流式链路里的可逆脱敏接线。
 *
 * <p>这里直接调用两个包内方法，而不是构造完整的上游 mock —— 要守住的是「请求体被换过、响应块被换回去」
 * 这两个动作本身，链路的其余部分由既有的流式测试覆盖。</p>
 */
class StreamingPiiWiringTest {

    private StreamingRequestProcessor processor;

    private ReversiblePiiService piiService;

    @BeforeEach
    void setUp() {
        processor = new StreamingRequestProcessor(null);
        piiService = Mockito.mock(ReversiblePiiService.class);
        ReflectionTestUtils.setField(processor, "reversiblePiiService", piiService);
        ReflectionTestUtils.setField(processor, "objectMapper", JsonMapper.builder().build());
    }

    @Test
    @DisplayName("请求体在发往上游前被换成占位符")
    void requestBodyIsTokenized() {
        when(piiService.enabled()).thenReturn(true);
        when(piiService.tokenizeNow(eq("scope-1"), anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(1)).replace("13812345678", "[PII_1]"));

        final Object body = java.util.Map.of("model", "qwen3:4b", "messages", List.of("call 13812345678"));
        final Object out = processor.piiTokenizedBody(body, "scope-1");

        assertThat(out).isInstanceOf(String.class);
        assertThat((String) out).contains("[PII_1]").doesNotContain("13812345678");
    }

    @Test
    @DisplayName("未启用时原样返回对象，不序列化")
    void disabledLeavesBodyUntouched() {
        when(piiService.enabled()).thenReturn(false);
        final Object body = java.util.Map.of("model", "qwen3:4b");
        assertThat(processor.piiTokenizedBody(body, "scope-1")).isSameAs(body);
    }

    @Test
    @DisplayName("脱敏抛异常时按未脱敏转发，不阻塞请求")
    void failureFallsBackToOriginalBody() {
        when(piiService.enabled()).thenReturn(true);
        when(piiService.tokenizeNow(anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        final Object body = java.util.Map.of("model", "qwen3:4b");
        assertThat(processor.piiTokenizedBody(body, "scope-1")).isSameAs(body);
    }

    @Test
    @DisplayName("响应块被换回原文")
    void chunkIsDetokenized() {
        when(piiService.enabled()).thenReturn(true);
        when(piiService.detokenizeNow(eq("scope-1"), anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(1)).replace("[PII_1]", "13812345678"));

        final String chunk = "data: {\"choices\":[{\"delta\":{\"content\":\"call [PII_1]\"}}]}";
        assertThat(processor.detokenizeChunk("scope-1", chunk, new java.util.concurrent.atomic.AtomicReference<>(""))).contains("13812345678").doesNotContain("[PII_1]");
    }

    @Test
    @DisplayName("未启用时响应块原样通过")
    void disabledLeavesChunkUntouched() {
        when(piiService.enabled()).thenReturn(false);
        final String chunk = "data: {\"choices\":[]}";
        assertThat(processor.detokenizeChunk("scope-1", chunk, new java.util.concurrent.atomic.AtomicReference<>(""))).isSameAs(chunk);
    }

    @Test
    @DisplayName("占位符被切成两块时仍能还原，客户端看不到碎片")
    void placeholderSplitAcrossChunksIsRestored() {
        when(piiService.enabled()).thenReturn(true);
        when(piiService.detokenizeNow(eq("scope-1"), anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(1)).replace("[PII_1]", "13812345678"));

        final java.util.concurrent.atomic.AtomicReference<String> pending =
                new java.util.concurrent.atomic.AtomicReference<>("");

        final String first = processor.detokenizeChunk("scope-1", "call [P", pending);
        final String second = processor.detokenizeChunk("scope-1", "II_1] now", pending);

        assertThat(first).isEqualTo("call ");
        assertThat(second).isEqualTo("13812345678 now");
        assertThat(pending.get()).isEmpty();
    }
}

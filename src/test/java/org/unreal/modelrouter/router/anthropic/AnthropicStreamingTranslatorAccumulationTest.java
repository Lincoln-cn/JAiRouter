package org.unreal.modelrouter.router.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v3.2.3 (#127): Anthropic 流式翻译器累积上界测试。
 *
 * <p>text/toolArguments 累积受 {@code max-content-chars} 约束；下游未给 usage 时的
 * {@code output_tokens} 估算走独立字符计数，截断不得压低账单数字。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@DisplayName("Anthropic 流式翻译器累积上界 (#127)")
class AnthropicStreamingTranslatorAccumulationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("超上界：output_tokens 估算仍按全量文本精确（截断不影响账单）")
    void overCap_estimationRemainsExact() {
        // 上界 100 字符；实际文本 500 英文字符 → 估算 125 token（500/4）
        final org.unreal.modelrouter.config.core.StreamingSafetyProperties props =
                new org.unreal.modelrouter.config.core.StreamingSafetyProperties();
        props.setMaxContentChars(100);
        final AnthropicStreamingTranslator translator = new AnthropicStreamingTranslator(MAPPER, props);

        final String piece = "a".repeat(50);
        // 10 块 × 50 字符 = 500 字符；无 usage → 按累计文本估算
        final Flux<ServerSentEvent<String>> downstream = Flux.fromIterable(
                IntStream.range(0, 10)
                        .mapToObj(i -> chunk(piece))
                        .toList());

        final List<ServerSentEvent<String>> events =
                translator.toEventStream(downstream, "m", 0L).collectList().block();

        final long outputTokens = outputTokensOf(events);
        assertEquals(125L, outputTokens, "估算必须等于全量文本估算（500/4=125），不受上界截断影响");
    }

    @Test
    @DisplayName("上界之下：output_tokens 估算与全量文本估算一致")
    void underCap_estimationMatchesFullText() {
        final String piece = "b".repeat(20);
        // 5 块 × 20 = 100 字符 → 25 token
        final Flux<ServerSentEvent<String>> downstream = Flux.fromIterable(
                IntStream.range(0, 5)
                        .mapToObj(i -> chunk(piece))
                        .toList());

        final AnthropicStreamingTranslator translator = new AnthropicStreamingTranslator(MAPPER);
        final List<ServerSentEvent<String>> events =
                translator.toEventStream(downstream, "m", 0L).collectList().block();

        final long outputTokens = outputTokensOf(events);
        assertEquals(25L, outputTokens);
        assertEquals(AnthropicTokenEstimator.estimateText("b".repeat(100)), outputTokens,
                "无上界截断时应与 AnthropicTokenEstimator 同源");
    }

    @Test
    @DisplayName("下游自带 usage.completion_tokens：优先使用，与累积无关")
    void backendUsage_wins() {
        final AnthropicStreamingTranslator translator = new AnthropicStreamingTranslator(MAPPER);
        final Flux<ServerSentEvent<String>> downstream = Flux.just(
                ServerSentEvent.<String>builder().data(
                        "{\"choices\":[{\"delta\":{\"content\":\"hello\"}}],"
                                + "\"usage\":{\"completion_tokens\":7}}").build());

        final List<ServerSentEvent<String>> events =
                translator.toEventStream(downstream, "m", 0L).collectList().block();

        assertEquals(7L, outputTokensOf(events));
    }

    private ServerSentEvent<String> chunk(final String text) {
        return ServerSentEvent.<String>builder()
                .data("{\"choices\":[{\"delta\":{\"content\":\"" + text + "\"}}]}")
                .build();
    }

    private long outputTokensOf(final List<ServerSentEvent<String>> events) {
        assertTrue(events != null && !events.isEmpty());
        for (ServerSentEvent<String> event : events) {
            if ("message_delta".equals(event.event())) {
                try {
                    return MAPPER.readTree(event.data()).path("usage").path("output_tokens").asLong();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new AssertionError("未找到 message_delta 事件");
    }
}

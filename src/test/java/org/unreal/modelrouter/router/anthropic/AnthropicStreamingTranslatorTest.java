package org.unreal.modelrouter.router.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnthropicStreamingTranslator} 单测（v3.1 PR-4c）.
 *
 * <p>用真实 DeepSeek（OpenAI 兼容）流式 chunk 形态驱动翻译器，覆盖：
 * 事件序列与逐事件 JSON 形状（{@code message_start} 的 {@code stop_reason:null}、
 * {@code message_delta} 的 {@code stop_sequence:null} 在 NON_NULL 全局策略下仍存在）、
 * {@code [DONE]}/脏块/非 SSE 元素过滤、下游 usage 优先与无 usage 时按累计文本估算、
 * {@code finish_reason} 映射、中途错误 → {@code event: error}、以及真实 SSE 线格式
 * （{@link ServerSentEventHttpMessageWriter} 序列化后 {@code event:}/{@code data:} 行）。</p>
 */
@DisplayName("Anthropic 流式翻译器")
class AnthropicStreamingTranslatorTest {

    /**
     * 模拟全局 ObjectMapper：NON_NULL 策略（{@code JacksonConfig} 配置）
     * ——用于验证各事件 record 上的 {@code @JsonInclude(ALWAYS)} 确实生效。
     */
    private static final ObjectMapper NON_NULL_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String CHUNK_ROLE = """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}""";

    private static final String CHUNK_NIHAO = """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}""";

    private static final String CHUNK_BANG = """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"delta":{"content":"！"},"finish_reason":null}]}""";

    private static final String CHUNK_STOP = """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""";

    private static final String CHUNK_STOP_LENGTH = """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"delta":{},"finish_reason":"length"}]}""";

    private static final String CHUNK_USAGE = """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[],"usage":{"prompt_tokens":10,"completion_tokens":7,"total_tokens":17}}""";

    private AnthropicStreamingTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new AnthropicStreamingTranslator(NON_NULL_MAPPER);
    }

    private List<ServerSentEvent<String>> events(final Flux<ServerSentEvent<String>> stream) {
        return stream.collectList().block();
    }

    private JsonNode payload(final ServerSentEvent<String> event) {
        try {
            return NON_NULL_MAPPER.readTree(event.data());
        } catch (Exception e) {
            throw new IllegalStateException("事件 data 段不是合法 JSON: " + event.data(), e);
        }
    }

    private List<String> names(final List<ServerSentEvent<String>> events) {
        return events.stream().map(ServerSentEvent::event).toList();
    }

    private Flux<ServerSentEvent<String>> downstream(final String... chunks) {
        return Flux.fromArray(chunks)
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    @Test
    @DisplayName("DeepSeek 流式 chunk → 完整 Anthropic 事件序列（逐事件形状）")
    void translatesDeepSeekStream() {
        List<ServerSentEvent<String>> events = events(translator.toEventStream(
                downstream(CHUNK_ROLE, CHUNK_NIHAO, CHUNK_BANG, CHUNK_STOP, "[DONE]"),
                "deepseek-chat", 11L));

        assertEquals(List.of(
                AnthropicStreamEvent.EVENT_MESSAGE_START,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                AnthropicStreamEvent.EVENT_MESSAGE_STOP), names(events));

        // 1. message_start
        JsonNode start = payload(events.get(0));
        assertEquals("message_start", start.get("type").asText());
        JsonNode message = start.get("message");
        assertTrue(message.get("id").asText().startsWith("msg_"), "网关生成 Anthropic 风格 id");
        assertEquals("message", message.get("type").asText());
        assertEquals("assistant", message.get("role").asText());
        assertEquals("deepseek-chat", message.get("model").asText());
        assertTrue(message.get("content").isArray());
        assertEquals(0, message.get("content").size());
        assertTrue(message.has("stop_reason"), "message_start.message.stop_reason 字段必须存在");
        assertTrue(message.get("stop_reason").isNull());
        assertTrue(message.has("stop_sequence"), "message_start.message.stop_sequence 字段必须存在");
        assertTrue(message.get("stop_sequence").isNull());
        assertEquals(11, message.get("usage").get("input_tokens").asInt());
        assertEquals(0, message.get("usage").get("output_tokens").asInt());

        // 2. content_block_start
        JsonNode blockStart = payload(events.get(1));
        assertEquals("content_block_start", blockStart.get("type").asText());
        assertEquals(0, blockStart.get("index").asInt());
        assertEquals("text", blockStart.get("content_block").get("type").asText());
        assertEquals("", blockStart.get("content_block").get("text").asText());

        // 3. content_block_delta × 2
        JsonNode delta1 = payload(events.get(2));
        assertEquals("content_block_delta", delta1.get("type").asText());
        assertEquals(0, delta1.get("index").asInt());
        assertEquals("text_delta", delta1.get("delta").get("type").asText());
        assertEquals("你好", delta1.get("delta").get("text").asText());
        assertEquals("！", payload(events.get(3)).get("delta").get("text").asText());

        // 4. content_block_stop
        JsonNode blockStop = payload(events.get(4));
        assertEquals("content_block_stop", blockStop.get("type").asText());
        assertEquals(0, blockStop.get("index").asInt());

        // 5. message_delta：stop_reason=end_turn、stop_sequence 字段存在且为 null、输出估算（3 汉字/2 → 2）
        JsonNode messageDelta = payload(events.get(5));
        assertEquals("message_delta", messageDelta.get("type").asText());
        assertEquals("end_turn", messageDelta.get("delta").get("stop_reason").asText());
        assertTrue(messageDelta.get("delta").has("stop_sequence"));
        assertTrue(messageDelta.get("delta").get("stop_sequence").isNull());
        assertEquals(2, messageDelta.get("usage").get("output_tokens").asInt());

        // 6. message_stop
        assertEquals("message_stop", payload(events.get(6)).get("type").asText());
    }

    @Test
    @DisplayName("[DONE] / 脏块 / 非 SSE 元素被过滤，不中断事件序列")
    void filtersDoneAndDirtyChunks() {
        Flux<?> mixed = Flux.concat(
                downstream(CHUNK_ROLE),
                Flux.just(ServerSentEvent.<String>builder().data("not-json").build()),
                Flux.just("plain-object-element"),
                downstream("data: " + CHUNK_NIHAO.replace("\n", "")),
                downstream("   "),
                downstream("[DONE]"),
                downstream(CHUNK_STOP));

        List<ServerSentEvent<String>> events = events(translator.toEventStream(mixed, "deepseek-chat", 3L));

        assertEquals(List.of(
                AnthropicStreamEvent.EVENT_MESSAGE_START,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                AnthropicStreamEvent.EVENT_MESSAGE_STOP), names(events));
        assertEquals("你好", payload(events.get(2)).get("delta").get("text").asText());
        assertEquals(3, payload(events.get(0)).get("message").get("usage").get("input_tokens").asInt());
    }

    @Test
    @DisplayName("空流也产出形状完整的事件序列（空文本）")
    void emptyStreamStillWellFormed() {
        List<ServerSentEvent<String>> events = events(
                translator.toEventStream(Flux.empty(), "deepseek-chat", 0L));

        assertEquals(List.of(
                AnthropicStreamEvent.EVENT_MESSAGE_START,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,
                AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                AnthropicStreamEvent.EVENT_MESSAGE_STOP), names(events));
        assertEquals(0, payload(events.get(3)).get("usage").get("output_tokens").asInt());
    }

    @Test
    @DisplayName("下游 usage.completion_tokens 优先于累计文本估算")
    void prefersDownstreamUsage() {
        List<ServerSentEvent<String>> events = events(translator.toEventStream(
                downstream(CHUNK_ROLE, CHUNK_NIHAO, CHUNK_BANG, CHUNK_USAGE, CHUNK_STOP),
                "deepseek-chat", 10L));

        assertEquals(7, payload(events.get(events.size() - 2))
                .get("usage").get("output_tokens").asInt());
    }

    @Test
    @DisplayName("finish_reason=length → stop_reason=max_tokens")
    void mapsLengthToMaxTokens() {
        List<ServerSentEvent<String>> events = events(translator.toEventStream(
                downstream(CHUNK_NIHAO, CHUNK_STOP_LENGTH), "deepseek-chat", 1L));

        JsonNode messageDelta = payload(events.get(events.size() - 2));
        assertEquals("max_tokens", messageDelta.get("delta").get("stop_reason").asText());
    }

    @Test
    @DisplayName("finish_reason 缺失 → stop_reason=end_turn")
    void defaultsToEndTurn() {
        List<ServerSentEvent<String>> events = events(translator.toEventStream(
                downstream(CHUNK_NIHAO), "deepseek-chat", 1L));

        assertEquals("end_turn", payload(events.get(events.size() - 2))
                .get("delta").get("stop_reason").asText());
    }

    @Test
    @DisplayName("下游中途报错 → 发 event: error 收尾（连接已 200，不能只截断）")
    void downstreamErrorProducesErrorEvent() {
        Flux<ServerSentEvent<String>> failing = downstream(CHUNK_ROLE, CHUNK_NIHAO)
                .concatWith(Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "下游鉴权失败")));

        List<ServerSentEvent<String>> events = events(
                translator.toEventStream(failing, "deepseek-chat", 2L));

        assertEquals(AnthropicStreamEvent.EVENT_ERROR, names(events).get(events.size() - 1));
        JsonNode error = payload(events.get(events.size() - 1));
        assertEquals("error", error.get("type").asText());
        assertEquals("authentication_error", error.get("error").get("type").asText());
        assertEquals("下游鉴权失败", error.get("error").get("message").asText());
        assertFalse(names(events).contains(AnthropicStreamEvent.EVENT_MESSAGE_STOP),
                "错误收尾时不得再发 message_stop");
    }

    @Test
    @DisplayName("429 → rate_limit_error；普通异常 → api_error")
    void mapsErrorTypes() {
        List<ServerSentEvent<String>> tooMany = events(translator.toEventStream(
                Flux.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "限流")),
                "m", 0L));
        assertEquals("rate_limit_error", payload(tooMany.get(tooMany.size() - 1))
                .get("error").get("type").asText());

        List<ServerSentEvent<String>> generic = events(translator.toEventStream(
                Flux.error(new IllegalStateException("boom")), "m", 0L));
        assertEquals("api_error", payload(generic.get(generic.size() - 1))
                .get("error").get("type").asText());
    }

    @Test
    @DisplayName("每次订阅独立（Flux.defer 内建状态，id 不共享、序列不串味）")
    void eachSubscriptionIsIndependent() {
        Flux<ServerSentEvent<String>> stream = translator.toEventStream(
                downstream(CHUNK_NIHAO, CHUNK_STOP), "deepseek-chat", 1L);

        List<ServerSentEvent<String>> first = events(stream);
        List<ServerSentEvent<String>> second = events(stream);

        assertEquals(names(first), names(second));
        assertNotEquals(payload(first.get(0)).get("message").get("id").asText(),
                payload(second.get(0)).get("message").get("id").asText());
        assertEquals("你好", payload(second.get(2)).get("delta").get("text").asText());
    }

    @Test
    @DisplayName("流到一半被取消不抛异常（不阻塞 event loop）")
    void cancellationDoesNotHang() {
        StepVerifier.create(translator.toEventStream(
                        downstream(CHUNK_ROLE, CHUNK_NIHAO, CHUNK_STOP), "deepseek-chat", 1L))
                .expectNextCount(2)
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("真实 SSE 线格式：event: <name> + data: <json> + 空行")
    void wireFormatMatchesAnthropicSse() {
        MockServerHttpResponse response = new MockServerHttpResponse();
        ServerSentEventHttpMessageWriter writer = new ServerSentEventHttpMessageWriter();

        writer.write(translator.toEventStream(downstream(CHUNK_NIHAO, CHUNK_STOP), "deepseek-chat", 4L),
                        ResolvableType.forClass(ServerSentEvent.class),
                        MediaType.TEXT_EVENT_STREAM,
                        response,
                        Map.of())
                .block();

        String body = response.getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("event:message_start"), "必须写出 event 行: " + body);
        assertTrue(body.contains("event:content_block_delta"));
        assertTrue(body.contains("event:message_stop"));
        assertTrue(body.contains("data:{"), "data 行应为事件 JSON: " + body);
        assertTrue(body.contains("\"type\":\"text_delta\""));
        assertTrue(body.contains("\"output_tokens\":1"));
        assertFalse(body.contains("chatcmpl-1"), "下游 OpenAI 形态不得出现在 Anthropic 事件流中");
    }

    @Nested
    @DisplayName("工具调用流式翻译（PR-5）")
    class ToolStreaming {

        /**
         * 真实 DeepSeek 工具调用流形态：文本 → 工具（arguments 分多片）→ finish_reason=tool_calls.
         */
        private final String[] toolStream = {
            """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}""",
            chunk("{\"content\":\"我来查询\"}"),
            chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\",\"type\":\"function\","
                    + "\"function\":{\"name\":\"get_weather\",\"arguments\":\"\"}}]}"),
            chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"city\\\":\"}}]}"),
            chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"北京\\\"}\"}}]}"),
            """
            {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}],
             "usage":{"prompt_tokens":20,"completion_tokens":12,"total_tokens":32}}""",
            "[DONE]"
        };

        private String chunk(final String delta) {
            return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":" + delta + ",\"finish_reason\":null}]}";
        }

        @Test
        @DisplayName("文本 + 工具混排 → 事件序列/块下标/partial_json/stop_reason 全对")
        void textThenToolStream() {
            List<ServerSentEvent<String>> events = events(
                    translator.toEventStream(downstream(toolStream), "deepseek-chat", 20L));

            assertEquals(List.of(
                    AnthropicStreamEvent.EVENT_MESSAGE_START,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 0 文本
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,   // index 0 文本增量
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,    // index 0 关闭
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 1 tool_use
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,   // index 1 partial_json
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,   // index 1 partial_json
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,    // index 1 关闭
                    AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                    AnthropicStreamEvent.EVENT_MESSAGE_STOP), names(events));

            // 文本块仍为 index 0（保持既有行为）
            assertEquals(0, payload(events.get(1)).get("index").asInt());
            assertEquals("我来查询", payload(events.get(2)).get("delta").get("text").asText());

            // 工具块起始事件：id/name 来自下游首片
            JsonNode toolStart = payload(events.get(4));
            assertEquals(1, toolStart.get("index").asInt());
            assertEquals("tool_use", toolStart.get("content_block").get("type").asText());
            assertEquals("call_abc", toolStart.get("content_block").get("id").asText());
            assertEquals("get_weather", toolStart.get("content_block").get("name").asText());

            // 入参分片原样透传（客户端拼接后即 {"city":"北京"}）
            assertEquals(1, payload(events.get(5)).get("index").asInt());
            assertEquals("input_json_delta", payload(events.get(5)).get("delta").get("type").asText());
            assertEquals("{\"city\":", payload(events.get(5)).get("delta").get("partial_json").asText());
            assertEquals("\"北京\"}", payload(events.get(6)).get("delta").get("partial_json").asText());

            assertEquals(1, payload(events.get(7)).get("index").asInt());
            assertEquals("tool_use", payload(events.get(8)).get("delta").get("stop_reason").asText());
            assertEquals(12, payload(events.get(8)).get("usage").get("output_tokens").asInt());
        }

        @Test
        @DisplayName("只有工具调用时不产出文本增量，空文本块先行关闭且块序不交叉")
        void toolOnlyStreamKeepsBlockOrder() {
            List<ServerSentEvent<String>> events = events(translator.toEventStream(downstream(
                    chunk("{\"role\":\"assistant\"}"),
                    chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"c0\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"ping\",\"arguments\":\"{}\"}}]}"),
                    chunk("{\"tool_calls\":[{\"index\":1,\"id\":\"c1\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"pong\",\"arguments\":\"{\\\"a\\\":1}\"}}]}"),
                    """
                    {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
                    "[DONE]"), "m", 5L));

            assertEquals(List.of(
                    AnthropicStreamEvent.EVENT_MESSAGE_START,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 0 文本（空）
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,    // index 0 关闭
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 1 工具 c0
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,   // index 1 "{}"
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,    // index 1 关闭
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 2 工具 c1
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,   // index 2 入参
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,    // index 2 关闭
                    AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                    AnthropicStreamEvent.EVENT_MESSAGE_STOP), names(events));

            assertEquals(1, payload(events.get(3)).get("index").asInt());
            assertEquals("c0", payload(events.get(3)).get("content_block").get("id").asText());
            assertEquals(2, payload(events.get(6)).get("index").asInt());
            assertEquals("pong", payload(events.get(6)).get("content_block").get("name").asText());
            assertEquals("tool_use", payload(events.get(9)).get("delta").get("stop_reason").asText());
        }

        @Test
        @DisplayName("工具后恢复文本 → 新文本块下标递增且块不交叉")
        void textResumesAfterToolWithNextIndex() {
            List<ServerSentEvent<String>> events = events(translator.toEventStream(downstream(
                    chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"c0\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"ping\",\"arguments\":\"{}\"}}]}"),
                    chunk("{\"content\":\"继续回答\"}"),
                    chunk("{}"),
                    """
                    {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
                    "[DONE]"), "m", 5L));

            assertEquals(List.of(
                    AnthropicStreamEvent.EVENT_MESSAGE_START,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 0 空文本
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 1 工具
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,   // index 2 文本（递增）
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,    // index 2
                    AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                    AnthropicStreamEvent.EVENT_MESSAGE_STOP), names(events));

            JsonNode resumed = payload(events.get(6));
            assertEquals(2, resumed.get("index").asInt());
            assertEquals("text", resumed.get("content_block").get("type").asText());
            assertEquals(2, payload(events.get(7)).get("index").asInt());
            assertEquals("继续回答", payload(events.get(7)).get("delta").get("text").asText());
            assertEquals(2, payload(events.get(8)).get("index").asInt());
        }

        @Test
        @DisplayName("下游 finish_reason 缺失但有工具块 → stop_reason 上修为 tool_use")
        void toolBlocksUpgradeEndTurnToToolUse() {
            List<ServerSentEvent<String>> events = events(translator.toEventStream(downstream(
                    chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"c0\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"ping\",\"arguments\":\"{}\"}}]}"),
                    "[DONE]"), "m", 5L));

            assertEquals("tool_use", payload(events.get(events.size() - 2))
                    .get("delta").get("stop_reason").asText());
        }

        @Test
        @DisplayName("finish_reason=length 且带工具块 → 保留 max_tokens（截断语义优先）")
        void lengthWinsOverToolUse() {
            List<ServerSentEvent<String>> events = events(translator.toEventStream(downstream(
                    chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"c0\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"ping\",\"arguments\":\"{\\\"a\\\":\"}}]}"),
                    """
                    {"choices":[{"index":0,"delta":{},"finish_reason":"length"}]}""",
                    "[DONE]"), "m", 5L));

            assertEquals("max_tokens", payload(events.get(events.size() - 2))
                    .get("delta").get("stop_reason").asText());
        }

        @Test
        @DisplayName("工具 arguments 片段计入输出 token 估算（下游未给 usage 时）")
        void toolArgumentsCountTowardsOutputTokens() {
            List<ServerSentEvent<String>> events = events(translator.toEventStream(downstream(
                    chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"c0\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"ping\","
                            + "\"arguments\":\"{\\\"city\\\":\\\"beijing\\\"}\"}}]}"),
                    "[DONE]"), "m", 5L));

            assertTrue(payload(events.get(events.size() - 2))
                    .get("usage").get("output_tokens").asInt() > 0, "工具入参应计入输出估算");
        }

        @Test
        @DisplayName("下游缺 tool_calls[].id 时生成 toolu_* 块 ID")
        void generatesToolUseIdWhenDownstreamOmitsIt() {
            List<ServerSentEvent<String>> events = events(translator.toEventStream(downstream(
                    chunk("{\"tool_calls\":[{\"index\":0,\"type\":\"function\","
                            + "\"function\":{\"name\":\"ping\",\"arguments\":\"{}\"}}]}"),
                    "[DONE]"), "m", 5L));

            String id = payload(events.get(3)).get("content_block").get("id").asText();
            assertTrue(id.startsWith("toolu_"), "应生成 Anthropic 风格工具 ID: " + id);
        }

        @Test
        @DisplayName("无工具的下游流回归：事件序列/块下标与 PR-4c 一致（单文本块）")
        void plainStreamUnchanged() {
            List<ServerSentEvent<String>> events = events(translator.toEventStream(
                    downstream(CHUNK_ROLE, CHUNK_NIHAO, CHUNK_BANG, CHUNK_STOP, "[DONE]"),
                    "deepseek-chat", 7L));

            assertEquals(List.of(
                    AnthropicStreamEvent.EVENT_MESSAGE_START,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                    AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                    AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                    AnthropicStreamEvent.EVENT_MESSAGE_STOP), names(events));
            assertEquals(1, names(events).stream()
                    .filter(name -> AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START.equals(name)).count(),
                    "无工具流只应有一个 content_block_start");
            assertEquals(0, payload(events.get(1)).get("index").asInt());
            assertEquals(0, payload(events.get(4)).get("index").asInt());
            assertEquals("end_turn", payload(events.get(5)).get("delta").get("stop_reason").asText());
        }
    }
}

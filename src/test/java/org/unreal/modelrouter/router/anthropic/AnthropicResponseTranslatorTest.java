package org.unreal.modelrouter.router.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnthropicResponseTranslator} 单测（v3.1 PR-4b）.
 *
 * <p>使用真实 DeepSeek（OpenAI 兼容）响应 JSON 验证字段映射、{@code finish_reason} 映射与
 * 缺字段容错；并校验序列化后的 Anthropic 协议形状（{@code stop_sequence} 必须显式输出 null）。</p>
 */
@DisplayName("Anthropic 响应翻译器")
class AnthropicResponseTranslatorTest {

    /**
     * 真实 DeepSeek chat/completions 非流式响应形态（含 usage）.
     */
    private static final String DEEPSEEK_RESPONSE = """
            {"id":"8c5f1d3e-1f2a-4b3c-9d4e-5f6a7b8c9d0e",
             "object":"chat.completion",
             "created":1735689600,
             "model":"deepseek-chat",
             "choices":[{"index":0,
                         "message":{"role":"assistant","content":"你好！有什么可以帮你的？"},
                         "logprobs":null,
                         "finish_reason":"stop"}],
             "usage":{"prompt_tokens":12,"completion_tokens":9,"total_tokens":21,
                      "prompt_tokens_details":{"cached_tokens":0},
                      "completion_tokens_details":{"reasoning_tokens":0}},
             "system_fingerprint":"fp_1c1d2e3f"}""";

    private static final ObjectMapper PROTOCOL_MAPPER = new ObjectMapper();

    /**
     * 模拟全局 ObjectMapper：NON_NULL 策略（{@code JacksonConfig} 配置）——用于验证
     * {@link AnthropicMessagesResponse} 上的 {@code @JsonInclude(ALWAYS)} 确实生效。
     */
    private static final ObjectMapper NON_NULL_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private AnthropicResponseTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new AnthropicResponseTranslator(PROTOCOL_MAPPER);
    }

    @Nested
    @DisplayName("字段映射")
    class Mapping {

        @Test
        @DisplayName("DeepSeek 原生 JSON → Anthropic Message 字段全量映射")
        void mapsDeepSeekResponse() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage(DEEPSEEK_RESPONSE, "deepseek-chat");

            assertEquals("8c5f1d3e-1f2a-4b3c-9d4e-5f6a7b8c9d0e", message.id());
            assertEquals("message", message.type());
            assertEquals("assistant", message.role());
            assertEquals("deepseek-chat", message.model());
            assertEquals(1, message.content().size());
            assertEquals("text", message.content().get(0).type());
            assertEquals("你好！有什么可以帮你的？", message.content().get(0).text());
            assertEquals("end_turn", message.stopReason());
            assertNull(message.stopSequence());
            assertEquals(12, message.usage().inputTokens());
            assertEquals(9, message.usage().outputTokens());
        }

        @Test
        @DisplayName("下游缺 model 时回填请求侧模型名")
        void fallsBackToRequestedModel() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage(
                    "{\"id\":\"x\",\"choices\":[{\"message\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}",
                    "requested-model");

            assertEquals("requested-model", message.model());
            assertEquals("hi", message.content().get(0).text());
        }

        @Test
        @DisplayName("下游 model 存在时优先于请求侧模型名（池路由回显）")
        void prefersDownstreamModel() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage(
                    "{\"model\":\"deepseek-reasoner\",\"choices\":[{\"message\":{\"content\":\"hi\"}}]}",
                    "deepseek-chat");

            assertEquals("deepseek-reasoner", message.model());
        }

        @Test
        @DisplayName("finish_reason: stop → end_turn")
        void finishReasonStop() {
            assertEquals("end_turn", stopReasonOf("stop"));
        }

        @Test
        @DisplayName("finish_reason: length → max_tokens")
        void finishReasonLength() {
            assertEquals("max_tokens", stopReasonOf("length"));
        }

        @Test
        @DisplayName("finish_reason: 其他值（含 missing/未知）→ end_turn")
        void finishReasonOthers() {
            assertEquals("end_turn", stopReasonOf("content_filter"));
            assertEquals("end_turn", stopReasonOf(null));
        }

        @Test
        @DisplayName("finish_reason: tool_calls → tool_use（PR-5）")
        void finishReasonToolCalls() {
            assertEquals("tool_use", stopReasonOf("tool_calls"));
        }

        @Test
        @DisplayName("content 为多模态 part 数组时拼接文本")
        void joinsContentParts() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage(
                    "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"a\"},"
                            + "{\"type\":\"text\",\"text\":\"b\"}]},\"finish_reason\":\"stop\"}]}", "m");

            assertEquals("ab", message.content().get(0).text());
        }
    }

    @Nested
    @DisplayName("容错与协议形状")
    class Tolerance {

        @Test
        @DisplayName("缺 id 时生成 msg_ 前缀 UUID")
        void generatesIdWhenMissing() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage(
                    "{\"choices\":[{\"message\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}", "m");

            assertNotNull(message.id());
            assertTrue(message.id().startsWith("msg_"), "应生成 Anthropic 风格消息 ID: " + message.id());
            assertEquals("msg_".length() + 32, message.id().length());
        }

        @Test
        @DisplayName("缺 usage 时 token 计数降级为 0")
        void zeroUsageWhenMissing() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage(
                    "{\"choices\":[{\"message\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}", "m");

            assertNotNull(message.usage());
            assertEquals(0, message.usage().inputTokens());
            assertEquals(0, message.usage().outputTokens());
        }

        @Test
        @DisplayName("缺 choices 或 content 为 null 时降级为空文本")
        void emptyTextWhenNoChoices() {
            assertEquals("", translator.toAnthropicMessage("{\"id\":\"x\",\"usage\":{\"prompt_tokens\":1}}", "m")
                    .content().get(0).text());
            assertEquals("", translator.toAnthropicMessage(
                    "{\"choices\":[{\"message\":{\"content\":null},\"finish_reason\":\"stop\"}]}", "m")
                    .content().get(0).text());
            assertEquals("", translator.toAnthropicMessage("{\"choices\":[]}", "m").content().get(0).text());
        }

        @Test
        @DisplayName("响应体为空或非合法 JSON 时降级而不抛异常")
        void malformedBodyDegrades() {
            AnthropicMessagesResponse empty = translator.toAnthropicMessage(null, "m");
            assertEquals("m", empty.model());
            assertEquals("", empty.content().get(0).text());
            assertEquals("end_turn", empty.stopReason());
            assertEquals(0, empty.usage().inputTokens());

            AnthropicMessagesResponse notJson = translator.toAnthropicMessage("<html>502 Bad Gateway</html>", "m");
            assertEquals("", notJson.content().get(0).text());
            assertTrue(notJson.id().startsWith("msg_"));
        }

        @Test
        @DisplayName("序列化形状符合 Anthropic 协议（NON_NULL 下 stop_sequence 仍输出 null）")
        void serializesAnthropicShape() throws Exception {
            AnthropicMessagesResponse message = translator.toAnthropicMessage(DEEPSEEK_RESPONSE, "deepseek-chat");
            JsonNode json = NON_NULL_MAPPER.readTree(NON_NULL_MAPPER.writeValueAsString(message));

            assertEquals("message", json.get("type").asText());
            assertEquals("assistant", json.get("role").asText());
            assertEquals("end_turn", json.get("stop_reason").asText());
            assertTrue(json.has("stop_sequence"), "Anthropic 客户端要求 stop_sequence 字段存在");
            assertTrue(json.get("stop_sequence").isNull());
            assertEquals("text", json.get("content").get(0).get("type").asText());
            assertEquals(12, json.get("usage").get("input_tokens").asInt());
            assertEquals(9, json.get("usage").get("output_tokens").asInt());
            assertEquals("8c5f1d3e-1f2a-4b3c-9d4e-5f6a7b8c9d0e", json.get("id").asText());
            assertEquals("deepseek-chat", json.get("model").asText());
        }
    }

    private String stopReasonOf(final String finishReason) {
        String response = "{\"choices\":[{\"message\":{\"content\":\"x\"}," + "\"finish_reason\":"
                + (finishReason == null ? "null" : "\"" + finishReason + "\"") + "}]}";
        return translator.toAnthropicMessage(response, "m").stopReason();
    }

    @Nested
    @DisplayName("工具调用响应（PR-5）")
    class ToolCalls {

        /**
         * 真实 DeepSeek function calling 响应形态（content 与 tool_calls 共存）.
         */
        private static final String DEEPSEEK_TOOL_RESPONSE = """
                {"id":"8c5f1d3e-1f2a-4b3c-9d4e-5f6a7b8c9d0e","object":"chat.completion",
                 "created":1735689600,"model":"deepseek-chat",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"我来查询天气",
                   "tool_calls":[
                     {"index":0,"id":"call_0","type":"function",
                      "function":{"name":"get_weather","arguments":"{\\"city\\":\\"北京\\"}"}},
                     {"index":1,"id":"call_1","type":"function",
                      "function":{"name":"get_time","arguments":"{}"}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":30,"completion_tokens":18,"total_tokens":48}}""";

        @Test
        @DisplayName("多工具 → 文本块 + 各 tool_use 块，input 解析为对象，stop_reason=tool_use")
        void mapsToolCallsToToolUseBlocks() {
            AnthropicMessagesResponse message =
                    translator.toAnthropicMessage(DEEPSEEK_TOOL_RESPONSE, "deepseek-chat");

            assertEquals("tool_use", message.stopReason());
            assertEquals(3, message.content().size());
            assertEquals("text", message.content().get(0).type());
            assertEquals("我来查询天气", message.content().get(0).text());

            AnthropicMessagesResponse.ContentBlock first = message.content().get(1);
            assertEquals("tool_use", first.type());
            assertEquals("call_0", first.id());
            assertEquals("get_weather", first.name());
            assertNull(first.text(), "tool_use 块不得带 text 字段");
            assertEquals(Map.of("city", "北京"), first.input());

            AnthropicMessagesResponse.ContentBlock second = message.content().get(2);
            assertEquals("call_1", second.id());
            assertEquals("get_time", second.name());
            assertEquals(Map.of(), second.input());
            assertEquals(30, message.usage().inputTokens());
            assertEquals(18, message.usage().outputTokens());
        }

        @Test
        @DisplayName("只有工具调用（无文本）时不产出空文本块")
        void toolOnlyProducesNoTextBlock() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage("""
                    {"choices":[{"message":{"role":"assistant","content":"",
                     "tool_calls":[{"id":"call_0","type":"function",
                                    "function":{"name":"ping","arguments":"{}"}}]},
                     "finish_reason":"tool_calls"}]}""", "m");

            assertEquals(1, message.content().size());
            assertEquals("tool_use", message.content().get(0).type());
            assertEquals("tool_use", message.stopReason());
        }

        @Test
        @DisplayName("arguments 非法 JSON → input 保留原文并记 warn（不抛异常）")
        void invalidArgumentsKeptAsRaw() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage("""
                    {"choices":[{"message":{"tool_calls":[{"id":"call_0","type":"function",
                     "function":{"name":"run","arguments":"{not-json"}}]},"finish_reason":"tool_calls"}]}""",
                    "m");

            AnthropicMessagesResponse.ContentBlock block = message.content().get(0);
            assertEquals("tool_use", block.type());
            assertEquals(Map.of("raw_arguments", "{not-json"), block.input());
        }

        @Test
        @DisplayName("arguments 缺失/空串/非对象 JSON → input 降级为对象（{} 或原文包装）")
        void argumentsFallbacks() {
            assertInput(Map.of(), null);
            assertInput(Map.of(), "");
            assertInput(Map.of("raw_arguments", "[1,2]"), "[1,2]");
        }

        @Test
        @DisplayName("下游缺 tool_calls[].id 时生成 toolu_* ID")
        void generatesToolUseIdWhenMissing() {
            AnthropicMessagesResponse message = translator.toAnthropicMessage("""
                    {"choices":[{"message":{"tool_calls":[{"type":"function",
                     "function":{"name":"ping","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""", "m");

            String id = message.content().get(0).id();
            assertNotNull(id);
            assertTrue(id.startsWith("toolu_"), "应生成 Anthropic 风格工具 ID: " + id);
        }

        @Test
        @DisplayName("序列化形状：文本块与 PR-4c 逐字节一致，tool_use 块带 id/name/input 且无 text")
        void serializesToolUseShape() throws Exception {
            AnthropicMessagesResponse message =
                    translator.toAnthropicMessage(DEEPSEEK_TOOL_RESPONSE, "deepseek-chat");
            JsonNode json = NON_NULL_MAPPER.readTree(NON_NULL_MAPPER.writeValueAsString(message));

            JsonNode textBlock = json.get("content").get(0);
            assertEquals("{\"type\":\"text\",\"text\":\"我来查询天气\"}", NON_NULL_MAPPER
                    .writeValueAsString(NON_NULL_MAPPER.treeToValue(textBlock, Object.class)));

            JsonNode toolBlock = json.get("content").get(1);
            assertEquals("tool_use", toolBlock.get("type").asText());
            assertEquals("call_0", toolBlock.get("id").asText());
            assertEquals("get_weather", toolBlock.get("name").asText());
            assertEquals("北京", toolBlock.get("input").get("city").asText());
            assertFalse(toolBlock.has("text"), "tool_use 块不得出现 text 字段: " + toolBlock);
            assertEquals("tool_use", json.get("stop_reason").asText());
        }

        private void assertInput(final Map<String, Object> expected, final String arguments) {
            String response = "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"call_0\","
                    + "\"type\":\"function\",\"function\":{\"name\":\"run\",\"arguments\":"
                    + (arguments == null ? "null" : "\"" + arguments.replace("\"", "\\\"") + "\"")
                    + "}}]},\"finish_reason\":\"tool_calls\"}]}";
            assertEquals(expected, translator.toAnthropicMessage(response, "m").content().get(0).input());
        }
    }
}

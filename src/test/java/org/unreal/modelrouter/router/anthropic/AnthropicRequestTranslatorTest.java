package org.unreal.modelrouter.router.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnthropicRequestTranslator} 单测（v3.1 PR-4b）.
 *
 * <p>覆盖 Anthropic 请求体反序列化（system/content 的「字符串或块数组」联合类型）与
 * Anthropic → {@link ChatDTO.Request} 的字段映射。</p>
 */
@DisplayName("Anthropic 请求翻译器")
class AnthropicRequestTranslatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 模拟全局 ObjectMapper：NON_NULL 策略（{@code JacksonConfig} 配置）——用于比对
     * 「文本视图 messages」与「wire messages」的序列化结果是否逐字节一致。
     */
    private static final ObjectMapper NON_NULL_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private AnthropicRequestTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new AnthropicRequestTranslator();
    }

    private AnthropicMessagesRequest parse(final String json) throws JsonProcessingException {
        return MAPPER.readValue(json, AnthropicMessagesRequest.class);
    }

    @Nested
    @DisplayName("请求体反序列化")
    class Deserialization {

        @Test
        @DisplayName("system 为字符串时归一化为单文本块")
        void systemAsString() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"deepseek-chat","system":"be brief",
                     "messages":[{"role":"user","content":"hi"}],"max_tokens":64}""");

            assertNotNull(request.system());
            assertEquals(1, request.system().size());
            assertEquals("text", request.system().get(0).type());
            assertEquals("be brief", request.system().get(0).text());
        }

        @Test
        @DisplayName("system 为块数组时逐块解析（含 cache_control 扩展字段）")
        void systemAsBlockArray() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"deepseek-chat",
                     "system":[{"type":"text","text":"part1"},
                               {"type":"text","text":"part2","cache_control":{"type":"ephemeral"}}],
                     "messages":[{"role":"user","content":"hi"}]}""");

            assertEquals(2, request.system().size());
            assertEquals("part1", request.system().get(0).text());
            assertEquals("part2", request.system().get(1).text());
        }

        @Test
        @DisplayName("messages[].content 同时支持字符串与块数组")
        void messagesContentBothShapes() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"deepseek-chat","messages":[
                      {"role":"user","content":"plain"},
                      {"role":"assistant","content":[
                        {"type":"text","text":"block"},
                        {"type":"tool_use","id":"t1","name":"get_weather","input":{}}]}]}""");

            assertEquals(2, request.messages().size());
            assertEquals(1, request.messages().get(0).content().size());
            assertEquals("text", request.messages().get(0).content().get(0).type());
            assertEquals("plain", request.messages().get(0).content().get(0).text());
            assertEquals(2, request.messages().get(1).content().size());
            assertEquals("text", request.messages().get(1).content().get(0).type());
            assertEquals("block", request.messages().get(1).content().get(0).text());
            assertEquals("tool_use", request.messages().get(1).content().get(1).type());
            assertNull(request.messages().get(1).content().get(1).text());
        }

        @Test
        @DisplayName("未知字段（tools/thinking/metadata 等）不导致反序列化失败")
        void toleratesUnknownFields() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"deepseek-chat","max_tokens":128,"temperature":0.2,"top_p":0.9,"stream":false,
                     "stop_sequences":["</stop>"],
                     "messages":[{"role":"user","content":"hi"}],
                     "tools":[{"name":"get_weather","description":"d","input_schema":{"type":"object"}}],
                     "tool_choice":{"type":"auto"},
                     "metadata":{"user_id":"u1"},
                     "thinking":{"type":"enabled","budget_tokens":1024},
                     "totally_unknown_field":123}""");

            assertEquals("deepseek-chat", request.model());
            assertEquals(128, request.maxTokens());
            assertEquals(0.2, request.temperature());
            assertEquals(0.9, request.topP());
            assertEquals(Boolean.FALSE, request.stream());
            assertEquals(List.of("</stop>"), request.stopSequences());
            assertEquals(1, request.tools().size());
            assertEquals("get_weather", request.tools().get(0).name());
            assertEquals("object", request.tools().get(0).inputSchema().get("type"));
            assertEquals("auto", ((Map<?, ?>) request.toolChoice()).get("type"));
            assertEquals("u1", request.metadata().get("user_id"));
        }

        @Test
        @DisplayName("content 为非法类型时抛反序列化异常（交由框架转 400）")
        void rejectsNonStringNonArrayContent() {
            assertThrows(JsonProcessingException.class,
                    () -> parse("{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":42}]}"));
        }

        @Test
        @DisplayName("缺失 system/content 保持 null 语义")
        void missingFieldsStayNull() throws Exception {
            AnthropicMessagesRequest request = parse("{\"model\":\"m\",\"messages\":[{\"role\":\"user\"}]}");
            assertNull(request.system());
            assertNull(request.messages().get(0).content());
            assertNull(request.stream());
            assertNull(request.tools());
            assertNull(request.metadata());
        }
    }

    @Nested
    @DisplayName("请求翻译")
    class Translation {

        @Test
        @DisplayName("system 字符串转为首条 system 消息，用户消息紧随其后")
        void systemStringBecomesFirstMessage() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"deepseek-chat","system":"you are helpful","max_tokens":256,
                     "messages":[{"role":"user","content":"hello"}]}""");

            ChatDTO.Request chat = translator.toChatRequest(request);

            assertEquals(2, chat.messages().size());
            assertEquals("system", chat.messages().get(0).role());
            assertEquals("you are helpful", chat.messages().get(0).content());
            assertEquals("user", chat.messages().get(1).role());
            assertEquals("hello", chat.messages().get(1).content());
            assertEquals("deepseek-chat", chat.model());
            assertEquals(256, chat.maxTokens());
            assertEquals(Boolean.FALSE, chat.stream());
        }

        @Test
        @DisplayName("system 块数组拼接为单条 system 消息")
        void systemBlocksJoined() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","system":[{"type":"text","text":"a"},{"type":"text","text":"b"}],
                     "messages":[{"role":"user","content":"q"}]}""");

            assertEquals("ab", translator.toChatRequest(request).messages().get(0).content());
        }

        @Test
        @DisplayName("内容块只拼接 type=text，其他块类型跳过")
        void onlyTextBlocksJoined() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","messages":[{"role":"user","content":[
                      {"type":"text","text":"看这张图 "},
                      {"type":"image","source":{"type":"base64","media_type":"image/png","data":"AAA"}},
                      {"type":"text","text":"描述一下"}]}]}""");

            ChatDTO.Request chat = translator.toChatRequest(request);

            assertEquals(1, chat.messages().size());
            assertEquals("看这张图 描述一下", chat.messages().get(0).content());
            assertFalse(chat.messages().get(0).content().contains("image"));
        }

        @Test
        @DisplayName("全为非文本块的消息内容降级为空串（保持角色序列不被破坏）")
        void nonTextOnlyMessageBecomesEmpty() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","messages":[
                      {"role":"user","content":"hi"},
                      {"role":"user","content":[{"type":"image","source":{"type":"url","url":"http://x"}}]}]}""");

            ChatDTO.Request chat = translator.toChatRequest(request);

            assertEquals(2, chat.messages().size());
            assertEquals("", chat.messages().get(1).content());
        }

        @Test
        @DisplayName("max_tokens/temperature/top_p 直传，stop_sequences 映射为 stop")
        void scalarFieldsMapped() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","max_tokens":1024,"temperature":0.3,"top_p":0.75,
                     "stop_sequences":["END","STOP"],
                     "messages":[{"role":"user","content":"q"}]}""");

            ChatDTO.Request chat = translator.toChatRequest(request);

            assertEquals(1024, chat.maxTokens());
            assertEquals(0.3, chat.temperature());
            assertEquals(0.75, chat.topP());
            assertEquals(List.of("END", "STOP"), chat.stop());
            assertNull(chat.topK());
            assertNull(chat.frequencyPenalty());
            assertNull(chat.presencePenalty());
            assertNull(chat.options());
        }

        @Test
        @DisplayName("stop_sequences 缺失或为空时 stop 为 null")
        void emptyStopSequencesBecomesNull() throws Exception {
            assertNull(translator.toChatRequest(parse(
                    "{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"q\"}]}")).stop());
            assertNull(translator.toChatRequest(parse(
                    "{\"model\":\"m\",\"stop_sequences\":[],"
                            + "\"messages\":[{\"role\":\"user\",\"content\":\"q\"}]}")).stop());
        }

        @Test
        @DisplayName("messages 缺失或为空不崩（返回空消息列表）")
        void missingMessagesDoesNotBreak() throws Exception {
            ChatDTO.Request withoutMessages = translator.toChatRequest(
                    parse("{\"model\":\"m\",\"max_tokens\":8}"));
            assertNotNull(withoutMessages.messages());
            assertTrue(withoutMessages.messages().isEmpty());
            assertEquals("m", withoutMessages.model());

            ChatDTO.Request emptyMessages = translator.toChatRequest(parse("{\"model\":\"m\",\"messages\":[]}"));
            assertTrue(emptyMessages.messages().isEmpty());

            ChatDTO.Request nullRequest = translator.toChatRequest(null);
            assertTrue(nullRequest.messages().isEmpty());
            assertEquals(Boolean.FALSE, nullRequest.stream());
        }

        @Test
        @DisplayName("只有 system 时 system 消息仍保留在首位")
        void systemOnlyRequestKeepsSystemMessage() throws Exception {
            ChatDTO.Request chat = translator.toChatRequest(parse("{\"model\":\"m\",\"system\":\"s\"}"));
            assertEquals(1, chat.messages().size());
            assertEquals("system", chat.messages().get(0).role());
            assertEquals("s", chat.messages().get(0).content());
        }

        @Test
        @DisplayName("metadata 不映射到 user（本版本仅接收不转发）")
        void metadataNotMappedToUser() throws Exception {
            ChatDTO.Request chat = translator.toChatRequest(parse("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "metadata":{"user_id":"u1"},
                     "tools":[{"name":"t","description":"d","input_schema":{}}]}"""));

            assertNull(chat.user());
            assertEquals(1, chat.messages().size());
        }
    }

    @Nested
    @DisplayName("工具调用映射（PR-5）")
    class ToolMapping {

        private ChatDTO.Request chatOf(final String json) throws Exception {
            return translator.toChatRequest(parse(json));
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> functionOf(final ChatDTO.Request chat) {
            final Map<String, Object> tool = chat.tools().get(0);
            assertEquals("function", tool.get("type"));
            return (Map<String, Object>) tool.get("function");
        }

        @Test
        @DisplayName("tools.input_schema 直接作为 OpenAI function.parameters")
        void inputSchemaBecomesParameters() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"deepseek-chat","messages":[{"role":"user","content":"北京天气"}],
                     "tools":[{"name":"get_weather","description":"查询天气",
                               "input_schema":{"type":"object","properties":{"city":{"type":"string"}},
                                               "required":["city"]}}]}""");

            assertEquals(1, chat.tools().size());
            Map<String, Object> function = functionOf(chat);
            assertEquals("get_weather", function.get("name"));
            assertEquals("查询天气", function.get("description"));
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            assertEquals("object", parameters.get("type"));
            assertEquals(List.of("city"), parameters.get("required"));
            // 文本视图不变（tools 不进 messages），options 仅在需要时填充
            assertEquals(1, chat.messages().size());
            assertEquals("北京天气", chat.messages().get(0).content());
        }

        @Test
        @DisplayName("input_schema 缺失时补空 object schema（下游要求 parameters 存在）")
        void missingInputSchemaFallsBackToEmptyObjectSchema() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "tools":[{"name":"ping"}]}""");

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) functionOf(chat).get("parameters");
            assertEquals("object", parameters.get("type"));
            assertEquals(Map.of(), parameters.get("properties"));
        }

        @Test
        @DisplayName("tool_choice: auto（字符串与对象形态）→ auto")
        void toolChoiceAuto() throws Exception {
            assertEquals("auto", chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "tools":[{"name":"ping","input_schema":{}}],"tool_choice":"auto"}""").toolChoice());
            assertEquals("auto", chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "tools":[{"name":"ping","input_schema":{}}],"tool_choice":{"type":"auto"}}""")
                    .toolChoice());
        }

        @Test
        @DisplayName("tool_choice: any → required")
        void toolChoiceAny() throws Exception {
            assertEquals("required", chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "tools":[{"name":"ping","input_schema":{}}],"tool_choice":"any"}""").toolChoice());
            assertEquals("required", chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "tools":[{"name":"ping","input_schema":{}}],"tool_choice":{"type":"any"}}""")
                    .toolChoice());
        }

        @Test
        @DisplayName("tool_choice: {type:tool,name} → {type:function,function:{name}}")
        void toolChoiceNamedTool() throws Exception {
            Object choice = chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "tools":[{"name":"get_weather","input_schema":{}}],
                     "tool_choice":{"type":"tool","name":"get_weather"}}""").toolChoice();

            @SuppressWarnings("unchecked")
            Map<String, Object> choiceMap = (Map<String, Object>) choice;
            assertEquals("function", choiceMap.get("type"));
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) choiceMap.get("function");
            assertEquals("get_weather", function.get("name"));
        }

        @Test
        @DisplayName("tool_choice: none → 不传 tools 也不传 tool_choice")
        void toolChoiceNoneDropsTools() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}],
                     "tools":[{"name":"ping","input_schema":{}}],"tool_choice":"none"}""");

            assertNull(chat.tools());
            assertNull(chat.toolChoice());
        }

        @Test
        @DisplayName("无 tools 且会话中无工具块 → options 为 null（PR-4c 行为不变）")
        void plainRequestKeepsOptionsNull() throws Exception {
            assertNull(chatOf("""
                    {"model":"m","messages":[{"role":"user","content":"q"}]}""").options());
            assertNull(chatOf("""
                    {"model":"m","system":"s","messages":[{"role":"user","content":"q"}]}""").options());
        }

        @Test
        @DisplayName("带 tools 但会话为纯文本时，wire messages 与文本视图逐字节一致")
        void wireMessagesMatchTextMessages() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","system":"be brief","messages":[
                       {"role":"user","content":"q"},
                       {"role":"assistant","content":[{"type":"text","text":"a"}]},
                       {"role":"user","content":"b"}],
                     "tools":[{"name":"ping","input_schema":{"type":"object"}}]}""");

            assertNotNull(chat.wireMessages());
            assertEquals(NON_NULL_MAPPER.writeValueAsString(chat.messages()),
                    NON_NULL_MAPPER.writeValueAsString(chat.wireMessages()));
        }

        @Test
        @DisplayName("assistant 的 tool_use → tool_calls（content 与工具块共存、arguments 为 input JSON）")
        void assistantToolUseBecomesToolCalls() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","messages":[
                      {"role":"user","content":"北京天气"},
                      {"role":"assistant","content":[
                        {"type":"text","text":"我来查一下 "},
                        {"type":"tool_use","id":"toolu_1","name":"get_weather","input":{"city":"北京","days":2}}]},
                      {"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_1",
                                                 "content":"晴 25℃"}]}]}""");

            List<Map<String, Object>> wire = chat.wireMessages();
            assertEquals(3, wire.size());
            assertEquals("user", wire.get(0).get("role"));
            assertEquals("北京天气", wire.get(0).get("content"));

            Map<String, Object> assistant = wire.get(1);
            assertEquals("assistant", assistant.get("role"));
            assertEquals("我来查一下 ", assistant.get("content"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistant.get("tool_calls");
            assertEquals(1, toolCalls.size());
            assertEquals("toolu_1", toolCalls.get(0).get("id"));
            assertEquals("function", toolCalls.get(0).get("type"));
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
            assertEquals("get_weather", function.get("name"));
            assertEquals("{\"city\":\"北京\",\"days\":2}", function.get("arguments"));

            Map<String, Object> toolMessage = wire.get(2);
            assertEquals("tool", toolMessage.get("role"));
            assertEquals("toolu_1", toolMessage.get("tool_call_id"));
            assertEquals("晴 25℃", toolMessage.get("content"));
        }

        @Test
        @DisplayName("assistant 只有工具块时 content 为空串；多个 tool_use 保持块序")
        void assistantToolOnlyKeepsOrder() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","messages":[
                      {"role":"user","content":"q"},
                      {"role":"assistant","content":[
                        {"type":"tool_use","id":"t1","name":"first","input":{}},
                        {"type":"tool_use","id":"t2","name":"second","input":{"a":1}}]}]}""");

            Map<String, Object> assistant = chat.wireMessages().get(1);
            assertEquals("", assistant.get("content"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistant.get("tool_calls");
            assertEquals(2, toolCalls.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) toolCalls.get(0).get("function");
            @SuppressWarnings("unchecked")
            Map<String, Object> second = (Map<String, Object>) toolCalls.get(1).get("function");
            assertEquals("first", first.get("name"));
            assertEquals("{}", first.get("arguments"));
            assertEquals("second", second.get("name"));
            assertEquals("{\"a\":1}", second.get("arguments"));
        }

        @Test
        @DisplayName("tool_result 的 content 支持文本块数组；tool_result 先于同消息文本（下游协议约束）")
        void toolResultBlocksFlattenedAndOrderedFirst() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","messages":[
                      {"role":"user","content":"q"},
                      {"role":"assistant","content":[{"type":"tool_use","id":"t1","name":"ping","input":{}}]},
                      {"role":"user","content":[
                        {"type":"text","text":"谢谢"},
                        {"type":"tool_result","tool_use_id":"t1",
                         "content":[{"type":"text","text":"pong"},{"type":"text","text":"!"}]}]}]}""");

            List<Map<String, Object>> wire = chat.wireMessages();
            assertEquals(4, wire.size());
            assertEquals("tool", wire.get(2).get("role"));
            assertEquals("t1", wire.get(2).get("tool_call_id"));
            assertEquals("pong!", wire.get(2).get("content"));
            assertEquals("user", wire.get(3).get("role"));
            assertEquals("谢谢", wire.get(3).get("content"));
        }

        @Test
        @DisplayName("tool_use 缺 id 时生成 call_* 调用 ID（下游 tool_call_id 必须存在）")
        void missingToolUseIdGeneratesCallId() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","messages":[
                      {"role":"user","content":"q"},
                      {"role":"assistant","content":[{"type":"tool_use","name":"ping","input":{}}]}]}""");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) chat.wireMessages()
                    .get(1).get("tool_calls");
            assertTrue(String.valueOf(toolCalls.get(0).get("id")).startsWith("call_"),
                    "应生成 call_ 前缀 ID: " + toolCalls.get(0).get("id"));
        }

        @Test
        @DisplayName("工具块只影响 wire messages，文本视图仍按文本块拼接")
        void textViewStaysTextOnly() throws Exception {
            ChatDTO.Request chat = chatOf("""
                    {"model":"m","messages":[
                      {"role":"user","content":"q"},
                      {"role":"assistant","content":[
                        {"type":"text","text":"先说结论 "},
                        {"type":"tool_use","id":"t1","name":"ping","input":{}}]},
                      {"role":"user","content":[{"type":"tool_result","tool_use_id":"t1","content":"pong"}]}]}""");

            assertEquals(3, chat.messages().size());
            assertEquals("先说结论 ", chat.messages().get(1).content());
            assertEquals("", chat.messages().get(2).content());
        }
    }
}

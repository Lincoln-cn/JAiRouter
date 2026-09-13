package org.unreal.modelrouter.router.anthropic;

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
}

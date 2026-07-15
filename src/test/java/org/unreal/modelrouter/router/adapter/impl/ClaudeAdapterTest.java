package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformerImpl;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformerImpl;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ClaudeAdapter 单元测试
 * 验证 Claude API 适配器的请求/响应转换逻辑
 *
 * @since v2.8.0
 */
@ExtendWith(MockitoExtension.class)
class ClaudeAdapterTest {

    @Mock
    private AdapterContext context;

    @Mock
    private RequestProcessingSupport requestSupport;

    @Mock
    private ResilienceSupport resilienceSupport;

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private AdapterTracingManager tracingManager;

    private ObjectMapper objectMapper;
    private TestClaudeAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        OpenAiRequestTransformerImpl requestTransformer = new OpenAiRequestTransformerImpl(objectMapper);
        OpenAiResponseTransformerImpl responseTransformerImpl = new OpenAiResponseTransformerImpl(objectMapper);

        lenient().when(context.getObjectMapper()).thenReturn(objectMapper);
        lenient().when(requestSupport.getResponseTransformer()).thenReturn(responseTransformer);
        lenient().when(resilienceSupport.getTracingManager()).thenReturn(tracingManager);
        lenient().when(responseTransformer.adaptModelName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        adapter = new TestClaudeAdapter(context, requestSupport, resilienceSupport,
                requestTransformer, responseTransformerImpl);
    }

    private JsonNode toJsonNode(Object result) throws Exception {
        if (result instanceof JsonNode) {
            return (JsonNode) result;
        }
        return objectMapper.readTree(result.toString());
    }

    /**
     * 测试用的 ClaudeAdapter 子类，暴露 protected 方法
     */
    private static class TestClaudeAdapter extends ClaudeAdapter {
        public TestClaudeAdapter(AdapterContext context,
                                 RequestProcessingSupport requestSupport,
                                 ResilienceSupport resilienceSupport,
                                 org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer openAiRequestTransformer,
                                 org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer openAiResponseTransformer) {
            super(context, requestSupport, resilienceSupport, openAiRequestTransformer, openAiResponseTransformer);
        }

        public String getAdapterTypePublic() { return getAdapterType(); }
        public Object transformRequestPublic(Object request, String adapterType) { return transformRequest(request, adapterType); }
        public Object transformResponsePublic(Object response, String adapterType) { return transformResponse(response, adapterType); }
        public String getAuthorizationHeaderPublic(String authorization, String adapterType) {
            return getAuthorizationHeader(authorization, adapterType);
        }
        public Map<String, String> getAdditionalHeadersPublic() { return getAdditionalHeaders(); }
    }

    // ========== 基本能力测试 ==========

    @Nested
    @DisplayName("supportCapability 测试")
    class SupportCapabilityTests {
        @Test
        @DisplayName("应支持 chat 和 streaming")
        void shouldSupportChatAndStreaming() {
            AdapterCapabilities capabilities = adapter.supportCapability();
            assertNotNull(capabilities);
            assertTrue(capabilities.isSupportChat());
            assertTrue(capabilities.isSupportStreaming());
        }

        @Test
        @DisplayName("不应支持 embedding")
        void shouldNotSupportEmbedding() {
            AdapterCapabilities capabilities = adapter.supportCapability();
            assertFalse(capabilities.isSupportEmbedding());
        }
    }

    @Nested
    @DisplayName("getAdapterType 测试")
    class GetAdapterTypeTests {
        @Test
        @DisplayName("应返回 claude 类型")
        void shouldReturnClaudeType() {
            assertEquals("claude", adapter.getAdapterTypePublic());
        }
    }

    // ========== 认证头测试 ==========

    @Nested
    @DisplayName("认证头测试")
    class AuthenticationTests {

        @Test
        @DisplayName("getAuthorizationHeader 应返回 null")
        void shouldReturnNullForAuthorization() {
            String result = adapter.getAuthorizationHeaderPublic("Bearer test-key", "claude");
            assertNull(result);
        }

        @Test
        @DisplayName("getAdditionalHeaders 应包含 anthropic-version")
        void shouldContainAnthropicVersion() {
            Map<String, String> headers = adapter.getAdditionalHeadersPublic();
            assertNotNull(headers);
            assertEquals("2023-06-01", headers.get("anthropic-version"));
        }
    }

    // ========== Chat 请求转换测试 ==========

    @Nested
    @DisplayName("transformChatRequest 测试")
    class TransformChatRequestTests {

        @Test
        @DisplayName("基本 Chat 请求转换")
        void shouldTransformBasicChatRequest() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "claude-3-sonnet-20240229", messages, false, 1000, 0.7, null, null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "claude");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("claude-3-sonnet-20240229", json.get("model").asText());
            assertEquals(1000, json.get("max_tokens").asInt());
            assertEquals(0.7, json.get("temperature").asDouble(), 0.01);
            assertFalse(json.has("system")); // 无 system 消息时不应有 system 字段
        }

        @Test
        @DisplayName("带 system 消息的 Chat 请求")
        void shouldExtractSystemMessage() throws Exception {
            List<ChatDTO.Message> messages = Arrays.asList(
                    new ChatDTO.Message("system", "You are a helpful assistant", null),
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "claude-3-opus-20240229", messages, false, 2000, 0.5, null, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "claude"));

            // system 消息应被提取到顶级 system 字段
            assertTrue(json.has("system"));
            assertEquals("You are a helpful assistant", json.get("system").asText());

            // messages 数组应只包含非 system 消息
            assertEquals(1, json.get("messages").size());
            assertEquals("user", json.get("messages").get(0).get("role").asText());
            assertEquals("Hello", json.get("messages").get(0).get("content").asText());
        }

        @Test
        @DisplayName("多个 system 消息应合并")
        void shouldMergeMultipleSystemMessages() throws Exception {
            List<ChatDTO.Message> messages = Arrays.asList(
                    new ChatDTO.Message("system", "First instruction", null),
                    new ChatDTO.Message("system", "Second instruction", null),
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "claude-3-sonnet-20240229", messages, false, null, null, null, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "claude"));

            // 最后一个 system 消息应覆盖前面的
            assertTrue(json.has("system"));
            assertEquals(1, json.get("messages").size());
        }

        @Test
        @DisplayName("带 stream 参数的请求")
        void shouldTransformChatRequestWithStream() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "claude-3-sonnet-20240229", messages, true, 1000, null, null, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "claude"));
            assertTrue(json.get("stream").asBoolean());
        }

        @Test
        @DisplayName("带 stop 参数的请求")
        void shouldTransformStopToStopSequences() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "claude-3-sonnet-20240229", messages, false, 1000, null, null,
                    null, null, null, "STOP", null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "claude"));
            assertTrue(json.has("stop_sequences"));
            assertEquals(1, json.get("stop_sequences").size());
            assertEquals("STOP", json.get("stop_sequences").get(0).asText());
        }

        @Test
        @DisplayName("max_tokens 缺失时使用默认值")
        void shouldUseDefaultMaxTokens() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "claude-3-sonnet-20240229", messages, false, null, null, null, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "claude"));
            assertEquals(4096, json.get("max_tokens").asInt());
        }
    }

    // ========== 响应转换测试 ==========

    @Nested
    @DisplayName("transformResponse 测试")
    class TransformResponseTests {

        @Test
        @DisplayName("Claude 响应转换为 OpenAI 格式")
        void shouldTransformClaudeResponseToOpenAi() throws Exception {
            String claudeResponse = """
                {
                    "id": "msg_01XFDUDYJgAACjnvnFvZwEge",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "text", "text": "Hello! How can I help you?"}],
                    "model": "claude-3-sonnet-20240229",
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 10, "output_tokens": 8}
                }
                """;

            Object result = adapter.transformResponsePublic(claudeResponse, "claude");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("chat.completion", json.get("object").asText());
            assertEquals("claude-3-sonnet-20240229", json.get("model").asText());
            assertTrue(json.has("choices"));
            assertEquals(1, json.get("choices").size());
            assertEquals("assistant", json.get("choices").get(0).get("message").get("role").asText());
            assertEquals("Hello! How can I help you?", json.get("choices").get(0).get("message").get("content").asText());
            assertEquals("stop", json.get("choices").get(0).get("finish_reason").asText());
        }

        @Test
        @DisplayName("Claude 响应 usage 转换")
        void shouldTransformUsageCorrectly() throws Exception {
            String claudeResponse = """
                {
                    "id": "msg_123",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "text", "text": "Test"}],
                    "model": "claude-3-haiku-20240307",
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 5, "output_tokens": 3}
                }
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(claudeResponse, "claude"));
            assertTrue(json.has("usage"));
            assertEquals(5, json.get("usage").get("prompt_tokens").asInt());
            assertEquals(3, json.get("usage").get("completion_tokens").asInt());
            assertEquals(8, json.get("usage").get("total_tokens").asInt());
        }

        @Test
        @DisplayName("stop_reason 映射为 finish_reason")
        void shouldMapStopReasonCorrectly() throws Exception {
            String[] stopReasons = {"end_turn", "max_tokens", "stop_sequence"};
            String[] expectedFinishReasons = {"stop", "length", "stop"};

            for (int i = 0; i < stopReasons.length; i++) {
                String claudeResponse = """
                    {
                        "id": "msg_123",
                        "type": "message",
                        "role": "assistant",
                        "content": [{"type": "text", "text": "Test"}],
                        "model": "claude-3-sonnet-20240229",
                        "stop_reason": "%s",
                        "usage": {"input_tokens": 1, "output_tokens": 1}
                    }
                    """.formatted(stopReasons[i]);

                JsonNode json = toJsonNode(adapter.transformResponsePublic(claudeResponse, "claude"));
                assertEquals(expectedFinishReasons[i], json.get("choices").get(0).get("finish_reason").asText(),
                        "stop_reason " + stopReasons[i] + " should map to " + expectedFinishReasons[i]);
            }
        }

        @Test
        @DisplayName("非 Claude 格式响应直接返回")
        void shouldReturnNonClaudeResponseAsIs() {
            String openAiResponse = """
                {"id":"chat-123","object":"chat.completion","model":"gpt-4","choices":[]}
                """;

            Object result = adapter.transformResponsePublic(openAiResponse, "claude");
            assertEquals(openAiResponse, result);
        }

        @Test
        @DisplayName("非字符串响应直接返回")
        void shouldReturnNonStringResponseAsIs() {
            Object response = new Object();
            Object result = adapter.transformResponsePublic(response, "claude");
            assertNotNull(result);
        }

        @Test
        @DisplayName("无效 JSON 响应返回原字符串")
        void shouldReturnInvalidJsonAsIs() {
            String invalidJson = "not a valid json";
            Object result = adapter.transformResponsePublic(invalidJson, "claude");
            assertEquals(invalidJson, result);
        }

        @Test
        @DisplayName("多 content 块合并")
        void shouldMergeMultipleContentBlocks() throws Exception {
            String claudeResponse = """
                {
                    "id": "msg_123",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {"type": "text", "text": "Part 1 "},
                        {"type": "text", "text": "Part 2"}
                    ],
                    "model": "claude-3-sonnet-20240229",
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 1, "output_tokens": 2}
                }
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(claudeResponse, "claude"));
            assertEquals("Part 1 Part 2", json.get("choices").get(0).get("message").get("content").asText());
        }
    }

    // ========== 未知请求类型测试 ==========

    @Nested
    @DisplayName("未知请求类型测试")
    class UnknownRequestTests {
        @Test
        @DisplayName("未知请求类型返回原请求")
        void shouldReturnUnknownRequestAsIs() {
            Object unknownRequest = "unknown request";
            Object result = adapter.transformRequestPublic(unknownRequest, "claude");
            assertSame(unknownRequest, result);
        }
    }
}

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
 * GeminiAdapter 单元测试
 * 验证 Gemini API 适配器的请求/响应转换逻辑
 *
 * @since v2.8.3
 */
@ExtendWith(MockitoExtension.class)
class GeminiAdapterTest {

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
    private TestGeminiAdapter adapter;

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

        adapter = new TestGeminiAdapter(context, requestSupport, resilienceSupport,
                requestTransformer, responseTransformerImpl);
    }

    private JsonNode toJsonNode(Object result) throws Exception {
        if (result instanceof JsonNode) {
            return (JsonNode) result;
        }
        return objectMapper.readTree(result.toString());
    }

    /**
     * 测试用的 GeminiAdapter 子类，暴露 protected 方法
     */
    private static class TestGeminiAdapter extends GeminiAdapter {
        public TestGeminiAdapter(AdapterContext context,
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
        @DisplayName("应支持 embedding")
        void shouldSupportEmbedding() {
            AdapterCapabilities capabilities = adapter.supportCapability();
            assertTrue(capabilities.isSupportEmbedding());
        }
    }

    @Nested
    @DisplayName("getAdapterType 测试")
    class GetAdapterTypeTests {
        @Test
        @DisplayName("应返回 gemini 类型")
        void shouldReturnGeminiType() {
            assertEquals("gemini", adapter.getAdapterTypePublic());
        }
    }

    // ========== 认证头测试 ==========

    @Nested
    @DisplayName("认证头测试")
    class AuthenticationTests {
        @Test
        @DisplayName("getAuthorizationHeader 应返回 null（阻止 Bearer 头）")
        void shouldReturnNullForAuthorization() {
            String result = adapter.getAuthorizationHeaderPublic("Bearer test-key", "chat");
            assertNull(result);
        }

        @Test
        @DisplayName("getAdditionalHeaders 应返回空 Map")
        void shouldReturnEmptyAdditionalHeaders() {
            Map<String, String> headers = adapter.getAdditionalHeadersPublic();
            assertNotNull(headers);
            assertTrue(headers.isEmpty());
        }
    }

    // ========== 请求转换测试 ==========

    @Nested
    @DisplayName("Chat 请求转换测试")
    class TransformChatRequestTests {
        @Test
        @DisplayName("应正确转换基本 Chat 请求")
        void shouldTransformBasicChatRequest() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "gemini-pro", messages, false, null, null, null, null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");
            JsonNode geminiRequest = toJsonNode(result);

            assertTrue(geminiRequest.has("contents"));
            assertEquals(1, geminiRequest.get("contents").size());
            assertEquals("user", geminiRequest.get("contents").get(0).get("role").asText());
            assertEquals("Hello", geminiRequest.get("contents").get(0).get("parts").get(0).get("text").asText());
        }

        @Test
        @DisplayName("应提取 system 消息到 systemInstruction 字段")
        void shouldExtractSystemMessage() throws Exception {
            List<ChatDTO.Message> messages = Arrays.asList(
                    new ChatDTO.Message("system", "You are helpful", null),
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "gemini-pro", messages, false, null, null, null, null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");
            JsonNode geminiRequest = toJsonNode(result);

            assertTrue(geminiRequest.has("systemInstruction"));
            assertEquals("You are helpful", geminiRequest.get("systemInstruction").get("parts").get(0).get("text").asText());
            assertEquals(1, geminiRequest.get("contents").size());
            assertEquals("user", geminiRequest.get("contents").get(0).get("role").asText());
        }

        @Test
        @DisplayName("应将 assistant 角色转换为 model 角色")
        void shouldConvertAssistantToModelRole() throws Exception {
            List<ChatDTO.Message> messages = Arrays.asList(
                    new ChatDTO.Message("user", "Hello", null),
                    new ChatDTO.Message("assistant", "Hi there", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "gemini-pro", messages, false, null, null, null, null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");
            JsonNode geminiRequest = toJsonNode(result);

            assertEquals(2, geminiRequest.get("contents").size());
            assertEquals("user", geminiRequest.get("contents").get(0).get("role").asText());
            assertEquals("model", geminiRequest.get("contents").get(1).get("role").asText());
        }

        @Test
        @DisplayName("应设置 generationConfig 参数")
        void shouldSetGenerationConfig() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "gemini-pro", messages, false, 100, 0.7, null, null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");
            JsonNode geminiRequest = toJsonNode(result);

            assertTrue(geminiRequest.has("generationConfig"));
            JsonNode config = geminiRequest.get("generationConfig");
            assertEquals(0.7, config.get("temperature").asDouble(), 0.01);
            assertEquals(100, config.get("maxOutputTokens").asInt());
        }

        @Test
        @DisplayName("应转换 stop 参数为 stopSequences 数组")
        void shouldConvertStopToStopSequences() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "gemini-pro", messages, false, null, null, null, null, null, null, "STOP", null, null);

            Object result = adapter.transformRequestPublic(request, "chat");
            JsonNode geminiRequest = toJsonNode(result);

            assertTrue(geminiRequest.get("generationConfig").has("stopSequences"));
            JsonNode stopSequences = geminiRequest.get("generationConfig").get("stopSequences");
            assertTrue(stopSequences.isArray());
            assertEquals(1, stopSequences.size());
            assertEquals("STOP", stopSequences.get(0).asText());
        }

        @Test
        @DisplayName("应支持 topK 参数")
        void shouldSupportTopK() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "gemini-pro", messages, false, null, null, null, 40, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");
            JsonNode geminiRequest = toJsonNode(result);

            assertEquals(40, geminiRequest.get("generationConfig").get("topK").asInt());
        }
    }

    // ========== 响应转换测试 ==========

    @Nested
    @DisplayName("响应转换测试")
    class TransformResponseTests {
        @Test
        @DisplayName("应正确转换 Gemini 响应为 OpenAI 格式")
        void shouldTransformGeminiResponseToOpenAi() throws Exception {
            String geminiResponse = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [{"text": "Hello! How can I help you?"}],
                            "role": "model"
                          },
                          "finishReason": "STOP"
                        }
                      ],
                      "usageMetadata": {
                        "promptTokenCount": 10,
                        "candidatesTokenCount": 15,
                        "totalTokenCount": 25
                      }
                    }
                    """;

            Object result = adapter.transformResponsePublic(geminiResponse, "chat");
            JsonNode openAiResponse = toJsonNode(result);

            assertTrue(openAiResponse.has("id"));
            assertTrue(openAiResponse.get("id").asText().startsWith("chatcmpl-"));
            assertEquals("chat.completion", openAiResponse.get("object").asText());
            assertTrue(openAiResponse.has("created"));
            assertEquals("gemini", openAiResponse.get("model").asText());

            // 验证 choices
            assertTrue(openAiResponse.has("choices"));
            assertEquals(1, openAiResponse.get("choices").size());
            JsonNode choice = openAiResponse.get("choices").get(0);
            assertEquals("assistant", choice.get("message").get("role").asText());
            assertEquals("Hello! How can I help you?", choice.get("message").get("content").asText());
            assertEquals("stop", choice.get("finish_reason").asText());

            // 验证 usage
            assertTrue(openAiResponse.has("usage"));
            assertEquals(10, openAiResponse.get("usage").get("prompt_tokens").asInt());
            assertEquals(15, openAiResponse.get("usage").get("completion_tokens").asInt());
            assertEquals(25, openAiResponse.get("usage").get("total_tokens").asInt());
        }

        @Test
        @DisplayName("应正确映射 MAX_TOKENS 为 length")
        void shouldMapMaxTokensToLength() throws Exception {
            String geminiResponse = """
                    {
                      "candidates": [
                        {
                          "content": {"parts": [{"text": "Truncated"}], "role": "model"},
                          "finishReason": "MAX_TOKENS"
                        }
                      ]
                    }
                    """;

            Object result = adapter.transformResponsePublic(geminiResponse, "chat");
            JsonNode openAiResponse = toJsonNode(result);

            assertEquals("length", openAiResponse.get("choices").get(0).get("finish_reason").asText());
        }

        @Test
        @DisplayName("应正确映射 SAFETY 为 content_filter")
        void shouldMapSafetyToContentFilter() throws Exception {
            String geminiResponse = """
                    {
                      "candidates": [
                        {
                          "content": {"parts": [{"text": ""}], "role": "model"},
                          "finishReason": "SAFETY"
                        }
                      ]
                    }
                    """;

            Object result = adapter.transformResponsePublic(geminiResponse, "chat");
            JsonNode openAiResponse = toJsonNode(result);

            assertEquals("content_filter", openAiResponse.get("choices").get(0).get("finish_reason").asText());
        }

        @Test
        @DisplayName("非 Gemini 格式响应应原样返回")
        void shouldReturnNonGeminiResponseAsIs() throws Exception {
            String nonGeminiResponse = "{\"some\": \"other format\"}";

            Object result = adapter.transformResponsePublic(nonGeminiResponse, "chat");
            assertEquals(nonGeminiResponse, result);
        }

        @Test
        @DisplayName("非字符串响应应原样返回")
        void shouldReturnNonStringResponseAsIs() {
            Object nonStringResponse = Map.of("some", "object");

            Object result = adapter.transformResponsePublic(nonStringResponse, "chat");
            assertEquals(nonStringResponse, result);
        }

        @Test
        @DisplayName("应处理多个 content parts 合并")
        void shouldMergeMultipleContentParts() throws Exception {
            String geminiResponse = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {"text": "Hello "},
                              {"text": "World"}
                            ],
                            "role": "model"
                          },
                          "finishReason": "STOP"
                        }
                      ]
                    }
                    """;

            Object result = adapter.transformResponsePublic(geminiResponse, "chat");
            JsonNode openAiResponse = toJsonNode(result);

            assertEquals("Hello World", openAiResponse.get("choices").get(0).get("message").get("content").asText());
        }
    }

    // ========== Embedding 请求转换测试 ==========

    @Nested
    @DisplayName("Embedding 请求转换测试")
    class TransformEmbeddingRequestTests {
        @Test
        @DisplayName("应正确转换 Embedding 请求")
        void shouldTransformEmbeddingRequest() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "text-embedding-004",
                    "Hello world",
                    null, null, null, null
            );

            Object result = adapter.transformRequestPublic(request, "embedding");
            JsonNode geminiRequest = toJsonNode(result);

            assertTrue(geminiRequest.has("requests"));
            JsonNode embeddingReq = geminiRequest.get("requests").get(0);
            assertEquals("models/text-embedding-004", embeddingReq.get("model").asText());
            assertEquals("Hello world", embeddingReq.get("content").get("parts").get(0).get("text").asText());
        }
    }
}

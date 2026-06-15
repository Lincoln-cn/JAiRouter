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
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LocalAiAdapter 单元测试
 *
 * @since v2.9.x
 */
@ExtendWith(MockitoExtension.class)
class LocalAiAdapterTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ModelCallStatsRepository statsRepository;

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
    private TestLocalAiAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        lenient().when(context.getObjectMapper()).thenReturn(objectMapper);
        lenient().when(requestSupport.getResponseTransformer()).thenReturn(responseTransformer);
        lenient().when(resilienceSupport.getTracingManager()).thenReturn(tracingManager);
        lenient().when(responseTransformer.adaptModelName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        adapter = new TestLocalAiAdapter(context, requestSupport, resilienceSupport);
    }

    private JsonNode toJsonNode(Object result) throws Exception {
        if (result instanceof JsonNode) {
            return (JsonNode) result;
        }
        return objectMapper.readTree(result.toString());
    }

    private static class TestLocalAiAdapter extends LocalAiAdapter {
        public TestLocalAiAdapter(AdapterContext context,
                                  RequestProcessingSupport requestSupport,
                                  ResilienceSupport resilienceSupport) {
            super(context, requestSupport, resilienceSupport);
        }

        public String getAdapterTypePublic() { return getAdapterType(); }
        public Object transformRequestPublic(Object request, String adapterType) { return transformRequest(request, adapterType); }
        public Object transformResponsePublic(Object response, String adapterType) { return transformResponse(response, adapterType); }
        public String getAuthorizationHeaderPublic(String authorization, String adapterType) { return getAuthorizationHeader(authorization, adapterType); }
        public String transformStreamChunkPublic(String chunk) { return transformStreamChunk(chunk); }
    }

    // ========== 基本能力测试 ==========

    @Nested
    @DisplayName("supportCapability 测试")
    class SupportCapabilityTests {
        @Test
        @DisplayName("应返回所有能力支持")
        void shouldSupportAllCapabilities() {
            AdapterCapabilities capabilities = adapter.supportCapability();
            assertNotNull(capabilities);
            assertTrue(capabilities.isSupportChat());
            assertTrue(capabilities.isSupportEmbedding());
            assertTrue(capabilities.isSupportRerank());
            assertTrue(capabilities.isSupportTts());
            assertTrue(capabilities.isSupportStt());
        }
    }

    @Nested
    @DisplayName("getAdapterType 测试")
    class GetAdapterTypeTests {
        @Test
        @DisplayName("应返回localai类型")
        void shouldReturnLocalAiType() {
            assertEquals("localai", adapter.getAdapterTypePublic());
        }
    }

    // ========== Chat请求转换测试 ==========

    @Nested
    @DisplayName("transformChatRequest 测试")
    class TransformChatRequestTests {

        @Test
        @DisplayName("基本Chat请求转换")
        void shouldTransformBasicChatRequest() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama-3", messages, false, 100, 0.7, 0.9, null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "localai");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("llama-3", json.get("model").asText());
            assertTrue(json.has("messages"));
            assertEquals(100, json.get("max_tokens").asInt());
            assertEquals(0.7, json.get("temperature").asDouble(), 0.01);
        }

        @Test
        @DisplayName("带stream参数的Chat请求")
        void shouldTransformChatRequestWithStream() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "mistral-7b", messages, true, 200, 0.5, 0.95, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertTrue(json.get("stream").asBoolean());
        }

        @Test
        @DisplayName("带stop参数的Chat请求")
        void shouldTransformChatRequestWithStop() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama-3", messages, false, 100, 0.7, 0.9,
                    null, null, null, Arrays.asList("STOP", "END"), null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertTrue(json.has("stop"));
            assertEquals(2, json.get("stop").size());
        }

        @Test
        @DisplayName("带所有参数的Chat请求")
        void shouldTransformChatRequestWithAllParams() throws Exception {
            List<ChatDTO.Message> messages = Arrays.asList(
                    new ChatDTO.Message("system", "You are helpful", null),
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "mixtral-8x7b", messages, true, 500, 0.8, 0.95,
                    null, 0.5, 0.3, Collections.singletonList("STOP"), "user-123", null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertEquals("mixtral-8x7b", json.get("model").asText());
            assertEquals(500, json.get("max_tokens").asInt());
            assertEquals(0.8, json.get("temperature").asDouble(), 0.01);
            assertEquals(0.95, json.get("top_p").asDouble(), 0.01);
            assertEquals(0.5, json.get("frequency_penalty").asDouble(), 0.01);
            assertEquals(0.3, json.get("presence_penalty").asDouble(), 0.01);
            assertEquals("user-123", json.get("user").asText());
        }

        @Test
        @DisplayName("带extra_body扩展参数的Chat请求")
        void shouldTransformChatRequestWithExtraBody() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Options options = ChatDTO.Options.builder()
                    .repetitionPenalty(1.1)
                    .minP(0.05)
                    .priority(1)
                    .build();
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama-3", messages, false, 100, 0.7, 0.9,
                    40, null, null, null, null, options);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertTrue(json.has("extra_body"));
            JsonNode extraBody = json.get("extra_body");
            assertEquals(1.1, extraBody.get("repetition_penalty").asDouble(), 0.01);
            assertEquals(0.05, extraBody.get("min_p").asDouble(), 0.01);
            assertEquals(1, extraBody.get("priority").asInt());
        }

        @Test
        @DisplayName("带name参数的Message")
        void shouldTransformChatRequestWithMessageName() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", "Alice"));
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama-3", messages, false, 100, null, null, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertEquals("Alice", json.get("messages").get(0).get("name").asText());
        }
    }

    // ========== Embedding请求转换测试 ==========

    @Nested
    @DisplayName("transformEmbeddingRequest 测试")
    class TransformEmbeddingRequestTests {

        @Test
        @DisplayName("基本Embedding请求转换")
        void shouldTransformBasicEmbeddingRequest() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "all-MiniLM-L6-v2", "Hello world", null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "localai");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("all-MiniLM-L6-v2", json.get("model").asText());
            assertEquals("Hello world", json.get("input").asText());
        }

        @Test
        @DisplayName("带数组输入的Embedding请求")
        void shouldTransformEmbeddingRequestWithArrayInput() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "all-MiniLM-L6-v2", Arrays.asList("Hello", "World"),
                    "float", 384, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertTrue(json.get("input").isArray());
            assertEquals(2, json.get("input").size());
            assertEquals("float", json.get("encoding_format").asText());
            assertEquals(384, json.get("dimensions").asInt());
        }

        @Test
        @DisplayName("带extra_body的Embedding请求")
        void shouldTransformEmbeddingRequestWithExtraBody() throws Exception {
            EmbeddingDTO.Options options = EmbeddingDTO.Options.builder()
                    .truncatePromptTokens(true)
                    .requestId("req-123")
                    .build();
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "e5-large-v2", "Test input", null, 1024, null, options);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertTrue(json.has("extra_body"));
            assertEquals("req-123", json.get("extra_body").get("request_id").asText());
        }
    }

    // ========== Rerank请求转换测试 ==========

    @Nested
    @DisplayName("transformRerankRequest 测试")
    class TransformRerankRequestTests {

        @Test
        @DisplayName("基本Rerank请求转换")
        void shouldTransformBasicRerankRequest() throws Exception {
            RerankDTO.Request request = new RerankDTO.Request(
                    "bge-reranker-base", "search query",
                    Arrays.asList("Doc 1", "Doc 2", "Doc 3"), 3, true, null);

            Object result = adapter.transformRequestPublic(request, "localai");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("bge-reranker-base", json.get("model").asText());
            assertEquals("search query", json.get("query").asText());
            assertEquals(3, json.get("documents").size());
            assertEquals(3, json.get("top_n").asInt());
            assertTrue(json.get("return_documents").asBoolean());
        }

        @Test
        @DisplayName("不带return_documents的Rerank请求")
        void shouldTransformRerankRequestWithoutReturnDocuments() throws Exception {
            RerankDTO.Request request = new RerankDTO.Request(
                    "bge-reranker-large", "query", Collections.singletonList("Single doc"),
                    null, false, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertFalse(json.get("return_documents").asBoolean());
        }

        @Test
        @DisplayName("带extra_body的Rerank请求")
        void shouldTransformRerankRequestWithExtraBody() throws Exception {
            RerankDTO.Options options = RerankDTO.Options.builder()
                    .requestId("req-456")
                    .priority(1)
                    .build();
            RerankDTO.Request request = new RerankDTO.Request(
                    "bge-reranker-base", "query", Arrays.asList("Doc 1", "Doc 2"),
                    2, true, options);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertTrue(json.has("extra_body"));
            assertEquals("req-456", json.get("extra_body").get("request_id").asText());
            assertEquals(1, json.get("extra_body").get("priority").asInt());
        }
    }

    // ========== TTS请求转换测试 ==========

    @Nested
    @DisplayName("transformTtsRequest 测试")
    class TransformTtsRequestTests {
        @Test
        @DisplayName("基本TTS请求转换")
        void shouldTransformBasicTtsRequest() throws Exception {
            TtsDTO.Request request = new TtsDTO.Request(
                    "bark", "Hello, this is a test", "speaker-1", "wav", 1.0);

            Object result = adapter.transformRequestPublic(request, "localai");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("bark", json.get("model").asText());
            assertEquals("Hello, this is a test", json.get("input").asText());
            assertEquals("speaker-1", json.get("voice").asText());
            assertEquals("wav", json.get("response_format").asText());
            assertEquals(1.0, json.get("speed").asDouble(), 0.01);
        }

        @Test
        @DisplayName("不带response_format时使用默认mp3")
        void shouldUseDefaultResponseFormat() throws Exception {
            TtsDTO.Request request = new TtsDTO.Request(
                    "tts-local", "Test speech", "default", null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "localai"));
            assertEquals("mp3", json.get("response_format").asText());
        }
    }

    // ========== STT请求转换测试 ==========

    @Nested
    @DisplayName("transformSttRequest 测试")
    class TransformSttRequestTests {
        @Test
        @DisplayName("STT请求需要file，无file时返回原请求")
        void shouldReturnOriginalRequestWhenNoFile() {
            SttDTO.Request request = new SttDTO.Request(
                    "whisper-local", null, "en", null, "text", null);

            Object result = adapter.transformRequestPublic(request, "localai");
            assertSame(request, result);
        }
    }

    // ========== 响应转换测试 ==========

    @Nested
    @DisplayName("transformResponse 测试")
    class TransformResponseTests {

        @Test
        @DisplayName("Chat响应转换")
        void shouldTransformChatResponse() throws Exception {
            String localAiResponse = """
                {"id":"localai-123","model":"llama-3","choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """;

            Object result = adapter.transformResponsePublic(localAiResponse, "localai");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("chat.completion", json.get("object").asText());
            assertEquals("llama-3", json.get("model").asText());
            assertTrue(json.has("choices"));
            assertTrue(json.has("usage"));
            assertEquals("localai-adapter", json.get("system_fingerprint").asText());
        }

        @Test
        @DisplayName("Embedding响应转换")
        void shouldTransformEmbeddingResponse() throws Exception {
            String localAiResponse = """
                {"object":"list","model":"all-MiniLM-L6-v2","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}],"usage":{"prompt_tokens":5,"total_tokens":5}}
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(localAiResponse, "localai"));
            assertEquals("list", json.get("object").asText());
            assertTrue(json.has("data"));
        }

        @Test
        @DisplayName("Rerank响应转换")
        void shouldTransformRerankResponse() throws Exception {
            String localAiResponse = """
                {"model":"bge-reranker-base","results":[{"index":0,"relevance_score":0.95},{"index":1,"relevance_score":0.75}]}
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(localAiResponse, "localai"));
            assertTrue(json.has("results"));
            assertEquals(2, json.get("results").size());
        }

        @Test
        @DisplayName("无usage字段时添加默认usage")
        void shouldAddDefaultUsageWhenMissing() throws Exception {
            String localAiResponse = """
                {"id":"localai-123","model":"llama-3","choices":[{"index":0,"message":{"role":"assistant","content":"Hi"}}]}
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(localAiResponse, "localai"));
            assertTrue(json.has("usage"));
            assertEquals(0, json.get("usage").get("prompt_tokens").asInt());
        }

        @Test
        @DisplayName("非字符串响应直接返回")
        void shouldReturnNonStringResponseAsIs() {
            Object response = new Object();
            Object result = adapter.transformResponsePublic(response, "localai");
            assertSame(response, result);
        }

        @Test
        @DisplayName("无效JSON响应返回原字符串")
        void shouldReturnInvalidJsonAsIs() {
            String invalidJson = "not a valid json";
            Object result = adapter.transformResponsePublic(invalidJson, "localai");
            assertEquals(invalidJson, result);
        }
    }

    // ========== 认证头测试 ==========

    @Nested
    @DisplayName("getAuthorizationHeader 测试")
    class GetAuthorizationHeaderTests {

        @Test
        @DisplayName("已有Bearer前缀的认证头")
        void shouldReturnBearerHeaderAsIs() {
            String auth = "Bearer sk-test-key";
            String result = adapter.getAuthorizationHeaderPublic(auth, "localai");
            assertEquals("Bearer sk-test-key", result);
        }

        @Test
        @DisplayName("无Bearer前缀的认证头")
        void shouldAddBearerPrefix() {
            String auth = "sk-test-key";
            String result = adapter.getAuthorizationHeaderPublic(auth, "localai");
            assertEquals("Bearer sk-test-key", result);
        }

        @Test
        @DisplayName("空认证头返回null")
        void shouldReturnNullForNullAuth() {
            assertNull(adapter.getAuthorizationHeaderPublic(null, "localai"));
        }
    }

    // ========== 流式响应转换测试 ==========

    @Nested
    @DisplayName("transformStreamChunk 测试")
    class TransformStreamChunkTests {

        @Test
        @DisplayName("标准SSE格式的流式块")
        void shouldTransformStandardSseChunk() throws Exception {
            String chunk = "data: {\"model\":\"llama-3\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}";

            String result = adapter.transformStreamChunkPublic(chunk);
            assertNotNull(result);

            JsonNode json = objectMapper.readTree(result);
            assertEquals("chat.completion.chunk", json.get("object").asText());
            assertTrue(json.has("choices"));
            assertEquals("localai-adapter", json.get("system_fingerprint").asText());
        }

        @Test
        @DisplayName("DONE标记处理")
        void shouldHandleDoneMarker() {
            String chunk = "data: [DONE]";
            String result = adapter.transformStreamChunkPublic(chunk);
            assertEquals("[DONE]", result);
        }

        @Test
        @DisplayName("非SSE格式直接返回")
        void shouldReturnNonSseChunkAsIs() {
            String chunk = "plain text";
            String result = adapter.transformStreamChunkPublic(chunk);
            assertEquals("plain text", result);
        }

        @Test
        @DisplayName("带finish_reason的流式块")
        void shouldTransformChunkWithFinishReason() throws Exception {
            String chunk = "data: {\"model\":\"llama-3\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}";

            JsonNode json = objectMapper.readTree(adapter.transformStreamChunkPublic(chunk));
            assertEquals("stop", json.get("choices").get(0).get("finish_reason").asText());
        }

        @Test
        @DisplayName("无choices时构建标准choices")
        void shouldBuildStandardChoicesWhenMissing() throws Exception {
            String chunk = "data: {\"model\":\"llama-3\",\"content\":\"Generated text\"}";

            JsonNode json = objectMapper.readTree(adapter.transformStreamChunkPublic(chunk));
            assertTrue(json.has("choices"));
            assertEquals("Generated text", json.get("choices").get(0).get("delta").get("content").asText());
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
            Object result = adapter.transformRequestPublic(unknownRequest, "localai");
            assertSame(unknownRequest, result);
        }
    }
}

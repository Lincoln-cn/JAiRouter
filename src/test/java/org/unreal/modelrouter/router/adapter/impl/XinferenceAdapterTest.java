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
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.handler.ResponseHandler;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.request.NonStreamingRequestProcessor;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * XinferenceAdapter 单元测试
 *
 * @since v2.9.x
 */
@ExtendWith(MockitoExtension.class)
class XinferenceAdapterTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ModelCallStatsRepository statsRepository;

    @Mock
    private InstanceSelector instanceSelector;

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private CapabilityChecker capabilityChecker;

    @Mock
    private AdapterMetricsRecorder metricsRecorder;

    @Mock
    private AdapterTracingManager tracingManager;

    private ObjectMapper objectMapper;
    private TestXinferenceAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        adapter = new TestXinferenceAdapter(
                registry, objectMapper, statsRepository,
                new RequestBuilder(), new ResponseHandler(objectMapper),
                instanceSelector, responseTransformer, capabilityChecker,
                new AdapterErrorHandler(), new RetryPolicy(),
                new HttpRequestProcessor(), new ResponseMapper(objectMapper),
                metricsRecorder, tracingManager,
                new ErrorResponseBuilder(),
                new NonStreamingRequestProcessor(objectMapper, new RequestBuilder(), metricsRecorder)
        );

        lenient().when(responseTransformer.adaptModelName(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private JsonNode toJsonNode(Object result) throws Exception {
        if (result instanceof JsonNode) {
            return (JsonNode) result;
        }
        return objectMapper.readTree(result.toString());
    }

    private static class TestXinferenceAdapter extends XinferenceAdapter {
        public TestXinferenceAdapter(ModelServiceRegistry registry, ObjectMapper objectMapper,
                ModelCallStatsRepository statsRepository, RequestBuilder requestBuilder,
                ResponseHandler responseHandler, InstanceSelector instanceSelector,
                ResponseTransformer responseTransformer, CapabilityChecker capabilityChecker,
                AdapterErrorHandler errorHandler, RetryPolicy retryPolicy,
                HttpRequestProcessor httpRequestProcessor, ResponseMapper responseMapper,
                AdapterMetricsRecorder metricsRecorder, AdapterTracingManager tracingManager,
                ErrorResponseBuilder errorResponseBuilder, NonStreamingRequestProcessor nonStreamingProcessor) {
            super(registry, objectMapper, statsRepository, requestBuilder, responseHandler,
                    instanceSelector, responseTransformer, capabilityChecker, errorHandler,
                    retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder,
                    tracingManager, errorResponseBuilder, nonStreamingProcessor);
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
        @DisplayName("应返回chat, embedding, rerank能力支持，不支持TTS/STT")
        void shouldSupportChatEmbeddingRerankOnly() {
            AdapterCapabilities capabilities = adapter.supportCapability();
            assertNotNull(capabilities);
            assertTrue(capabilities.isSupportChat());
            assertTrue(capabilities.isSupportEmbedding());
            assertTrue(capabilities.isSupportRerank());
            assertFalse(capabilities.isSupportTts());
            assertFalse(capabilities.isSupportStt());
        }
    }

    @Nested
    @DisplayName("getAdapterType 测试")
    class GetAdapterTypeTests {
        @Test
        @DisplayName("应返回xinference类型")
        void shouldReturnXinferenceType() {
            assertEquals("xinference", adapter.getAdapterTypePublic());
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
                    "qwen2.5-72b", messages, false, 100, 0.7, 0.9, null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "xinference");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("qwen2.5-72b", json.get("model").asText());
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
                    "llama-3.1-70b", messages, true, 200, 0.5, 0.95, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
            assertTrue(json.get("stream").asBoolean());
        }

        @Test
        @DisplayName("带stop参数的Chat请求")
        void shouldTransformChatRequestWithStop() throws Exception {
            List<ChatDTO.Message> messages = Collections.singletonList(
                    new ChatDTO.Message("user", "Hello", null));
            ChatDTO.Request request = new ChatDTO.Request(
                    "qwen2.5-72b", messages, false, 100, 0.7, 0.9,
                    null, null, null, Arrays.asList("STOP", "END"), null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
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
                    "deepseek-v2.5", messages, true, 500, 0.8, 0.95,
                    null, 0.5, 0.3, Collections.singletonList("STOP"), "user-123", null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
            assertEquals("deepseek-v2.5", json.get("model").asText());
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
                    "qwen2.5-72b", messages, false, 100, 0.7, 0.9,
                    40, null, null, null, null, options);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
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
                    new ChatDTO.Message("user", "Hello", "Bob"));
            ChatDTO.Request request = new ChatDTO.Request(
                    "qwen2.5-72b", messages, false, 100, null, null, null, null, null, null, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
            assertEquals("Bob", json.get("messages").get(0).get("name").asText());
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
                    "bge-large-zh-v1.5", "Hello world", null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "xinference");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("bge-large-zh-v1.5", json.get("model").asText());
            assertEquals("Hello world", json.get("input").asText());
        }

        @Test
        @DisplayName("带数组输入的Embedding请求")
        void shouldTransformEmbeddingRequestWithArrayInput() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "bge-large-zh-v1.5", Arrays.asList("Hello", "World"),
                    "float", 1024, null, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
            assertTrue(json.get("input").isArray());
            assertEquals(2, json.get("input").size());
            assertEquals("float", json.get("encoding_format").asText());
            assertEquals(1024, json.get("dimensions").asInt());
        }

        @Test
        @DisplayName("带extra_body的Embedding请求")
        void shouldTransformEmbeddingRequestWithExtraBody() throws Exception {
            EmbeddingDTO.Options options = EmbeddingDTO.Options.builder()
                    .truncatePromptTokens(true)
                    .requestId("req-789")
                    .build();
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "bge-m3", "Test input", null, 1024, null, options);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
            assertTrue(json.has("extra_body"));
            assertEquals("req-789", json.get("extra_body").get("request_id").asText());
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
                    "bge-reranker-v2-m3", "search query",
                    Arrays.asList("Doc 1", "Doc 2", "Doc 3"), 3, true, null);

            Object result = adapter.transformRequestPublic(request, "xinference");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("bge-reranker-v2-m3", json.get("model").asText());
            assertEquals("search query", json.get("query").asText());
            assertEquals(3, json.get("documents").size());
            assertEquals(3, json.get("top_n").asInt());
            assertTrue(json.get("return_documents").asBoolean());
        }

        @Test
        @DisplayName("不带return_documents的Rerank请求")
        void shouldTransformRerankRequestWithoutReturnDocuments() throws Exception {
            RerankDTO.Request request = new RerankDTO.Request(
                    "bge-reranker-v2-m3", "query", Collections.singletonList("Single doc"),
                    null, false, null);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
            assertFalse(json.get("return_documents").asBoolean());
        }

        @Test
        @DisplayName("带extra_body的Rerank请求")
        void shouldTransformRerankRequestWithExtraBody() throws Exception {
            RerankDTO.Options options = RerankDTO.Options.builder()
                    .requestId("req-999")
                    .priority(2)
                    .build();
            RerankDTO.Request request = new RerankDTO.Request(
                    "bge-reranker-v2-m3", "query", Arrays.asList("Doc 1", "Doc 2"),
                    2, true, options);

            JsonNode json = toJsonNode(adapter.transformRequestPublic(request, "xinference"));
            assertTrue(json.has("extra_body"));
            assertEquals("req-999", json.get("extra_body").get("request_id").asText());
            assertEquals(2, json.get("extra_body").get("priority").asInt());
        }
    }

    // ========== TTS请求转换测试 (Xinference不支持但会返回原请求) ==========

    @Nested
    @DisplayName("transformTtsRequest 测试")
    class TransformTtsRequestTests {
        @Test
        @DisplayName("TTS请求会返回原请求")
        void shouldReturnOriginalRequestForTts() {
            TtsDTO.Request request = new TtsDTO.Request(
                    "tts-1", "Hello, this is a test", "alloy", "mp3", 1.0);

            Object result = adapter.transformRequestPublic(request, "xinference");
            // Xinference不支持TTS，transformer会返回原请求
            assertNotNull(result);
        }
    }

    // ========== STT请求转换测试 (Xinference不支持但会返回原请求) ==========

    @Nested
    @DisplayName("transformSttRequest 测试")
    class TransformSttRequestTests {
        @Test
        @DisplayName("STT请求会返回原请求")
        void shouldReturnOriginalRequestForStt() {
            SttDTO.Request request = new SttDTO.Request(
                    "whisper-1", null, "en", null, "text", null);

            Object result = adapter.transformRequestPublic(request, "xinference");
            // Xinference不支持STT，transformer会返回原请求
            assertNotNull(result);
        }
    }

    // ========== 响应转换测试 ==========

    @Nested
    @DisplayName("transformResponse 测试")
    class TransformResponseTests {

        @Test
        @DisplayName("Chat响应转换")
        void shouldTransformChatResponse() throws Exception {
            String xinferenceResponse = """
                {"id":"xinference-123","model":"qwen2.5-72b","choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """;

            Object result = adapter.transformResponsePublic(xinferenceResponse, "xinference");
            assertNotNull(result);

            JsonNode json = toJsonNode(result);
            assertEquals("chat.completion", json.get("object").asText());
            assertEquals("qwen2.5-72b", json.get("model").asText());
            assertTrue(json.has("choices"));
            assertTrue(json.has("usage"));
        }

        @Test
        @DisplayName("Embedding响应转换")
        void shouldTransformEmbeddingResponse() throws Exception {
            String xinferenceResponse = """
                {"object":"list","model":"bge-large-zh-v1.5","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}],"usage":{"prompt_tokens":5,"total_tokens":5}}
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(xinferenceResponse, "xinference"));
            assertEquals("list", json.get("object").asText());
            assertTrue(json.has("data"));
        }

        @Test
        @DisplayName("Rerank响应转换")
        void shouldTransformRerankResponse() throws Exception {
            String xinferenceResponse = """
                {"model":"bge-reranker-v2-m3","results":[{"index":0,"relevance_score":0.95},{"index":1,"relevance_score":0.75}]}
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(xinferenceResponse, "xinference"));
            assertTrue(json.has("results"));
            assertEquals(2, json.get("results").size());
        }

        @Test
        @DisplayName("无usage字段时添加默认usage")
        void shouldAddDefaultUsageWhenMissing() throws Exception {
            String xinferenceResponse = """
                {"id":"xinference-123","model":"qwen2.5-72b","choices":[{"index":0,"message":{"role":"assistant","content":"Hi"}}]}
                """;

            JsonNode json = toJsonNode(adapter.transformResponsePublic(xinferenceResponse, "xinference"));
            assertTrue(json.has("usage"));
            assertEquals(0, json.get("usage").get("prompt_tokens").asInt());
        }

        @Test
        @DisplayName("非字符串响应直接返回")
        void shouldReturnNonStringResponseAsIs() {
            Object response = new Object();
            Object result = adapter.transformResponsePublic(response, "xinference");
            assertSame(response, result);
        }

        @Test
        @DisplayName("无效JSON响应返回原字符串")
        void shouldReturnInvalidJsonAsIs() {
            String invalidJson = "not a valid json";
            Object result = adapter.transformResponsePublic(invalidJson, "xinference");
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
            String result = adapter.getAuthorizationHeaderPublic(auth, "xinference");
            assertEquals("Bearer sk-test-key", result);
        }

        @Test
        @DisplayName("无Bearer前缀的认证头")
        void shouldAddBearerPrefix() {
            String auth = "sk-test-key";
            String result = adapter.getAuthorizationHeaderPublic(auth, "xinference");
            assertEquals("Bearer sk-test-key", result);
        }

        @Test
        @DisplayName("空认证头返回null")
        void shouldReturnNullForNullAuth() {
            assertNull(adapter.getAuthorizationHeaderPublic(null, "xinference"));
        }
    }

    // ========== 流式响应转换测试 ==========

    @Nested
    @DisplayName("transformStreamChunk 测试")
    class TransformStreamChunkTests {

        @Test
        @DisplayName("标准SSE格式的流式块")
        void shouldTransformStandardSseChunk() throws Exception {
            String chunk = "data: {\"model\":\"qwen2.5-72b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}";

            String result = adapter.transformStreamChunkPublic(chunk);
            assertNotNull(result);

            JsonNode json = objectMapper.readTree(result);
            assertEquals("chat.completion.chunk", json.get("object").asText());
            assertTrue(json.has("choices"));
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
            String chunk = "data: {\"model\":\"qwen2.5-72b\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}";

            JsonNode json = objectMapper.readTree(adapter.transformStreamChunkPublic(chunk));
            assertEquals("stop", json.get("choices").get(0).get("finish_reason").asText());
        }

        @Test
        @DisplayName("无choices时构建标准choices")
        void shouldBuildStandardChoicesWhenMissing() throws Exception {
            String chunk = "data: {\"model\":\"qwen2.5-72b\",\"content\":\"Generated text\"}";

            JsonNode json = objectMapper.readTree(adapter.transformStreamChunkPublic(chunk));
            assertTrue(json.has("choices"));
            assertEquals("Generated text", json.get("choices").get(0).get("delta").get("content").asText());
        }

        @Test
        @DisplayName("带usage的流式块")
        void shouldTransformChunkWithUsage() throws Exception {
            String chunk = "data: {\"model\":\"qwen2.5-72b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"test\"}}],\"usage\":{\"prompt_tokens\":10,\"total_tokens\":15}}";

            JsonNode json = objectMapper.readTree(adapter.transformStreamChunkPublic(chunk));
            assertTrue(json.has("usage"));
            assertEquals(10, json.get("usage").get("prompt_tokens").asInt());
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
            Object result = adapter.transformRequestPublic(unknownRequest, "xinference");
            assertSame(unknownRequest, result);
        }
    }
}

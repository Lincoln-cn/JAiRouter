package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * OllamaAdapter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class OllamaAdapterTest {

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
    private TestOllamaAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(context.getObjectMapper()).thenReturn(objectMapper);
        lenient().when(requestSupport.getResponseTransformer()).thenReturn(responseTransformer);
        lenient().when(resilienceSupport.getTracingManager()).thenReturn(tracingManager);
        lenient().when(responseTransformer.adaptModelName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        adapter = new TestOllamaAdapter(context, requestSupport, resilienceSupport);
    }

    // 测试辅助类 - 暴露 protected 方法
    private static class TestOllamaAdapter extends OllamaAdapter {
        public TestOllamaAdapter(AdapterContext context,
                                 RequestProcessingSupport requestSupport,
                                 ResilienceSupport resilienceSupport) {
            super(context, requestSupport, resilienceSupport);
        }

        public String getAdapterTypePublic() {
            return getAdapterType();
        }

        public Object transformRequestPublic(Object request, String adapterType) {
            return transformRequest(request, adapterType);
        }

        public Object transformResponsePublic(Object response, String adapterType) {
            return transformResponse(response, adapterType);
        }

        public String getAuthorizationHeaderPublic(String authorization, String adapterType) {
            return getAuthorizationHeader(authorization, adapterType);
        }

        public String transformStreamChunkPublic(String chunk) {
            return transformStreamChunk(chunk);
        }
    }

    @Nested
    @DisplayName("SupportCapability Tests")
    class SupportCapabilityTests {

        @Test
        @DisplayName("should support chat and embedding only")
        void testSupportCapability() {
            AdapterCapabilities capabilities = adapter.supportCapability();

            assertTrue(capabilities.isSupportChat(), "Should support chat");
            assertTrue(capabilities.isSupportEmbedding(), "Should support embedding");
            assertFalse(capabilities.isSupportRerank(), "Should not support rerank");
            assertFalse(capabilities.isSupportTts(), "Should not support TTS");
            assertFalse(capabilities.isSupportStt(), "Should not support STT");
        }
    }

    @Nested
    @DisplayName("GetAdapterType Tests")
    class GetAdapterTypeTests {

        @Test
        @DisplayName("should return 'ollama'")
        void testGetAdapterType() {
            assertEquals("ollama", adapter.getAdapterTypePublic());
        }
    }

    @Nested
    @DisplayName("TransformChatRequest Tests")
    class TransformChatRequestTests {

        @Test
        @DisplayName("should transform basic chat request")
        void testTransformBasicChatRequest() throws Exception {
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2", Collections.emptyList(), false, 100, 0.7, 0.9,
                    null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");

            assertNotNull(result);
            JsonNode jsonNode = toJsonNode(result);
            assertEquals("llama2", jsonNode.path("model").asText());
            assertTrue(jsonNode.has("messages"));
            assertTrue(jsonNode.has("options"));
        }

        @Test
        @DisplayName("should transform chat request with temperature and maxTokens")
        void testTransformChatRequestWithOptions() throws Exception {
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2", Collections.emptyList(), false, 1000, 0.7, 0.9,
                    null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals(0.7, jsonNode.path("options").path("temperature").asDouble(), 0.001);
            assertEquals(1000, jsonNode.path("options").path("num_predict").asInt());
        }

        @Test
        @DisplayName("should include stream flag when set")
        void testTransformChatRequestWithStream() throws Exception {
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2", Collections.emptyList(), true, 100, 0.7, 0.9,
                    null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");

            JsonNode jsonNode = toJsonNode(result);
            assertTrue(jsonNode.has("stream"));
            assertTrue(jsonNode.get("stream").asBoolean());
        }
    }

    @Nested
    @DisplayName("TransformEmbeddingRequest Tests")
    class TransformEmbeddingRequestTests {

        @Test
        @DisplayName("should transform embedding request with string input")
        void testTransformEmbeddingRequestStringInput() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "nomic-embed-text", "Hello world", null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "embedding");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals("nomic-embed-text", jsonNode.path("model").asText());
            assertEquals("Hello world", jsonNode.path("prompt").asText());
        }

        @Test
        @DisplayName("should transform embedding request with list input")
        void testTransformEmbeddingRequestListInput() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "nomic-embed-text", java.util.Arrays.asList("Hello", "World"), null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "embedding");

            JsonNode jsonNode = toJsonNode(result);
            assertTrue(jsonNode.has("input"));
            assertTrue(jsonNode.get("input").isArray());
        }
    }

    @Nested
    @DisplayName("TransformRerankRequest Tests")
    class TransformRerankRequestTests {

        @Test
        @DisplayName("should transform rerank request")
        void testTransformRerankRequest() throws Exception {
            RerankDTO.Request request = new RerankDTO.Request(
                    "rerank-model", "What is AI?", java.util.Arrays.asList("AI is intelligence", "AI is artificial"), 3, null, null);

            Object result = adapter.transformRequestPublic(request, "rerank");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals("rerank-model", jsonNode.path("model").asText());
            assertEquals("What is AI?", jsonNode.path("query").asText());
            assertEquals(3, jsonNode.path("top_n").asInt());
            assertTrue(jsonNode.has("documents"));
        }
    }

    @Nested
    @DisplayName("TransformTtsRequest Tests")
    class TransformTtsRequestTests {

        @Test
        @DisplayName("should transform TTS request")
        void testTransformTtsRequest() throws Exception {
            TtsDTO.Request request = new TtsDTO.Request(
                    "tts-model", "Hello world", "alloy", "mp3", 1.0);

            Object result = adapter.transformRequestPublic(request, "tts");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals("tts-model", jsonNode.path("model").asText());
            assertEquals("Hello world", jsonNode.path("input").asText());
            assertEquals("alloy", jsonNode.path("voice").asText());
        }
    }

    @Nested
    @DisplayName("TransformSttRequest Tests")
    class TransformSttRequestTests {

        @Test
        @DisplayName("should return original request when file is null")
        void testTransformSttRequestNullFile() {
            SttDTO.Request request = new SttDTO.Request(
                    "whisper", null, "en", null, "json", 0.0);

            Object result = adapter.transformRequestPublic(request, "stt");

            // STT with null file returns original request
            assertSame(request, result);
        }
    }

    @Nested
    @DisplayName("TransformResponse Tests")
    class TransformResponseTests {

        @Test
        @DisplayName("should transform chat response with choices")
        void testTransformChatResponse() throws Exception {
            ObjectNode ollamaResponse = objectMapper.createObjectNode();
            ollamaResponse.put("id", "chat-123");
            ollamaResponse.put("model", "llama2");
            ollamaResponse.putArray("choices").addObject()
                    .putObject("message").put("role", "assistant").put("content", "Hello");

            Object result = adapter.transformResponsePublic(ollamaResponse, "chat");

            assertNotNull(result);
            JsonNode jsonNode = toJsonNode(result);
            assertEquals("chat.completion", jsonNode.path("object").asText());
        }

        @Test
        @DisplayName("should transform embedding response")
        void testTransformEmbeddingResponse() throws Exception {
            ObjectNode ollamaResponse = objectMapper.createObjectNode();
            ollamaResponse.put("model", "nomic-embed-text");
            ollamaResponse.putArray("data").addObject().put("embedding", "vector");

            Object result = adapter.transformResponsePublic(ollamaResponse, "embedding");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals("list", jsonNode.path("object").asText());
            assertTrue(jsonNode.has("data"));
        }

        @Test
        @DisplayName("should transform rerank response")
        void testTransformRerankResponse() throws Exception {
            ObjectNode ollamaResponse = objectMapper.createObjectNode();
            ollamaResponse.putArray("results").addObject()
                    .put("index", 0).put("relevance_score", 0.95);

            Object result = adapter.transformResponsePublic(ollamaResponse, "rerank");

            JsonNode jsonNode = toJsonNode(result);
            assertTrue(jsonNode.has("results"));
        }

        @Test
        @DisplayName("should return original response for unknown format")
        void testTransformResponseUnknownFormat() {
            ObjectNode unknownResponse = objectMapper.createObjectNode();
            unknownResponse.put("unknown", "data");

            Object result = adapter.transformResponsePublic(unknownResponse, "unknown");

            assertNotNull(result);
        }

        @Test
        @DisplayName("should transform string response")
        void testTransformStringResponse() {
            String jsonResponse = "{\"model\":\"llama2\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"}}]}";

            Object result = adapter.transformResponsePublic(jsonResponse, "chat");

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("GetAuthorizationHeader Tests")
    class GetAuthorizationHeaderTests {

        @Test
        @DisplayName("should return original authorization")
        void testGetAuthorizationHeader() {
            String auth = "Bearer sk-test-key";
            String result = adapter.getAuthorizationHeaderPublic(auth, "ollama");

            assertEquals(auth, result);
        }

        @Test
        @DisplayName("should return null for null authorization")
        void testGetAuthorizationHeaderNull() {
            String result = adapter.getAuthorizationHeaderPublic(null, "ollama");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("TransformStreamChunk Tests")
    class TransformStreamChunkTests {

        @Test
        @DisplayName("should transform data chunk")
        void testTransformDataChunk() {
            String chunk = "data: {\"model\":\"llama2\",\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";

            String result = adapter.transformStreamChunkPublic(chunk);

            assertTrue(result.startsWith("data: "));
            assertTrue(result.contains("chat.completion.chunk"));
        }

        @Test
        @DisplayName("should handle [DONE] marker")
        void testTransformDoneChunk() {
            String chunk = "data: [DONE]";

            String result = adapter.transformStreamChunkPublic(chunk);

            assertEquals("[DONE]", result);
        }

        @Test
        @DisplayName("should pass through non-data chunks")
        void testTransformNonDataChunk() {
            String chunk = "some random text";

            String result = adapter.transformStreamChunkPublic(chunk);

            assertEquals(chunk, result);
        }
    }

    @Nested
    @DisplayName("Unknown Request Tests")
    class UnknownRequestTests {

        @Test
        @DisplayName("should return original request for unknown type")
        void testUnknownRequestType() {
            Object unknownRequest = new Object();

            Object result = adapter.transformRequestPublic(unknownRequest, "unknown");

            assertSame(unknownRequest, result);
        }
    }

    // Helper method
    private JsonNode toJsonNode(Object result) throws Exception {
        if (result instanceof JsonNode) {
            return (JsonNode) result;
        }
        if (result instanceof java.util.Map) {
            return objectMapper.valueToTree(result);
        }
        return objectMapper.readTree(result.toString());
    }
}

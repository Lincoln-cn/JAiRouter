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
 * VllmAdapter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class VllmAdapterTest {

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
    private TestVllmAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(context.getObjectMapper()).thenReturn(objectMapper);
        lenient().when(requestSupport.getResponseTransformer()).thenReturn(responseTransformer);
        lenient().when(resilienceSupport.getTracingManager()).thenReturn(tracingManager);
        lenient().when(responseTransformer.adaptModelName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        adapter = new TestVllmAdapter(context, requestSupport, resilienceSupport);
    }

    // 测试辅助类 - 暴露 protected 方法
    private static class TestVllmAdapter extends VllmAdapter {
        public TestVllmAdapter(AdapterContext context,
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
    }

    @Nested
    @DisplayName("SupportCapability Tests")
    class SupportCapabilityTests {

        @Test
        @DisplayName("should support chat, embedding and rerank")
        void testSupportCapability() {
            AdapterCapabilities capabilities = adapter.supportCapability();

            assertTrue(capabilities.isSupportChat(), "Should support chat");
            assertTrue(capabilities.isSupportEmbedding(), "Should support embedding");
            assertTrue(capabilities.isSupportRerank(), "Should support rerank");
            assertFalse(capabilities.isSupportTts(), "Should not support TTS");
            assertFalse(capabilities.isSupportStt(), "Should not support STT");
        }
    }

    @Nested
    @DisplayName("GetAdapterType Tests")
    class GetAdapterTypeTests {

        @Test
        @DisplayName("should return 'vllm'")
        void testGetAdapterType() {
            assertEquals("vllm", adapter.getAdapterTypePublic());
        }
    }

    @Nested
    @DisplayName("TransformChatRequest Tests")
    class TransformChatRequestTests {

        @Test
        @DisplayName("should transform basic chat request")
        void testTransformBasicChatRequest() throws Exception {
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama-3", Collections.emptyList(), false, 100, 0.7, 0.9,
                    null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");

            assertNotNull(result);
            JsonNode jsonNode = toJsonNode(result);
            assertEquals("llama-3", jsonNode.path("model").asText());
            assertTrue(jsonNode.has("messages"));
            assertEquals(0.7, jsonNode.path("temperature").asDouble(), 0.001);
            assertEquals(100, jsonNode.path("max_tokens").asInt());
        }

        @Test
        @DisplayName("should transform chat request with stream")
        void testTransformChatRequestWithStream() throws Exception {
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama-3", Collections.emptyList(), true, 100, 0.7, 0.9,
                    null, null, null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");

            JsonNode jsonNode = toJsonNode(result);
            assertTrue(jsonNode.has("stream"));
            assertTrue(jsonNode.get("stream").asBoolean());
        }

        @Test
        @DisplayName("should transform chat request with top_p and frequency_penalty")
        void testTransformChatRequestWithPenalty() throws Exception {
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama-3", Collections.emptyList(), false, 100, 0.7, 0.95,
                    null, 0.5, 0.3, null, null, null);

            Object result = adapter.transformRequestPublic(request, "chat");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals(0.95, jsonNode.path("top_p").asDouble(), 0.001);
            assertEquals(0.5, jsonNode.path("frequency_penalty").asDouble(), 0.001);
            assertEquals(0.3, jsonNode.path("presence_penalty").asDouble(), 0.001);
        }
    }

    @Nested
    @DisplayName("TransformEmbeddingRequest Tests")
    class TransformEmbeddingRequestTests {

        @Test
        @DisplayName("should transform embedding request with string input")
        void testTransformEmbeddingRequestStringInput() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "text-embedding-3-small", "Hello world", null, null, null, null);

            Object result = adapter.transformRequestPublic(request, "embedding");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals("text-embedding-3-small", jsonNode.path("model").asText());
            assertEquals("Hello world", jsonNode.path("input").asText());
        }

        @Test
        @DisplayName("should transform embedding request with dimensions")
        void testTransformEmbeddingRequestWithDimensions() throws Exception {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "text-embedding-3-small", "Hello world", "float", 1536, null, null);

            Object result = adapter.transformRequestPublic(request, "embedding");

            JsonNode jsonNode = toJsonNode(result);
            assertEquals("float", jsonNode.path("encoding_format").asText());
            assertEquals(1536, jsonNode.path("dimensions").asInt());
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
            String jsonResponse = "{\"id\":\"chat-123\",\"model\":\"llama-3\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}]}";

            Object result = adapter.transformResponsePublic(jsonResponse, "chat");

            assertNotNull(result);
        }

        @Test
        @DisplayName("should return original response for unknown format")
        void testTransformResponseUnknownFormat() {
            Object unknownResponse = new Object();

            Object result = adapter.transformResponsePublic(unknownResponse, "unknown");

            assertSame(unknownResponse, result);
        }
    }

    @Nested
    @DisplayName("GetAuthorizationHeader Tests")
    class GetAuthorizationHeaderTests {

        @Test
        @DisplayName("should add Bearer prefix if not present")
        void testGetAuthorizationHeaderWithoutBearer() {
            String auth = "sk-test-key";
            String result = adapter.getAuthorizationHeaderPublic(auth, "vllm");

            assertEquals("Bearer sk-test-key", result);
        }

        @Test
        @DisplayName("should return original if already has Bearer")
        void testGetAuthorizationHeaderWithBearer() {
            String auth = "Bearer sk-test-key";
            String result = adapter.getAuthorizationHeaderPublic(auth, "vllm");

            assertEquals("Bearer sk-test-key", result);
        }

        @Test
        @DisplayName("should return null for null authorization")
        void testGetAuthorizationHeaderNull() {
            String result = adapter.getAuthorizationHeaderPublic(null, "vllm");

            assertNull(result);
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
        return objectMapper.readTree(result.toString());
    }
}

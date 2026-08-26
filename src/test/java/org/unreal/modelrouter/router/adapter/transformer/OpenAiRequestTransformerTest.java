package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAiRequestTransformer 测试类
 *
 * @since v2.7.18
 */
class OpenAiRequestTransformerTest {

    private OpenAiRequestTransformer transformer;
    private OpenAiRequestTransformer.ModelNameAdapter modelNameAdapter;

    @BeforeEach
    void setUp() {
        transformer = new OpenAiRequestTransformerImpl(new ObjectMapper());
        modelNameAdapter = model -> model; // 使用原始模型名
    }

    @Nested
    @DisplayName("Chat Request Transformation Tests")
    class ChatRequestTests {

        @Test
        @DisplayName("Should transform basic chat request")
        void transformBasicChatRequest() {
            ChatDTO.Request request = new ChatDTO.Request(
                "gpt-4",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("gpt-4"));
        }

        @Test
        @DisplayName("Should transform chat request with temperature")
        void transformChatRequestWithTemperature() {
            ChatDTO.Request request = new ChatDTO.Request(
                "gpt-4",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, 0.7, null, null, null, null, null, null, null
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("temperature"));
        }

        @Test
        @DisplayName("Should transform chat request with max tokens")
        void transformChatRequestWithMaxTokens() {
            ChatDTO.Request request = new ChatDTO.Request(
                "gpt-4",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, 100, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("max_tokens"));
        }
    }

    @Nested
    @DisplayName("Embedding Request Transformation Tests")
    class EmbeddingRequestTests {

        @Test
        @DisplayName("Should transform embedding request with string input")
        void transformEmbeddingRequestWithStringInput() {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                "text-embedding-3-small",
                "Hello world",
                null, null, null, null
            );

            Object result = transformer.transformEmbeddingRequest(request, modelNameAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("text-embedding-3-small"));
        }

        @Test
        @DisplayName("Should transform embedding request with dimensions")
        void transformEmbeddingRequestWithDimensions() {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                "text-embedding-3-small",
                "Hello world",
                "float", 1536, null, null
            );

            Object result = transformer.transformEmbeddingRequest(request, modelNameAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("dimensions"));
        }
    }

    @Nested
    @DisplayName("Rerank Request Transformation Tests")
    class RerankRequestTests {

        @Test
        @DisplayName("Should transform rerank request")
        void transformRerankRequest() {
            RerankDTO.Request request = new RerankDTO.Request(
                "rerank-1",
                "What is AI?",
                List.of("AI is artificial intelligence", "Machine learning is a subset of AI"),
                5, true, null
            );

            Object result = transformer.transformRerankRequest(request, modelNameAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("rerank-1"));
            assertTrue(result.toString().contains("query"));
        }
    }

    @Nested
    @DisplayName("TTS Request Transformation Tests")
    class TtsRequestTests {

        @Test
        @DisplayName("Should transform TTS request")
        void transformTtsRequest() {
            TtsDTO.Request request = new TtsDTO.Request(
                "tts-1",
                "Hello, this is a test.",
                "alloy",
                "mp3",
                1.0
            );

            Object result = transformer.transformTtsRequest(request, modelNameAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("tts-1"));
            assertTrue(result.toString().contains("voice"));
        }
    }

    @Nested
    @DisplayName("Model Name Adapter Tests")
    class ModelNameAdapterTests {

        @Test
        @DisplayName("Should use model name adapter")
        void shouldUseModelNameAdapter() {
            OpenAiRequestTransformer.ModelNameAdapter prefixAdapter = model -> "prefix-" + model;

            ChatDTO.Request request = new ChatDTO.Request(
                "gpt-4",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transformChatRequest(request, prefixAdapter);

            assertNotNull(result);
            assertTrue(result.toString().contains("prefix-gpt-4"));
        }
    }

    // v2.9.0: 前缀卫生回归测试 — 确保消息数组逐字透传(不被注入/重排/规范化)
    @Nested
    @DisplayName("v2.9.0 Prefix Hygiene Regression Tests")
    class PrefixHygieneTests {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("Messages array is forwarded verbatim to upstream (prefix cache safety)")
        void messagesForwardedVerbatim() throws Exception {
            // 构造包含多轮对话的请求(系统提示 + 用户消息 + 助手回复 + 用户追问)
            ChatDTO.Message systemMsg = new ChatDTO.Message("system", "You are a helpful assistant.", null);
            ChatDTO.Message userMsg1 = new ChatDTO.Message("user", "What is prefix caching?", null);
            ChatDTO.Message assistantMsg = new ChatDTO.Message("assistant",
                    "Prefix caching allows reusing KV cache for shared prompt prefixes.", null);
            ChatDTO.Message userMsg2 = new ChatDTO.Message("user", "How does it work in vLLM?", null);

            ChatDTO.Request request = new ChatDTO.Request(
                "deepseek-chat",
                List.of(systemMsg, userMsg1, assistantMsg, userMsg2),
                null, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);
            JsonNode resultNode = objectMapper.valueToTree(result);

            // 消息数量必须与输入一致
            JsonNode messages = resultNode.get("messages");
            assertNotNull(messages, "messages array must be present");
            assertEquals(4, messages.size(), "messages count must match input (4 messages)");

            // 逐条验证角色和内容逐字透传(前缀缓存依赖消息顺序和内容不变)
            assertEquals("system", messages.get(0).get("role").asText());
            assertEquals("You are a helpful assistant.", messages.get(0).get("content").asText());
            assertEquals("user", messages.get(1).get("role").asText());
            assertEquals("What is prefix caching?", messages.get(1).get("content").asText());
            assertEquals("assistant", messages.get(2).get("role").asText());
            assertEquals("Prefix caching allows reusing KV cache for shared prompt prefixes.",
                    messages.get(2).get("content").asText());
            assertEquals("user", messages.get(3).get("role").asText());
            assertEquals("How does it work in vLLM?", messages.get(3).get("content").asText());
        }

        @Test
        @DisplayName("Message order is preserved (critical for prefix cache hit)")
        void messageOrderPreserved() throws Exception {
            // 构造特定顺序的消息，确保不被重排
            ChatDTO.Request request = new ChatDTO.Request(
                "model-a",
                List.of(
                    new ChatDTO.Message("system", "PROMPT-A", null),
                    new ChatDTO.Message("user", "PROMPT-B", null),
                    new ChatDTO.Message("assistant", "RESPONSE-B", null),
                    new ChatDTO.Message("user", "PROMPT-C", null),
                    new ChatDTO.Message("assistant", "RESPONSE-C", null),
                    new ChatDTO.Message("user", "PROMPT-D", null)
                ),
                null, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);
            JsonNode resultNode = objectMapper.valueToTree(result);
            JsonNode messages = resultNode.get("messages");

            assertEquals(6, messages.size());
            // 验证消息顺序严格保持
            assertEquals("PROMPT-A", messages.get(0).get("content").asText());
            assertEquals("PROMPT-B", messages.get(1).get("content").asText());
            assertEquals("RESPONSE-B", messages.get(2).get("content").asText());
            assertEquals("PROMPT-C", messages.get(3).get("content").asText());
            assertEquals("RESPONSE-C", messages.get(4).get("content").asText());
            assertEquals("PROMPT-D", messages.get(5).get("content").asText());
        }
    }

    // v2.9.0: 缓存参数透传测试
    @Nested
    @DisplayName("v2.9.0 Cache Parameter Passthrough Tests")
    class CacheParameterTests {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("prefix_cache_hash is forwarded to extra_body")
        void prefixCacheHashForwarded() throws Exception {
            ChatDTO.Options options = ChatDTO.Options.builder()
                    .prefixCacheHash("abc123hash")
                    .build();
            ChatDTO.Request request = new ChatDTO.Request(
                "vllm-model",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null, options
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);
            JsonNode resultNode = objectMapper.valueToTree(result);

            assertTrue(resultNode.has("extra_body"), "extra_body must be present");
            assertEquals("abc123hash",
                    resultNode.get("extra_body").get("prefix_cache_hash").asText());
        }

        @Test
        @DisplayName("enable_prefix_caching is forwarded to extra_body")
        void enablePrefixCachingForwarded() throws Exception {
            ChatDTO.Options options = ChatDTO.Options.builder()
                    .enablePrefixCaching(true)
                    .build();
            ChatDTO.Request request = new ChatDTO.Request(
                "vllm-model",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null, options
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);
            JsonNode resultNode = objectMapper.valueToTree(result);

            assertTrue(resultNode.has("extra_body"));
            assertTrue(resultNode.get("extra_body").get("enable_prefix_caching").asBoolean());
        }

        @Test
        @DisplayName("cache_salt is forwarded to extra_body")
        void cacheSaltForwarded() throws Exception {
            ChatDTO.Options options = ChatDTO.Options.builder()
                    .cacheSalt("salt-value")
                    .build();
            ChatDTO.Request request = new ChatDTO.Request(
                "vllm-model",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null, options
            );

            Object result = transformer.transformChatRequest(request, modelNameAdapter);
            JsonNode resultNode = objectMapper.valueToTree(result);

            assertTrue(resultNode.has("extra_body"));
            assertEquals("salt-value",
                    resultNode.get("extra_body").get("cache_salt").asText());
        }
    }
}

package org.unreal.modelrouter.router.adapter.transformer;

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
}

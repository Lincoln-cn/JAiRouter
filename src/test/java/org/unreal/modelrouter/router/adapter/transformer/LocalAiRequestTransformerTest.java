package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalAiRequestTransformer 单元测试
 */
@DisplayName("LocalAiRequestTransformer 测试")
class LocalAiRequestTransformerTest {

    private LocalAiRequestTransformer transformer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        transformer = new LocalAiRequestTransformer(objectMapper);
    }

    @Nested
    @DisplayName("Chat请求转换测试")
    class ChatRequestTransformTest {

        @Test
        @DisplayName("测试基本Chat请求转换")
        void testBasicChatRequest() {
            ChatDTO.Message msg = new ChatDTO.Message("user", "Hello", null);
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2",
                    List.of(msg),
                    null, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transform(request);

            assertNotNull(result);
            assertTrue(result instanceof ObjectNode);
            ObjectNode node = (ObjectNode) result;
            assertEquals("llama2", node.get("model").asText());
            assertTrue(node.has("messages"));
        }

        @Test
        @DisplayName("测试带温度参数的Chat请求")
        void testChatRequestWithTemperature() {
            ChatDTO.Message msg = new ChatDTO.Message("user", "Hello", null);
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2",
                    List.of(msg),
                    null, null, 0.7, null, null, null, null, null, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertEquals(0.7, node.get("temperature").asDouble(), 0.01);
        }

        @Test
        @DisplayName("测试带maxTokens参数的Chat请求")
        void testChatRequestWithMaxTokens() {
            ChatDTO.Message msg = new ChatDTO.Message("user", "Hello", null);
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2",
                    List.of(msg),
                    null, 1000, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertEquals(1000, node.get("max_tokens").asInt());
        }

        @Test
        @DisplayName("测试带stream参数的Chat请求")
        void testChatRequestWithStream() {
            ChatDTO.Message msg = new ChatDTO.Message("user", "Hello", null);
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2",
                    List.of(msg),
                    true, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertTrue(node.get("stream").asBoolean());
        }

        @Test
        @DisplayName("测试多消息Chat请求")
        void testMultiMessageChatRequest() {
            ChatDTO.Message msg1 = new ChatDTO.Message("system", "You are helpful", null);
            ChatDTO.Message msg2 = new ChatDTO.Message("user", "Hello", null);
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2",
                    List.of(msg1, msg2),
                    null, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertTrue(node.get("messages").isArray());
            assertEquals(2, node.get("messages").size());
        }

        @Test
        @DisplayName("测试带name的消息")
        void testMessageWithName() {
            ChatDTO.Message msg = new ChatDTO.Message("user", "Hello", "Alice");
            ChatDTO.Request request = new ChatDTO.Request(
                    "llama2",
                    List.of(msg),
                    null, null, null, null, null, null, null, null, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertTrue(node.get("messages").isArray());
            assertEquals("Alice", node.get("messages").get(0).get("name").asText());
        }
    }

    @Nested
    @DisplayName("Embedding请求转换测试")
    class EmbeddingRequestTransformTest {

        @Test
        @DisplayName("测试基本Embedding请求转换")
        void testBasicEmbeddingRequest() {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "all-minilm",
                    "Hello world",
                    null, null, null, null
            );

            Object result = transformer.transform(request);

            assertNotNull(result);
            assertTrue(result instanceof ObjectNode);
            ObjectNode node = (ObjectNode) result;
            assertEquals("all-minilm", node.get("model").asText());
            assertEquals("Hello world", node.get("input").asText());
        }

        @Test
        @DisplayName("测试带dimensions参数的Embedding请求")
        void testEmbeddingRequestWithDimensions() {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "all-minilm",
                    "Hello world",
                    null, 768, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertEquals(768, node.get("dimensions").asInt());
        }

        @Test
        @DisplayName("测试数组输入的Embedding请求")
        void testEmbeddingRequestWithArrayInput() {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "all-minilm",
                    List.of("Hello", "World"),
                    null, null, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertTrue(node.get("input").isArray());
            assertEquals(2, node.get("input").size());
        }

        @Test
        @DisplayName("测试带encoding_format参数的Embedding请求")
        void testEmbeddingRequestWithEncodingFormat() {
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "all-minilm",
                    "Hello world",
                    "float", null, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertEquals("float", node.get("encoding_format").asText());
        }
    }

    @Nested
    @DisplayName("Rerank请求转换测试")
    class RerankRequestTransformTest {

        @Test
        @DisplayName("测试基本Rerank请求转换")
        void testBasicRerankRequest() {
            RerankDTO.Request request = new RerankDTO.Request(
                    "rerank-model",
                    "What is AI?",
                    List.of("AI is artificial intelligence", "Machine learning is a subset of AI"),
                    null, null, null
            );

            Object result = transformer.transform(request);

            assertNotNull(result);
            assertTrue(result instanceof ObjectNode);
            ObjectNode node = (ObjectNode) result;
            assertEquals("rerank-model", node.get("model").asText());
            assertEquals("What is AI?", node.get("query").asText());
            assertTrue(node.get("documents").isArray());
            assertEquals(2, node.get("documents").size());
        }

        @Test
        @DisplayName("测试带topN参数的Rerank请求")
        void testRerankRequestWithTopN() {
            RerankDTO.Request request = new RerankDTO.Request(
                    "rerank-model",
                    "What is AI?",
                    List.of("Doc1", "Doc2", "Doc3"),
                    2, null, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertEquals(2, node.get("top_n").asInt());
        }

        @Test
        @DisplayName("测试带returnDocuments参数的Rerank请求")
        void testRerankRequestWithReturnDocuments() {
            RerankDTO.Request request = new RerankDTO.Request(
                    "rerank-model",
                    "What is AI?",
                    List.of("Doc1", "Doc2"),
                    null, true, null
            );

            Object result = transformer.transform(request);

            ObjectNode node = (ObjectNode) result;
            assertTrue(node.get("return_documents").asBoolean());
        }
    }

    @Nested
    @DisplayName("未知请求类型测试")
    class UnknownRequestTest {

        @Test
        @DisplayName("测试未知类型请求直接返回")
        void testUnknownRequest() {
            Object unknownRequest = new Object();

            Object result = transformer.transform(unknownRequest);

            assertSame(unknownRequest, result);
        }

        @Test
        @DisplayName("测试null请求")
        void testNullRequest() {
            Object result = transformer.transform(null);

            assertNull(result);
        }
    }
}

package org.unreal.modelrouter.router.adapter.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StreamingRequestProcessor 单元测试
 */
@ExtendWith(MockitoExtension.class)
class StreamingRequestProcessorTest {

    @Mock
    private ResponseTransformer responseTransformer;

    @InjectMocks
    private StreamingRequestProcessor processor;

    @BeforeEach
    void setUp() {
        lenient().when(responseTransformer.transformStreamChunk(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("extractUsageAndContent 测试")
    class ExtractUsageAndContentTests {
        @Test
        @DisplayName("应提取 usage 信息")
        void shouldExtractUsageInfo() throws Exception {
            AtomicLong promptTokens = new AtomicLong(0);
            AtomicLong completionTokens = new AtomicLong(0);
            AtomicLong totalTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "{\"model\":\"gpt-4\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens, contentBuilder, modelRef);

            assertEquals(10, promptTokens.get());
            assertEquals(20, completionTokens.get());
            assertEquals(30, totalTokens.get());
            assertEquals("gpt-4", modelRef.get());
        }

        @Test
        @DisplayName("应处理 data: 前缀")
        void shouldHandleDataPrefix() throws Exception {
            AtomicLong promptTokens = new AtomicLong(0);
            AtomicLong completionTokens = new AtomicLong(0);
            AtomicLong totalTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "data: {\"model\":\"gpt-4\",\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":10,\"total_tokens\":15}}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens, contentBuilder, modelRef);

            assertEquals(5, promptTokens.get());
            assertEquals(10, completionTokens.get());
            assertEquals(15, totalTokens.get());
        }

        @Test
        @DisplayName("应处理 [DONE] 标记")
        void shouldHandleDoneMarker() throws Exception {
            AtomicLong promptTokens = new AtomicLong(0);
            AtomicLong completionTokens = new AtomicLong(0);
            AtomicLong totalTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "data: [DONE]";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens, contentBuilder, modelRef);

            // Should not change values
            assertEquals(0, promptTokens.get());
            assertEquals(0, completionTokens.get());
            assertEquals(0, totalTokens.get());
        }

        @Test
        @DisplayName("应累积响应内容")
        void shouldAccumulateContent() throws Exception {
            AtomicLong promptTokens = new AtomicLong(0);
            AtomicLong completionTokens = new AtomicLong(0);
            AtomicLong totalTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "{\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens, contentBuilder, modelRef);

            assertEquals("Hello", contentBuilder.toString());
        }

        @Test
        @DisplayName("无效 JSON 不应抛出异常")
        void shouldNotThrowOnInvalidJson() throws Exception {
            AtomicLong promptTokens = new AtomicLong(0);
            AtomicLong completionTokens = new AtomicLong(0);
            AtomicLong totalTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "invalid json";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens, contentBuilder, modelRef);
        }
    }

    @Nested
    @DisplayName("estimateTokens 测试")
    class EstimateTokensTests {
        @Test
        @DisplayName("null 内容应返回 0")
        void shouldReturnZeroForNull() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("estimateTokens", String.class);
            method.setAccessible(true);

            long result = (long) method.invoke(processor, (String) null);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("空内容应返回 0")
        void shouldReturnZeroForEmpty() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("estimateTokens", String.class);
            method.setAccessible(true);

            long result = (long) method.invoke(processor, "");
            assertEquals(0, result);
        }

        @Test
        @DisplayName("英文内容应正确估算")
        void shouldEstimateEnglishContent() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("estimateTokens", String.class);
            method.setAccessible(true);

            // 8 个英文字符 ≈ 2 tokens
            long result = (long) method.invoke(processor, "HelloWorld");
            assertTrue(result > 0);
        }

        @Test
        @DisplayName("中文内容应正确估算")
        void shouldEstimateChineseContent() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("estimateTokens", String.class);
            method.setAccessible(true);

            // 4 个中文字符 ≈ 2 tokens
            long result = (long) method.invoke(processor, "你好世界");
            assertTrue(result > 0);
        }
    }

    @Nested
    @DisplayName("captureApiKeyId 测试")
    class CaptureApiKeyIdTests {
        @Test
        @DisplayName("httpRequest 为 null 时应返回 null")
        void shouldReturnNullWhenHttpRequestIsNull() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("captureApiKeyId", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, (ServerHttpRequest) null);
            assertNull(result);
        }

        @Test
        @DisplayName("属性中无 keyId 时应返回 null")
        void shouldReturnNullWhenNoKeyIdAttribute() throws Exception {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            when(request.getAttributes()).thenReturn(Map.of());

            var method = StreamingRequestProcessor.class.getDeclaredMethod("captureApiKeyId", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, request);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("updateApiKeyTokenUsage 测试")
    class UpdateApiKeyTokenUsageTests {
        @Test
        @DisplayName("apiKeyId 为 null 时不应更新")
        void shouldNotUpdateWhenApiKeyIdIsNull() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("updateApiKeyTokenUsage", String.class, long.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(processor, null, 100L);
        }
    }

    @Nested
    @DisplayName("transformAndWrapChunk 测试")
    class TransformAndWrapChunkTests {
        @Test
        @DisplayName("有 transformFn 时应使用 transformFn")
        void shouldUseTransformFnWhenProvided() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("transformAndWrapChunk",
                    String.class, java.util.function.Function.class);
            method.setAccessible(true);

            java.util.function.Function<String, String> customFn = chunk -> "transformed";
            var result = (org.springframework.http.codec.ServerSentEvent<?>) method.invoke(processor, "original", customFn);

            assertEquals("transformed", result.data());
        }

        @Test
        @DisplayName("无 transformFn 时应使用默认转换器")
        void shouldUseDefaultTransformerWhenNoFn() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("transformAndWrapChunk",
                    String.class, java.util.function.Function.class);
            method.setAccessible(true);

            var result = (org.springframework.http.codec.ServerSentEvent<?>) method.invoke(processor, "original", (Object) null);

            assertEquals("original", result.data());
            verify(responseTransformer).transformStreamChunk("original");
        }
    }
}

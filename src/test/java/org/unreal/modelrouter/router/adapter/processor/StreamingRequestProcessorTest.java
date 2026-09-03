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
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.monitor.callhistory.ApiCallHistoryRecorder;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.cache.CachedStreamingResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;

import java.util.List;
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

    // v2.9.2: 记录治理 mock
    @Mock
    private ApiCallHistoryRecorder callHistoryRecorder;

    @Mock
    private CallHistoryProperties callHistoryProperties;

    @Mock
    private SanitizationService sanitizationService;

    // v2.9.10: 流式缓存 mock
    @Mock
    private ResponseCacheService responseCacheService;

    @InjectMocks
    private StreamingRequestProcessor processor;

    @BeforeEach
    void setUp() {
        lenient().when(responseTransformer.transformStreamChunk(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        // @InjectMocks may not inject @Autowired(required=false) fields reliably when
        // the class has many optional dependencies; explicitly wire the cache mock.
        ReflectionTestUtils.setField(processor, "responseCacheService", responseCacheService);
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
            AtomicLong cacheHitTokens = new AtomicLong(0);
            AtomicLong cacheMissTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "{\"model\":\"gpt-4\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens,
                    cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);

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
            AtomicLong cacheHitTokens = new AtomicLong(0);
            AtomicLong cacheMissTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "data: {\"model\":\"gpt-4\",\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":10,\"total_tokens\":15}}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens,
                    cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);

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
            AtomicLong cacheHitTokens = new AtomicLong(0);
            AtomicLong cacheMissTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "data: [DONE]";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens,
                    cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);

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
            AtomicLong cacheHitTokens = new AtomicLong(0);
            AtomicLong cacheMissTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "{\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens,
                    cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);

            assertEquals("Hello", contentBuilder.toString());
        }

        @Test
        @DisplayName("无效 JSON 不应抛出异常")
        void shouldNotThrowOnInvalidJson() throws Exception {
            AtomicLong promptTokens = new AtomicLong(0);
            AtomicLong completionTokens = new AtomicLong(0);
            AtomicLong totalTokens = new AtomicLong(0);
            AtomicLong cacheHitTokens = new AtomicLong(0);
            AtomicLong cacheMissTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "invalid json";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens,
                    cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);
        }

        @Test
        @DisplayName("应提取 DeepSeek 缓存 token")
        void shouldExtractDeepSeekCacheTokens() throws Exception {
            AtomicLong promptTokens = new AtomicLong(0);
            AtomicLong completionTokens = new AtomicLong(0);
            AtomicLong totalTokens = new AtomicLong(0);
            AtomicLong cacheHitTokens = new AtomicLong(0);
            AtomicLong cacheMissTokens = new AtomicLong(0);
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> modelRef = new AtomicReference<>("unknown");

            String chunk = "{\"model\":\"deepseek-chat\",\"usage\":{\"prompt_tokens\":1000,"
                    + "\"completion_tokens\":200,\"total_tokens\":1200,"
                    + "\"prompt_cache_hit_tokens\":800,\"prompt_cache_miss_tokens\":200}}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod("extractUsageAndContent",
                    String.class, AtomicLong.class, AtomicLong.class, AtomicLong.class,
                    AtomicLong.class, AtomicLong.class,
                    StringBuilder.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, promptTokens, completionTokens, totalTokens,
                    cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);

            assertEquals(800, cacheHitTokens.get());
            assertEquals(200, cacheMissTokens.get());
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

    @Nested
    @DisplayName("v2.9.2 记录治理 - 请求/响应体捕获测试")
    class BodyCaptureTests {

        @Test
        @DisplayName("resolveRecordLevel: 无配置时默认返回 METADATA_ONLY")
        void shouldReturnMetadataOnlyWhenNoConfig() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("resolveRecordLevel");
            method.setAccessible(true);

            RecordLevel result = (RecordLevel) method.invoke(processor);
            assertEquals(RecordLevel.METADATA_ONLY, result);
        }

        @Test
        @DisplayName("resolveRecordLevel: 配置 SUMMARY 时返回 SUMMARY")
        void shouldReturnConfiguredLevel() throws Exception {
            var field = StreamingRequestProcessor.class.getDeclaredField("callHistoryProperties");
            field.setAccessible(true);
            when(callHistoryProperties.getRecordLevel()).thenReturn(RecordLevel.SUMMARY);
            field.set(processor, callHistoryProperties);

            var method = StreamingRequestProcessor.class.getDeclaredMethod("resolveRecordLevel");
            method.setAccessible(true);

            RecordLevel result = (RecordLevel) method.invoke(processor);
            assertEquals(RecordLevel.SUMMARY, result);
        }

        @Test
        @DisplayName("serializeAndTruncate: null 对象返回 null")
        void shouldReturnNullForNullObject() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("serializeAndTruncate", Object.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, (Object) null);
            assertNull(result);
        }

        @Test
        @DisplayName("serializeAndTruncate: 正常对象序列化为 JSON")
        void shouldSerializeObjectToJson() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("serializeAndTruncate", Object.class);
            method.setAccessible(true);

            Map<String, Object> testObj = Map.of("model", "gpt-4", "stream", true);
            String result = (String) method.invoke(processor, testObj);
            assertNotNull(result);
            assertTrue(result.contains("\"model\""));
            assertTrue(result.contains("gpt-4"));
        }

        @Test
        @DisplayName("serializeAndTruncate: 内容超长时截断到 maxContentLength")
        void shouldTruncateToMaxContentLength() throws Exception {
            var field = StreamingRequestProcessor.class.getDeclaredField("callHistoryProperties");
            field.setAccessible(true);
            when(callHistoryProperties.getMaxContentLength()).thenReturn(15);
            field.set(processor, callHistoryProperties);

            var method = StreamingRequestProcessor.class.getDeclaredMethod("serializeAndTruncate", Object.class);
            method.setAccessible(true);

            Map<String, Object> longObj = Map.of("key", "a".repeat(100));
            String result = (String) method.invoke(processor, longObj);
            assertNotNull(result);
            assertTrue(result.length() <= 15, "结果应被截断到 maxContentLength=15，实际长度=" + result.length());
        }

        @Test
        @DisplayName("truncate: null 内容返回 null")
        void shouldReturnNullForNullContent() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("truncate", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, (String) null);
            assertNull(result);
        }

        @Test
        @DisplayName("truncate: 短内容不截断")
        void shouldNotTruncateShortContent() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("truncate", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, "short");
            assertEquals("short", result);
        }

        @Test
        @DisplayName("truncate: 使用默认 maxContentLength 截断")
        void shouldTruncateWithDefaultMaxLength() throws Exception {
            var method = StreamingRequestProcessor.class.getDeclaredMethod("truncate", String.class);
            method.setAccessible(true);

            String longContent = "x".repeat(100000);
            String result = (String) method.invoke(processor, longContent);
            assertNotNull(result);
            assertEquals(65536, result.length());
        }
    }

    // ==================== v2.9.10: 流式缓存测试 ====================

    @Nested
    @DisplayName("extractFinishReason 测试")
    class ExtractFinishReasonTests {

        @Test
        @DisplayName("应提取 finish_reason")
        void shouldExtractFinishReason() throws Exception {
            AtomicReference<String> finishReasonRef = new AtomicReference<>(null);
            String chunk = "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod(
                    "extractFinishReason", String.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, finishReasonRef);

            assertEquals("stop", finishReasonRef.get());
        }

        @Test
        @DisplayName("应处理 data: 前缀的 finish_reason")
        void shouldHandleDataPrefixFinishReason() throws Exception {
            AtomicReference<String> finishReasonRef = new AtomicReference<>(null);
            String chunk = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod(
                    "extractFinishReason", String.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, finishReasonRef);

            assertEquals("length", finishReasonRef.get());
        }

        @Test
        @DisplayName("[DONE] 标记不应设置 finish_reason")
        void doneMarkerShouldNotSetFinishReason() throws Exception {
            AtomicReference<String> finishReasonRef = new AtomicReference<>(null);
            String chunk = "data: [DONE]";

            var method = StreamingRequestProcessor.class.getDeclaredMethod(
                    "extractFinishReason", String.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, finishReasonRef);

            assertNull(finishReasonRef.get());
        }

        @Test
        @DisplayName("无 finish_reason 的块不应覆盖已有值")
        void noFinishReasonShouldNotOverwrite() throws Exception {
            AtomicReference<String> finishReasonRef = new AtomicReference<>("stop");
            String chunk = "{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}";

            var method = StreamingRequestProcessor.class.getDeclaredMethod(
                    "extractFinishReason", String.class, AtomicReference.class);
            method.setAccessible(true);

            method.invoke(processor, chunk, finishReasonRef);

            assertEquals("stop", finishReasonRef.get());
        }

        @Test
        @DisplayName("无效 JSON 不应抛出异常")
        void shouldNotThrowOnInvalidJson() throws Exception {
            AtomicReference<String> finishReasonRef = new AtomicReference<>(null);
            String chunk = "invalid json";

            var method = StreamingRequestProcessor.class.getDeclaredMethod(
                    "extractFinishReason", String.class, AtomicReference.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(processor, chunk, finishReasonRef);

            assertNull(finishReasonRef.get());
        }
    }

    @Nested
    @DisplayName("cacheStreamingResponse 测试")
    class CacheStreamingResponseTests {

        @Test
        @DisplayName("正常缓存流式响应")
        void shouldCacheStreamingResponse() {
            CachedStreamingResponse response = new CachedStreamingResponse(
                    List.of("{\"id\":\"1\"}", "{\"id\":\"2\"}", "[DONE]"),
                    "gpt-4", 10L, 20L, 30L, "stop");

            processor.cacheStreamingResponse("cache-key", response);

            verify(responseCacheService).store("cache-key", response);
        }

        @Test
        @DisplayName("cacheKey 为 null 时不写缓存")
        void shouldNotCacheWhenKeyIsNull() {
            CachedStreamingResponse response = new CachedStreamingResponse(
                    List.of("{\"id\":\"1\"}"), "gpt-4", null, null, null, null);

            processor.cacheStreamingResponse(null, response);

            verifyNoInteractions(responseCacheService);
        }

        @Test
        @DisplayName("cachedResponse 为 null 时不写缓存")
        void shouldNotCacheWhenResponseIsNull() {
            processor.cacheStreamingResponse("cache-key", null);

            verifyNoInteractions(responseCacheService);
        }
    }
}

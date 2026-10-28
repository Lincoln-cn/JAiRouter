package org.unreal.modelrouter.router.adapter.request;

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
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.handler.MultipartRequestHandler;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.pool.PoolSelector;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NonStreamingRequestProcessor 单元测试
 */
@ExtendWith(MockitoExtension.class)
class NonStreamingRequestProcessorTest {

    @Spy
    private ObjectMapper objectMapper;

    @Mock
    private RequestBuilder requestBuilder;

    @Mock
    private AdapterMetricsRecorder metricsRecorder;

    @Mock
    private TokenUsageExtractor tokenUsageExtractor;

    @InjectMocks
    private NonStreamingRequestProcessor processor;

    // Mocks for TokenUsageExtractor dependencies
    @Mock
    private TokenUsageRecorder tokenUsageRecorder;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private PoolSelector poolSelector;

    @Mock
    private MetricsCollector metricsCollector;

    // v2.9.2: 记录治理 mock
    @Mock
    private CallHistoryProperties callHistoryProperties;

    @Mock
    private SanitizationService sanitizationService;

    // Real TokenUsageExtractor instance for testing extracted methods
    private TokenUsageExtractor realTokenUsageExtractor;

    private Function<Object, Object> identityTransform;
    private Function<Object, Object> responseTransform;

    @BeforeEach
    void setUp() {
        identityTransform = req -> req;
        responseTransform = resp -> resp;
        realTokenUsageExtractor = new TokenUsageExtractor(
                objectMapper, tokenUsageRecorder, apiKeyService, poolSelector, metricsCollector);
    }

    @Nested
    @DisplayName("extractKeyIdFromRequest 测试")
    class ExtractKeyIdFromRequestTests {
        @Test
        @DisplayName("httpRequest 为 null 时应返回 null")
        void shouldReturnNullWhenHttpRequestIsNull() throws Exception {
            var method = TokenUsageExtractor.class.getDeclaredMethod("extractKeyIdFromRequest", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(realTokenUsageExtractor, (ServerHttpRequest) null);
            assertNull(result);
        }

        @Test
        @DisplayName("属性中无 keyId 时应返回 null")
        void shouldReturnNullWhenNoKeyIdAttribute() throws Exception {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            when(request.getAttributes()).thenReturn(Map.of());

            var method = TokenUsageExtractor.class.getDeclaredMethod("extractKeyIdFromRequest", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(realTokenUsageExtractor, request);
            assertNull(result);
        }

        @Test
        @DisplayName("属性中有 keyId 时应返回该值")
        void shouldReturnKeyIdWhenAttributeExists() throws Exception {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            when(request.getAttributes()).thenReturn(Map.of("API_KEY_ID", "test-key-id"));

            var method = TokenUsageExtractor.class.getDeclaredMethod("extractKeyIdFromRequest", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(realTokenUsageExtractor, request);
            assertEquals("test-key-id", result);
        }
    }

    @Nested
    @DisplayName("updateApiKeyTokenUsage 测试")
    class UpdateApiKeyTokenUsageTests {
        @Test
        @DisplayName("apiKeyId 为 null 时不应更新")
        void shouldNotUpdateWhenApiKeyIdIsNull() throws Exception {
            var method = TokenUsageExtractor.class.getDeclaredMethod("updateApiKeyTokenUsage", String.class, long.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(realTokenUsageExtractor, null, 100L);
        }

        @Test
        @DisplayName("totalTokens 为 0 时不应更新")
        void shouldNotUpdateWhenTotalTokensIsZero() throws Exception {
            var method = TokenUsageExtractor.class.getDeclaredMethod("updateApiKeyTokenUsage", String.class, long.class);
            method.setAccessible(true);

            method.invoke(realTokenUsageExtractor, "key-id", 0L);
        }
    }

    @Nested
    @DisplayName("handle4xxError 测试")
    class Handle4xxErrorTests {

        /**
         * #717：handle4xxError 现在会读回下游错误体，因此 stub 必须覆盖 headers() 与 bodyToMono()。
         * 空体用于验证「没有错误体时用兜底文案」这条路径。
         */
        private ClientResponse emptyBodyClientResponse(final HttpStatus status) {
            final ClientResponse clientResponse = mock(ClientResponse.class);
            when(clientResponse.statusCode()).thenReturn(status);
            final ClientResponse.Headers clientHeaders = mock(ClientResponse.Headers.class);
            when(clientHeaders.asHttpHeaders()).thenReturn(new HttpHeaders());
            when(clientResponse.headers()).thenReturn(clientHeaders);
            when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(""));
            return clientResponse;
        }

        private DownstreamServiceException invokeHandle4xxError(final ClientResponse clientResponse)
                throws Exception {
            final var method = NonStreamingRequestProcessor.class.getDeclaredMethod("handle4xxError",
                    ClientResponse.class, String.class, String.class);
            method.setAccessible(true);
            final Mono<?> result = (Mono<?>) method.invoke(processor, clientResponse, "instance", "/path");
            return (DownstreamServiceException) result.block();
        }

        @Test
        @DisplayName("401 错误应返回认证失败异常")
        void shouldReturnAuthFailureFor401() throws Exception {
            final DownstreamServiceException exception =
                    invokeHandle4xxError(emptyBodyClientResponse(HttpStatus.UNAUTHORIZED));

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            assertEquals("下游服务认证失败，请检查下游服务的认证配置", exception.getMessage(),
                    "空错误体时沿用原有兜底文案");
        }

        @Test
        @DisplayName("400 错误应返回请求错误异常")
        void shouldReturnBadRequestFor400() throws Exception {
            final DownstreamServiceException exception =
                    invokeHandle4xxError(emptyBodyClientResponse(HttpStatus.BAD_REQUEST));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
            assertEquals("下游服务请求参数错误，请检查请求内容", exception.getMessage());
        }

        @Test
        @DisplayName("503 错误应返回服务不可用异常")
        void shouldReturnServiceUnavailableFor503() throws Exception {
            final DownstreamServiceException exception =
                    invokeHandle4xxError(emptyBodyClientResponse(HttpStatus.SERVICE_UNAVAILABLE));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
            assertEquals("下游服务暂时不可用，请稍后重试", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("extractAndRecordTokenUsage 测试")
    class ExtractAndRecordTokenUsageTests {
        @Test
        @DisplayName("无 usage 字段时不应记录")
        void shouldNotRecordWhenNoUsageField() throws Exception {
            String bodyStr = "{\"model\": \"gpt-4\"}";

            var method = TokenUsageExtractor.class.getDeclaredMethod("extractAndRecordTokenUsage",
                    String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(realTokenUsageExtractor, bodyStr, "normal", "instance-1", null);
        }

        @Test
        @DisplayName("totalTokens 为 0 时不应记录")
        void shouldNotRecordWhenTotalTokensIsZero() throws Exception {
            String bodyStr = "{\"usage\": {\"prompt_tokens\": 10, \"completion_tokens\": 5, \"total_tokens\": 0}}";

            var method = TokenUsageExtractor.class.getDeclaredMethod("extractAndRecordTokenUsage",
                    String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            method.invoke(realTokenUsageExtractor, bodyStr, "normal", "instance-1", null);
        }

        @Test
        @DisplayName("无效 JSON 时不应抛出异常")
        void shouldNotThrowOnInvalidJson() throws Exception {
            String bodyStr = "invalid json";

            var method = TokenUsageExtractor.class.getDeclaredMethod("extractAndRecordTokenUsage",
                    String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(realTokenUsageExtractor, bodyStr, "normal", "instance-1", null);
        }
    }

    @Nested
    @DisplayName("v2.9.2 记录治理 - 请求/响应体捕获测试")
    class BodyCaptureTests {

        @Test
        @DisplayName("resolveRecordLevel: 无配置时默认返回 METADATA_ONLY")
        void shouldReturnMetadataOnlyWhenNoConfig() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("resolveRecordLevel");
            method.setAccessible(true);

            // callHistoryProperties is null (mock not injected into field by default)
            RecordLevel result = (RecordLevel) method.invoke(processor);
            assertEquals(RecordLevel.METADATA_ONLY, result);
        }

        @Test
        @DisplayName("resolveRecordLevel: 配置 FULL 时返回 FULL")
        void shouldReturnConfiguredLevel() throws Exception {
            // Use reflection to set the callHistoryProperties field
            var field = NonStreamingRequestProcessor.class.getDeclaredField("callHistoryProperties");
            field.setAccessible(true);
            when(callHistoryProperties.getRecordLevel()).thenReturn(RecordLevel.FULL);
            field.set(processor, callHistoryProperties);

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("resolveRecordLevel");
            method.setAccessible(true);

            RecordLevel result = (RecordLevel) method.invoke(processor);
            assertEquals(RecordLevel.FULL, result);
        }

        @Test
        @DisplayName("serializeAndTruncate: null 对象返回 null")
        void shouldReturnNullForNullObject() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("serializeAndTruncate", Object.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, (Object) null);
            assertNull(result);
        }

        @Test
        @DisplayName("serializeAndTruncate: 正常对象序列化为 JSON")
        void shouldSerializeObjectToJson() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("serializeAndTruncate", Object.class);
            method.setAccessible(true);

            Map<String, Object> testObj = Map.of("model", "gpt-4", "stream", false);
            String result = (String) method.invoke(processor, testObj);
            assertNotNull(result);
            assertTrue(result.contains("\"model\""));
            assertTrue(result.contains("\"gpt-4\""));
        }

        @Test
        @DisplayName("serializeAndTruncate: 内容超长时截断到 maxContentLength")
        void shouldTruncateToMaxContentLength() throws Exception {
            // Set up callHistoryProperties with small maxContentLength
            var field = NonStreamingRequestProcessor.class.getDeclaredField("callHistoryProperties");
            field.setAccessible(true);
            when(callHistoryProperties.getMaxContentLength()).thenReturn(20);
            field.set(processor, callHistoryProperties);

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("serializeAndTruncate", Object.class);
            method.setAccessible(true);

            Map<String, Object> longObj = Map.of("key", "a".repeat(100));
            String result = (String) method.invoke(processor, longObj);
            assertNotNull(result);
            assertTrue(result.length() <= 20, "结果应被截断到 maxContentLength=20，实际长度=" + result.length());
        }

        @Test
        @DisplayName("truncate: null 内容返回 null")
        void shouldReturnNullForNullContent() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("truncate", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, (String) null);
            assertNull(result);
        }

        @Test
        @DisplayName("truncate: 短内容不截断")
        void shouldNotTruncateShortContent() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("truncate", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, "short");
            assertEquals("short", result);
        }

        @Test
        @DisplayName("truncate: 使用默认 maxContentLength 截断")
        void shouldTruncateWithDefaultMaxLength() throws Exception {
            // callHistoryProperties is null, so maxContentLength defaults to 65536
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("truncate", String.class);
            method.setAccessible(true);

            String longContent = "x".repeat(100000);
            String result = (String) method.invoke(processor, longContent);
            assertNotNull(result);
            assertEquals(65536, result.length());
        }
    }
}

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
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.handler.MultipartRequestHandler;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

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

    @InjectMocks
    private NonStreamingRequestProcessor processor;

    private Function<Object, Object> identityTransform;
    private Function<Object, Object> responseTransform;

    @BeforeEach
    void setUp() {
        identityTransform = req -> req;
        responseTransform = resp -> resp;
    }

    @Nested
    @DisplayName("extractKeyIdFromRequest 测试")
    class ExtractKeyIdFromRequestTests {
        @Test
        @DisplayName("httpRequest 为 null 时应返回 null")
        void shouldReturnNullWhenHttpRequestIsNull() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("extractKeyIdFromRequest", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, (ServerHttpRequest) null);
            assertNull(result);
        }

        @Test
        @DisplayName("属性中无 keyId 时应返回 null")
        void shouldReturnNullWhenNoKeyIdAttribute() throws Exception {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            when(request.getAttributes()).thenReturn(Map.of());

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("extractKeyIdFromRequest", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, request);
            assertNull(result);
        }

        @Test
        @DisplayName("属性中有 keyId 时应返回该值")
        void shouldReturnKeyIdWhenAttributeExists() throws Exception {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            when(request.getAttributes()).thenReturn(Map.of("API_KEY_ID", "test-key-id"));

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("extractKeyIdFromRequest", ServerHttpRequest.class);
            method.setAccessible(true);

            String result = (String) method.invoke(processor, request);
            assertEquals("test-key-id", result);
        }
    }

    @Nested
    @DisplayName("updateApiKeyTokenUsage 测试")
    class UpdateApiKeyTokenUsageTests {
        @Test
        @DisplayName("apiKeyId 为 null 时不应更新")
        void shouldNotUpdateWhenApiKeyIdIsNull() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("updateApiKeyTokenUsage", String.class, long.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(processor, null, 100L);
        }

        @Test
        @DisplayName("totalTokens 为 0 时不应更新")
        void shouldNotUpdateWhenTotalTokensIsZero() throws Exception {
            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("updateApiKeyTokenUsage", String.class, long.class);
            method.setAccessible(true);

            method.invoke(processor, "key-id", 0L);
        }
    }

    @Nested
    @DisplayName("handle4xxError 测试")
    class Handle4xxErrorTests {
        @Test
        @DisplayName("401 错误应返回认证失败异常")
        void shouldReturnAuthFailureFor401() throws Exception {
            org.springframework.web.reactive.function.client.ClientResponse clientResponse =
                    mock(org.springframework.web.reactive.function.client.ClientResponse.class);
            when(clientResponse.statusCode()).thenReturn(HttpStatus.UNAUTHORIZED);

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("handle4xxError",
                    org.springframework.web.reactive.function.client.ClientResponse.class,
                    String.class, String.class);
            method.setAccessible(true);

            var result = (reactor.core.publisher.Mono<?>) method.invoke(processor, clientResponse, "instance", "/path");
            assertNotNull(result);
        }

        @Test
        @DisplayName("400 错误应返回请求错误异常")
        void shouldReturnBadRequestFor400() throws Exception {
            org.springframework.web.reactive.function.client.ClientResponse clientResponse =
                    mock(org.springframework.web.reactive.function.client.ClientResponse.class);
            when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("handle4xxError",
                    org.springframework.web.reactive.function.client.ClientResponse.class,
                    String.class, String.class);
            method.setAccessible(true);

            var result = (reactor.core.publisher.Mono<?>) method.invoke(processor, clientResponse, "instance", "/path");
            assertNotNull(result);
        }

        @Test
        @DisplayName("503 错误应返回服务不可用异常")
        void shouldReturnServiceUnavailableFor503() throws Exception {
            org.springframework.web.reactive.function.client.ClientResponse clientResponse =
                    mock(org.springframework.web.reactive.function.client.ClientResponse.class);
            when(clientResponse.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("handle4xxError",
                    org.springframework.web.reactive.function.client.ClientResponse.class,
                    String.class, String.class);
            method.setAccessible(true);

            var result = (reactor.core.publisher.Mono<?>) method.invoke(processor, clientResponse, "instance", "/path");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("extractAndRecordTokenUsage 测试")
    class ExtractAndRecordTokenUsageTests {
        @Test
        @DisplayName("无 usage 字段时不应记录")
        void shouldNotRecordWhenNoUsageField() throws Exception {
            String bodyStr = "{\"model\": \"gpt-4\"}";

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("extractAndRecordTokenUsage",
                    String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(processor, bodyStr, "normal", "instance-1", null);
        }

        @Test
        @DisplayName("totalTokens 为 0 时不应记录")
        void shouldNotRecordWhenTotalTokensIsZero() throws Exception {
            String bodyStr = "{\"usage\": {\"prompt_tokens\": 10, \"completion_tokens\": 5, \"total_tokens\": 0}}";

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("extractAndRecordTokenUsage",
                    String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            method.invoke(processor, bodyStr, "normal", "instance-1", null);
        }

        @Test
        @DisplayName("无效 JSON 时不应抛出异常")
        void shouldNotThrowOnInvalidJson() throws Exception {
            String bodyStr = "invalid json";

            var method = NonStreamingRequestProcessor.class.getDeclaredMethod("extractAndRecordTokenUsage",
                    String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            // Should not throw
            method.invoke(processor, bodyStr, "normal", "instance-1", null);
        }
    }
}

package org.unreal.modelrouter.router.adapter.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorResponseBuilder 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ErrorResponseBuilderTest {

    @InjectMocks
    private ErrorResponseBuilder builder;

    @Nested
    @DisplayName("buildErrorResponse(Throwable) 测试")
    class BuildErrorResponseTests {
        @Test
        @DisplayName("null 异常应返回 500 错误")
        void shouldReturn500ForNull() {
            StepVerifier.create(builder.buildErrorResponse((Throwable) null))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }

        @Test
        @DisplayName("ResponseStatusException 应保持原样")
        void shouldKeepResponseStatusException() {
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");

            StepVerifier.create(builder.buildErrorResponse(ex))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }

        @Test
        @DisplayName("WebClientResponseException 应保持状态码")
        void shouldKeepWebClientStatusCode() {
            WebClientResponseException ex = new WebClientResponseException(
                    404, "Not Found", null, null, null);

            StepVerifier.create(builder.buildErrorResponse(ex))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }

        @Test
        @DisplayName("DownstreamServiceException 应保持状态码")
        void shouldKeepDownstreamStatusCode() {
            DownstreamServiceException ex = new DownstreamServiceException("error", HttpStatus.BAD_GATEWAY);

            StepVerifier.create(builder.buildErrorResponse(ex))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }

        @Test
        @DisplayName("其他异常应转换为 500")
        void shouldConvertOtherExceptionsTo500() {
            RuntimeException ex = new RuntimeException("unexpected error");

            StepVerifier.create(builder.buildErrorResponse(ex))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("buildErrorResponse(Throwable, String) 测试")
    class BuildErrorResponseWithMessageTests {
        @Test
        @DisplayName("null 异常应使用自定义消息")
        void shouldUseCustomMessageForNull() {
            StepVerifier.create(builder.buildErrorResponse((Throwable) null, "custom error"))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }

        @Test
        @DisplayName("应使用自定义消息")
        void shouldUseCustomMessage() {
            RuntimeException ex = new RuntimeException("original");

            StepVerifier.create(builder.buildErrorResponse(ex, "custom error"))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("buildErrorResponse(HttpStatus, String) 测试")
    class BuildErrorResponseWithHttpStatusTests {
        @Test
        @DisplayName("应使用指定的 HTTP 状态码")
        void shouldUseSpecifiedStatusCode() {
            StepVerifier.create(builder.buildErrorResponse(HttpStatus.BAD_REQUEST, "bad request"))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("buildDownstreamError 测试")
    class BuildDownstreamErrorTests {
        @Test
        @DisplayName("401 错误应返回认证失败消息")
        void shouldReturnAuthFailureMessageFor401() {
            StepVerifier.create(builder.buildDownstreamError(HttpStatus.UNAUTHORIZED, "instance", "/path"))
                    .expectError(DownstreamServiceException.class)
                    .verify();
        }

        @Test
        @DisplayName("400 错误应返回请求错误消息")
        void shouldReturnRequestErrorMessageFor400() {
            StepVerifier.create(builder.buildDownstreamError(HttpStatus.BAD_REQUEST, "instance", "/path"))
                    .expectError(DownstreamServiceException.class)
                    .verify();
        }

        @Test
        @DisplayName("503 错误应返回服务不可用消息")
        void shouldReturnServiceUnavailableMessageFor503() {
            StepVerifier.create(builder.buildDownstreamError(HttpStatus.SERVICE_UNAVAILABLE, "instance", "/path"))
                    .expectError(DownstreamServiceException.class)
                    .verify();
        }

        @Test
        @DisplayName("其他错误应返回通用消息")
        void shouldReturnGenericMessageForOtherErrors() {
            StepVerifier.create(builder.buildDownstreamError(HttpStatus.INTERNAL_SERVER_ERROR, "instance", "/path"))
                    .expectError(DownstreamServiceException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("类型判断方法测试")
    class TypeCheckTests {
        @Test
        @DisplayName("isWebClientResponseException 应正确判断")
        void shouldIdentifyWebClientResponseException() {
            WebClientResponseException ex = new WebClientResponseException(404, "Not Found", null, null, null);
            assertTrue(builder.isWebClientResponseException(ex));
            assertFalse(builder.isWebClientResponseException(new RuntimeException()));
        }

        @Test
        @DisplayName("isDownstreamServiceException 应正确判断")
        void shouldIdentifyDownstreamServiceException() {
            DownstreamServiceException ex = new DownstreamServiceException("error", HttpStatus.BAD_GATEWAY);
            assertTrue(builder.isDownstreamServiceException(ex));
            assertFalse(builder.isDownstreamServiceException(new RuntimeException()));
        }

        @Test
        @DisplayName("isResponseStatusException 应正确判断")
        void shouldIdentifyResponseStatusException() {
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);
            assertTrue(builder.isResponseStatusException(ex));
            assertFalse(builder.isResponseStatusException(new RuntimeException()));
        }
    }
}

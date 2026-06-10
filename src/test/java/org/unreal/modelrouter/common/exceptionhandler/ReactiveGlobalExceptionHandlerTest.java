package org.unreal.modelrouter.common.exceptionhandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.common.exception.AuthorizationException;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.common.exception.SecurityException;
import org.unreal.modelrouter.common.exception.SecurityAuthenticationException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ReactiveGlobalExceptionHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ReactiveGlobalExceptionHandlerTest {

    @Mock
    private ErrorTracker errorTracker;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private HttpHeaders headers;

    private ReactiveGlobalExceptionHandler handler;
    private DefaultDataBufferFactory bufferFactory;

    @BeforeEach
    void setUp() {
        handler = new ReactiveGlobalExceptionHandler(errorTracker);
        bufferFactory = new DefaultDataBufferFactory();

        lenient().when(exchange.getResponse()).thenReturn(response);
        lenient().when(response.bufferFactory()).thenReturn(bufferFactory);
        lenient().when(response.getHeaders()).thenReturn(headers);
        lenient().when(response.isCommitted()).thenReturn(false);
        lenient().when(headers.containsKey(anyString())).thenReturn(false);
        lenient().when(response.writeWith(any())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("AuthenticationException 处理测试")
    class AuthenticationExceptionTests {

        @Test
        @DisplayName("处理认证异常 - 返回401")
        void testHandleAuthenticationException() {
            // Arrange
            AuthenticationException ex = AuthenticationException.invalidApiKey();

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("处理JWT过期异常")
        void testHandleJwtExpiredException() {
            // Arrange
            AuthenticationException ex = AuthenticationException.expiredJwtToken();

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("AuthorizationException 处理测试")
    class AuthorizationExceptionTests {

        @Test
        @DisplayName("处理授权异常 - 返回403")
        void testHandleAuthorizationException() {
            // Arrange
            AuthorizationException ex = AuthorizationException.accessDenied("/api/admin");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("SanitizationException 处理测试")
    class SanitizationExceptionTests {

        @Test
        @DisplayName("处理脱敏异常 - 返回500")
        void testHandleSanitizationException() {
            // Arrange
            SanitizationException ex = SanitizationException.sanitizationFailed("test error");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("SecurityException 处理测试")
    class SecurityExceptionTests {

        @Test
        @DisplayName("处理通用安全异常")
        void testHandleGenericSecurityException() {
            // Arrange
            SecurityException ex = new SecurityException("Test security error", "SEC_001", HttpStatus.BAD_REQUEST);

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("SecurityAuthenticationException 处理测试")
    class SecurityAuthenticationExceptionTests {

        @Test
        @DisplayName("处理Spring Security认证异常")
        void testHandleSecurityAuthenticationException() {
            // Arrange
            SecurityAuthenticationException ex = new SecurityAuthenticationException("AUTH_001", "Auth failed");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("常规异常处理测试")
    class GeneralExceptionTests {

        @Test
        @DisplayName("处理ServerWebInputException - 返回400")
        void testHandleServerWebInputException() {
            // Arrange
            ServerWebInputException ex = new ServerWebInputException("Invalid request body");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("处理ResponseStatusException - 返回对应状态码")
        void testHandleResponseStatusException() {
            // Arrange
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("处理IllegalArgumentException - 返回400")
        void testHandleIllegalArgumentException() {
            // Arrange
            IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("处理未知异常 - 返回500")
        void testHandleUnknownException() {
            // Arrange
            RuntimeException ex = new RuntimeException("Unknown error");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("处理空消息异常")
        void testHandleExceptionWithNullMessage() {
            // Arrange
            RuntimeException ex = new RuntimeException((String) null);

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("响应已提交场景测试")
    class CommittedResponseTests {

        @Test
        @DisplayName("响应已提交时跳过处理")
        void testCommittedResponse() {
            // Arrange
            when(response.isCommitted()).thenReturn(true);
            RuntimeException ex = new RuntimeException("Test error");

            // Act
            Mono<Void> result = handler.handle(exchange, ex);

            // Assert
            StepVerifier.create(result).verifyComplete();
            verify(response, never()).setStatusCode(any());
        }
    }

    @Nested
    @DisplayName("ErrorTracker 记录测试")
    class ErrorTrackerTests {

        @Test
        @DisplayName("非认证错误会记录到ErrorTracker")
        void testErrorTrackerRecordsNonAuthError() {
            // Arrange
            RuntimeException ex = new RuntimeException("System error");

            // Act
            handler.handle(exchange, ex).block();

            // Assert
            verify(errorTracker).trackError(any(), anyString(), any());
        }

        @Test
        @DisplayName("AuthenticationException 错误会被记录")
        void testErrorTrackerRecordsAuthException() {
            // Arrange
            AuthenticationException ex = AuthenticationException.invalidApiKey();

            // Act
            handler.handle(exchange, ex).block();

            // Assert
            verify(errorTracker).trackError(any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("响应头设置测试")
    class ResponseHeaderTests {

        @Test
        @DisplayName("设置Content-Type头")
        void testSetContentTypeHeader() {
            // Arrange
            RuntimeException ex = new RuntimeException("Test error");
            when(headers.containsKey("Content-Type")).thenReturn(false);

            // Act
            handler.handle(exchange, ex).block();

            // Assert
            verify(headers).add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        }

        @Test
        @DisplayName("已存在Content-Type时不重复设置")
        void testDoNotDuplicateContentType() {
            // Arrange
            RuntimeException ex = new RuntimeException("Test error");
            when(headers.containsKey("Content-Type")).thenReturn(true);

            // Act
            handler.handle(exchange, ex).block();

            // Assert
            verify(headers, never()).add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        }
    }
}

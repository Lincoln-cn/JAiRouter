package org.unreal.moduler.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.RequestPath;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.exception.exception.AuthorizationException;
import org.unreal.modelrouter.exception.exception.SanitizationException;
import org.unreal.modelrouter.exception.exception.SecurityException;
import org.unreal.modelrouter.exceptionhandler.ReactiveSecurityExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReactiveSecurityExceptionHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ReactiveSecurityExceptionHandlerTest {

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private RequestPath requestPath;

    @Mock
    private HttpHeaders httpHeaders;

    private ReactiveSecurityExceptionHandler exceptionHandler;
    private ObjectMapper objectMapper;
    private DataBufferFactory bufferFactory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        bufferFactory = new DefaultDataBufferFactory();
        exceptionHandler = new ReactiveSecurityExceptionHandler(objectMapper);

        // Mock exchange setup
        lenient().when(exchange.getRequest()).thenReturn(request);
        lenient().when(exchange.getResponse()).thenReturn(response);
        lenient().when(response.bufferFactory()).thenReturn(bufferFactory);
        lenient().when(response.getHeaders()).thenReturn(httpHeaders);
        lenient().when(request.getPath()).thenReturn(requestPath);
        lenient().when(requestPath.value()).thenReturn("/api/test");
        lenient().when(response.writeWith(any())).thenReturn(Mono.empty());
    }

    @Test
    void testHandleAuthenticationException() {
        // Given
        AuthenticationException exception = AuthenticationException.invalidApiKey();

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(httpHeaders).add("Content-Type", "application/json");
        verify(response).writeWith(any());
    }

    @Test
    void testHandleAuthorizationException() {
        // Given
        AuthorizationException exception = AuthorizationException.insufficientPermissions("admin");

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(httpHeaders).add("Content-Type", "application/json");
        verify(response).writeWith(any());
    }

    @Test
    void testHandleSanitizationException() {
        // Given
        SanitizationException exception = SanitizationException.sanitizationFailed("规则编译失败");

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(httpHeaders).add("Content-Type", "application/json");
        verify(response).writeWith(any());
    }

    @Test
    void testHandleGenericSecurityException() {
        // Given
        SecurityException exception = new SecurityException("通用安全异常", "GENERIC_ERROR", HttpStatus.BAD_REQUEST);

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        verify(httpHeaders).add("Content-Type", "application/json");
        verify(response).writeWith(any());
    }

    @Test
    void testHandleNonSecurityException() {
        // Given
        RuntimeException exception = new RuntimeException("非安全异常");

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        // 不应该设置响应状态码
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void testHandleExpiredJwtTokenException() {
        // Given
        AuthenticationException exception = AuthenticationException.expiredJwtToken();

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(httpHeaders).add("Content-Type", "application/json");
        verify(response).writeWith(any());
    }

    @Test
    void testHandleResourceForbiddenException() {
        // Given
        AuthorizationException exception = AuthorizationException.resourceForbidden("/api/sensitive");

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(httpHeaders).add("Content-Type", "application/json");
        verify(response).writeWith(any());
    }

    @Test
    void testHandleRuleCompilationFailedException() {
        // Given
        SanitizationException exception = SanitizationException.ruleCompilationFailed("RULE_001", new RuntimeException());

        // When
        Mono<Void> result = exceptionHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(httpHeaders).add("Content-Type", "application/json");
        verify(response).writeWith(any());
    }
}
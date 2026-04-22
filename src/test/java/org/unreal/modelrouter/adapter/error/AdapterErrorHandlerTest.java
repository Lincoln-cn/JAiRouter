package org.unreal.modelrouter.adapter.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.exception.DownstreamServiceException;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdapterErrorHandler 单元测试
 *
 * @author JAiRouter Team
 * @since v2.3.0
 */
class AdapterErrorHandlerTest {

    private AdapterErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorHandler = new AdapterErrorHandler();
    }

    @Test
    void testClassifyError_ResponseStatusException_4xx() {
        // Given
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);

        // When
        String errorCode = errorHandler.classifyError(exception);

        // Then
        assertEquals("400", errorCode);
    }

    @Test
    void testClassifyError_ResponseStatusException_5xx() {
        // Given
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        // When
        String errorCode = errorHandler.classifyError(exception);

        // Then
        assertEquals("500", errorCode);
    }

    @Test
    void testClassifyError_DownstreamServiceException() {
        // Given
        DownstreamServiceException exception = new DownstreamServiceException("Service unavailable", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        // When
        String errorCode = errorHandler.classifyError(exception);

        // Then
        assertEquals("503", errorCode);
    }

    @Test
    void testClassifyError_TimeoutException() {
        // Given
        TimeoutException exception = new TimeoutException("Request timed out");

        // When
        String errorCode = errorHandler.classifyError(exception);

        // Then
        assertEquals("504", errorCode);
    }

    @Test
    void testClassifyError_ConnectException() {
        // Given
        java.net.ConnectException exception = new java.net.ConnectException("Connection refused");

        // When
        String errorCode = errorHandler.classifyError(exception);

        // Then
        assertEquals("503", errorCode);
    }

    @Test
    void testClassifyError_SocketTimeoutException() {
        // Given
        java.net.SocketTimeoutException exception = new java.net.SocketTimeoutException("Read timed out");

        // When
        String errorCode = errorHandler.classifyError(exception);

        // Then
        assertEquals("504", errorCode);
    }

    @Test
    void testClassifyError_UnknownException() {
        // Given
        RuntimeException exception = new RuntimeException("Unknown error");

        // When
        String errorCode = errorHandler.classifyError(exception);

        // Then
        assertEquals("500", errorCode);
    }

    @Test
    void testClassifyError_Null() {
        // When
        String errorCode = errorHandler.classifyError(null);

        // Then
        assertEquals("500", errorCode);
    }

    @Test
    void testCreateErrorResponse_String() {
        // Given
        Exception exception = new Exception("Test error");
        Class<String> responseType = String.class;

        // When
        Mono<ResponseEntity<String>> result = errorHandler.createErrorResponse(exception, responseType);

        // Then
        assertNotNull(result);
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("error"));
    }

    @Test
    void testCreateErrorResponse_BadRequest() {
        // Given
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad request");
        Class<String> responseType = String.class;

        // When
        Mono<ResponseEntity<String>> result = errorHandler.createErrorResponse(exception, responseType);

        // Then
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testCreateErrorResponse_ServiceUnavailable() {
        // Given
        DownstreamServiceException exception = new DownstreamServiceException("Service unavailable", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        Class<String> responseType = String.class;

        // When
        Mono<ResponseEntity<String>> result = errorHandler.createErrorResponse(exception, responseType);

        // Then
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void testCreateErrorResponse_GatewayTimeout() {
        // Given
        TimeoutException exception = new TimeoutException("Gateway timeout");
        Class<String> responseType = String.class;

        // When
        Mono<ResponseEntity<String>> result = errorHandler.createErrorResponse(exception, responseType);

        // Then
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
    }

    @Test
    void testShouldRetry_WithinMaxRetries() {
        // Given
        Exception exception = new Exception("Retryable error");
        int retryCount = 0;
        int maxRetries = 3;

        // When
        boolean result = errorHandler.shouldRetry(exception, retryCount, maxRetries);

        // Then
        assertTrue(result);
    }

    @Test
    void testShouldRetry_ExceedMaxRetries() {
        // Given
        Exception exception = new Exception("Retryable error");
        int retryCount = 3;
        int maxRetries = 3;

        // When
        boolean result = errorHandler.shouldRetry(exception, retryCount, maxRetries);

        // Then
        assertFalse(result);
    }

    @Test
    void testShouldRetry_HttpClientErrorException() {
        // Given
        org.springframework.web.client.HttpClientErrorException exception =
            new org.springframework.web.client.HttpClientErrorException(HttpStatus.BAD_REQUEST);

        // When
        boolean result = errorHandler.shouldRetry(exception, 0, 3);

        // Then
        assertFalse(result);
    }

    @Test
    void testShouldRetry_TimeoutException() {
        // Given
        TimeoutException exception = new TimeoutException("Timeout");

        // When
        boolean result = errorHandler.shouldRetry(exception, 0, 3);

        // Then
        assertTrue(result);
    }

    @Test
    void testShouldRetry_ConnectException() {
        // Given
        java.net.ConnectException exception = new java.net.ConnectException("Connection refused");

        // When
        boolean result = errorHandler.shouldRetry(exception, 0, 3);

        // Then
        assertTrue(result);
    }

    @Test
    void testShouldRetry_DownstreamServiceException() {
        // Given
        DownstreamServiceException exception = new DownstreamServiceException("Service unavailable", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        // When
        boolean result = errorHandler.shouldRetry(exception, 0, 3);

        // Then
        assertTrue(result);
    }

    @Test
    void testShouldRetry_ServerError() {
        // Given
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        // When
        boolean result = errorHandler.shouldRetry(exception, 0, 3);

        // Then
        assertTrue(result);
    }

    @Test
    void testExecuteFallback_WithFallbackResponse() {
        // Given
        String fallbackResponse = "Fallback response";
        Exception error = new Exception("Original error");

        // When
        Mono<ResponseEntity<String>> result = errorHandler.executeFallback(fallbackResponse, error);

        // Then
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Fallback response", response.getBody());
    }

    @Test
    void testExecuteFallback_NullFallbackResponse() {
        // Given
        String fallbackResponse = null;
        Exception error = new Exception("Original error");

        // When & Then - null 降级响应会尝试获取 error 的 class，导致 NPE
        // 这是预期的行为，因为 executeFallback 设计用于有降级响应的场景
        assertThrows(NullPointerException.class, () -> {
            errorHandler.executeFallback(fallbackResponse, error).block();
        });
    }
}

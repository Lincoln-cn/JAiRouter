package org.unreal.modelrouter.adapter.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RequestProcessor 单元测试
 *
 * @author JAiRouter Team
 * @since v2.3.1.1
 */
class HttpRequestProcessorTest {

    private HttpRequestProcessor requestProcessor;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private ClientResponse clientResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        requestProcessor = new HttpRequestProcessor();
    }

    @Test
    void testConfigureHeaders_Null() {
        // Given
        Map<String, String> headers = null;

        // When
        WebClient.RequestBodySpec result = requestProcessor.configureHeaders(requestBodySpec, headers);

        // Then
        assertNotNull(result);
        verify(requestBodySpec, never()).header(anyString(), anyString());
    }

    @Test
    void testConfigureHeaders_Empty() {
        // Given
        Map<String, String> headers = new HashMap<>();

        // When
        WebClient.RequestBodySpec result = requestProcessor.configureHeaders(requestBodySpec, headers);

        // Then
        assertNotNull(result);
        verify(requestBodySpec, never()).header(anyString(), anyString());
    }

    @Test
    void testConfigureHeaders_WithHeaders() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom-Header", "value1");
        headers.put("X-Another-Header", "value2");
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);

        // When
        WebClient.RequestBodySpec result = requestProcessor.configureHeaders(requestBodySpec, headers);

        // Then
        assertNotNull(result);
        verify(requestBodySpec).header("X-Custom-Header", "value1");
        verify(requestBodySpec).header("X-Another-Header", "value2");
    }

    @Test
    void testIsSuccess_2xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.OK);

        // When
        boolean result = requestProcessor.isSuccess(clientResponse);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsSuccess_Non2xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        boolean result = requestProcessor.isSuccess(clientResponse);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsServerError_5xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

        // When
        boolean result = requestProcessor.isServerError(clientResponse);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsServerError_Non5xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        boolean result = requestProcessor.isServerError(clientResponse);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsClientError_4xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        boolean result = requestProcessor.isClientError(clientResponse);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsClientError_Non4xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.OK);

        // When
        boolean result = requestProcessor.isClientError(clientResponse);

        // Then
        assertFalse(result);
    }

    @Test
    void testHandleResponseError_401() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.UNAUTHORIZED);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Unauthorized"));

        // When
        Mono<Throwable> result = requestProcessor.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof org.springframework.web.server.ResponseStatusException);
        assertEquals(HttpStatus.UNAUTHORIZED, ((org.springframework.web.server.ResponseStatusException) error).getStatusCode());
    }

    @Test
    void testHandleResponseError_400() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Bad request"));

        // When
        Mono<Throwable> result = requestProcessor.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof org.springframework.web.server.ResponseStatusException);
        assertEquals(HttpStatus.BAD_REQUEST, ((org.springframework.web.server.ResponseStatusException) error).getStatusCode());
    }

    @Test
    void testHandleResponseError_503() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Service unavailable"));

        // When
        Mono<Throwable> result = requestProcessor.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof org.springframework.web.server.ResponseStatusException);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ((org.springframework.web.server.ResponseStatusException) error).getStatusCode());
    }

    @Test
    void testHandleResponseError_500() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Internal server error"));

        // When
        Mono<Throwable> result = requestProcessor.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof org.springframework.web.server.ResponseStatusException);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ((org.springframework.web.server.ResponseStatusException) error).getStatusCode());
    }

    @Test
    void testHandleResponseError_EmptyBody() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.empty());

        // When
        Mono<Throwable> result = requestProcessor.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof org.springframework.web.server.ResponseStatusException);
    }
}

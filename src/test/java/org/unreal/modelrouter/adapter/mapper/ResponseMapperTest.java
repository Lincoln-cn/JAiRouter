package org.unreal.modelrouter.adapter.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ResponseMapper 单元测试
 *
 * @author JAiRouter Team
 * @since v2.3.1.2
 */
class ResponseMapperTest {

    private ResponseMapper responseMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ClientResponse clientResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        responseMapper = new ResponseMapper(new ObjectMapper());
    }

    @Test
    void testMapResponse_Success() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.OK);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Success"));

        // When
        Mono<ResponseEntity<String>> result = responseMapper.mapResponse(clientResponse, String.class);

        // Then
        assertNotNull(result);
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Success", response.getBody());
    }

    @Test
    void testMapResponse_5xxError() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

        // When
        Mono<ResponseEntity<String>> result = responseMapper.mapResponse(clientResponse, String.class);

        // Then
        assertNotNull(result);
        assertThrows(ResponseStatusException.class, () -> result.block());
    }

    @Test
    void testMapResponse_4xxError() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        Mono<ResponseEntity<String>> result = responseMapper.mapResponse(clientResponse, String.class);

        // Then
        assertNotNull(result);
        assertThrows(ResponseStatusException.class, () -> result.block());
    }

    @Test
    void testMapStreamChunk() {
        // Given
        String content = "{\"message\": \"test\"}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(content.getBytes(StandardCharsets.UTF_8));

        // When
        String result = responseMapper.mapStreamChunk(dataBuffer);

        // Then
        assertNotNull(result);
        assertEquals(content, result);
    }

    @Test
    void testMapStreamChunk_Empty() {
        // Given
        DataBuffer dataBuffer = new DefaultDataBufferFactory().allocateBuffer(0);

        // When
        String result = responseMapper.mapStreamChunk(dataBuffer);

        // Then
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    void testHandleResponseError_401() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.UNAUTHORIZED);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Unauthorized"));

        // When
        Mono<Throwable> result = responseMapper.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof ResponseStatusException);
        assertEquals(HttpStatus.UNAUTHORIZED, ((ResponseStatusException) error).getStatusCode());
    }

    @Test
    void testHandleResponseError_400() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Bad request"));

        // When
        Mono<Throwable> result = responseMapper.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof ResponseStatusException);
        assertEquals(HttpStatus.BAD_REQUEST, ((ResponseStatusException) error).getStatusCode());
    }

    @Test
    void testHandleResponseError_503() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just("Service unavailable"));

        // When
        Mono<Throwable> result = responseMapper.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof ResponseStatusException);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ((ResponseStatusException) error).getStatusCode());
    }

    @Test
    void testHandleResponseError_EmptyBody() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.empty());

        // When
        Mono<Throwable> result = responseMapper.handleResponseError(clientResponse, "test-instance", "/api/test");

        // Then
        assertNotNull(result);
        Throwable error = result.block();
        assertNotNull(error);
        assertTrue(error instanceof ResponseStatusException);
    }

    @Test
    void testFromJson_Success() {
        // Given
        String json = "{\"name\":\"test\"}";
        ObjectMapper mapper = new ObjectMapper();
        ResponseMapper mapperUnderTest = new ResponseMapper(mapper);

        // When
        TestDto result = mapperUnderTest.fromJson(json, TestDto.class);

        // Then
        assertNotNull(result);
        assertEquals("test", result.getName());
    }

    @Test
    void testFromJson_InvalidJson() {
        // Given
        String json = "invalid json";
        ObjectMapper mapper = new ObjectMapper();
        ResponseMapper mapperUnderTest = new ResponseMapper(mapper);

        // When
        TestDto result = mapperUnderTest.fromJson(json, TestDto.class);

        // Then
        assertNull(result);
    }

    @Test
    void testToJson_Success() {
        // Given
        TestDto dto = new TestDto("test");
        ObjectMapper mapper = new ObjectMapper();
        ResponseMapper mapperUnderTest = new ResponseMapper(mapper);

        // When
        String result = mapperUnderTest.toJson(dto);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("test"));
    }

    @Test
    void testIsSuccess_2xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.OK);

        // When
        boolean result = responseMapper.isSuccess(clientResponse);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsSuccess_Non2xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        boolean result = responseMapper.isSuccess(clientResponse);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsServerError_5xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

        // When
        boolean result = responseMapper.isServerError(clientResponse);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsServerError_Non5xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        boolean result = responseMapper.isServerError(clientResponse);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsClientError_4xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        boolean result = responseMapper.isClientError(clientResponse);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsClientError_Non4xx() {
        // Given
        when(clientResponse.statusCode()).thenReturn(HttpStatus.OK);

        // When
        boolean result = responseMapper.isClientError(clientResponse);

        // Then
        assertFalse(result);
    }

    // 测试用 DTO
    static class TestDto {
        private String name;

        public TestDto() {}

        public TestDto(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}

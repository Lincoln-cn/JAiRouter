package org.unreal.modelrouter.router.adapter.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResponseHandler 单元测试
 * 
 * @author AI Assistant
 * @since v2.2.3
 */
@ExtendWith(MockitoExtension.class)
class ResponseHandlerTest {

    private ResponseHandler responseHandler;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        responseHandler = new ResponseHandler(objectMapper);
    }

    @Test
    void testHandleResponse_Success() {
        // 准备测试数据
        ResponseEntity<String> successResponse = ResponseEntity
                .ok("{\"status\": \"success\", \"data\": {\"result\": \"test\"}}");

        // 执行测试
        Mono<ResponseEntity<?>> result = responseHandler.handleResponse(successResponse, "test-adapter");

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                })
                .verifyComplete();
    }

    @Test
    void testHandleResponse_EmptyBody() {
        // 准备测试数据
        ResponseEntity<String> emptyResponse = ResponseEntity.ok("");

        // 执行测试
        Mono<ResponseEntity<?>> result = responseHandler.handleResponse(emptyResponse, "test-adapter");

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void testHandleResponse_NullBody() {
        // 准备测试数据
        ResponseEntity<String> nullResponse = ResponseEntity.ok(null);

        // 执行测试
        Mono<ResponseEntity<?>> result = responseHandler.handleResponse(nullResponse, "test-adapter");

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void testHandleResponse_Error() {
        // 准备测试数据
        ResponseEntity<String> errorResponse = ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"test error\"}");

        // 执行测试
        Mono<ResponseEntity<?>> result = responseHandler.handleResponse(errorResponse, "test-adapter");

        // 验证结果
        StepVerifier.create(result)
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void testHandleResponse_InvalidJson() {
        // 准备测试数据
        ResponseEntity<String> invalidJsonResponse = ResponseEntity
                .ok("invalid json content");

        // 执行测试
        Mono<ResponseEntity<?>> result = responseHandler.handleResponse(invalidJsonResponse, "test-adapter");

        // 验证结果
        StepVerifier.create(result)
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void testIsSuccess_WithSuccessResponse() {
        // 准备测试数据
        ResponseEntity<String> successResponse = ResponseEntity.ok("success");

        // 执行测试
        boolean result = responseHandler.isSuccess(successResponse);

        // 验证结果
        assertTrue(result);
    }

    @Test
    void testIsSuccess_WithErrorResponse() {
        // 准备测试数据
        ResponseEntity<String> errorResponse = ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("error");

        // 执行测试
        boolean result = responseHandler.isSuccess(errorResponse);

        // 验证结果
        assertFalse(result);
    }

    @Test
    void testIsSuccess_WithNullResponse() {
        // 执行测试
        boolean result = responseHandler.isSuccess(null);

        // 验证结果
        assertFalse(result);
    }

    @Test
    void testExtractErrorMessage_FromJsonError() {
        // 准备测试数据
        String errorJson = "{\"error\": {\"message\": \"Test error message\", \"type\": \"invalid_request\"}}";
        ResponseEntity<String> errorResponse = ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorJson);

        // 执行测试
        String result = responseHandler.extractErrorMessage(errorResponse);

        // 验证结果
        assertEquals("Test error message", result);
    }

    @Test
    void testExtractErrorMessage_FromSimpleError() {
        // 准备测试数据
        String simpleError = "Simple error message";
        ResponseEntity<String> errorResponse = ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(simpleError);

        // 执行测试
        String result = responseHandler.extractErrorMessage(errorResponse);

        // 验证结果
        assertEquals("Simple error message", result);
    }

    @Test
    void testExtractErrorMessage_FromNullResponse() {
        // 执行测试
        String result = responseHandler.extractErrorMessage(null);

        // 验证结果
        assertEquals("未知错误", result);
    }

    @Test
    void testExtractErrorMessage_FromEmptyBody() {
        // 准备测试数据
        ResponseEntity<String> emptyResponse = ResponseEntity.ok("");

        // 执行测试
        String result = responseHandler.extractErrorMessage(emptyResponse);

        // 验证结果
        assertEquals("", result);
    }

    @Test
    void testCreateEmptyResponse() {
        // 执行测试
        ResponseEntity<Void> result = responseHandler.createEmptyResponse(
                HttpStatus.OK,
                MediaType.APPLICATION_JSON
        );

        // 验证结果
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
        assertNull(result.getBody());
    }

    @Test
    void testTransformResponse_Default() {
        // 准备测试数据
        Map<String, Object> testData = Map.of("key", "value");

        // 执行测试
        Object result = responseHandler.transformResponse(testData, "test-adapter");

        // 验证结果 - 默认不转换，返回原数据
        assertEquals(testData, result);
    }

    @Test
    void testLogResponse() {
        // 准备测试数据
        ResponseEntity<String> response = ResponseEntity.ok("{\"data\": \"test\"}");

        // 执行测试（主要验证不抛异常）
        assertDoesNotThrow(() -> 
            responseHandler.logResponse(response, "test-instance", "/test/path")
        );
    }
}

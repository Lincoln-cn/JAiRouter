package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DebugMultipartController RESTful 接口测试
 *
 * 测试范围：
 * - POST /v1/debug/multipart-info - 获取multipart请求信息
 * - POST /v1/debug/raw-multipart - 解析原始multipart请求
 * - POST /v1/debug/simple-multipart - 简单multipart处理
 * - POST /v1/debug/raw-body - 读取原始请求体
 * - POST /v1/debug/flexible-multipart - 灵活multipart处理
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("DebugMultipartController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DebugMultipartControllerTest {

    @InjectMocks
    private DebugMultipartController controller;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        // 配置默认请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setContentLength(1024L);
        headers.set("X-API-Key", "test-api-key");
        headers.set("Jairouter_token", "test-token");
        headers.set("Authorization", "Bearer auth-token");

        lenient().when(httpRequest.getHeaders()).thenReturn(headers);
        lenient().when(httpRequest.getMethod()).thenReturn(org.springframework.http.HttpMethod.POST);
        lenient().when(httpRequest.getURI()).thenReturn(java.net.URI.create("http://localhost/v1/debug/multipart-info"));
        
        // Mock getPath() to avoid NullPointerException
        org.springframework.http.server.RequestPath mockPath = mock(org.springframework.http.server.RequestPath.class);
        lenient().when(mockPath.value()).thenReturn("/v1/debug/multipart-info");
        lenient().when(httpRequest.getPath()).thenReturn(mockPath);

        lenient().when(exchange.getRequest()).thenReturn(httpRequest);
    }

    // ==================== Multipart Info 测试 ====================

    @Nested
    @DisplayName("POST /v1/debug/multipart-info - 获取multipart信息测试")
    class MultipartInfoTests {

        @Test
        @DisplayName("DEBUG-001: 成功获取multipart请求信息")
        void testMultipartInfo_success() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.debugMultipartInfo(exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertNotNull(response.getBody());
                        assertEquals("test-api-key", response.getBody().get("xApiKey"));
                        assertEquals("test-token", response.getBody().get("jairouterToken"));
                        assertEquals("Bearer auth-token", response.getBody().get("authorization"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DEBUG-002: 验证Content-Type信息")
        void testMultipartInfo_contentType() {
            // Given
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary");
            when(httpRequest.getHeaders()).thenReturn(headers);

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.debugMultipartInfo(exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        Map<String, Object> body = response.getBody();
                        assertNotNull(body);
                        assertTrue((Boolean) body.get("hasBoundary"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== Raw Multipart 测试 ====================

    @Nested
    @DisplayName("POST /v1/debug/raw-multipart - 原始multipart解析测试")
    class RawMultipartTests {

        @Test
        @DisplayName("DEBUG-003: 非multipart请求返回400")
        void testRawMultipart_notMultipart() {
            // Given
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            when(httpRequest.getHeaders()).thenReturn(headers);

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleRawMultipart(exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                        assertEquals("error", response.getBody().get("status"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== Simple Multipart 测试 ====================

    @Nested
    @DisplayName("POST /v1/debug/simple-multipart - 简单multipart处理测试")
    class SimpleMultipartTests {

        @Test
        @DisplayName("DEBUG-004: 无文本和文件返回成功")
        void testSimpleMultipart_empty() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleSimpleMultipart(null, null, exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertEquals("success", response.getBody().get("status"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DEBUG-005: 带文本部分")
        void testSimpleMultipart_withText() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleSimpleMultipart("test text", null, exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertEquals("test text", response.getBody().get("text"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DEBUG-006: 带文件部分")
        void testSimpleMultipart_withFile() {
            // Given
            FilePart mockFilePart = mock(FilePart.class);
            when(mockFilePart.filename()).thenReturn("test.txt");
            HttpHeaders fileHeaders = new HttpHeaders();
            when(mockFilePart.headers()).thenReturn(fileHeaders);

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleSimpleMultipart(null, mockFilePart, exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertEquals("test.txt", response.getBody().get("fileName"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== Raw Body 测试 ====================

    @Nested
    @DisplayName("POST /v1/debug/raw-body - 原始请求体读取测试")
    class RawBodyTests {

        @Test
        @DisplayName("DEBUG-007: 空请求体返回成功")
        void testRawBody_empty() {
            // Given
            when(exchange.getRequest()).thenReturn(httpRequest);
            when(httpRequest.getBody()).thenReturn(Flux.empty());

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleRawBody(exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertEquals("success", response.getBody().get("status"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DEBUG-008: 有请求体返回预览")
        void testRawBody_withBody() {
            // Given
            DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
            DataBuffer buffer = factory.wrap("test body content".getBytes(StandardCharsets.UTF_8));
            when(httpRequest.getBody()).thenReturn(Flux.just(buffer));

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleRawBody(exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertEquals("success", response.getBody().get("status"));
                        assertNotNull(response.getBody().get("bodyPreview"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== Flexible Multipart 测试 ====================

    @Nested
    @DisplayName("POST /v1/debug/flexible-multipart - 灵活multipart处理测试")
    class FlexibleMultipartTests {

        @Test
        @DisplayName("DEBUG-009: 空parts返回成功")
        void testFlexibleMultipart_empty() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleFlexibleMultipart(Flux.empty());

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertEquals("success", response.getBody().get("status"));
                        assertEquals(0, response.getBody().get("partCount"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DEBUG-010: 带FormFieldPart返回成功")
        void testFlexibleMultipart_withFormFieldPart() {
            // Given
            org.springframework.http.codec.multipart.Part mockPart = mock(FormFieldPart.class);
            when(mockPart.name()).thenReturn("field1");
            when(((FormFieldPart) mockPart).value()).thenReturn("test value");

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.handleFlexibleMultipart(Flux.just(mockPart));

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertEquals("success", response.getBody().get("status"));
                        assertEquals(1, response.getBody().get("partCount"));
                    })
                    .verifyComplete();
        }
    }
}

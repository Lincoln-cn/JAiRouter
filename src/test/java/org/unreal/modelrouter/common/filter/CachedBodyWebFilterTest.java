package org.unreal.modelrouter.common.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CachedBodyWebFilter 单元测试
 *
 * <p>测试请求体缓存过滤器功能</p>
 *
 * @version v2.10.0
 * @since 2026-05-24
 */
@DisplayName("CachedBodyWebFilter 请求体缓存过滤器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CachedBodyWebFilterTest {

    private CachedBodyWebFilter filter;

    @Mock
    private WebFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CachedBodyWebFilter();
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    // ==================== 过滤器优先级测试 ====================

    @Nested
    @DisplayName("过滤器优先级测试")
    class OrderTests {

        @Test
        @DisplayName("FILTER-001: 过滤器具有最高优先级")
        void testGetOrder_HighestPrecedence() {
            assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
        }
    }

    // ==================== 无Body请求测试 ====================

    @Nested
    @DisplayName("无Body请求测试")
    class NoBodyRequestTests {

        @Test
        @DisplayName("FILTER-002: GET请求跳过缓存")
        void testFilter_GetRequest_SkipsCache() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("FILTER-003: DELETE请求跳过缓存")
        void testFilter_DeleteRequest_SkipsCache() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .delete("/api/test/1")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("FILTER-004: 无Content-Length的请求跳过缓存")
        void testFilter_NoContentLength_SkipsCache() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(exchange);
        }
    }

    // ==================== 标准请求体缓存测试 ====================

    @Nested
    @DisplayName("标准请求体缓存测试")
    class StandardRequestBodyTests {

        @Test
        @DisplayName("FILTER-005: POST请求带JSON体成功缓存")
        void testFilter_PostJson_CachesBody() {
            // Given
            String body = "{\"name\":\"test\"}";
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length())
                .body(body);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("FILTER-006: PUT请求带JSON体成功缓存")
        void testFilter_PutJson_CachesBody() {
            // Given
            String body = "{\"id\":1,\"name\":\"updated\"}";
            MockServerHttpRequest request = MockServerHttpRequest
                .put("/api/test/1")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length())
                .body(body);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("FILTER-007: PATCH请求带XML体成功缓存")
        void testFilter_PatchXml_CachesBody() {
            // Given
            String body = "<data><name>test</name></data>";
            MockServerHttpRequest request = MockServerHttpRequest
                .patch("/api/test/1")
                .contentType(MediaType.APPLICATION_XML)
                .contentLength(body.length())
                .body(body);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("FILTER-008: 纯文本请求体成功缓存")
        void testFilter_PlainText_CachesBody() {
            // Given
            String body = "plain text content";
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(body.length())
                .body(body);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(any(ServerWebExchange.class));
        }
    }

    // ==================== Multipart请求测试 ====================

    @Nested
    @DisplayName("Multipart请求测试")
    class MultipartRequestTests {

        @Test
        @DisplayName("FILTER-009: Multipart请求跳过缓存")
        void testFilter_Multipart_SkipsCache() {
            // Given
            String boundary = "----WebKitFormBoundary";
            String body = "------WebKitFormBoundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n" +
                "file content\r\n" +
                "------WebKitFormBoundary--\r\n";
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
                .body(body);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(any(ServerWebExchange.class));
        }
    }

    // ==================== Transfer-Encoding测试 ====================

    @Nested
    @DisplayName("Transfer-Encoding测试")
    class TransferEncodingTests {

        @Test
        @DisplayName("FILTER-010: chunked编码请求成功缓存")
        void testFilter_ChunkedEncoding_CachesBody() {
            // Given
            String body = "{\"chunked\":\"data\"}";
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(any(ServerWebExchange.class));
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("FILTER-011: 空请求体成功处理")
        void testFilter_EmptyBody_HandledGracefully() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(0)
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("FILTER-012: 小请求体缓存后重新包装，下游可再次读取")
        void testFilter_SmallBody_IsRewrappedForDownstream() {
            // issue #783：本例原名「大请求体跳过缓存」，但体只有 15 字节、也没有任何
            // 「缓存/跳过」断言 —— 名字与覆盖不符。真实超限行为由
            // CachedBodyWebFilterPayloadLimitTest（10MB+1 → 413、chain 不被调用）守着；
            // 本例验证它真正覆盖的那条契约：过滤器把体读干缓存后必须**重新包装**请求交给下游，
            // 下游才能再次读到完整字节。
            // 真实容器的请求体只能消费一次，这里用「第二次订阅即为空」的 publisher 复刻该语义：
            // 若过滤器直接把原始 exchange 交下去，下游读到空体 ⇒ 本例转红。
            String body = "{\"test\":\"data\"}";
            byte[] original = body.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = new DefaultDataBufferFactory().wrap(original);
            AtomicInteger subscriptions = new AtomicInteger();
            Flux<DataBuffer> oneShotBody = Flux.defer(() -> subscriptions.incrementAndGet() == 1
                ? Flux.just(buffer)
                : Flux.empty());
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(original.length)
                .body(oneShotBody);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
            when(filterChain.filter(any())).thenAnswer(invocation -> {
                downstream.set(invocation.getArgument(0));
                return Mono.empty();
            });

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then - 下游拿到的是重新包装过的请求，且读到的字节与原始完全一致
            StepVerifier.create(result).verifyComplete();
            assertNotNull(downstream.get(), "下游必须被调用");
            assertNotSame(exchange.getRequest(), downstream.get().getRequest(),
                "交给下游的必须是重新包装过的请求，否则一次性 body 已被过滤器读干");
            assertNotSame(exchange.getRequest(), downstream.get().getRequest(),
                "交给下游的必须是重新包装过的请求，否则一次性 body 已被过滤器读干");
            byte[] seen = DataBufferUtils.join(downstream.get().getRequest().getBody())
                .map(b -> {
                    byte[] bytes = new byte[b.readableByteCount()];
                    b.read(bytes);
                    DataBufferUtils.release(b);
                    return bytes;
                })
                .block();
            assertNotNull(seen, "下游必须能读到请求体");
            assertEquals(body, new String(seen, StandardCharsets.UTF_8));
        }
    }
}

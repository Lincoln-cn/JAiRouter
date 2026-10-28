package org.unreal.modelrouter.router.adapter.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #717：非流式路径读回下游错误体。
 *
 * <p>直接验证 {@code NonStreamingRequestProcessor#toDownstreamError} 这一层：状态码保留、
 * 上游 {@code error.message} 出现在消息里、{@code error.code} 与 {@code Retry-After} 被带上；
 * 读体失败或空体时退回调用方给的兜底文案，且不吞掉状态码。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("非流式路径的下游错误体透出（#717）")
class NonStreamingDownstreamErrorTest {

    private static final String FALLBACK = "下游服务暂时不可用，请稍后重试";

    @Test
    @DisplayName("400 + 标准错误体：消息里带 context_length_exceeded，错误码取上游 code")
    void clientErrorBodyIsPropagated() {
        final ClientResponse response = jsonResponse(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"context_length_exceeded\",\"type\":\"invalid_request_error\","
                        + "\"code\":\"context_length_exceeded\"}}");

        final DownstreamServiceException exception =
                NonStreamingRequestProcessor.toDownstreamError(response, FALLBACK).block();

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode(), "下游状态码必须保留");
        assertTrue(exception.getMessage().contains("context_length_exceeded"),
                "客户端必须能看到上游语义，实际: " + exception.getMessage());
        assertEquals("context_length_exceeded", exception.getUpstreamCode());
        assertNull(exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("429 + Retry-After：退避秒数原样带上")
    void retryAfterIsPropagated() {
        final ClientResponse response = jsonResponse(HttpStatus.TOO_MANY_REQUESTS,
                "{\"error\":{\"message\":\"rate limit reached\",\"code\":\"rate_limit_exceeded\"}}",
                HttpHeaders.RETRY_AFTER, "12");

        final DownstreamServiceException exception =
                NonStreamingRequestProcessor.toDownstreamError(response, FALLBACK).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertEquals("rate_limit_exceeded", exception.getUpstreamCode());
        assertEquals(12L, exception.getRetryAfterSeconds());
        assertTrue(exception.getMessage().contains("rate limit reached"));
    }

    @Test
    @DisplayName("500 + 纯文本错误体：原文进消息，错误码退回状态码")
    void serverErrorPlainText() {
        final ClientResponse response = ClientResponse.create(HttpStatus.BAD_GATEWAY)
                .header("Content-Type", "text/plain")
                .body("upstream boom")
                .build();

        final DownstreamServiceException exception =
                NonStreamingRequestProcessor.toDownstreamError(response, FALLBACK).block();

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("upstream boom"));
        assertEquals("502", exception.getUpstreamCode());
    }

    @Test
    @DisplayName("空错误体：用兜底文案，状态码仍保留")
    void emptyBodyFallsBack() {
        final ClientResponse response = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build();

        final DownstreamServiceException exception =
                NonStreamingRequestProcessor.toDownstreamError(response, FALLBACK).block();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertEquals(FALLBACK, exception.getMessage());
        assertEquals("500", exception.getUpstreamCode());
    }

    // ==================== 辅助 ====================

    private ClientResponse jsonResponse(final HttpStatus status, final String body, final String... headers) {
        final ClientResponse.Builder builder = ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(body);
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return builder.build();
    }
}

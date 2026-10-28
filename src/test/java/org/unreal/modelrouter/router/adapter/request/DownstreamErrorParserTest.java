package org.unreal.modelrouter.router.adapter.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #717：下游错误体解析单元测试。
 *
 * <p>只钉「提取」这一层：JSON 的 {@code error.message}/{@code error.code} 优先，取不到时退回原文，
 * 原文超长截断；{@code Retry-After} 只认秒数形态。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("下游错误体解析（#717）")
class DownstreamErrorParserTest {

    @Test
    @DisplayName("OpenAI 形状：error.message 优先，error.code 作为错误码")
    void openAiShape() {
        final String body = "{\"error\":{\"message\":\"context_length_exceeded\","
                + "\"type\":\"invalid_request_error\",\"code\":\"context_length_exceeded\"}}";

        assertEquals("context_length_exceeded",
                DownstreamErrorParser.extractMessage(body, HttpStatus.BAD_REQUEST));
        assertEquals("context_length_exceeded",
                DownstreamErrorParser.extractCode(body, HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("error 是字符串时直接用该字符串")
    void errorAsText() {
        final String body = "{\"error\":\"model not found\"}";

        assertEquals("model not found", DownstreamErrorParser.extractMessage(body, HttpStatus.NOT_FOUND));
        assertEquals("404", DownstreamErrorParser.extractCode(body, HttpStatus.NOT_FOUND),
                "无 code 字段时错误码退回状态码");
    }

    @Test
    @DisplayName("顶层 message 也可用（另一类上游形状）")
    void topLevelMessage() {
        final String body = "{\"message\":\"quota exceeded\",\"code\":\"insufficient_quota\"}";

        assertEquals("quota exceeded", DownstreamErrorParser.extractMessage(body, HttpStatus.TOO_MANY_REQUESTS));
        assertEquals("insufficient_quota",
                DownstreamErrorParser.extractCode(body, HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("非 JSON 错误体：保留原文（便于排障），错误码退回状态码")
    void plainTextBody() {
        assertEquals("upstream boom",
                DownstreamErrorParser.extractMessage("  upstream boom  ", HttpStatus.BAD_GATEWAY));
        assertEquals("502", DownstreamErrorParser.extractCode("upstream boom", HttpStatus.BAD_GATEWAY));
    }

    @Test
    @DisplayName("空体 / null：不给消息（由调用方用兜底文案），错误码仍是状态码")
    void emptyBody() {
        assertNull(DownstreamErrorParser.extractMessage(null, HttpStatus.INTERNAL_SERVER_ERROR));
        assertNull(DownstreamErrorParser.extractMessage("   ", HttpStatus.INTERNAL_SERVER_ERROR));
        assertEquals("500", DownstreamErrorParser.extractCode(null, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("超长错误体截断，避免把上游整段 HTML 塞给客户端")
    void longBodyIsTruncated() {
        final String body = "x".repeat(DownstreamErrorParser.MAX_MESSAGE_LENGTH + 100);

        final String message = DownstreamErrorParser.extractMessage(body, HttpStatus.BAD_GATEWAY);

        assertTrue(message.endsWith("…"), "截断后应有省略号");
        assertTrue(message.length() <= DownstreamErrorParser.MAX_MESSAGE_LENGTH + 1);
    }

    @Test
    @DisplayName("Retry-After：秒数可用，HTTP-date 与非数字不吃")
    void retryAfter() {
        final HttpHeaders numeric = new HttpHeaders();
        numeric.set(HttpHeaders.RETRY_AFTER, "12");
        assertEquals(12L, DownstreamErrorParser.retryAfterSeconds(numeric));

        final HttpHeaders dateForm = new HttpHeaders();
        dateForm.set(HttpHeaders.RETRY_AFTER, "Wed, 21 Oct 2026 07:28:00 GMT");
        assertNull(DownstreamErrorParser.retryAfterSeconds(dateForm), "HTTP-date 形态不解析");

        assertNull(DownstreamErrorParser.retryAfterSeconds(new HttpHeaders()), "缺头时为 null");
        assertNull(DownstreamErrorParser.retryAfterSeconds(null), "null 头安全");
    }
}

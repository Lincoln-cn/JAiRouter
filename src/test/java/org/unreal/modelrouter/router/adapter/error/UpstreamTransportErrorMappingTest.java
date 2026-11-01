package org.unreal.modelrouter.router.adapter.error;

import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上游传输层错误的 504 / 502 映射（#924）.
 *
 * <p>超时与连不上此前都落进兜底分支：客户端拿到 {@code 500} 和一段 Netty 原文
 * （实测 {@code Connection prematurely closed BEFORE response}）。网关因此无法区分
 * 「上游超时」（504）、「上游连不上」（502）与「网关自身出错」（500），
 * OpenAI SDK 之类的重试中间件也会把 500 当网关 bug。本类钉住三类映射与它们各自的文案。</p>
 *
 * @since v3.5.9
 */
class UpstreamTransportErrorMappingTest {

    private static final URI UPSTREAM = URI.create("http://127.0.0.1:11471/v1/chat/completions");

    private final ErrorResponseBuilder builder = new ErrorResponseBuilder();

    private static WebClientRequestException requestException(final Throwable cause) {
        return new WebClientRequestException(cause, HttpMethod.POST, UPSTREAM, new HttpHeaders());
    }

    @Test
    @DisplayName("923-101 传输层读超时映射为 504，文案不暴露 Netty 细节")
    void mapsReadTimeoutTo504() {
        final IllegalStateException wrapped = new IllegalStateException(
                "Connection prematurely closed BEFORE response", requestException(ReadTimeoutException.INSTANCE));

        StepVerifier.create(builder.buildErrorResponse(wrapped))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    final ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(status.getReason()).isEqualTo("下游服务响应超时");
                    assertThat(status.getReason()).doesNotContain("prematurely");
                })
                .verify();
    }

    @Test
    @DisplayName("923-102 整体超时（java.util.concurrent.TimeoutException）同样是 504")
    void mapsOverallTimeoutTo504() {
        StepVerifier.create(builder.buildErrorResponse(requestException(new TimeoutException("request timed out"))))
                .expectErrorSatisfies(error -> {
                    final ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(status.getReason()).isEqualTo("下游服务响应超时");
                })
                .verify();
    }

    @Test
    @DisplayName("923-103 连不上上游映射为 502")
    void mapsConnectFailureTo502() {
        StepVerifier.create(builder.buildErrorResponse(
                        requestException(new ConnectException("Connection refused"))))
                .expectErrorSatisfies(error -> {
                    final ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(status.getReason()).isEqualTo("下游服务连接失败");
                })
                .verify();
    }

    @Test
    @DisplayName("923-104 自定义消息重载同样保持 504（不因换文案退回 500）")
    void keeps504ForCustomMessageOverload() {
        StepVerifier.create(builder.buildErrorResponse(
                        requestException(ReadTimeoutException.INSTANCE), "上游响应超时"))
                .expectErrorSatisfies(error -> {
                    final ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(status.getReason()).isEqualTo("上游响应超时");
                })
                .verify();
    }

    @Test
    @DisplayName("923-105 普通内部异常仍映射为 500（防止把一切都当 504/502）")
    void ordinaryExceptionStill500() {
        StepVerifier.create(builder.buildErrorResponse(new IllegalStateException("boom")))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .verify();
    }
}

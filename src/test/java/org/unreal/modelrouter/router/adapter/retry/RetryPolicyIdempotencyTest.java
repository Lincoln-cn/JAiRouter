package org.unreal.modelrouter.router.adapter.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按服务类型判定重试（#827）。
 *
 * <p>判据：chat / embedding / rerank 重复下发无副作用；TTS / STT / 生图 / 图像编辑
 * 会被上游真的生成一次并计费，所以只有「请求根本没发出去」（连接被拒 / 域名解析失败）
 * 才允许重试。读超时不算 —— 请求已经发出去了。</p>
 */
class RetryPolicyIdempotencyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy();

    @Test
    @DisplayName("RP-827-001: chat 遇到下游 502 仍可重试")
    void chatRetriesServerError() {
        assertTrue(retryPolicy.isRetryableForService(
                new DownstreamServiceException("上游 502", HttpStatus.BAD_GATEWAY),
                ModelServiceRegistry.ServiceType.chat));
    }

    @Test
    @DisplayName("RP-827-002: 生图遇到下游 502 不重试（可能已生成并计费）")
    void imageGenerationDoesNotRetryServerError() {
        assertFalse(retryPolicy.isRetryableForService(
                new DownstreamServiceException("上游 502", HttpStatus.BAD_GATEWAY),
                ModelServiceRegistry.ServiceType.imgGen));
    }

    @Test
    @DisplayName("RP-827-003: 生图遇到连接被拒可以重试（请求没发出去）")
    void imageGenerationRetriesConnectionRefused() {
        assertTrue(retryPolicy.isRetryableForService(
                new ConnectException("Connection refused"),
                ModelServiceRegistry.ServiceType.imgGen));
    }

    @Test
    @DisplayName("RP-827-004: TTS 遇到被包装的建连失败仍可重试（要沿 cause 链找）")
    void ttsRetriesWrappedConnectionFailure() {
        final RuntimeException wrapped =
                new RuntimeException("request failed", new ConnectException("Connection refused"));
        assertTrue(retryPolicy.isRetryableForService(wrapped, ModelServiceRegistry.ServiceType.tts));
    }

    @Test
    @DisplayName("RP-827-005: STT 读超时不重试（请求已发出，上游可能在生成）")
    void speechToTextDoesNotRetryTimeout() {
        assertFalse(retryPolicy.isRetryableForService(
                new SocketTimeoutException("Read timed out"),
                ModelServiceRegistry.ServiceType.stt));
        assertFalse(retryPolicy.isRetryableForService(
                new TimeoutException("timeout"),
                ModelServiceRegistry.ServiceType.stt));
    }

    @Test
    @DisplayName("RP-827-006: 图像编辑遇到 4xx 不重试（调用方问题）")
    void imageEditDoesNotRetryClientError() {
        assertFalse(retryPolicy.isRetryableForService(
                new DownstreamServiceException("参数错误", HttpStatus.BAD_REQUEST),
                ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("RP-827-007: embedding 遇到 503 仍可重试")
    void embeddingRetriesUnavailable() {
        assertTrue(retryPolicy.isRetryableForService(
                new DownstreamServiceException("上游 503", HttpStatus.SERVICE_UNAVAILABLE),
                ModelServiceRegistry.ServiceType.embedding));
    }

    @Test
    @DisplayName("RP-827-008: 域名解析失败算「没发出去」，媒体类可重试")
    void unknownHostCountsAsNotSent() {
        assertTrue(retryPolicy.isRetryableForService(
                new UnknownHostException("unknown"),
                ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("RP-827-009: 幂等判定只覆盖四类媒体服务")
    void idempotencyClassification() {
        assertTrue(RetryPolicy.isNonIdempotent(ModelServiceRegistry.ServiceType.tts));
        assertTrue(RetryPolicy.isNonIdempotent(ModelServiceRegistry.ServiceType.stt));
        assertTrue(RetryPolicy.isNonIdempotent(ModelServiceRegistry.ServiceType.imgGen));
        assertTrue(RetryPolicy.isNonIdempotent(ModelServiceRegistry.ServiceType.imgEdit));
        assertFalse(RetryPolicy.isNonIdempotent(ModelServiceRegistry.ServiceType.chat));
        assertFalse(RetryPolicy.isNonIdempotent(ModelServiceRegistry.ServiceType.embedding));
        assertFalse(RetryPolicy.isNonIdempotent(ModelServiceRegistry.ServiceType.rerank));
    }
}

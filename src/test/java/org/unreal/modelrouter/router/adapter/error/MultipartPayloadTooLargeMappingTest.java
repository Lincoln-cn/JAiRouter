package org.unreal.modelrouter.router.adapter.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.exception.MultipartPayloadTooLargeException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传超限的 413 映射（#862）.
 *
 * <p>超限异常在「写请求体」阶段抛出，WebClient 会包一层（{@code WebClientRequestException}）。
 * 若不沿 cause 链识别，客户端会拿到 500 与一段内部异常文本；本类钉住「413 + 可行动文案」。</p>
 *
 * @since v3.5.8
 */
class MultipartPayloadTooLargeMappingTest {

    private final ErrorResponseBuilder builder = new ErrorResponseBuilder();

    @Test
    @DisplayName("862-101 被包装的超限异常应映射为 413，且文案是面向客户端的那句")
    void mapsWrappedPayloadTooLargeTo413() {
        final MultipartPayloadTooLargeException tooLarge = new MultipartPayloadTooLargeException(
                "big.bin", 26_215_424L, 26_214_400L,
                "上传文件过大：big.bin（26215424 字节）超过上限 26214400 字节（25 MiB）");
        final RuntimeException wrapped = new RuntimeException("Request body write failed", tooLarge);

        StepVerifier.create(builder.buildErrorResponse(wrapped))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    final ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(status.getReason())
                            .isEqualTo("上传文件过大：big.bin（26215424 字节）超过上限 26214400 字节（25 MiB）");
                })
                .verify();
    }

    @Test
    @DisplayName("862-102 自定义消息重载同样保持 413（不因换成通用文案而退回 500）")
    void keeps413ForCustomMessageOverload() {
        final MultipartPayloadTooLargeException tooLarge = new MultipartPayloadTooLargeException(
                "big.bin", 2048L, 1024L, "上传文件过大：big.bin（2048 字节）超过上限 1024 字节（1 KiB）");

        StepVerifier.create(builder.buildErrorResponse(new RuntimeException(tooLarge), "上传文件太大"))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE))
                .verify();
    }

    @Test
    @DisplayName("862-103 普通异常仍映射为 500（防止识别逻辑把一切都当 413）")
    void ordinaryExceptionStill500() {
        StepVerifier.create(builder.buildErrorResponse(new IllegalStateException("boom")))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .verify();
    }
}

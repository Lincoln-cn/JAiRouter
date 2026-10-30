package org.unreal.modelrouter.router.adapter.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.common.exception.MultipartPayloadTooLargeException;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传体积上限的守卫（#862）.
 *
 * <p>背景：上传的音频/图像此前被无上限 {@code collectList()} 读进堆内存，并发大文件可打满堆。
 * 本类钉住三条语义：限额内正常读取、超限回 413（带上限与实际大小）、空部件不报错。</p>
 *
 * @since v3.5.8
 */
class MultipartRequestHandlerLimitTest {

    private static final DefaultDataBufferFactory FACTORY = new DefaultDataBufferFactory();

    private static MultipartRequestHandler handlerWithLimit(final long limit) {
        final MultipartRequestHandler handler = new MultipartRequestHandler();
        ReflectionTestUtils.setField(handler, "maxFileSizeBytes", limit);
        return handler;
    }

    private static FilePart filePart(final String filename, final int sizeInBytes) {
        final byte[] payload = new byte[sizeInBytes];
        Arrays.fill(payload, (byte) 'a');
        return new StubFilePart(filename, payload);
    }

    @Test
    @DisplayName("862-001 限额内的文件可以被完整读出")
    void readsFileWithinLimit() {
        final MultipartRequestHandler handler = handlerWithLimit(1024);
        final byte[] bytes = handler.readBounded(filePart("clip.wav", 1024)).block();

        assertThat(bytes).isNotNull();
        assertThat(bytes).hasSize(1024);
    }

    @Test
    @DisplayName("862-002 超过上限时回 413，且文案给出文件名、实际大小与上限")
    void rejectsOversizedFileWithPayloadTooLarge() {
        final MultipartRequestHandler handler = handlerWithLimit(1024);
        final Mono<byte[]> mono = handler.readBounded(filePart("huge.wav", 2048));

        StepVerifier.create(mono)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MultipartPayloadTooLargeException.class);
                    final MultipartPayloadTooLargeException status = (MultipartPayloadTooLargeException) error;
                    assertThat(status.getMessage())
                            .contains("huge.wav")
                            .contains("超过上限")
                            .contains("1024");
                })
                .verify();
    }

    @Test
    @DisplayName("862-003 图像编辑链路同样受上限约束（超限时在组部件阶段就 413）")
    void imageEditPartsRejectOversizedImage() {
        final MultipartRequestHandler handler = handlerWithLimit(1024);
        final ImageEditDTO.Request request = new ImageEditDTO.Request(
                List.of(filePart("cat.png", 4096)), "a cat", null, null, null,
                "stable-diffusion-2-1", null, null, null, null, null, null, null, null, null);

        StepVerifier.create(handler.buildImageEditParts(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MultipartPayloadTooLargeException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("862-004 空文件不报错（返回空数组，避免把「没传内容」误判成超限）")
    void emptyFileIsAccepted() {
        final MultipartRequestHandler handler = handlerWithLimit(1024);

        StepVerifier.create(handler.readBounded(filePart("empty.wav", 0)))
                .assertNext(bytes -> assertThat(bytes).isEmpty())
                .verifyComplete();
    }

    /** 最小化的 FilePart 实现：内容按块下发，便于构造超限场景. */
    private static final class StubFilePart implements FilePart {

        private final String filename;

        private final byte[] payload;

        private StubFilePart(final String filename, final byte[] payload) {
            this.filename = filename;
            this.payload = payload;
        }

        @Override
        public String name() {
            return "file";
        }

        @Override
        public HttpHeaders headers() {
            final HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "form-data; name=\"file\"; filename=\"" + filename + "\"");
            return headers;
        }

        @Override
        public Flux<DataBuffer> content() {
            if (payload.length == 0) {
                return Flux.just(FACTORY.wrap(new byte[0]));
            }
            // 切成 512 字节一块，模拟真实的分块到达
            final int chunkSize = 512;
            final java.util.List<DataBuffer> chunks = new java.util.ArrayList<>();
            for (int offset = 0; offset < payload.length; offset += chunkSize) {
                final int end = Math.min(offset + chunkSize, payload.length);
                chunks.add(FACTORY.wrap(Arrays.copyOfRange(payload, offset, end)));
            }
            return Flux.fromIterable(chunks);
        }

        @Override
        public Mono<Void> transferTo(final Path dest) {
            return Mono.fromRunnable(() -> {
                throw new UnsupportedOperationException("测试桩不落盘");
            });
        }

        @Override
        public Mono<Void> transferTo(final File dest) {
            return transferTo(dest.toPath());
        }

        @Override
        public String filename() {
            return filename;
        }
    }
}

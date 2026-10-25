package org.unreal.modelrouter.router.adapter.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.MultiValueMap;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MultipartRequestHandler} 图像编辑部件组装测试.
 *
 * <p>覆盖 #637：入站 {@link FilePart} 必须被读成内存资源后再作为 multipart 部件发送，
 * 否则 WebClient 出站无法编码文件部件。
 *
 * @author JAiRouter Team
 * @since v3.5.2
 */
@DisplayName("MultipartRequestHandler 图像编辑部件组装")
class MultipartRequestHandlerTest {

    private final MultipartRequestHandler handler = new MultipartRequestHandler();

    private static FilePart stubFilePart(final String filename, final String content) {
        final FilePart part = mock(FilePart.class);
        when(part.name()).thenReturn("image[]");
        when(part.filename()).thenReturn(filename);
        when(part.headers()).thenReturn(new HttpHeaders());
        when(part.content()).thenAnswer(invocation -> Flux.just(
                new DefaultDataBufferFactory().wrap(content.getBytes(StandardCharsets.UTF_8))));
        return part;
    }

    @Test
    @DisplayName("MP-001: 图像与掩码被读成带文件名的内存资源，文本字段原样透传")
    void buildImageEditParts_convertsFilePartsAndFormFields() {
        final FilePart first = stubFilePart("first.png", "first-bytes");
        final FilePart second = stubFilePart("second.png", "second-bytes");
        final FilePart mask = stubFilePart("mask.png", "mask-bytes");
        final ImageEditDTO.Request request = new ImageEditDTO.Request(
                List.of(first, second), "add a hat", null, null, mask, "gpt-image-1",
                2, null, "png", null, "high", null, "1024x1024", Boolean.FALSE, "alice");

        StepVerifier.create(handler.buildImageEditParts(request))
                .assertNext(parts -> {
                    assertResources(parts, "image[]", 2);
                    final Object firstImage = parts.getFirst("image[]");
                    assertNotNull(firstImage);
                    final ByteArrayResource firstResource = (ByteArrayResource) firstImage;
                    assertEquals("first.png", firstResource.getFilename());
                    assertArrayEquals("first-bytes".getBytes(StandardCharsets.UTF_8),
                            firstResource.getByteArray());

                    assertResources(parts, "mask", 1);
                    assertEquals("mask.png",
                            ((ByteArrayResource) parts.getFirst("mask")).getFilename());
                    assertEquals("gpt-image-1", parts.getFirst("model"));
                    assertEquals("add a hat", parts.getFirst("prompt"));
                    assertEquals("2", parts.getFirst("n"));
                    assertEquals("png", parts.getFirst("output_format"));
                    assertEquals("high", parts.getFirst("quality"));
                    assertEquals("1024x1024", parts.getFirst("size"));
                    assertEquals("false", parts.getFirst("stream"));
                    assertEquals("alice", parts.getFirst("user"));
                    assertFalse(parts.containsKey("background"));
                    assertFalse(parts.containsKey("input_fidelity"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("MP-002: 无掩码与空文本字段时不写入多余部件")
    void buildImageEditParts_skipsNullFields() {
        final FilePart image = stubFilePart("only.png", "only-bytes");
        final ImageEditDTO.Request request = new ImageEditDTO.Request(
                List.of(image), "blur", null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        StepVerifier.create(handler.buildImageEditParts(request))
                .assertNext(parts -> {
                    assertResources(parts, "image[]", 1);
                    assertFalse(parts.containsKey("mask"));
                    assertEquals("blur", parts.getFirst("prompt"));
                    assertFalse(parts.containsKey("model"));
                    assertFalse(parts.containsKey("n"));
                })
                .verifyComplete();
    }

    private static void assertResources(final MultiValueMap<String, Object> parts,
            final String key, final int expectedSize) {
        assertNotNull(parts.get(key), "缺少部件: " + key);
        assertEquals(expectedSize, parts.get(key).size(), "部件数量不符: " + key);
    }
}

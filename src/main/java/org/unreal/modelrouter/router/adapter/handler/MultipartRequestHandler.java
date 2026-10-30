package org.unreal.modelrouter.router.adapter.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.exception.MultipartPayloadTooLargeException;
import org.unreal.modelrouter.common.dto.SttDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multipart请求处理器
 * 负责处理 multipart/form-data 格式的请求（STT语音转文本、图像编辑等）
 *
 * @since v2.15.1
 * @since v2.26.0 注册为Spring Service，确保始终可用
 */
@Service
public class MultipartRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(MultipartRequestHandler.class);

    /**
     * 单个上传文件的体积上限（字节），默认 25 MiB。
     *
     * <p>超过上限时不再把内容读进堆内存，而是直接回 413（#862）：此前的实现会对
     * {@code FilePart} 无上限 {@code collectList()}，并发大文件上传可以把堆打满。</p>
     */
    @Value("${jairouter.multipart.max-file-size-bytes:26214400}")
    private long maxFileSizeBytes = 26_214_400L;

    /**
     * 创建请求体
     * 根据请求类型选择合适的请求体格式
     *
     * @param request 请求对象
     * @return BodyInserter 用于插入请求体
     */
    public BodyInserter<?, ? super ClientHttpRequest> createRequestBody(final Object request) {
        logger.debug("创建请求体，请求类型: {}", request.getClass().getSimpleName());

        // 检查是否已经是转换后的multipart数据
        if (request instanceof MultiValueMap) {
            logger.debug("检测到已转换的multipart数据，直接使用");
            return BodyInserters.fromMultipartData((MultiValueMap<String, ?>) request);
        } else if (request instanceof SttDTO.Request) {
            logger.debug("检测到STT请求，使用multipart处理");
            return createSttMultipartBody((SttDTO.Request) request);
        } else if (request instanceof ImageEditDTO.Request) {
            logger.debug("检测到图像编辑请求，使用multipart处理");
            return createImageEditMultipartBody((ImageEditDTO.Request) request);
        } else {
            // 普通JSON请求
            logger.debug("使用JSON请求体处理");
            return BodyInserters.fromValue(request);
        }
    }

    /**
     * 创建STT请求的multipart表单数据
     *
     * @param sttRequest STT请求对象
     * @return BodyInserter 用于插入multipart请求体
     */
    private BodyInserter<?, ? super ClientHttpRequest> createSttMultipartBody(final SttDTO.Request sttRequest) {
        logger.debug("创建STT multipart请求体: model={}, file={}, language={}",
                sttRequest.model(),
                sttRequest.file() != null ? sttRequest.file().filename() : "null",
                sttRequest.language());

        // 使用 LinkedMultiValueMap 配合资源对象构建 multipart 请求
        // 注意：FilePart 需要特殊处理，使用 InMemoryResource 包装文件内容
        return (outputMessage, context) -> {
            // 读取文件内容（带上限：超限抛 413，见 #862）
            return readBounded(sttRequest.file())
                    .flatMap(bytes -> {
                        final int totalSize = bytes.length;

                        // 创建内存资源
                        org.springframework.core.io.buffer.DefaultDataBufferFactory factory = 
                                new org.springframework.core.io.buffer.DefaultDataBufferFactory();
                        org.springframework.core.io.buffer.DataBuffer dataBuffer = factory.wrap(bytes);
                        
                        org.springframework.core.io.Resource resource = 
                                new org.springframework.core.io.ByteArrayResource(bytes) {
                                    @Override
                                    public String getFilename() {
                                        return sttRequest.file().filename();
                                    }
                                };
                        
                        // 构建 multipart 表单
                        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
                        formData.add("file", resource);
                        
                        if (sttRequest.model() != null) {
                            formData.add("model", sttRequest.model());
                        }
                        if (sttRequest.language() != null) {
                            formData.add("language", sttRequest.language());
                        }
                        if (sttRequest.prompt() != null) {
                            formData.add("prompt", sttRequest.prompt());
                        }
                        if (sttRequest.responseFormat() != null) {
                            formData.add("response_format", sttRequest.responseFormat());
                        }
                        if (sttRequest.temperature() != null) {
                            formData.add("temperature", sttRequest.temperature().toString());
                        }
                        
                        logger.debug("Multipart表单数据创建完成, 文件大小: {} bytes", totalSize);
                        
                        // 使用 BodyInserters 发送 multipart 数据
                        return BodyInserters.fromMultipartData(formData)
                                .insert(outputMessage, context);
                    });
        };
    }

    /**
     * 创建图像编辑请求的multipart表单数据.
     *
     * <p>图像与掩码是 {@link FilePart}，WebClient 出站时不能直接作为部件值发送，
     * 因此与 STT 一样先把内容读成内存资源再组部件（部件名遵循 OpenAI 契约：
     * 多张图像用 {@code image[]}、掩码用 {@code mask}）。
     *
     * @param imageEditRequest 图像编辑请求对象
     * @return BodyInserter 用于插入multipart请求体
     * @since v3.5.2 起改为资源化出站（#637）
     */
    private BodyInserter<?, ? super ClientHttpRequest> createImageEditMultipartBody(
            final ImageEditDTO.Request imageEditRequest) {
        return (outputMessage, context) -> buildImageEditParts(imageEditRequest)
                .flatMap(parts -> BodyInserters.fromMultipartData(parts).insert(outputMessage, context));
    }

    /**
     * 组图像编辑的 multipart 部件.
     *
     * @param imageEditRequest 图像编辑请求
     * @return 部件映射（含 {@code image[]} 与 {@code mask} 文件部件、其余文本部件）
     */
    Mono<MultiValueMap<String, Object>> buildImageEditParts(final ImageEditDTO.Request imageEditRequest) {
        final MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        final List<FilePart> images = imageEditRequest.image() == null
                ? List.of() : imageEditRequest.image();

        final Mono<Void> imagesPart = Flux.fromIterable(images)
                .concatMap(this::toResource)
                .doOnNext(resource -> parts.add("image[]", resource))
                .then();
        final Mono<Void> maskPart = imageEditRequest.mask() == null
                ? Mono.empty()
                : toResource(imageEditRequest.mask())
                        .doOnNext(resource -> parts.add("mask", resource))
                        .then();

        return imagesPart.then(maskPart).then(Mono.fromSupplier(() -> {
            addFormField(parts, "model", imageEditRequest.model());
            addFormField(parts, "prompt", imageEditRequest.prompt());
            addFormField(parts, "background", imageEditRequest.background());
            addFormField(parts, "input_fidelity", imageEditRequest.input_fidelity());
            addFormField(parts, "n", imageEditRequest.n());
            addFormField(parts, "output_compression", imageEditRequest.output_compression());
            addFormField(parts, "output_format", imageEditRequest.output_format());
            addFormField(parts, "partial_images", imageEditRequest.partial_images());
            addFormField(parts, "quality", imageEditRequest.quality());
            addFormField(parts, "response_format", imageEditRequest.response_format());
            addFormField(parts, "size", imageEditRequest.size());
            addFormField(parts, "stream", imageEditRequest.stream());
            addFormField(parts, "user", imageEditRequest.user());
            logger.debug("图像编辑Multipart表单数据创建完成，包含{}个字段", parts.size());
            return parts;
        }));
    }


    /**
     * 把入站文件部件读成内存资源（保留原文件名，便于上游识别）.
     *
     * <p>读取受 {@code jairouter.multipart.max-file-size-bytes} 约束（#862）：
     * 超限时抛出 {@link MultipartPayloadTooLargeException}（映射为 413），避免把任意大小的文件读进堆内存。</p>
     *
     * @param part 文件部件
     * @return 内存资源
     */
    private Mono<ByteArrayResource> toResource(final FilePart part) {
        return readBounded(part).map(bytes -> new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return part.filename();
            }
        });
    }

    /**
     * 有上限地读取文件部件内容（#862）.
     *
     * <p>累积超过 {@link #maxFileSizeBytes} 时立即释放已缓存的 {@link DataBuffer} 并终止，
     * 由调用方把 413 透出给客户端；空部件返回空数组。</p>
     *
     * @param part 文件部件
     * @return 文件内容
     */
    Mono<byte[]> readBounded(final FilePart part) {
        return Mono.defer(() -> {
            final List<DataBuffer> chunks = new ArrayList<>();
            final AtomicLong total = new AtomicLong();
            return part.content()
                    .handle((buffer, sink) -> {
                        final long size = total.addAndGet(buffer.readableByteCount());
                        if (size > maxFileSizeBytes) {
                            releaseAll(chunks);
                            DataBufferUtils.release(buffer);
                            sink.error(tooLarge(part, size));
                            return;
                        }
                        chunks.add(buffer);
                    })
                    .then(Mono.fromSupplier(() -> mergeChunks(chunks, (int) total.get())))
                    .doOnError(error -> releaseAll(chunks));
        });
    }

    /** 释放并清空已缓存的块. */
    private static void releaseAll(final List<DataBuffer> chunks) {
        for (final DataBuffer chunk : chunks) {
            DataBufferUtils.release(chunk);
        }
        chunks.clear();
    }

    /** 把已缓存的块合并成一个字节数组（并释放原块）. */
    private static byte[] mergeChunks(final List<DataBuffer> chunks, final int totalSize) {
        final byte[] bytes = new byte[totalSize];
        int offset = 0;
        for (final DataBuffer chunk : chunks) {
            final int length = chunk.readableByteCount();
            chunk.read(bytes, offset, length);
            offset += length;
            DataBufferUtils.release(chunk);
        }
        chunks.clear();
        return bytes;
    }

    /** 构造超限异常，文案里给出文件名、实际大小与上限，便于客户端自我纠正（映射为 413，见 #862）. */
    private MultipartPayloadTooLargeException tooLarge(final FilePart part, final long actualBytes) {
        final long mib = 1024L * 1024L;
        final String limitText = maxFileSizeBytes >= mib
                ? (maxFileSizeBytes / mib) + " MiB"
                : (maxFileSizeBytes / 1024L) + " KiB";
        final String message = String.format(
                "上传文件过大：%s（%d 字节）超过上限 %d 字节（%s）",
                part.filename(), actualBytes, maxFileSizeBytes, limitText);
        logger.warn("拒绝过大的上传文件: file={}, size={}B, limit={}B", part.filename(), actualBytes, maxFileSizeBytes);
        return new MultipartPayloadTooLargeException(part.filename(), actualBytes, maxFileSizeBytes, message);
    }
    /**
     * 添加非空表单字段.
     *
     * @param parts 部件映射
     * @param key   字段名
     * @param value 字段值（{@code null} 跳过）
     */
    private static void addFormField(final MultiValueMap<String, Object> parts,
            final String key, final Object value) {
        if (value != null) {
            parts.add(key, String.valueOf(value));
        }
    }

    /**
     * 配置请求头
     * 根据请求类型设置合适的Content-Type
     *
     * @param requestSpec 请求规格
     * @param request     请求对象
     * @return 配置后的请求规格
     */
    public WebClient.RequestBodySpec configureRequestHeaders(
            final WebClient.RequestBodySpec requestSpec,
            final Object request) {

        // STT和图像编辑需要multipart格式
        if (request instanceof SttDTO.Request || request instanceof ImageEditDTO.Request) {
            requestSpec.contentType(MediaType.MULTIPART_FORM_DATA);
            logger.debug("设置Content-Type: multipart/form-data");
        } else {
            // 默认JSON格式
            requestSpec.header("Content-Type", "application/json");
            logger.debug("设置Content-Type: application/json");
        }

        return requestSpec;
    }

    /**
     * 配置请求头（带实例自定义headers）
     *
     * @param requestSpec 请求规格
     * @param request     请求对象
     * @param instance    服务实例配置
     * @return 配置后的请求规格
     */
    public WebClient.RequestBodySpec configureRequestHeaders(
            final WebClient.RequestBodySpec requestSpec,
            final Object request,
            final org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance instance) {

        // 首先应用默认的请求头配置
        WebClient.RequestBodySpec spec = configureRequestHeaders(requestSpec, request);

        // 然后应用实例配置中的自定义headers
        if (instance != null && instance.getHeaders() != null) {
            for (Map.Entry<String, String> header : instance.getHeaders().entrySet()) {
                spec = spec.header(header.getKey(), header.getValue());
                logger.debug("应用实例自定义请求头: {} = {}", header.getKey(), header.getValue());
            }
        }

        return spec;
    }

    /**
     * 判断是否需要multipart格式
     *
     * @param request 请求对象
     * @return 是否需要multipart格式
     */
    public boolean isMultipartRequest(final Object request) {
        // 检查原始请求类型
        if (request instanceof SttDTO.Request || request instanceof ImageEditDTO.Request) {
            return true;
        }
        // 检查已转换的multipart数据（由Adapter.transformSttRequest返回）
        if (request instanceof MultiValueMap) {
            return true;
        }
        return false;
    }

    /**
     * 获取请求的Content-Type
     *
     * @param request 请求对象
     * @return Content-Type
     */
    public MediaType getContentType(final Object request) {
        if (isMultipartRequest(request)) {
            return MediaType.MULTIPART_FORM_DATA;
        }
        return MediaType.APPLICATION_JSON;
    }
}
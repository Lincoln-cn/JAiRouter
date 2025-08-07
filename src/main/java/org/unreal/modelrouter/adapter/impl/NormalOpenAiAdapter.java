package org.unreal.modelrouter.adapter.impl;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.adapter.AdapterCapabilities;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.ImageEditDTO;
import org.unreal.modelrouter.dto.SttDTO;

import java.util.List;

public class NormalOpenAiAdapter extends BaseAdapter {

    public NormalOpenAiAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.all();
    }

    @Override
    protected String getAdapterType() {
        return "normal";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        if (request instanceof SttDTO.Request sttRequest) {
            return transformSttRequest(sttRequest);
        }else if (request instanceof ImageEditDTO.Request imageEditRequest) {
            return transformImageEditRequestRequest(imageEditRequest);
        }
        return super.transformRequest(request, adapterType);
    }

    private Object transformImageEditRequestRequest(ImageEditDTO.Request imageEditRequest) {

        if (imageEditRequest.model() == null || imageEditRequest.model().isEmpty()) {
            throw new IllegalArgumentException("model is required");
        }

        if (imageEditRequest.image() == null || imageEditRequest.image().isEmpty()) {
            throw new IllegalArgumentException("At least one image file is required");
        }

        if (imageEditRequest.prompt() == null || imageEditRequest.prompt().trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt is required");
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        
        // 添加 model 字段
        if (imageEditRequest.model() != null) {
            builder.part("model", imageEditRequest.model());
        }

        // 添加 prompt 字段
        if (imageEditRequest.prompt() != null) {
            builder.part("prompt", imageEditRequest.prompt());
        }

        // 添加 background 字段
        if (imageEditRequest.background() != null) {
            builder.part("background", imageEditRequest.background());
        }

        // 添加 input_fidelity 字段
        if (imageEditRequest.input_fidelity() != null) {
            builder.part("input_fidelity", imageEditRequest.input_fidelity());
        }

        // 添加 mask 字段
        if (imageEditRequest.mask() != null) {
            builder.part("mask", imageEditRequest.mask());
        }

        // 添加 n 字段
        if (imageEditRequest.n() != null) {
            builder.part("n", imageEditRequest.n());
        }

        // 添加 output_compression 字段
        if (imageEditRequest.output_compression() != null) {
            builder.part("output_compression", imageEditRequest.output_compression());
        }

        // 添加 output_format 字段
        if (imageEditRequest.output_format() != null) {
            builder.part("output_format", imageEditRequest.output_format());
        }

        // 添加 partial_images 字段
        if (imageEditRequest.partial_images() != null) {
            builder.part("partial_images", imageEditRequest.partial_images());
        }

        // 添加 quality 字段
        if (imageEditRequest.quality() != null) {
            builder.part("quality", imageEditRequest.quality());
        }

        // 添加 response_format 字段
        if (imageEditRequest.response_format() != null) {
            builder.part("response_format", imageEditRequest.response_format());
        }

        // 添加 size 字段
        if (imageEditRequest.size() != null) {
            builder.part("size", imageEditRequest.size());
        }

        // 添加 stream 字段
        if (imageEditRequest.stream() != null) {
            builder.part("stream", imageEditRequest.stream());
        }

        // 添加 user 字段
        if (imageEditRequest.user() != null) {
            builder.part("user", imageEditRequest.user());
        }

        // 处理 image 文件列表
        if (imageEditRequest.image() != null && !imageEditRequest.image().isEmpty()) {
            for (FilePart filePart : imageEditRequest.image()) {
                // 动态检测文件类型
                MediaType contentType = determineImageContentType(filePart.filename());

                builder.asyncPart("image", filePart.content(), DataBuffer.class)
                        .filename(filePart.filename())
                        .contentType(contentType);
            }
        }

        return builder.build();
    }

    private MediaType determineImageContentType(String filename) {
        if (filename == null) return MediaType.IMAGE_PNG;

        String lowercaseFilename = filename.toLowerCase();
        if (lowercaseFilename.endsWith(".jpg") || lowercaseFilename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lowercaseFilename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lowercaseFilename.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (lowercaseFilename.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_PNG; // 默认
    }

    /**
     * STT请求需要特殊的multipart处理
     */
    private Object transformSttRequest(SttDTO.Request sttRequest) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", sttRequest.model());
        builder.part("language", sttRequest.language());

        // 使用 asyncPart 处理文件内容流
        builder.asyncPart("file", sttRequest.file().content(), DataBuffer.class)
                .filename(sttRequest.file().filename())
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        return builder.build();
    }

    @Override
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(WebClient.RequestBodySpec requestSpec, T request) {
        // Normal adapter保持标准的OpenAI格式，不需要特殊的头部配置
        return super.configureRequestHeaders(requestSpec, request);
    }
}
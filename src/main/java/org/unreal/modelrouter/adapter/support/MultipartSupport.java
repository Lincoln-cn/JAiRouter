package org.unreal.modelrouter.adapter.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.unreal.modelrouter.dto.ImageEditDTO;
import org.unreal.modelrouter.dto.SttDTO;

/**
 * 多部分表单支持工具类
 * 处理 STT、图像编辑等多部分请求
 */
@Slf4j
public class MultipartSupport {

    /**
     * 创建请求体 - 处理不同类型的请求体格式
     */
    public BodyInserter<?, ?> createRequestBody(Object request) {
        log.debug("创建请求体，请求类型：{}", request.getClass().getSimpleName());

        if (request instanceof MultiValueMap) {
            log.debug("检测到已转换的 multipart 数据，直接使用");
            return BodyInserters.fromMultipartData((MultiValueMap<String, ?>) request);
        } else if (request instanceof SttDTO.Request) {
            log.debug("检测到 STT 请求，使用 multipart 处理");
            return createMultipartBody((SttDTO.Request) request);
        } else if (request instanceof ImageEditDTO.Request) {
            log.debug("检测到图像编辑请求，使用 multipart 处理");
            return createMultipartBody((ImageEditDTO.Request) request);
        } else {
            log.debug("使用 JSON 请求体处理");
            return BodyInserters.fromValue(request);
        }
    }

    /**
     * 创建 STT 请求的 multipart 表单数据
     */
    public BodyInserter<?, ?> createMultipartBody(SttDTO.Request sttRequest) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        log.debug("创建 STT multipart 请求体：model={}, file={}, language={}",
                sttRequest.model(),
                sttRequest.file() != null ? sttRequest.file().filename() : "null",
                sttRequest.language());

        if (sttRequest.file() != null) {
            parts.add("file", sttRequest.file());
            log.debug("添加文件部分：filename={}", sttRequest.file().filename());
        }

        if (sttRequest.model() != null) {
            parts.add("model", sttRequest.model());
            log.debug("添加 model 字段：{}", sttRequest.model());
        }
        if (sttRequest.language() != null) {
            parts.add("language", sttRequest.language());
            log.debug("添加 language 字段：{}", sttRequest.language());
        }
        if (sttRequest.prompt() != null) {
            parts.add("prompt", sttRequest.prompt());
            log.debug("添加 prompt 字段：{}", sttRequest.prompt());
        }
        if (sttRequest.responseFormat() != null) {
            parts.add("response_format", sttRequest.responseFormat());
            log.debug("添加 response_format 字段：{}", sttRequest.responseFormat());
        }
        if (sttRequest.temperature() != null) {
            parts.add("temperature", sttRequest.temperature().toString());
            log.debug("添加 temperature 字段：{}", sttRequest.temperature());
        }

        log.debug("Multipart 表单数据创建完成，包含{}个字段", parts.size());
        return BodyInserters.fromMultipartData(parts);
    }

    /**
     * 创建图像编辑请求的 multipart 表单数据
     */
    public BodyInserter<?, ?> createMultipartBody(ImageEditDTO.Request imageEditRequest) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        if (imageEditRequest.model() != null) {
            parts.add("model", imageEditRequest.model());
        }
        if (imageEditRequest.image() != null) {
            parts.add("image", imageEditRequest.image());
        }
        if (imageEditRequest.mask() != null) {
            parts.add("mask", imageEditRequest.mask());
        }
        if (imageEditRequest.prompt() != null) {
            parts.add("prompt", imageEditRequest.prompt());
        }
        if (imageEditRequest.n() != null) {
            parts.add("n", imageEditRequest.n().toString());
        }
        if (imageEditRequest.size() != null) {
            parts.add("size", imageEditRequest.size());
        }

        return BodyInserters.fromMultipartData(parts);
    }
}

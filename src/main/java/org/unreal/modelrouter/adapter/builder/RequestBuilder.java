package org.unreal.modelrouter.adapter.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.unreal.modelrouter.dto.ImageEditDTO;
import org.unreal.modelrouter.dto.SttDTO;

/**
 * 请求构建器
 * 
 * 负责构建各种类型的请求体
 * 支持 JSON、multipart 等多种格式
 * 
 * @author AI Assistant
 * @since v2.2.1
 */
@Component("adapterRequestBuilder")
public class RequestBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RequestBuilder.class);

    /**
     * 创建请求体 - 处理不同类型的请求体格式
     *
     * @param request 请求对象
     * @return 请求体插入器
     */
    public BodyInserter<?, ? super ClientHttpRequest> createRequestBody(final Object request) {
        logger.debug("创建请求体，请求类型：{}", request.getClass().getSimpleName());

        // 检查是否已经是转换后的 multipart 数据
        if (request instanceof MultiValueMap) {
            logger.debug("检测到已转换的 multipart 数据，直接使用");
            return BodyInserters.fromMultipartData((MultiValueMap<String, ?>) request);
        } else if (request instanceof SttDTO.Request) {
            logger.debug("检测到 STT 请求，使用 multipart 处理");
            return createSttMultipartBody((SttDTO.Request) request);
        } else if (request instanceof ImageEditDTO.Request) {
            logger.debug("检测到图像编辑请求，使用 multipart 处理");
            return createImageEditMultipartBody((ImageEditDTO.Request) request);
        } else {
            // 普通 JSON 请求
            logger.debug("使用 JSON 请求体处理");
            return BodyInserters.fromValue(request);
        }
    }

    /**
     * 创建 STT 请求的 multipart 表单数据
     *
     * @param sttRequest STT 请求对象
     * @return multipart 请求体
     */
    public BodyInserter<?, ? super ClientHttpRequest> createSttMultipartBody(final SttDTO.Request sttRequest) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        logger.debug("创建 STT multipart 请求体：model={}, file={}, language={}",
                sttRequest.model(),
                sttRequest.file() != null ? "present" : "null",
                sttRequest.language());

        // 添加文件部分
        if (sttRequest.file() != null) {
            parts.add("file", sttRequest.file());
            logger.debug("添加文件部分");
        }

        // 添加其他表单字段
        addFormField(parts, "model", sttRequest.model());
        addFormField(parts, "language", sttRequest.language());
        addFormField(parts, "prompt", sttRequest.prompt());
        addFormField(parts, "response_format", sttRequest.responseFormat());
        addFormField(parts, "temperature", sttRequest.temperature());

        logger.debug("Multipart 表单数据创建完成，包含{}个字段", parts.size());
        return BodyInserters.fromMultipartData(parts);
    }

    /**
     * 创建图像编辑请求的 multipart 表单数据
     *
     * @param imageEditRequest 图像编辑请求对象
     * @return multipart 请求体
     */
    public BodyInserter<?, ? super ClientHttpRequest> createImageEditMultipartBody(final ImageEditDTO.Request imageEditRequest) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 添加图像文件（支持单个或多个文件）
        if (imageEditRequest.image() != null && !imageEditRequest.image().isEmpty()) {
            for (Object file : imageEditRequest.image()) {
                parts.add("image[]", file);
                logger.debug("添加图像文件：{}", file);
            }
        }

        // 添加 mask 文件（如果有）
        if (imageEditRequest.mask() != null) {
            parts.add("mask", imageEditRequest.mask());
            logger.debug("添加 mask 文件");
        }

        // 添加其他表单字段
        addFormField(parts, "model", imageEditRequest.model());
        addFormField(parts, "prompt", imageEditRequest.prompt());
        addFormField(parts, "background", imageEditRequest.background());
        addFormField(parts, "input_fidelity", imageEditRequest.input_fidelity());
        addFormField(parts, "n", imageEditRequest.n());
        addFormField(parts, "output_format", imageEditRequest.output_format());
        addFormField(parts, "quality", imageEditRequest.quality());
        addFormField(parts, "response_format", imageEditRequest.response_format());
        addFormField(parts, "size", imageEditRequest.size());
        addFormField(parts, "user", imageEditRequest.user());

        logger.debug("图像编辑 multipart 表单数据创建完成，包含{}个字段", parts.size());
        return BodyInserters.fromMultipartData(parts);
    }

    /**
     * 通用的 multipart 请求构建方法
     *
     * @param parts multipart 数据
     * @return 请求体插入器
     */
    public BodyInserter<?, ? super ClientHttpRequest> createMultipartBody(final MultiValueMap<String, Object> parts) {
        return BodyInserters.fromMultipartData(parts);
    }

    /**
     * 创建 JSON 请求体
     *
     * @param request 请求对象
     * @return 请求体插入器
     */
    public BodyInserter<?, ? super ClientHttpRequest> createJsonBody(final Object request) {
        logger.debug("创建 JSON 请求体，请求类型：{}", request.getClass().getSimpleName());
        return BodyInserters.fromValue(request);
    }

    /**
     * 创建表单数据
     *
     * @param key 键
     * @param value 值
     * @return 表单数据
     */
    public MultiValueMap<String, Object> createFormData(final String key,final Object value) {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add(key, value);
        return formData;
    }

    /**
     * 向表单数据添加字段
     *
     * @param parts 表单数据
     * @param key 键
     * @param value 值
     */
    public void addFormField(final MultiValueMap<String, Object> parts,final String key,final Object value) {
        if (value != null) {
            if (value instanceof Number) {
                parts.add(key, value.toString());
            } else {
                parts.add(key, value);
            }
            logger.debug("添加表单字段：{}={}", key, value);
        }
    }

    /**
     * 向表单数据添加文件
     *
     * @param parts 表单数据
     * @param key 键
     * @param file 文件对象
     */
    public void addFileField(final MultiValueMap<String, Object> parts,final String key,final Object file) {
        if (file != null) {
            parts.add(key, file);
            String filename = extractFilename(file);
            logger.debug("添加文件字段：{} (filename={})", key, filename);
        }
    }

    /**
     * 从文件对象中提取文件名
     *
     * @param file 文件对象
     * @return 文件名
     */
    private String extractFilename(final Object file) {
        if (file == null) {
            return "null";
        }
        try {
            java.lang.reflect.Method filenameMethod = file.getClass().getMethod("filename");
            return (String) filenameMethod.invoke(file);
        } catch (Exception e) {
            return file.toString();
        }
    }
}

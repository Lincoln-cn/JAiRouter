package org.unreal.modelrouter.router.adapter.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.SttDTO;

import java.util.Map;

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
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        logger.debug("创建STT multipart请求体: model={}, file={}, language={}",
                sttRequest.model(),
                sttRequest.file() != null ? sttRequest.file().filename() : "null",
                sttRequest.language());

        // 添加文件部分
        if (sttRequest.file() != null) {
            parts.add("file", sttRequest.file());
            logger.debug("添加文件部分: filename={}", sttRequest.file().filename());
        }

        // 添加其他表单字段
        if (sttRequest.model() != null) {
            parts.add("model", sttRequest.model());
            logger.debug("添加model字段: {}", sttRequest.model());
        }
        if (sttRequest.language() != null) {
            parts.add("language", sttRequest.language());
            logger.debug("添加language字段: {}", sttRequest.language());
        }
        if (sttRequest.prompt() != null) {
            parts.add("prompt", sttRequest.prompt());
            logger.debug("添加prompt字段: {}", sttRequest.prompt());
        }
        if (sttRequest.responseFormat() != null) {
            parts.add("response_format", sttRequest.responseFormat());
            logger.debug("添加response_format字段: {}", sttRequest.responseFormat());
        }
        if (sttRequest.temperature() != null) {
            parts.add("temperature", sttRequest.temperature().toString());
            logger.debug("添加temperature字段: {}", sttRequest.temperature());
        }

        logger.debug("Multipart表单数据创建完成，包含{}个字段", parts.size());
        return BodyInserters.fromMultipartData(parts);
    }

    /**
     * 创建图像编辑请求的multipart表单数据
     *
     * @param imageEditRequest 图像编辑请求对象
     * @return BodyInserter 用于插入multipart请求体
     */
    private BodyInserter<?, ? super ClientHttpRequest> createImageEditMultipartBody(
            final ImageEditDTO.Request imageEditRequest) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 根据ImageEditDTO.Request的实际结构添加字段
        // 如果有image字段
        if (imageEditRequest.image() != null) {
            parts.add("image", imageEditRequest.image());
            logger.debug("添加image字段");
        }

        // 如果有mask字段
        if (imageEditRequest.mask() != null) {
            parts.add("mask", imageEditRequest.mask());
            logger.debug("添加mask字段");
        }

        // 添加其他参数字段
        if (imageEditRequest.model() != null) {
            parts.add("model", imageEditRequest.model());
            logger.debug("添加model字段: {}", imageEditRequest.model());
        }

        if (imageEditRequest.prompt() != null) {
            parts.add("prompt", imageEditRequest.prompt());
            logger.debug("添加prompt字段: {}", imageEditRequest.prompt());
        }

        if (imageEditRequest.n() != null) {
            parts.add("n", imageEditRequest.n().toString());
            logger.debug("添加n字段: {}", imageEditRequest.n());
        }

        if (imageEditRequest.size() != null) {
            parts.add("size", imageEditRequest.size());
            logger.debug("添加size字段: {}", imageEditRequest.size());
        }

        logger.debug("图像编辑Multipart表单数据创建完成，包含{}个字段", parts.size());
        return BodyInserters.fromMultipartData(parts);
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
        return request instanceof SttDTO.Request || request instanceof ImageEditDTO.Request;
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
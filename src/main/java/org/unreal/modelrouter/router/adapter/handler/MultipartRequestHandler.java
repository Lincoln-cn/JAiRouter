package org.unreal.modelrouter.router.adapter.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
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
        logger.debug("创建STT multipart请求体: model={}, file={}, language={}",
                sttRequest.model(),
                sttRequest.file() != null ? sttRequest.file().filename() : "null",
                sttRequest.language());

        // 使用 LinkedMultiValueMap 配合资源对象构建 multipart 请求
        // 注意：FilePart 需要特殊处理，使用 InMemoryResource 包装文件内容
        return (outputMessage, context) -> {
            // 读取文件内容到内存
            return sttRequest.file().content()
                    .collectList()
                    .flatMap(dataBuffers -> {
                        // 合并所有 DataBuffer 为字节数组
                        int totalSize = dataBuffers.stream()
                                .mapToInt(db -> db.readableByteCount())
                                .sum();
                        byte[] bytes = new byte[totalSize];
                        int offset = 0;
                        for (org.springframework.core.io.buffer.DataBuffer db : dataBuffers) {
                            int len = db.readableByteCount();
                            java.nio.ByteBuffer byteBuffer = db.asByteBuffer();
                            byteBuffer.get(bytes, offset, len);
                            offset += len;
                            org.springframework.core.io.buffer.DataBufferUtils.release(db);
                        }
                        
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
     * 创建图像编辑请求的multipart表单数据
     *
     * @param imageEditRequest 图像编辑请求对象
     * @return BodyInserter 用于插入multipart请求体
     */
    private BodyInserter<?, ? super ClientHttpRequest> createImageEditMultipartBody(
            final ImageEditDTO.Request imageEditRequest) {
        // 使用 MultipartBodyBuilder 构建 multipart 请求
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        // 根据ImageEditDTO.Request的实际结构添加字段
        // 如果有image字段
        if (imageEditRequest.image() != null) {
            builder.part("image", imageEditRequest.image());
            logger.debug("添加image字段");
        }

        // 如果有mask字段
        if (imageEditRequest.mask() != null) {
            builder.part("mask", imageEditRequest.mask());
            logger.debug("添加mask字段");
        }

        // 添加其他参数字段（字符串类型）
        if (imageEditRequest.model() != null) {
            builder.part("model", imageEditRequest.model());
            logger.debug("添加model字段: {}", imageEditRequest.model());
        }

        if (imageEditRequest.prompt() != null) {
            builder.part("prompt", imageEditRequest.prompt());
            logger.debug("添加prompt字段: {}", imageEditRequest.prompt());
        }

        if (imageEditRequest.n() != null) {
            // 转为字符串避免 Content-Type 问题
            builder.part("n", imageEditRequest.n().toString());
            logger.debug("添加n字段: {}", imageEditRequest.n());
        }

        if (imageEditRequest.size() != null) {
            builder.part("size", imageEditRequest.size());
            logger.debug("添加size字段: {}", imageEditRequest.size());
        }

        logger.debug("图像编辑Multipart表单数据创建完成");
        return BodyInserters.fromMultipartData(builder.build());
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
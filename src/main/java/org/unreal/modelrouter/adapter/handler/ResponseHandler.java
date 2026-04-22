package org.unreal.modelrouter.adapter.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 响应处理器
 * 
 * 负责处理和转换下游服务的响应
 * 支持错误处理、响应转换和格式化
 * 
 * @author AI Assistant
 * @since v2.2.1
 */
@Component("adapterResponseHandler")
public class ResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ResponseHandler.class);

    private final ObjectMapper objectMapper;

    public ResponseHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理下游服务响应
     *
     * @param responseEntity 原始响应实体
     * @param adapterType 适配器类型
     * @return 处理后的响应
     */
    public Mono<ResponseEntity<?>> handleResponse(ResponseEntity<String> responseEntity, String adapterType) {
        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            String bodyStr = responseEntity.getBody() != null ? responseEntity.getBody() : "";
            return Mono.error(new ResponseStatusException(
                    responseEntity.getStatusCode(),
                    "下游服务异常：" + bodyStr
            ));
        }

        try {
            String bodyStr = responseEntity.getBody();
            Object downstreamData;

            if (bodyStr == null || bodyStr.isEmpty()) {
                // 如果下游成功响应但 body 为空，则 data 部分为 null
                downstreamData = null;
            } else {
                // 1. 将下游服务的 JSON 响应体解析为通用 Object
                downstreamData = objectMapper.readValue(bodyStr, Object.class);
            }

            // 2. 对解析后的数据应用转换逻辑（子类可重写此方法）
            Object transformedData = transformResponse(downstreamData, adapterType);

            // 3. 将最终数据包装到 RouterResponse 中
            org.unreal.modelrouter.controller.response.RouterResponse<Object> finalResponse =
                    org.unreal.modelrouter.controller.response.RouterResponse.success(transformedData, "请求成功");

            // 4. 构建并返回包含 RouterResponse 的最终 ResponseEntity
            return Mono.just(
                    ResponseEntity.status(responseEntity.getStatusCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(finalResponse)
            );
        } catch (JsonProcessingException e) {
            // 如果下游返回的不是合法的 JSON，则处理错误
            logger.error("无法解析下游服务的响应体：{}", responseEntity.getBody(), e);
            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法解析下游服务响应"));
        }
    }

    /**
     * 处理二进制响应
     *
     * @param clientResponse 客户端响应
     * @param instanceName 实例名称
     * @param path 请求路径
     * @return 处理后的响应
     */
    public Mono<ResponseEntity<byte[]>> handleBinaryResponse(
            ClientResponse clientResponse,
            String instanceName,
            String path) {

        // 处理 5xx 服务器错误
        if (clientResponse.statusCode().is5xxServerError()) {
            return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
        }

        // 处理 4xx 客户端错误
        if (clientResponse.statusCode().is4xxClientError()) {
            return handleClientError(clientResponse, instanceName, path);
        }

        // 获取响应体和响应头
        return clientResponse.bodyToMono(byte[].class)
                .map(body -> {
                    // 构建包含响应头信息的 ResponseEntity
                    ResponseEntity.BodyBuilder responseBuilder;

                    if (clientResponse.statusCode().is2xxSuccessful()) {
                        responseBuilder = ResponseEntity.ok();
                    } else {
                        responseBuilder = ResponseEntity.status(clientResponse.statusCode());
                    }

                    // 获取并复制重要的响应头
                    org.springframework.http.HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();

                    // 设置 Content-Type（最重要）
                    if (downstreamHeaders.getContentType() != null) {
                        responseBuilder.contentType(downstreamHeaders.getContentType());
                    }

                    // 设置 Content-Length
                    if (downstreamHeaders.getContentLength() > 0) {
                        responseBuilder.contentLength(downstreamHeaders.getContentLength());
                    }

                    // 复制 Content-Disposition（文件下载重要）
                    String contentDisposition = downstreamHeaders.getFirst("Content-Disposition");
                    if (contentDisposition != null) {
                        responseBuilder.header("Content-Disposition", contentDisposition);
                    }

                    // 复制缓存相关头信息
                    String cacheControl = downstreamHeaders.getFirst("Cache-Control");
                    if (cacheControl != null) {
                        responseBuilder.header("Cache-Control", cacheControl);
                    }

                    String etag = downstreamHeaders.getFirst("ETag");
                    if (etag != null) {
                        responseBuilder.header("ETag", etag);
                    }

                    String lastModified = downstreamHeaders.getFirst("Last-Modified");
                    if (lastModified != null) {
                        responseBuilder.header("Last-Modified", lastModified);
                    }

                    // 直接返回二进制内容，保持原始格式
                    return responseBuilder.body(body);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    // 处理空响应体的情况
                    ResponseEntity.BodyBuilder responseBuilder = clientResponse.statusCode().is2xxSuccessful()
                            ? ResponseEntity.ok()
                            : ResponseEntity.status(clientResponse.statusCode());

                    org.springframework.http.HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();
                    if (downstreamHeaders.getContentType() != null) {
                        responseBuilder.contentType(downstreamHeaders.getContentType());
                    }

                    return responseBuilder.build();
                }));
    }

    /**
     * 处理客户端错误
     *
     * @param clientResponse 客户端响应
     * @param instanceName 实例名称
     * @param path 请求路径
     * @return 错误 Mono
     */
    private Mono<ResponseEntity<byte[]>> handleClientError(
            ClientResponse clientResponse,
            String instanceName,
            String path) {

        int statusCode = clientResponse.statusCode().value();

        if (statusCode == 401) {
            logger.error("下游服务认证失败 (401): instance={}, path={}, response={}",
                    instanceName, path, clientResponse.statusCode());
        } else if (statusCode == 400) {
            logger.error("下游服务请求错误 (400): instance={}, path={}, response={}",
                    instanceName, path, clientResponse.statusCode());
        } else if (statusCode == 503) {
            logger.error("下游服务请求错误 (503): instance={}, path={}, response={}",
                    instanceName, path, clientResponse.statusCode());
        }

        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
    }

    /**
     * 处理 5xx 服务器错误
     *
     * @param clientResponse 客户端响应
     * @param instanceName 实例名称
     * @param path 请求路径
     * @return 错误 Mono
     */
    public Mono<Void> handleServerError(ClientResponse clientResponse, String instanceName, String path) {
        logger.error("下游服务 5xx 错误：instance={}, path={}, status={}",
                instanceName, path, clientResponse.statusCode());
        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
    }

    /**
     * 转换响应数据
     *
     * @param downstreamData 下游响应数据
     * @param adapterType 适配器类型
     * @return 转换后的数据
     */
    protected Object transformResponse(Object downstreamData, String adapterType) {
        // 默认不进行转换，子类可以重写此方法
        return downstreamData;
    }

    /**
     * 检查响应状态是否为成功
     *
     * @param response 响应实体
     * @return true 如果成功
     */
    public boolean isSuccess(ResponseEntity<?> response) {
        return response != null && response.getStatusCode().is2xxSuccessful();
    }

    /**
     * 提取响应中的错误信息
     *
     * @param response 响应实体
     * @return 错误信息
     */
    public String extractErrorMessage(ResponseEntity<String> response) {
        if (response == null || response.getBody() == null) {
            return "未知错误";
        }

        try {
            // 尝试从 JSON 中提取错误消息
            String body = response.getBody();
            if (body != null && body.startsWith("{")) {
                Map<String, Object> errorData = objectMapper.readValue(body, Map.class);
                if (errorData.containsKey("error")) {
                    Object error = errorData.get("error");
                    if (error instanceof Map) {
                        return (String) ((Map<?, ?>) error).get("message");
                    }
                    return error.toString();
                }
            }
            return body;
        } catch (JsonProcessingException e) {
            return response.getBody();
        }
    }

    /**
     * 记录响应日志
     *
     * @param responseEntity 响应实体
     * @param instanceName 实例名称
     * @param path 请求路径
     */
    public void logResponse(ResponseEntity<?> responseEntity, String instanceName, String path) {
        Object responseBody = responseEntity.getBody();
        String bodyStr = responseBody != null ? responseBody.toString() : "";

        logger.debug("下游服务响应成功：instance={}, path={}, status={}, body length={}",
                instanceName, path, responseEntity.getStatusCode(), bodyStr.length());

        if (!bodyStr.isEmpty()) {
            logger.debug("响应内容预览：{}", bodyStr.length() > 200 ? bodyStr.substring(0, 200) + "..." : bodyStr);
        }
    }

    /**
     * 处理空响应
     *
     * @param statusCode 状态码
     * @param contentType 内容类型
     * @return 空响应实体
     */
    public ResponseEntity<Void> createEmptyResponse(HttpStatusCode statusCode, MediaType contentType) {
        return ResponseEntity.status(statusCode)
                .contentType(contentType)
                .build();
    }
}

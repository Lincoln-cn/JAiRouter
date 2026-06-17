package org.unreal.modelrouter.router.adapter.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.handler.MultipartRequestHandler;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;

import java.util.Map;
import java.util.function.Function;

/**
 * 非流式请求处理器 - v2.26.6
 *
 * 从 BaseAdapter 提取的非流式请求处理逻辑，支持：
 * 1. 请求转换（transformRequest）
 * 2. 响应转换（transformResponse）
 * 3. RouterResponse 包装
 * 4. 二进制响应处理
 * 5. 指标记录
 *
 * @since v2.5.9
 */
import org.springframework.stereotype.Component;

@Component
public class NonStreamingRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NonStreamingRequestProcessor.class);

    private final ObjectMapper objectMapper;
    private final RequestBuilder requestBuilder;
    private final AdapterMetricsRecorder metricsRecorder;

    public NonStreamingRequestProcessor(
            final ObjectMapper objectMapper,
            final RequestBuilder requestBuilder,
            final AdapterMetricsRecorder metricsRecorder) {
        this.objectMapper = objectMapper;
        this.requestBuilder = requestBuilder;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * 处理非流式请求（统一入口）
     *
     * @param request            原始请求对象
     * @param authorization      授权头
     * @param client             WebClient 实例
     * @param path               请求路径
     * @param selectedInstance   选中的实例
     * @param serviceType        服务类型
     * @param responseType       响应类型（String.class 或 byte[].class）
     * @param adapterType        适配器类型
     * @param transformRequestFn 请求转换函数
     * @param transformResponseFn 响应转换函数
     * @param multipartHandler   Multipart 请求处理器
     * @return 响应实体
     */
    public <T> Mono<? extends ResponseEntity<?>> processRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelInstance selectedInstance,
            final ServiceType serviceType,
            final Class<?> responseType,
            final String adapterType,
            final Function<Object, Object> transformRequestFn,
            final Function<Object, Object> transformResponseFn,
            final MultipartRequestHandler multipartHandler) {

        // 1. 请求转换
        Object transformedRequest = transformRequestFn.apply(request);
        String instanceName = selectedInstance.getName();
        long requestStartTime = System.currentTimeMillis();

        logger.debug("发送请求到下游服务: instance={}, path={}, auth={}",
                instanceName, path, authorization != null ? "***" : "null");

        // 2. 构建请求 - 不在这里设置 Authorization，统一由实例 headers 处理
        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path);

        // 3. 配置请求头
        if (multipartHandler != null) {
            requestSpec = multipartHandler.configureRequestHeaders(requestSpec, request, selectedInstance);
        } else {
            // 对于普通JSON请求，设置Content-Type
            requestSpec = requestSpec.contentType(MediaType.APPLICATION_JSON);

            // 优先使用实例自定义headers中的Authorization，如果没有则使用参数传入的authorization
            boolean hasInstanceAuth = false;
            if (selectedInstance != null && selectedInstance.getHeaders() != null) {
                for (Map.Entry<String, String> header : selectedInstance.getHeaders().entrySet()) {
                    requestSpec = requestSpec.header(header.getKey(), header.getValue());
                    logger.debug("应用实例自定义请求头: {} = {}", header.getKey(), "***");
                    if ("Authorization".equalsIgnoreCase(header.getKey())) {
                        hasInstanceAuth = true;
                    }
                }
            }

            // 如果实例没有自定义Authorization，且参数提供了authorization，则使用参数值
            if (!hasInstanceAuth && authorization != null && !authorization.isEmpty()) {
                requestSpec = requestSpec.header("Authorization", authorization);
                logger.debug("使用参数Authorization头");
            }
        }

        // 4. 根据响应类型处理
        if (responseType == byte[].class) {
            return processBinaryResponse(requestSpec, transformedRequest, path,
                    instanceName, adapterType, serviceType, requestStartTime, multipartHandler);
        } else {
            return processJsonResponse(requestSpec, transformedRequest, instanceName,
                    adapterType, serviceType, requestStartTime, path, transformResponseFn, multipartHandler);
        }
    }

    /**
     * 处理二进制响应（TTS/STT 等）
     */
    private Mono<? extends ResponseEntity<?>> processBinaryResponse(
            final WebClient.RequestBodySpec requestSpec,
            final Object transformedRequest,
            final String path,
            final String instanceName,
            final String adapterType,
            final ServiceType serviceType,
            final long requestStartTime,
            final MultipartRequestHandler multipartHandler) {

        BodyInserter<?, ? super ClientHttpRequest> requestBody;
        if (multipartHandler != null) {
            requestBody = multipartHandler.createRequestBody(transformedRequest);
        } else {
            // 对于普通JSON请求，使用JSON body
            requestBody = org.springframework.web.reactive.function.BodyInserters.fromValue(transformedRequest);
        }

        return requestSpec
                .body(requestBody)
                .exchangeToMono(clientResponse -> {
                    // 处理 5xx 服务器错误
                    if (clientResponse.statusCode().is5xxServerError()) {
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    }

                    // 处理 4xx 客户端错误
                    if (clientResponse.statusCode().is4xxClientError()) {
                        handleClientError(clientResponse.statusCode(), instanceName, path);
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    }

                    // 获取响应体和响应头
                    return clientResponse.bodyToMono(byte[].class)
                            .map(body -> buildBinaryResponseEntity(clientResponse, body))
                            .switchIfEmpty(Mono.fromSupplier(() -> buildEmptyResponseEntity(clientResponse)));
                })
                .doOnSuccess(responseEntity -> {
                    if (metricsRecorder != null && responseEntity != null) {
                        long responseSize = responseEntity.getBody() != null
                                ? ((byte[]) responseEntity.getBody()).length : 0;
                        metricsRecorder.recordResponseTime(serviceType, "POST",
                                System.currentTimeMillis() - requestStartTime, "200");
                    }
                })
                .doOnError(throwable -> {
                    if (metricsRecorder != null) {
                        metricsRecorder.recordError(adapterType, instanceName,
                                throwable.getClass().getSimpleName(), throwable,
                                System.currentTimeMillis() - requestStartTime, serviceType);
                    }
                });
    }

    /**
     * 处理 JSON 响应
     */
    private Mono<? extends ResponseEntity<?>> processJsonResponse(
            final WebClient.RequestBodySpec requestSpec,
            final Object transformedRequest,
            final String instanceName,
            final String adapterType,
            final ServiceType serviceType,
            final long requestStartTime,
            final String path,
            final Function<Object, Object> transformResponseFn,
            final MultipartRequestHandler multipartHandler) {

        // 支持multipart请求（如STT）
        BodyInserter<?, ? super ClientHttpRequest> requestBody;
        if (multipartHandler != null && multipartHandler.isMultipartRequest(transformedRequest)) {
            requestBody = multipartHandler.createRequestBody(transformedRequest);
            logger.debug("使用multipart请求体处理STT请求");
        } else {
            requestBody = org.springframework.web.reactive.function.BodyInserters.fromValue(transformedRequest);
        }

        return requestSpec
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    logger.error("下游服务5xx错误: instance={}, path={}, status={}",
                            instanceName, path, clientResponse.statusCode());
                    return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                })
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    return handle4xxError(clientResponse, instanceName, path);
                })
                .toEntity(String.class)
                .flatMap(responseEntity -> {
                    if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                        String bodyStr = responseEntity.getBody() != null ? responseEntity.getBody() : "";
                        return Mono.<ResponseEntity<?>>error(new ResponseStatusException(
                                responseEntity.getStatusCode(), "下游服务异常: " + bodyStr));
                    }

                    try {
                        String bodyStr = responseEntity.getBody();
                        Object downstreamData;

                        if (bodyStr == null || bodyStr.isEmpty()) {
                            downstreamData = null;
                        } else {
                            downstreamData = objectMapper.readValue(bodyStr, Object.class);
                        }

                        // 响应转换
                        Object transformedData = transformResponseFn.apply(downstreamData);

                        // 包装 RouterResponse
                        RouterResponse<Object> finalResponse = RouterResponse.success(transformedData, "请求成功");

                        return Mono.just(
                                ResponseEntity.status(responseEntity.getStatusCode())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(finalResponse));

                    } catch (JsonProcessingException e) {
                        logger.error("无法解析下游服务的响应体: {}", responseEntity.getBody(), e);
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "无法解析下游服务响应"));
                    }
                })
                .doOnSuccess(responseEntity -> {
                    if (metricsRecorder != null && responseEntity != null) {
                        String bodyStr = responseEntity.getBody() != null
                                ? responseEntity.getBody().toString() : "";
                        metricsRecorder.recordRequestSize(serviceType,
                                transformedRequest.toString().getBytes().length, bodyStr.getBytes().length);
                        metricsRecorder.recordResponseTime(serviceType, "POST",
                                System.currentTimeMillis() - requestStartTime, responseEntity.getStatusCode().toString());
                    }
                })
                .doOnError(throwable -> {
                    if (metricsRecorder != null) {
                        metricsRecorder.recordError(adapterType, instanceName,
                                throwable.getClass().getSimpleName(), throwable,
                                System.currentTimeMillis() - requestStartTime, serviceType);
                    }
                });
    }

    /**
     * 处理 4xx 错误
     */
    private Mono<Throwable> handle4xxError(final ClientResponse clientResponse,
                                            final String instanceName,
                                            final String path) {
        HttpStatusCode statusCode = clientResponse.statusCode();

        if (statusCode.value() == 401) {
            logger.error("下游服务认证失败 (401): instance={}, path={}", instanceName, path);
            return Mono.error(new DownstreamServiceException(
                    "下游服务认证失败，请检查下游服务的认证配置", HttpStatus.valueOf(401)));
        } else if (statusCode.value() == 400) {
            logger.error("下游服务请求错误 (400): instance={}, path={}", instanceName, path);
            return Mono.error(new DownstreamServiceException(
                    "下游服务请求参数错误，请检查请求内容", HttpStatus.valueOf(400)));
        } else if (statusCode.value() == 503) {
            logger.error("下游服务不可用 (503): instance={}, path={}", instanceName, path);
            return Mono.error(new DownstreamServiceException(
                    "下游服务暂时不可用，请稍后重试", HttpStatus.valueOf(503)));
        }

        logger.error("下游服务4xx错误: instance={}, path={}, status={}",
                instanceName, path, statusCode);
        return Mono.error(new ResponseStatusException(statusCode));
    }

    /**
     * 处理客户端错误（记录日志）
     */
    private void handleClientError(final HttpStatusCode statusCode,
                                    final String instanceName,
                                    final String path) {
        if (statusCode.value() == 401) {
            logger.error("下游服务认证失败 (401): instance={}, path={}", instanceName, path);
        } else if (statusCode.value() == 400) {
            logger.error("下游服务请求错误 (400): instance={}, path={}", instanceName, path);
        } else if (statusCode.value() == 503) {
            logger.error("下游服务不可用 (503): instance={}, path={}", instanceName, path);
        }
    }

    /**
     * 构建二进制响应实体
     */
    private ResponseEntity<byte[]> buildBinaryResponseEntity(final ClientResponse clientResponse, final byte[] body) {
        ResponseEntity.BodyBuilder responseBuilder = clientResponse.statusCode().is2xxSuccessful()
                ? ResponseEntity.ok()
                : ResponseEntity.status(clientResponse.statusCode());

        HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();

        // 复制重要响应头
        if (downstreamHeaders.getContentType() != null) {
            responseBuilder.contentType(downstreamHeaders.getContentType());
        }
        if (downstreamHeaders.getContentLength() > 0) {
            responseBuilder.contentLength(downstreamHeaders.getContentLength());
        }

        // 复制 Content-Disposition（文件下载）
        String contentDisposition = downstreamHeaders.getFirst("Content-Disposition");
        if (contentDisposition != null) {
            responseBuilder.header("Content-Disposition", contentDisposition);
        }

        // 复制缓存相关头
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

        return responseBuilder.body(body);
    }

    /**
     * 构建空响应实体
     */
    private ResponseEntity<byte[]> buildEmptyResponseEntity(final ClientResponse clientResponse) {
        ResponseEntity.BodyBuilder responseBuilder = clientResponse.statusCode().is2xxSuccessful()
                ? ResponseEntity.ok()
                : ResponseEntity.status(clientResponse.statusCode());

        HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();
        if (downstreamHeaders.getContentType() != null) {
            responseBuilder.contentType(downstreamHeaders.getContentType());
        }

        return responseBuilder.build();
    }
}
package org.unreal.modelrouter.router.adapter.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;

/**
 * 非流式请求处理器 - v2.5.9
 * 
 * 从 BaseAdapter 提取的非流式请求处理逻辑
 * 
 * @since v2.5.9
 */
import org.springframework.stereotype.Component;

@Component
public class NonStreamingRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NonStreamingRequestProcessor.class);

    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;
    private final AdapterTracingManager tracingManager;
    private final RequestBuilder requestBuilder;

    public NonStreamingRequestProcessor(
            final MetricsCollector metricsCollector,
            final ObjectMapper objectMapper,
            final AdapterTracingManager tracingManager,
            final RequestBuilder requestBuilder) {
        this.metricsCollector = metricsCollector;
        this.objectMapper = objectMapper;
        this.tracingManager = tracingManager;
        this.requestBuilder = requestBuilder;
    }

    /**
     * 处理非流式请求（统一入口）
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
            final Object transformedRequest) {

        String instanceName = selectedInstance.getName();
        long requestStartTime = System.currentTimeMillis();

        logger.debug("发送请求到下游服务: instance={}, path={}, auth={}",
                instanceName, path, authorization != null ? "***" : "null");

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .header("Authorization", authorization);

        if (responseType == byte[].class) {
            return processBinaryResponse(requestSpec, transformedRequest, path,
                    instanceName, adapterType, serviceType, requestStartTime);
        } else {
            return processJsonResponse(requestSpec, transformedRequest, instanceName,
                    adapterType, serviceType, requestStartTime, path);
        }
    }

    private Mono<? extends ResponseEntity<?>> processBinaryResponse(
            final WebClient.RequestBodySpec requestSpec,
            final Object transformedRequest,
            final String path,
            final String instanceName,
            final String adapterType,
            final ServiceType serviceType,
            final long requestStartTime) {

        return requestSpec
                .body(requestBuilder.createRequestBody(transformedRequest))
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().is5xxServerError()) {
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    }
                    if (clientResponse.statusCode().is4xxClientError()) {
                        handleClientError(clientResponse.statusCode(), instanceName, path);
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    }
                    return clientResponse.bodyToMono(byte[].class)
                            .map(body -> buildResponseEntity(clientResponse, body));
                })
                .doOnSuccess(responseEntity -> recordSuccessMetrics(adapterType, instanceName, serviceType, requestStartTime))
                .doOnError(throwable -> recordErrorMetrics(adapterType, instanceName, requestStartTime));
    }

    private Mono<? extends ResponseEntity<?>> processJsonResponse(
            final WebClient.RequestBodySpec requestSpec,
            final Object transformedRequest,
            final String instanceName,
            final String adapterType,
            final ServiceType serviceType,
            final long requestStartTime,
            final String path) {

        return requestSpec
                .body(requestBuilder.createRequestBody(transformedRequest))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    logger.error("下游服务5xx错误: instance={}, path={}, status={}",
                            instanceName, path, clientResponse.statusCode());
                    return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                })
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    if (clientResponse.statusCode().value() == 401) {
                        logger.error("下游服务认证失败 (401): instance={}, path={}", instanceName, path);
                        return Mono.error(new DownstreamServiceException(
                                "下游服务认证失败，请检查下游服务的认证配置",
                                HttpStatus.valueOf(401)));
                    }
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new DownstreamServiceException(
                                    "下游服务错误: " + errorBody,
                                    HttpStatus.valueOf(clientResponse.statusCode().value()))));
                })
                .bodyToMono(String.class)
                .map(responseBody -> ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(responseBody))
                .doOnSuccess(responseEntity -> recordSuccessMetrics(adapterType, instanceName, serviceType, requestStartTime))
                .doOnError(throwable -> recordErrorMetrics(adapterType, instanceName, requestStartTime));
    }

    private void handleClientError(final HttpStatusCode statusCode, final String instanceName, final String path) {
        if (statusCode.value() == 401) {
            logger.error("下游服务认证失败 (401): instance={}, path={}", instanceName, path);
        } else if (statusCode.value() == 400) {
            logger.error("下游服务请求错误 (400): instance={}, path={}", instanceName, path);
        }
    }

    private ResponseEntity<byte[]> buildResponseEntity(final ClientResponse clientResponse, final byte[] body) {
        ResponseEntity.BodyBuilder responseBuilder = clientResponse.statusCode().is2xxSuccessful()
                ? ResponseEntity.ok()
                : ResponseEntity.status(clientResponse.statusCode());

        HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();
        if (downstreamHeaders.getContentType() != null) {
            responseBuilder.contentType(downstreamHeaders.getContentType());
        }
        if (downstreamHeaders.getContentLength() > 0) {
            responseBuilder.contentLength(downstreamHeaders.getContentLength());
        }

        return responseBuilder.body(body);
    }

    private void recordSuccessMetrics(final String adapterType, final String instanceName,
                                       final ServiceType serviceType, final long requestStartTime) {
        if (metricsCollector != null) {
            long responseTime = System.currentTimeMillis() - requestStartTime;
            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, true);
            metricsCollector.recordRequest(serviceType.name(), "POST", responseTime, "200");
        }
    }

    private void recordErrorMetrics(final String adapterType, final String instanceName, final long requestStartTime) {
        if (metricsCollector != null) {
            long responseTime = System.currentTimeMillis() - requestStartTime;
            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, false);
        }
    }
}
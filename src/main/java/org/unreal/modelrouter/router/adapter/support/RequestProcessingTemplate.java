package org.unreal.modelrouter.router.adapter.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

/**
 * 请求处理模板 - 处理非流式和流式请求
 *
 * <p>下游调用的通用模板：转发请求、把下游状态码映射成平台异常、把下游响应用 RouterResponse 包装。
 * <p><b>Deprecated / zero consumers:</b> nothing outside this file references it &mdash; the main + test
 * reference count is 0 (scan on 2026-10-20).
 *
 * <p><b>What it used to do:</b> it was the generic downstream call template &mdash; POST the transformed
 * body with the instance's {@code Authorization} header, map 5xx to {@code ResponseStatusException}, map
 * 401 / 400 / 503 to {@code DownstreamServiceException} with user-facing messages, then wrap the parsed
 * downstream JSON in {@code RouterResponse.success(data, "请求成功")}. That role moved to
 * {@code NonStreamingRequestProcessor} (v2.26.6), which adds response transformation, multipart, metrics,
 * call history and the v3.1 {@code /v1} native-JSON passthrough this template never had. Note that the
 * old javadoc promised streaming support the class never implemented.
 *
 * <p><b>Before deleting:</b> no bean and no controller references it; a full compile plus the adapter
 * request-processing tests is enough.
 *
 * @deprecated No caller remains in main or test sources (scan 2026-10-20); kept only until the dead-code
 *             sweep deletes it.
 */
@Slf4j
@Deprecated(forRemoval = true)
public class RequestProcessingTemplate {

    private final ObjectMapper objectMapper;
    private final MetricsSupport metricsSupport;

    public RequestProcessingTemplate(final ObjectMapper objectMapper, final MetricsSupport metricsSupport) {
        this.objectMapper = objectMapper;
        this.metricsSupport = metricsSupport;
    }

    /**
     * 处理非流式请求
     */
    public <T> Mono<ResponseEntity<?>> processNonStreamingRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final Class<?> responseType) {

        String instanceName = selectedInstance.getName();
        long requestStartTime = System.currentTimeMillis();

        String finalAuth = authorization;

        return client.post()
                .uri(path)
                .header("Authorization", finalAuth)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    log.error("下游服务 5xx 错误：instance={}, path={}, status={}",
                            instanceName, path, clientResponse.statusCode());
                    return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                })
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    return handleClientError(instanceName, path, clientResponse);
                })
                .toEntity(String.class)
                .flatMap(responseEntity -> processSuccessfulResponse(responseEntity));
    }

    private Mono<ResponseStatusException> handleClientError(
            final String instanceName, final String path,
            final org.springframework.web.reactive.function.client.ClientResponse clientResponse) {

        int statusCode = clientResponse.statusCode().value();

        return switch (statusCode) {
            case 401 -> {
                log.error("下游服务认证失败 (401): instance={}, path={}", instanceName, path);
                yield Mono.error(new DownstreamServiceException(
                        "下游服务认证失败，请检查下游服务的认证配置",
                        HttpStatus.UNAUTHORIZED));
            }
            case 400 -> {
                log.error("下游服务请求错误 (400): instance={}, path={}", instanceName, path);
                yield Mono.error(new DownstreamServiceException(
                        "下游服务请求参数错误，请检查请求内容",
                        HttpStatus.BAD_REQUEST));
            }
            case 503 -> {
                log.error("下游服务不可用 (503): instance={}, path={}", instanceName, path);
                yield Mono.error(new DownstreamServiceException(
                        "下游服务暂时不可用，请稍后重试",
                        HttpStatus.SERVICE_UNAVAILABLE));
            }
            default -> {
                log.error("下游服务 4xx 错误：instance={}, path={}, status={}",
                        instanceName, path, clientResponse.statusCode());
                yield Mono.error(new ResponseStatusException(clientResponse.statusCode()));
            }
        };
    }

    private Mono<ResponseEntity<?>> processSuccessfulResponse(final ResponseEntity<String> responseEntity) {
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
                downstreamData = null;
            } else {
                downstreamData = objectMapper.readValue(bodyStr, Object.class);
            }

            RouterResponse<Object> finalResponse = RouterResponse.success(downstreamData, "请求成功");

            return Mono.just(
                    ResponseEntity.status(responseEntity.getStatusCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(finalResponse)
            );
        } catch (JsonProcessingException e) {
            log.error("无法解析下游服务的响应体：{}", responseEntity.getBody(), e);
            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法解析下游服务响应"));
        }
    }
}

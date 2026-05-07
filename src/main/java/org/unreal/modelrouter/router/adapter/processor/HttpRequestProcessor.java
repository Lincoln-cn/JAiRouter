package org.unreal.modelrouter.router.adapter.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 请求处理器
 *
 * 负责处理 HTTP 请求的构建和发送，包括非流式和流式请求。
 *
 * @author JAiRouter Team
 * @since v2.3.1.1
 */
@Component
public class HttpRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestProcessor.class);

    /**
     * 发送非流式请求
     *
     * @param client WebClient 客户端
     * @param path 请求路径
     * @param authorization 授权头
     * @param requestBody 请求体
     * @return 客户端响应
     */
    public Mono<ClientResponse> sendNonStreaming(
            final WebClient client,
            final String path,
            final String authorization,
            final Object requestBody) {

        logger.debug("发送非流式请求：path={}", path);

        return client.post()
                .uri(path)
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchangeToMono(Mono::just);
    }

    /**
     * 发送流式请求
     *
     * @param client WebClient 客户端
     * @param path 请求路径
     * @param authorization 授权头
     * @param requestBody 请求体
     * @return SSE 数据流
     */
    public Flux<DataBuffer> sendStreaming(
            final WebClient client,
            final String path,
            final String authorization,
            final Object requestBody) {

        logger.debug("发送流式请求：path={}", path);

        return client.post()
                .uri(path)
                .header("Authorization", authorization)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class);
    }

    /**
     * 配置请求头
     *
     * @param requestSpec 请求规范
     * @param headers 额外头信息
     * @return 配置后的请求规范
     */
    public WebClient.RequestBodySpec configureHeaders(
            final WebClient.RequestBodySpec requestSpec,
            final Map<String, String> headers) {

        if (headers == null || headers.isEmpty()) {
            return requestSpec;
        }

        WebClient.RequestBodySpec specWithHeaders = requestSpec;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            specWithHeaders = specWithHeaders.header(entry.getKey(), entry.getValue());
        }

        return specWithHeaders;
    }

    /**
     * 处理响应错误
     *
     * @param clientResponse 客户端响应
     * @param instanceName 实例名称
     * @param path 请求路径
     * @return 错误 Mono
     */
    public Mono<Throwable> handleResponseError(
            final ClientResponse clientResponse,
            final String instanceName,
            final String path) {

        HttpStatus status = HttpStatus.valueOf(clientResponse.statusCode().value());

        return clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    if (status.value() == 401) {
                        logger.error("下游服务认证失败 (401): instance={}, path={}, response={}",
                                instanceName, path, status);
                    } else if (status.value() == 400) {
                        logger.error("下游服务请求错误 (400): instance={}, path={}, response={}",
                                instanceName, path, status);
                    } else if (status.value() == 503) {
                        logger.error("下游服务不可用 (503): instance={}, path={}, response={}",
                                instanceName, path, status);
                    } else {
                        logger.error("下游服务错误：instance={}, path={}, status={}",
                                instanceName, path, status);
                    }

                    return Mono.just((Throwable) new ResponseStatusException(status, errorBody));
                })
                .defaultIfEmpty(new ResponseStatusException(status, "未知错误"));
    }

    /**
     * 检查响应状态
     *
     * @param clientResponse 客户端响应
     * @return 是否成功
     */
    public boolean isSuccess(final ClientResponse clientResponse) {
        return clientResponse.statusCode().is2xxSuccessful();
    }

    /**
     * 检查是否是服务器错误
     *
     * @param clientResponse 客户端响应
     * @return 是否是服务器错误
     */
    public boolean isServerError(final ClientResponse clientResponse) {
        return clientResponse.statusCode().is5xxServerError();
    }

    /**
     * 检查是否是客户端错误
     *
     * @param clientResponse 客户端响应
     * @return 是否是客户端错误
     */
    public boolean isClientError(final ClientResponse clientResponse) {
        return clientResponse.statusCode().is4xxClientError();
    }
}

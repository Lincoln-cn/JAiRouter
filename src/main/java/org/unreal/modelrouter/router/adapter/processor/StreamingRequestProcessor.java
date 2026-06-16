package org.unreal.modelrouter.router.adapter.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 流式请求处理器
 * 负责 SSE (Server-Sent Events) 格式的流式请求处理
 *
 * @since v2.15.0
 */
@Component
public class StreamingRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StreamingRequestProcessor.class);

    private final ResponseTransformer responseTransformer;

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    public StreamingRequestProcessor(final ResponseTransformer responseTransformer) {
        this.responseTransformer = responseTransformer;
    }

    /**
     * 处理流式请求
     *
     * @param request            请求对象
     * @param authorization      授权信息
     * @param client             WebClient实例
     * @param path               请求路径
     * @param selectedInstance   选中的实例
     * @param serviceType        服务类型
     * @param adapterType        适配器类型
     * @param transformChunkFn   数据块转换函数（可选）
     * @return 流式响应 ResponseEntity
     */
    public <T> Mono<? extends org.springframework.http.ResponseEntity<?>> processStreamingRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String adapterType,
            final Function<String, String> transformChunkFn) {

        String instanceName = selectedInstance.getName();
        long requestStartTime = System.currentTimeMillis();

        logger.debug("开始流式请求: adapter={}, instance={}, path={}", adapterType, instanceName, path);

        // 使用 ServerSentEvent 包装每个数据块，确保 SSE 格式正确
        Flux<ServerSentEvent<String>> streamResponse = client.post()
                .uri(path)
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError, clientResponse -> {
                    logger.error("流式请求5xx错误: instance={}, status={}", instanceName, clientResponse.statusCode());
                    return Mono.error(new org.springframework.web.server.ResponseStatusException(
                            clientResponse.statusCode(), "下游服务错误"));
                })
                .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError, clientResponse -> {
                    logger.error("流式请求4xx错误: instance={}, status={}", instanceName, clientResponse.statusCode());
                    return Mono.error(new org.springframework.web.server.ResponseStatusException(
                            clientResponse.statusCode(), "请求错误"));
                })
                .bodyToFlux(String.class)
                .map(chunk -> transformAndWrapChunk(chunk, transformChunkFn))
                .doOnComplete(() -> recordStreamingComplete(serviceType, adapterType, instanceName, requestStartTime))
                .doOnError(throwable -> recordStreamingError(serviceType, adapterType, instanceName,
                        requestStartTime, throwable))
                .onErrorResume(throwable -> Flux.error(throwable));

        return Mono.just(org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(streamResponse));
    }

    /**
     * 转换并包装数据块为 SSE 格式
     */
    private ServerSentEvent<String> transformAndWrapChunk(final String chunk,
                                                            final Function<String, String> transformFn) {
        String transformed;
        if (transformFn != null) {
            transformed = transformFn.apply(chunk);
        } else {
            transformed = responseTransformer.transformStreamChunk(chunk);
        }

        return ServerSentEvent.<String>builder()
                .data(transformed)
                .build();
    }

    /**
     * 记录流式请求完成指标
     */
    private void recordStreamingComplete(final ModelServiceRegistry.ServiceType serviceType,
                                          final String adapterType,
                                          final String instanceName,
                                          final long startTime) {
        if (metricsCollector != null) {
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(serviceType.name(), "STREAM", responseTime, "200");
            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, true);
            logger.debug("流式请求完成: adapter={}, instance={}, duration={}ms",
                    adapterType, instanceName, responseTime);
        }
    }

    /**
     * 记录流式请求错误指标
     */
    private void recordStreamingError(final ModelServiceRegistry.ServiceType serviceType,
                                        final String adapterType,
                                        final String instanceName,
                                        final long startTime,
                                        final Throwable throwable) {
        if (metricsCollector != null) {
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, false);
            logger.error("流式请求错误: adapter={}, instance={}, error={}",
                    adapterType, instanceName, throwable.getMessage());
        }
    }

    /**
     * 获取默认的数据块转换器
     */
    public Function<String, String> getDefaultChunkTransformer(final String adapterType) {
        return chunk -> responseTransformer.transformStreamChunk(chunk);
    }

    /**
     * 处理流式请求（使用默认转换器）
     */
    public <T> Mono<? extends org.springframework.http.ResponseEntity<?>> processStreamingRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String adapterType) {

        return processStreamingRequest(request, authorization, client, path,
                selectedInstance, serviceType, adapterType, null);
    }
}
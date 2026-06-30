package org.unreal.modelrouter.router.adapter.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    
    // Token 估算系数
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;
    private static final double CHINESE_CHARS_PER_TOKEN = 2.0;

    private final ResponseTransformer responseTransformer;

    @Autowired(required = false)
    private MetricsCollector metricsCollector;
    
    @Autowired(required = false)
    private TokenUsageRecorder tokenUsageRecorder;

    @Autowired(required = false)
    private ApiKeyService apiKeyService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            final Function<String, String> transformChunkFn,
            final ServerHttpRequest httpRequest) {

        final String capturedKeyId = captureApiKeyId(httpRequest);

        String instanceName = selectedInstance.getName();
        long requestStartTime = System.currentTimeMillis();

        logger.debug("开始流式请求: adapter={}, instance={}, path={}", adapterType, instanceName, path);

        // Token 使用量追踪
        AtomicLong promptTokens = new AtomicLong(0);
        AtomicLong completionTokens = new AtomicLong(0);
        AtomicLong totalTokens = new AtomicLong(0);
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<String> modelRef = new AtomicReference<>("unknown");

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
                .map(chunk -> {
                    // 提取 usage 信息和累积内容
                    extractUsageAndContent(chunk, promptTokens, completionTokens, totalTokens,
                            contentBuilder, modelRef);
                    return transformAndWrapChunk(chunk, transformChunkFn);
                })
                .doOnComplete(() -> {
                    recordStreamingComplete(serviceType, adapterType, instanceName, requestStartTime);
                    // 记录 token 使用量
                    recordTokenUsage(adapterType, instanceName, modelRef.get(),
                            promptTokens.get(), completionTokens.get(), totalTokens.get(),
                            contentBuilder.toString(), capturedKeyId);
                })
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
            final String adapterType,
            final ServerHttpRequest httpRequest) {

        return processStreamingRequest(request, authorization, client, path,
                selectedInstance, serviceType, adapterType, null, httpRequest);
    }

    /**
     * 从 SSE chunk 提取 usage 信息和累积响应内容
     *
     * @param chunk              SSE 数据块
     * @param promptTokens       prompt tokens 计数器
     * @param completionTokens   completion tokens 计数器
     * @param totalTokens        total tokens 计数器
     * @param contentBuilder     内容累积器
     * @param modelRef           模型名称引用
     */
    private void extractUsageAndContent(final String chunk,
                                         final AtomicLong promptTokens,
                                         final AtomicLong completionTokens,
                                         final AtomicLong totalTokens,
                                         final StringBuilder contentBuilder,
                                         final AtomicReference<String> modelRef) {
        try {
            String jsonPart = chunk;
            if (chunk.startsWith("data: ")) {
                jsonPart = chunk.substring(6);
            }

            if ("[DONE]".equals(jsonPart.trim())) {
                return;
            }

            JsonNode jsonNode = objectMapper.readTree(jsonPart);

            // 提取模型名称
            if (jsonNode.has("model")) {
                modelRef.set(jsonNode.get("model").asText());
            }

            // 提取 usage 信息（如果后端提供）
            if (jsonNode.has("usage")) {
                JsonNode usage = jsonNode.get("usage");
                if (usage.has("prompt_tokens")) {
                    promptTokens.set(usage.get("prompt_tokens").asLong());
                }
                if (usage.has("completion_tokens")) {
                    completionTokens.set(usage.get("completion_tokens").asLong());
                }
                if (usage.has("total_tokens")) {
                    totalTokens.set(usage.get("total_tokens").asLong());
                }
            }

            // 累积响应内容（从 choices 中提取）
            if (jsonNode.has("choices")) {
                JsonNode choices = jsonNode.get("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode delta = choices.get(0).path("delta");
                    if (delta.has("content")) {
                        contentBuilder.append(delta.get("content").asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to parse chunk for usage extraction: {}", e.getMessage());
        }
    }

    /**
     * 记录 Token 使用量
     * 如果后端未提供 usage 信息，则根据累积的内容进行估算
     */
    private void recordTokenUsage(final String adapterType,
                                   final String instanceName,
                                   final String model,
                                   final long promptTokens,
                                   final long completionTokens,
                                   final long totalTokens,
                                   final String content,
                                   final String apiKeyId) {
        if (tokenUsageRecorder == null) {
            return;
        }

        long finalPromptTokens = promptTokens;
        long finalCompletionTokens = completionTokens;
        long finalTotalTokens = totalTokens;

        // 如果后端未返回 usage，则估算
        if (totalTokens == 0 && content.length() > 0) {
            finalCompletionTokens = estimateTokens(content);
            finalTotalTokens = finalPromptTokens + finalCompletionTokens;
            logger.debug("Token usage estimated: adapter={}, instance={}, prompt={}, completion={}, total={}",
                    adapterType, instanceName, finalPromptTokens, finalCompletionTokens, finalTotalTokens);
        } else if (totalTokens > 0) {
            logger.debug("Token usage from backend: adapter={}, instance={}, prompt={}, completion={}, total={}",
                    adapterType, instanceName, finalPromptTokens, finalCompletionTokens, finalTotalTokens);
        }

        // 只有当有实际 token 使用量时才记录
        if (finalTotalTokens > 0) {
            try {
                // 获取 traceId
                String traceId = TracingContextHolder.getCurrentTraceId();

                tokenUsageRecorder.recordTokenUsageNoAuth(
                        "CHAT",
                        model,
                        adapterType,
                        instanceName,
                        null, // instanceUrl
                        finalPromptTokens,
                        finalCompletionTokens,
                        finalTotalTokens,
                        traceId,
                        null, // clientIp
                        true, // isSuccess
                        null, // errorCode
                        null, // errorMessage
                        null  // responseTimeMs
                );

                // 更新 API Key 的每日 Token 使用量配额
                updateApiKeyTokenUsage(apiKeyId, finalTotalTokens);

            } catch (Exception e) {
                logger.warn("Failed to record token usage: {}", e.getMessage());
            }
        }
    }

    /**
     * 更新 API Key 的 Token 使用量
     */
    private void updateApiKeyTokenUsage(final String apiKeyId, final long totalTokens) {
        if (apiKeyService == null || totalTokens <= 0 || apiKeyId == null) {
            return;
        }
        try {
            apiKeyService.updateTokenUsage(apiKeyId, totalTokens);
            logger.debug("API Key token usage updated: keyId={}, tokens={}", apiKeyId, totalTokens);
        } catch (Exception e) {
            logger.debug("Failed to update API Key token usage: keyId={}, error={}", apiKeyId, e.getMessage());
        }
    }

    /**
     * 从请求属性中捕获 API Key ID
     * 使用 ServiceRequestHandler 预存的 keyId（与 NonStreamingRequestProcessor 一致）
     */
    private String captureApiKeyId(final ServerHttpRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        try {
            Object keyId = httpRequest.getAttributes()
                    .get(org.unreal.modelrouter.router.handler.ServiceRequestHandler.API_KEY_ID_ATTRIBUTE);
            if (keyId instanceof String key && !key.isBlank()) {
                return key;
            }
        } catch (Exception e) {
            logger.debug("Failed to capture API Key ID from request attributes: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 根据内容估算 token 数量
     * 英文约 4 字符/token，中文约 2 字符/token
     */
    private long estimateTokens(final String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int otherChars = 0;

        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }

        return (long) Math.ceil(chineseChars / CHINESE_CHARS_PER_TOKEN
                + otherChars / ENGLISH_CHARS_PER_TOKEN);
    }
}
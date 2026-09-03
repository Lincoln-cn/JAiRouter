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
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.monitor.callhistory.ApiCallHistoryRecorder;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.cache.CachedStreamingResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.pool.PoolSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // v2.8.9: 资源池选择器(池路由后改写下游流式请求的 model 字段为实际实例名)
    @Autowired(required = false)
    private PoolSelector poolSelector;

    // v2.9.2: 记录治理
    @Autowired(required = false)
    private ApiCallHistoryRecorder callHistoryRecorder;

    @Autowired(required = false)
    private CallHistoryProperties callHistoryProperties;

    @Autowired(required = false)
    private SanitizationService sanitizationService;

    // v2.9.10: 响应缓存门面（可选注入，未装配时跳过流式缓存写）
    @Autowired(required = false)
    private ResponseCacheService responseCacheService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StreamingRequestProcessor(final ResponseTransformer responseTransformer) {
        this.responseTransformer = responseTransformer;
    }

    /**
     * v2.8.9: 池路由后,将请求 Map 的 model 字段改写为实际实例名(仅池名触发)
     */
    @SuppressWarnings("unchecked")
    private Object rewriteModelField(final Object target, final String instanceName) {
        if (target instanceof Map && instanceName != null && poolSelector != null) {
            Object model = ((Map<String, Object>) target).get("model");
            if (model instanceof String && poolSelector.isPoolName((String) model)
                    && !model.equals(instanceName)) {
                ((Map<String, Object>) target).put("model", instanceName);
            }
        }
        return target;
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

        // v2.9.10: 流式缓存写 — 读取 handler 预存的缓存键（skipStreaming=false 且确定性请求时非空）
        final String cacheKeyForWrite;
        if (httpRequest != null && responseCacheService != null) {
            Object attr = httpRequest.getAttributes()
                    .get(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE);
            cacheKeyForWrite = (attr instanceof String key && !key.isBlank()) ? key : null;
        } else {
            cacheKeyForWrite = null;
        }

        // v2.9.2: 记录治理 - 在入口处序列化请求体
        final String capturedRequestBody;
        RecordLevel recordLevel = resolveRecordLevel();
        if (recordLevel != RecordLevel.METADATA_ONLY) {
            capturedRequestBody = serializeAndTruncate(request);
        } else {
            capturedRequestBody = null;
        }

        // Token 使用量追踪
        AtomicLong promptTokens = new AtomicLong(0);
        AtomicLong completionTokens = new AtomicLong(0);
        AtomicLong totalTokens = new AtomicLong(0);
        // v2.9.0: KV 缓存命中/未命中 token 追踪
        AtomicLong cacheHitTokens = new AtomicLong(0);
        AtomicLong cacheMissTokens = new AtomicLong(0);
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<String> modelRef = new AtomicReference<>("unknown");

        // v2.9.10: 流式缓存写 — 仅缓存键存在时收集变换后块与 finish_reason
        final List<String> transformedChunks = (cacheKeyForWrite != null) ? new ArrayList<>() : null;
        final AtomicReference<String> finishReasonRef = (cacheKeyForWrite != null)
                ? new AtomicReference<>(null) : null;

        // 使用 ServerSentEvent 包装每个数据块，确保 SSE 格式正确
        Flux<ServerSentEvent<String>> streamResponse = client.post()
                .uri(path)
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .bodyValue(rewriteModelField(request, instanceName))
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
                            cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);
                    // v2.9.10: 提取 finish_reason 用于流式缓存
                    if (cacheKeyForWrite != null) {
                        extractFinishReason(chunk, finishReasonRef);
                    }
                    ServerSentEvent<String> sse = transformAndWrapChunk(chunk, transformChunkFn);
                    // v2.9.10: 收集变换后 data 串用于流式缓存
                    if (transformedChunks != null) {
                        transformedChunks.add(sse.data());
                    }
                    return sse;
                })
                .doOnComplete(() -> {
                    recordStreamingComplete(serviceType, adapterType, instanceName, requestStartTime);

                    // v2.9.10: 流式缓存写 — 完整成功流结束后缓存（错误/中断不触发 doOnComplete）
                    if (cacheKeyForWrite != null && responseCacheService != null
                            && transformedChunks != null && !transformedChunks.isEmpty()) {
                        cacheStreamingResponse(cacheKeyForWrite, new CachedStreamingResponse(
                                List.copyOf(transformedChunks),
                                modelRef.get(),
                                promptTokens.get() > 0 ? promptTokens.get() : null,
                                completionTokens.get() > 0 ? completionTokens.get() : null,
                                totalTokens.get() > 0 ? totalTokens.get() : null,
                                finishReasonRef != null ? finishReasonRef.get() : null));
                    }

                    // 记录 token 使用量(含 KV 缓存指标)
                    recordTokenUsage(adapterType, instanceName, modelRef.get(),
                            promptTokens.get(), completionTokens.get(), totalTokens.get(),
                            cacheHitTokens.get(), cacheMissTokens.get(),
                            contentBuilder.toString(), capturedKeyId);

                    // v2.9.2: 记录治理 - 记录含请求/响应体的调用历史
                    if (recordLevel != RecordLevel.METADATA_ONLY && callHistoryRecorder != null) {
                        long duration = System.currentTimeMillis() - requestStartTime;
                        String rawResponseBody = truncate(contentBuilder.toString());
                        String responseBodyForRecord = rawResponseBody;

                        // SUMMARY 级别：对响应体进行脱敏处理
                        if (recordLevel == RecordLevel.SUMMARY && sanitizationService != null && rawResponseBody != null) {
                            try {
                                String sanitized = sanitizationService.sanitizeResponse(
                                        rawResponseBody, "application/json")
                                        .block(java.time.Duration.ofSeconds(5));
                                if (sanitized != null) {
                                    responseBodyForRecord = sanitized;
                                }
                            } catch (Exception e) {
                                logger.debug("流式响应体脱敏失败: {}", e.getMessage());
                            }
                        }

                        try {
                            CallHistoryRecordDTO dto = CallHistoryRecordDTO.builder()
                                    .serviceType(serviceType != null ? serviceType.name() : null)
                                    .modelName(modelRef.get())
                                    .provider(adapterType)
                                    .instanceName(instanceName)
                                    .instanceUrl(selectedInstance.getBaseUrl())
                                    .responseTimeMs(duration)
                                    .isSuccess(true)
                                    .promptTokens(promptTokens.get() > 0 ? promptTokens.get() : null)
                                    .completionTokens(completionTokens.get() > 0 ? completionTokens.get() : null)
                                    .totalTokens(totalTokens.get() > 0 ? totalTokens.get() : null)
                                    .apiKeyId(capturedKeyId)
                                    .requestBody(capturedRequestBody)
                                    .responseBody(responseBodyForRecord)
                                    .build();
                            callHistoryRecorder.record(dto);
                        } catch (Exception e) {
                            logger.debug("流式调用历史记录失败: {}", e.getMessage());
                        }
                    }
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
     * @param cacheHitTokens     v2.9.0: KV 缓存命中 token 计数器
     * @param cacheMissTokens    v2.9.0: KV 缓存未命中 token 计数器
     * @param contentBuilder     内容累积器
     * @param modelRef           模型名称引用
     */
    private void extractUsageAndContent(final String chunk,
                                         final AtomicLong promptTokens,
                                         final AtomicLong completionTokens,
                                         final AtomicLong totalTokens,
                                         final AtomicLong cacheHitTokens,
                                         final AtomicLong cacheMissTokens,
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
                // v2.9.0: 提取 KV 缓存命中/未命中 token 数
                // DeepSeek 形态
                if (usage.has("prompt_cache_hit_tokens")) {
                    cacheHitTokens.set(usage.get("prompt_cache_hit_tokens").asLong());
                }
                if (usage.has("prompt_cache_miss_tokens")) {
                    cacheMissTokens.set(usage.get("prompt_cache_miss_tokens").asLong());
                }
                // OpenAI/vLLM 形态: prompt_tokens_details.cached_tokens
                if (cacheHitTokens.get() == 0 && usage.has("prompt_tokens_details")) {
                    JsonNode details = usage.get("prompt_tokens_details");
                    if (details.has("cached_tokens")) {
                        cacheHitTokens.set(details.get("cached_tokens").asLong());
                    }
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
                                   final long cacheHitTokens,
                                   final long cacheMissTokens,
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

                // v2.9.0: 记录 KV 缓存命中/未命中指标
                if (metricsCollector != null && (cacheHitTokens > 0 || cacheMissTokens > 0)) {
                    metricsCollector.recordCacheTokenUsage(adapterType, instanceName,
                            cacheHitTokens, cacheMissTokens);
                }

            } catch (Exception e) {
                logger.warn("Failed to record token usage: {}", e.getMessage());
            }
        }

        // v2.9.0: 即使 totalTokens==0，如果有缓存命中信息也记录指标
        if (finalTotalTokens == 0 && metricsCollector != null && (cacheHitTokens > 0 || cacheMissTokens > 0)) {
            metricsCollector.recordCacheTokenUsage(adapterType, instanceName,
                    cacheHitTokens, cacheMissTokens);
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

    /**
     * v2.9.10: 从 SSE 块提取 finish_reason（用于流式缓存）.
     *
     * <p>与 {@code extractUsageAndContent} 独立，避免修改其签名破坏既有反射测试。
     *
     * @param chunk           原始 SSE 块（可能含 {@code data: } 前缀）
     * @param finishReasonRef finish_reason 累积引用（最后非 null 值生效）
     */
    private void extractFinishReason(final String chunk, final AtomicReference<String> finishReasonRef) {
        try {
            String jsonPart = chunk;
            if (chunk.startsWith("data: ")) {
                jsonPart = chunk.substring(6);
            }
            if ("[DONE]".equals(jsonPart.trim())) {
                return;
            }
            JsonNode jsonNode = objectMapper.readTree(jsonPart);
            if (jsonNode.has("choices")) {
                JsonNode choices = jsonNode.get("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode choice = choices.get(0);
                    if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                        finishReasonRef.set(choice.get("finish_reason").asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to extract finish_reason from chunk: {}", e.getMessage());
        }
    }

    /**
     * v2.9.10: 写入流式响应缓存.
     *
     * <p>仅在完整成功流（doOnComplete 正常触发）时调用；
     * 键为空或数据不完整时不写。
     *
     * @param cacheKey         缓存键
     * @param cachedResponse   流式缓存值
     */
    void cacheStreamingResponse(final String cacheKey, final CachedStreamingResponse cachedResponse) {
        if (cacheKey == null || cachedResponse == null || responseCacheService == null) {
            return;
        }
        responseCacheService.store(cacheKey, cachedResponse);
    }

    // ==================== v2.9.2: 记录治理辅助方法 ====================

    /**
     * 解析当前记录级别（默认 METADATA_ONLY）
     */
    private RecordLevel resolveRecordLevel() {
        if (callHistoryProperties != null) {
            return callHistoryProperties.getRecordLevel();
        }
        return RecordLevel.METADATA_ONLY;
    }

    /**
     * 将对象序列化为 JSON 字符串并截断到 maxContentLength
     */
    private String serializeAndTruncate(final Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            return truncate(json);
        } catch (Exception e) {
            logger.debug("序列化请求体失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 截断字符串到 maxContentLength
     */
    private String truncate(final String content) {
        if (content == null) {
            return null;
        }
        int maxLen = callHistoryProperties != null ? callHistoryProperties.getMaxContentLength() : 65536;
        if (content.length() > maxLen) {
            return content.substring(0, maxLen);
        }
        return content;
    }
}
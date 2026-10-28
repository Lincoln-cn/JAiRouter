package org.unreal.modelrouter.router.adapter.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.quota.QuotaReservation;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.pool.PoolSelector;

import java.util.Map;

/**
 * Token 使用量提取与记录辅助类
 *
 * 从 NonStreamingRequestProcessor 提取的 token 使用量相关方法，包括：
 * - 从响应中提取 token 使用量并记录
 * - 更新 API Key 的 token 使用量配额
 * - 从请求中提取 API Key ID
 * - 提取原始请求的 model 字段
 * - 池路由后改写 model 字段
 *
 * @since v2.26.6
 */
@Component
public class TokenUsageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(TokenUsageExtractor.class);

    /**
     * 英文/其它字符每 token 折算的字符数（与 StreamingRequestProcessor 的估算口径一致）。
     */
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;

    /**
     * 汉字每 token 折算的字符数（与 StreamingRequestProcessor 的估算口径一致）。
     */
    private static final double CHINESE_CHARS_PER_TOKEN = 2.0;

    private final ObjectMapper objectMapper;

    private final TokenUsageRecorder tokenUsageRecorder;

    private final ApiKeyService apiKeyService;

    private final PoolSelector poolSelector;

    private final MetricsCollector metricsCollector;

    public TokenUsageExtractor(
            final ObjectMapper objectMapper,
            final TokenUsageRecorder tokenUsageRecorder,
            final ApiKeyService apiKeyService,
            final PoolSelector poolSelector,
            final MetricsCollector metricsCollector) {
        this.objectMapper = objectMapper;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.apiKeyService = apiKeyService;
        this.poolSelector = poolSelector;
        this.metricsCollector = metricsCollector;
    }

    /**
     * 从下游服务响应中提取 token 使用量并记录（不带配额结算，保留既有签名）。
     *
     * @param bodyStr      下游响应体
     * @param adapterType  适配器类型
     * @param instanceName 实例名
     * @param apiKeyId     API Key ID
     */
    void extractAndRecordTokenUsage(final String bodyStr,
                                              final String adapterType,
                                              final String instanceName,
                                              final String apiKeyId) {
        extractAndRecordTokenUsage(bodyStr, adapterType, instanceName, apiKeyId, null);
    }

    /**
     * 从下游服务响应中提取 token 使用量并记录，同时完成配额结算（v3.1 PR-2）。
     *
     * <p>配额结算发生在“实际 token 落库点”：按 {@code 实际 − 估算} 冲正预留
     * （{@link QuotaReservation#settleSuccess(QuotaReservation, long)}）。响应不含 usage 或
     * 总量为 0 时本方法提前返回，预留保持原估算值（记账口径退化为“预留即计费”），
     * 不会被错误冲正为 0。</p>
     *
     * @param bodyStr        下游响应体
     * @param adapterType    适配器类型
     * @param instanceName   实例名
     * @param apiKeyId       API Key ID
     * @param quotaReservation 配额预留凭据，{@code null}（未预扣 / 账本未启用）时空操作
     */
    void extractAndRecordTokenUsage(final String bodyStr,
                                              final String adapterType,
                                              final String instanceName,
                                              final String apiKeyId,
                                              final QuotaReservation quotaReservation) {
        extractAndRecordTokenUsage(bodyStr, null, adapterType, instanceName, apiKeyId, quotaReservation);
    }

    /**
     * 从下游服务响应中提取 token 使用量并记录（带配额结算）。
     *
     * <p>#712：上游不返回 {@code usage} 时不再直接返回，而是按请求与响应文本估算用量，
     * 否则这类调用既不进 Token 统计也不结算配额（配额永远停在预估值）。</p>
     *
     * @param bodyStr          下游响应体
     * @param requestBodyStr   转发给下游的请求体，可为 null（仅用于估算 prompt）
     * @param adapterType      适配器类型
     * @param instanceName     实例名
     * @param apiKeyId         API Key ID
     * @param quotaReservation 配额预留，可为 null
     */
    void extractAndRecordTokenUsage(final String bodyStr,
                                              final String requestBodyStr,
                                              final String adapterType,
                                              final String instanceName,
                                              final String apiKeyId,
                                              final QuotaReservation quotaReservation) {
        try {
            final JsonNode jsonNode = objectMapper.readTree(bodyStr);

            long promptTokens = 0L;
            long completionTokens = 0L;
            long totalTokens = 0L;
            long cacheHitTokens = 0L;
            long cacheMissTokens = 0L;
            boolean estimated = false;

            final JsonNode usage = jsonNode.get("usage");
            if (usage != null) {
                promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : 0L;
                completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asLong() : 0L;
                totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asLong() : 0L;
                cacheHitTokens = extractCacheHitTokens(usage);
                cacheMissTokens = extractCacheMissTokens(usage);
            }

            if (totalTokens <= 0L) {
                // #712：上游没有 usage（自建推理服务、裁剪过的兼容层等）时按文本估算
                promptTokens = estimateTokens(extractRequestText(requestBodyStr));
                completionTokens = estimateTokens(extractResponseText(jsonNode));
                totalTokens = promptTokens + completionTokens;
                estimated = totalTokens > 0L;
                if (!estimated) {
                    logger.debug("No usage and no text to estimate: adapter={}, instance={}",
                            adapterType, instanceName);
                    return;
                }
            }

            // 如果只有 cacheHit 无 cacheMiss，估算 miss = promptTokens - cacheHit
            if (cacheHitTokens > 0L && cacheMissTokens == 0L) {
                cacheMissTokens = Math.max(0L, promptTokens - cacheHitTokens);
            }

            // 记录 KV 缓存指标
            if (metricsCollector != null && (cacheHitTokens > 0L || cacheMissTokens > 0L)) {
                metricsCollector.recordCacheTokenUsage(adapterType, instanceName,
                        cacheHitTokens, cacheMissTokens);
            }

            final String model = jsonNode.has("model") ? jsonNode.get("model").asText() : "unknown";
            recordUsage(model, adapterType, instanceName, apiKeyId,
                    promptTokens, completionTokens, totalTokens, quotaReservation, estimated);

        } catch (Exception e) {
            logger.debug("Failed to extract token usage from response: {}", e.getMessage());
        }
    }

    /**
     * 落库 + 更新 Key 的累计用量 + 冲正配额预留（恰一次）。
     */
    void recordUsage(final String model,
                     final String adapterType,
                     final String instanceName,
                     final String apiKeyId,
                     final long promptTokens,
                     final long completionTokens,
                     final long totalTokens,
                     final QuotaReservation quotaReservation,
                     final boolean estimated) {
        // 记录到 TokenUsage 表
        if (tokenUsageRecorder != null) {
            final String traceId = TracingContextHolder.getCurrentTraceId();
            tokenUsageRecorder.recordTokenUsageNoAuth(
                    "CHAT",
                    model,
                    adapterType,
                    instanceName,
                    null,
                    apiKeyId,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    traceId,
                    null,
                    true,
                    null,
                    null,
                    null
            );
        }

        // 更新 API Key 的每日 Token 使用量配额
        updateApiKeyTokenUsage(apiKeyId, totalTokens);

        // v3.1 PR-2: 配额结算 — 按实际用量冲正预留（恰一次）
        QuotaReservation.settleSuccess(quotaReservation, totalTokens);

        if (estimated) {
            logger.debug("Non-streaming token usage estimated: adapter={}, instance={}, model={}, prompt={}, "
                            + "completion={}, total={}",
                    adapterType, instanceName, model, promptTokens, completionTokens, totalTokens);
        } else {
            logger.debug("Non-streaming token usage recorded: adapter={}, instance={}, model={}, total={}",
                    adapterType, instanceName, model, totalTokens);
        }
    }

    /**
     * 提取 KV 缓存命中 token 数（DeepSeek 的 prompt_cache_hit_tokens / OpenAI 的 cached_tokens）。
     */
    private static long extractCacheHitTokens(final JsonNode usage) {
        if (usage.has("prompt_cache_hit_tokens")) {
            return usage.get("prompt_cache_hit_tokens").asLong();
        }
        if (usage.has("prompt_tokens_details")) {
            final JsonNode details = usage.get("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                return details.get("cached_tokens").asLong();
            }
        }
        return 0L;
    }

    /**
     * 提取 KV 缓存未命中 token 数。
     */
    private static long extractCacheMissTokens(final JsonNode usage) {
        return usage.has("prompt_cache_miss_tokens") ? usage.get("prompt_cache_miss_tokens").asLong() : 0L;
    }

    /**
     * 按字符类型估算 token 数（汉字 2 字符/token，其它非空白 4 字符/token）。
     */
    static long estimateTokens(final String content) {
        if (content == null || content.isEmpty()) {
            return 0L;
        }
        long chineseChars = 0L;
        long otherChars = 0L;
        for (int i = 0; i < content.length(); i++) {
            final char ch = content.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else if (!Character.isWhitespace(ch)) {
                otherChars++;
            }
        }
        return (long) Math.ceil(chineseChars / CHINESE_CHARS_PER_TOKEN + otherChars / ENGLISH_CHARS_PER_TOKEN);
    }

    /**
     * 从响应体里抽出可估算的文本（chat 的 choices[0].message.content / delta、completions 的 text 等）。
     */
    static String extractResponseText(final JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }
        final JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            final JsonNode firstChoice = choices.get(0);
            final JsonNode messageNode = firstChoice.get("message");
            if (messageNode != null) {
                final String fromMessage = textOf(messageNode.get("content"));
                if (fromMessage != null) {
                    return fromMessage;
                }
            }
            final JsonNode deltaNode = firstChoice.get("delta");
            if (deltaNode != null) {
                final String fromDelta = textOf(deltaNode.get("content"));
                if (fromDelta != null) {
                    return fromDelta;
                }
            }
            final String fromText = textOf(firstChoice.get("text"));
            if (fromText != null) {
                return fromText;
            }
        }
        final JsonNode outputText = jsonNode.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }
        return textOf(jsonNode.get("content"));
    }

    /**
     * 从请求体里抽出 messages 的文本内容，用于估算 prompt。
     */
    String extractRequestText(final String requestBodyStr) {
        if (requestBodyStr == null || requestBodyStr.isEmpty()) {
            return null;
        }
        try {
            final JsonNode requestNode = objectMapper.readTree(requestBodyStr);
            final JsonNode messages = requestNode.get("messages");
            if (messages == null || !messages.isArray()) {
                return null;
            }
            final StringBuilder text = new StringBuilder();
            for (final JsonNode message : messages) {
                final String content = textOf(message.get("content"));
                if (content != null) {
                    text.append(content).append(' ');
                }
            }
            return text.length() == 0 ? null : text.toString();
        } catch (JsonProcessingException e) {
            logger.debug("Cannot parse request body for token estimation: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 把 content / text 节点归一成字符串（支持字符串、{text: ...} 对象、以及数组形态）。
     */
    private static String textOf(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            final JsonNode text = node.get("text");
            return text != null && text.isTextual() ? text.asText() : null;
        }
        if (node.isArray()) {
            final StringBuilder text = new StringBuilder();
            for (final JsonNode item : node) {
                if (item.isTextual()) {
                    text.append(item.asText());
                } else if (item.isObject() && item.has("text")) {
                    text.append(item.get("text").asText());
                }
            }
            return text.length() == 0 ? null : text.toString();
        }
        return null;
    }

    /**
     * 更新 API Key 的 Token 使用量
     */
    void updateApiKeyTokenUsage(final String apiKeyId, final long totalTokens) {
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
     * 从 ServerHttpRequest 属性中获取 API Key ID
     */
    String extractKeyIdFromRequest(final ServerHttpRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        Object keyId = httpRequest.getAttributes().get(
                org.unreal.modelrouter.router.handler.ServiceRequestHandler.API_KEY_ID_ATTRIBUTE);
        if (keyId instanceof String) {
            return (String) keyId;
        }
        return null;
    }

    /**
     * v2.8.9: 提取原始请求的 model 字段(chat/embedding 形态)
     */
    String extractRequestModel(final Object request) {
        if (request instanceof ChatDTO.Request r) {
            return r.model();
        }
        if (request instanceof EmbeddingDTO.Request e) {
            return e.model();
        }
        return null;
    }

    /**
     * v2.8.9: 池路由后,将目标(Map)的 model 字段改写为实际实例名
     * 仅当原始请求 model 是池名/auto-model 时触发,不影响普通请求与规则行为
     */
    @SuppressWarnings("unchecked")
    void rewriteModelField(final Object target, final String requestedModel, final String instanceName) {
        if (target instanceof Map && instanceName != null && requestedModel != null
                && poolSelector != null && poolSelector.isPoolName(requestedModel)) {
            Object model = ((Map<String, Object>) target).get("model");
            if (model instanceof String && !model.equals(instanceName)) {
                ((Map<String, Object>) target).put("model", instanceName);
            }
        }
    }
}

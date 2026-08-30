package org.unreal.modelrouter.router.adapter.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
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
     * 从下游服务响应中提取 token 使用量并记录
     */
    void extractAndRecordTokenUsage(final String bodyStr,
                                              final String adapterType,
                                              final String instanceName,
                                              final String apiKeyId) {
        try {
            JsonNode jsonNode = objectMapper.readTree(bodyStr);

            if (!jsonNode.has("usage")) {
                return;
            }

            JsonNode usage = jsonNode.get("usage");
            long promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : 0;
            long completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asLong() : 0;
            long totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asLong() : 0;

            if (totalTokens <= 0) {
                return;
            }

            String model = jsonNode.has("model") ? jsonNode.get("model").asText() : "unknown";

            // v2.9.0: 提取 KV 缓存命中/未命中 token 数
            // DeepSeek 形态: prompt_cache_hit_tokens / prompt_cache_miss_tokens
            // OpenAI/vLLM 形态: prompt_tokens_details.cached_tokens
            long cacheHitTokens = 0;
            long cacheMissTokens = 0;
            if (usage.has("prompt_cache_hit_tokens")) {
                cacheHitTokens = usage.get("prompt_cache_hit_tokens").asLong();
            }
            if (usage.has("prompt_cache_miss_tokens")) {
                cacheMissTokens = usage.get("prompt_cache_miss_tokens").asLong();
            }
            // OpenAI 形态: prompt_tokens_details.cached_tokens → 视为 cacheHit
            if (cacheHitTokens == 0 && usage.has("prompt_tokens_details")) {
                JsonNode details = usage.get("prompt_tokens_details");
                if (details.has("cached_tokens")) {
                    cacheHitTokens = details.get("cached_tokens").asLong();
                }
            }
            // 如果只有 cacheHit 无 cacheMiss，估算 miss = promptTokens - cacheHit
            if (cacheHitTokens > 0 && cacheMissTokens == 0) {
                cacheMissTokens = Math.max(0, promptTokens - cacheHitTokens);
            }

            // 记录 KV 缓存指标
            if (metricsCollector != null && (cacheHitTokens > 0 || cacheMissTokens > 0)) {
                metricsCollector.recordCacheTokenUsage(adapterType, instanceName,
                        cacheHitTokens, cacheMissTokens);
            }

            // 记录到 TokenUsage 表
            if (tokenUsageRecorder != null) {
                String traceId = TracingContextHolder.getCurrentTraceId();
                tokenUsageRecorder.recordTokenUsageNoAuth(
                        "CHAT",
                        model,
                        adapterType,
                        instanceName,
                        null,
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

            logger.debug("Non-streaming token usage recorded: adapter={}, instance={}, model={}, total={}",
                    adapterType, instanceName, model, totalTokens);

        } catch (Exception e) {
            logger.debug("Failed to extract token usage from response: {}", e.getMessage());
        }
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

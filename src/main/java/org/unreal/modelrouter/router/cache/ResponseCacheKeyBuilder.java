package org.unreal.modelrouter.router.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * v2.9.9: 响应缓存键构建器（静态工具）.
 *
 * <p>按 serviceType 对请求体做规范化（稳定序列化：字段序固定、null 剔除、
 * 字符串空白归一 trim、嵌套 Map 字典序归一、列表保序），组装租户隔离键：
 *
 * <pre>
 * key = SHA-256(tenantKey | [user?] | serviceType | model | canonicalJson) 十六进制
 * </pre>
 *
 * <p>规范化字段范围（P0 三种服务）：
 * <ul>
 *   <li>CHAT：messages(逐条 role+content+name，列表稳定序) / stream / maxTokens /
 *       temperature / topP / topK / frequencyPenalty / presencePenalty / n / stop
 *       + options 白名单（排除 requestId/priority/prefixCacheHash/enablePrefixCaching 元数据；
 *       cacheSalt 非空时整体返回 null 表示显式绕过）</li>
 *   <li>EMBEDDING：input(String 或 List 稳定序) / encodingFormat / dimensions</li>
 *   <li>RERANK：query / documents(稳定序) / topN / returnDocuments</li>
 * </ul>
 *
 * <p>键内不含明文 api key / user / 消息内容（整体 SHA-256 哈希化）。
 * 其余服务类型或 DTO 形态不匹配时返回 null（不缓存）。
 *
 * @author JAiRouter Team
 * @since 2.9.9
 */
public final class ResponseCacheKeyBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SHA_256 = "SHA-256";

    /** 分隔符（与键段拼接一致） */
    private static final char DELIMITER = '|';

    private ResponseCacheKeyBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 构建响应缓存键.
     *
     * @param tenantKey 租户键（apiKeyId，缺省回退 clientIp，由调用方解析）
     * @param serviceType 服务类型（仅 P0 三种可缓存）
     * @param requestDto 原始请求 DTO
     * @return SHA-256 十六进制缓存键；不可缓存（类型不支持/cacheSalt 绕过/入参缺失）时返回 null
     */
    public static String build(final String tenantKey, final ServiceType serviceType,
                               final Object requestDto) {
        if (tenantKey == null || tenantKey.isBlank() || serviceType == null || requestDto == null) {
            return null;
        }
        Map<String, Object> body = normalizeBody(serviceType, requestDto);
        if (body == null) {
            return null;
        }
        StringBuilder input = new StringBuilder(tenantKey.trim());
        input.append(DELIMITER);
        String user = extractUser(requestDto);
        if (user != null && !user.isBlank()) {
            input.append(user.trim()).append(DELIMITER);
        }
        input.append(serviceType.name()).append(DELIMITER);
        String model = extractModel(requestDto);
        input.append(model != null ? model.trim() : "").append(DELIMITER);
        input.append(canonicalJson(body));
        return sha256Hex(input.toString());
    }

    /**
     * 按服务类型分派并规范化请求体.
     *
     * @param serviceType 服务类型
     * @param requestDto 请求 DTO
     * @return 规范化字段 Map；不可缓存时返回 null
     */
    private static Map<String, Object> normalizeBody(final ServiceType serviceType,
                                                     final Object requestDto) {
        switch (serviceType) {
            case chat:
                return normalizeChat(requestDto);
            case embedding:
                return normalizeEmbedding(requestDto);
            case rerank:
                return normalizeRerank(requestDto);
            default:
                return null;
        }
    }

    /**
     * 规范化 chat 请求体.
     */
    private static Map<String, Object> normalizeChat(final Object requestDto) {
        if (!(requestDto instanceof ChatDTO.Request request)) {
            return null;
        }
        // cacheSalt 非空 → 显式绕过缓存
        if (request.cacheSalt() != null && !request.cacheSalt().isBlank()) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> messages = normalizeMessages(request.messages());
        if (messages != null && !messages.isEmpty()) {
            body.put("messages", messages);
        }
        // stream: null 与 false 同义（均走非流式处理），归一为 false
        body.put("stream", Boolean.TRUE.equals(request.stream()));
        putIfNotNull(body, "maxTokens", request.maxTokens());
        putIfNotNull(body, "temperature", request.temperature());
        putIfNotNull(body, "topP", request.topP());
        putIfNotNull(body, "topK", request.topK());
        putIfNotNull(body, "frequencyPenalty", request.frequencyPenalty());
        putIfNotNull(body, "presencePenalty", request.presencePenalty());
        putIfNotNull(body, "n", request.n());
        putIfNotNull(body, "stop", request.stop());
        putChatOptions(body, request);
        return body;
    }

    /**
     * 规范化 embedding 请求体.
     */
    private static Map<String, Object> normalizeEmbedding(final Object requestDto) {
        if (!(requestDto instanceof EmbeddingDTO.Request request)) {
            return null;
        }
        // cacheSalt 非空 → 显式绕过缓存
        if (request.cacheSalt() != null && !request.cacheSalt().isBlank()) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        putIfNotNull(body, "input", request.input());
        putIfNotNull(body, "encodingFormat", request.encodingFormat());
        putIfNotNull(body, "dimensions", request.dimensions());
        return body;
    }

    /**
     * 规范化 rerank 请求体.
     */
    private static Map<String, Object> normalizeRerank(final Object requestDto) {
        if (!(requestDto instanceof RerankDTO.Request request)) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        putIfNotNull(body, "query", request.query());
        putIfNotNull(body, "documents", request.documents());
        putIfNotNull(body, "topN", request.topN());
        putIfNotNull(body, "returnDocuments", request.returnDocuments());
        return body;
    }

    /**
     * chat options 白名单：除元数据字段外的全部 options 入键（非空才参与）.
     */
    private static void putChatOptions(final Map<String, Object> body, final ChatDTO.Request request) {
        putIfNotNull(body, "seed", request.seed());
        putIfNotNull(body, "logprobs", request.logprobs());
        putIfNotNull(body, "topLogprobs", request.topLogprobs());
        putIfNotNull(body, "useBeamSearch", request.useBeamSearch());
        putIfNotNull(body, "minP", request.minP());
        putIfNotNull(body, "repetitionPenalty", request.repetitionPenalty());
        putIfNotNull(body, "lengthPenalty", request.lengthPenalty());
        putIfNotNull(body, "minTokens", request.minTokens());
        putIfNotNull(body, "skipSpecialTokens", request.skipSpecialTokens());
        putIfNotNull(body, "spacesBetweenSpecialTokens", request.spacesBetweenSpecialTokens());
        putIfNotNull(body, "truncatePromptTokens", request.truncatePromptTokens());
        putIfNotNull(body, "echo", request.echo());
        putIfNotNull(body, "addGenerationPrompt", request.addGenerationPrompt());
        putIfNotNull(body, "continueFinalMessage", request.continueFinalMessage());
        putIfNotNull(body, "addSpecialTokens", request.addSpecialTokens());
        putIfNotNull(body, "documents", request.documents());
        putIfNotNull(body, "chatTemplate", request.chatTemplate());
        putIfNotNull(body, "chatTemplateKwargs", request.chatTemplateKwargs());
        putIfNotNull(body, "structuredOutputs", request.structuredOutputs());
        putIfNotNull(body, "returnTokensAsTokenIds", request.returnTokensAsTokenIds());
        putIfNotNull(body, "returnTokenIds", request.returnTokenIds());
        putIfNotNull(body, "repetitionDetection", request.repetitionDetection());
        putIfNotNull(body, "repeatPenalty", request.repeatPenalty());
        putIfNotNull(body, "numKeep", request.numKeep());
        putIfNotNull(body, "tfsZ", request.tfsZ());
        putIfNotNull(body, "typicalP", request.typicalP());
        putIfNotNull(body, "repeatLastN", request.repeatLastN());
        putIfNotNull(body, "penalizeNewline", request.penalizeNewline());
    }

    /**
     * 规范化消息列表（逐条 role/content/name，null 剔除、字符串 trim、列表保序）.
     */
    private static List<Map<String, Object>> normalizeMessages(final List<ChatDTO.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> normalized = new ArrayList<>(messages.size());
        for (ChatDTO.Message message : messages) {
            if (message == null) {
                continue;
            }
            String role = trimToNull(message.role());
            String content = trimToNull(message.content());
            String name = trimToNull(message.name());
            if (role == null && content == null && name == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            putIfNotNull(item, "role", role);
            putIfNotNull(item, "content", content);
            putIfNotNull(item, "name", name);
            normalized.add(item);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 提取 user 段（chat/embedding；仅非空入键，空则退化为 apiKey 粒度）.
     */
    private static String extractUser(final Object requestDto) {
        if (requestDto instanceof ChatDTO.Request request) {
            return request.user();
        }
        if (requestDto instanceof EmbeddingDTO.Request request) {
            return request.user();
        }
        return null;
    }

    /**
     * 提取 model 段.
     */
    private static String extractModel(final Object requestDto) {
        if (requestDto instanceof ChatDTO.Request request) {
            return request.model();
        }
        if (requestDto instanceof EmbeddingDTO.Request request) {
            return request.model();
        }
        if (requestDto instanceof RerankDTO.Request request) {
            return request.model();
        }
        return null;
    }

    /**
     * 稳定序列化为 canonical JSON（null 剔除、字符串 trim、嵌套 Map 字典序、列表保序）.
     */
    private static String canonicalJson(final Map<String, Object> body) {
        try {
            return OBJECT_MAPPER.writeValueAsString(canonicalize(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonicalize response cache request body", e);
        }
    }

    /**
     * 递归规范化：Map 键排序（TreeMap）、字符串 trim、null/空剔除、列表保序.
     */
    private static Object canonicalize(final Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object normalized = canonicalize(entry.getValue());
                if (normalized != null) {
                    sorted.put(String.valueOf(entry.getKey()), normalized);
                }
            }
            return sorted.isEmpty() ? null : sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                Object itemValue = canonicalize(item);
                if (itemValue != null) {
                    normalized.add(itemValue);
                }
            }
            return normalized.isEmpty() ? null : normalized;
        }
        if (value instanceof String text) {
            return trimToNull(text);
        }
        return value;
    }

    /**
     * 计算 SHA-256 十六进制.
     */
    private static String sha256Hex(final String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 非 null 且 trim 后非空才放入 Map.
     */
    private static void putIfNotNull(final Map<String, Object> map, final String key, final Object value) {
        if (value instanceof String text) {
            String trimmed = trimToNull(text);
            if (trimmed != null) {
                map.put(key, trimmed);
            }
        } else if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * 字符串空白归一：trim 后为空返回 null.
     */
    private static String trimToNull(final String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

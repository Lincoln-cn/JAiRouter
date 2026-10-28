package org.unreal.modelrouter.router.adapter.request;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import java.util.Locale;

/**
 * #717：把下游错误响应体解析成「可透出给调用方」的语义。
 *
 * <p>非流式路径此前只保留状态码，上游 {@code context_length_exceeded}、额度耗尽之类的语义
 * 全部丢失，客户端无法自助排障、也无法按上游语义退避。此处只做**提取**，不做任何改写：
 * 拿不到可读消息时返回 {@code null}，由调用方给固定文案。</p>
 *
 * <p>提取顺序（够用即可，不做完整协议适配）：JSON 的 {@code error.message} →
 * {@code message} → {@code error}（字符串形态）→ 原文。消息长度上限 {@value #MAX_MESSAGE_LENGTH}
 * 字符，避免把上游大段 HTML/堆栈塞进客户端错误体。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
final class DownstreamErrorParser {

    /** 透出给调用方的消息长度上限。 */
    static final int MAX_MESSAGE_LENGTH = 512;

    private DownstreamErrorParser() {
    }

    /**
     * 从下游错误体里提取可读消息。
     *
     * @param body   下游错误体（可能为 {@code null}/空）
     * @param status 下游状态码（仅用于日志与兜底，不参与解析）
     * @return 可读消息；无可用内容时返回 {@code null}
     */
    static String extractMessage(final String body, final HttpStatusCode status) {
        final String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        final String fromJson = messageFromJson(trimmed);
        if (fromJson != null && !fromJson.isBlank()) {
            return truncate(fromJson.trim());
        }
        return truncate(trimmed);
    }

    /**
     * 从下游错误体里提取错误码（{@code error.code} / {@code error.type} / 顶层 {@code code}）。
     *
     * @param body   下游错误体
     * @param status 下游状态码（无 JSON 错误码时作为兜底）
     * @return 错误码；永不返回 {@code null}
     */
    static String extractCode(final String body, final HttpStatusCode status) {
        final String fromJson = codeFromJson(body == null ? "" : body.trim());
        if (fromJson != null && !fromJson.isBlank()) {
            return fromJson.trim();
        }
        return String.valueOf(status.value());
    }

    /**
     * 读取 {@code Retry-After} 头（只支持秒数形态；HTTP-date 形态返回 {@code null}）。
     *
     * @param headers 下游响应头
     * @return 秒数；不存在或无法解析时返回 {@code null}
     */
    static Long retryAfterSeconds(final HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        final String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            final long seconds = Long.parseLong(raw.trim());
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException ignored) {
            // HTTP-date 形态不解析：宁可不下发，也不下发错误的时间窗口
            return null;
        }
    }

    private static String truncate(final String text) {
        return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }

    private static String messageFromJson(final String body) {
        if (!body.startsWith("{")) {
            return null;
        }
        try {
            final com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            final com.fasterxml.jackson.databind.JsonNode error = root.get("error");
            if (error != null) {
                if (error.isTextual()) {
                    return error.asText();
                }
                final com.fasterxml.jackson.databind.JsonNode message = error.get("message");
                if (message != null && message.isTextual()) {
                    return message.asText();
                }
            }
            final com.fasterxml.jackson.databind.JsonNode message = root.get("message");
            return message != null && message.isTextual() ? message.asText() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return null;
        }
    }

    private static String codeFromJson(final String body) {
        if (!body.startsWith("{")) {
            return null;
        }
        try {
            final com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            final com.fasterxml.jackson.databind.JsonNode error = root.get("error");
            if (error != null && error.isObject()) {
                for (String field : new String[] {"code", "type"}) {
                    final com.fasterxml.jackson.databind.JsonNode node = error.get(field);
                    if (node != null && node.isTextual()) {
                        return node.asText();
                    }
                }
            }
            final com.fasterxml.jackson.databind.JsonNode code = root.get("code");
            return code != null && code.isTextual() ? code.asText() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return null;
        }
    }

    /**
     * 供日志使用的短描述（不含完整错误体）。
     *
     * @param status 下游状态码
     * @param code   已提取的错误码
     * @return 形如 {@code 400/context_length_exceeded}
     */
    static String describe(final HttpStatusCode status, final String code) {
        return status.value() + "/" + (code == null ? "unknown" : code.toLowerCase(Locale.ROOT));
    }
}

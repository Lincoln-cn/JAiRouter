package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * issue #781：413 的错误类型必须按协议面分开映射.
 *
 * <p>Anthropic 官方错误类型枚举里有 {@code request_too_large}，OpenAI 面没有对应取值、
 * 既有客户端按 {@code invalid_request_error} 解析（{@code CachedBodyWebFilterPayloadLimitTest}
 * 已把这一行为钉成断言）。因此本类同时守住两侧：</p>
 * <ul>
 *   <li>{@code /v1/messages}（含 {@code /v1/messages/count_tokens}）→ {@code request_too_large}；</li>
 *   <li>{@code /v1/chat/completions} 等 OpenAI 面 → {@code invalid_request_error}（不得回归）；</li>
 *   <li>其余状态码与旧的一参数重载行为不变。</li>
 * </ul>
 */
class V1PayloadTooLargeTypeTest {

    @Test
    @DisplayName("Anthropic 面 413 → request_too_large")
    void anthropicMessages413_shouldMapToRequestTooLarge() {
        assertEquals("request_too_large", V1ErrorBodyMapper.errorTypeOf("/v1/messages", 413));
        assertEquals("request_too_large",
            V1ErrorBodyMapper.errorTypeOf("/v1/messages/count_tokens", 413));
    }

    @Test
    @DisplayName("OpenAI 面 413 → invalid_request_error（既有客户端行为不得回归）")
    void openAiFace413_shouldKeepInvalidRequestError() {
        assertEquals("invalid_request_error",
            V1ErrorBodyMapper.errorTypeOf("/v1/chat/completions", 413));
        assertEquals("invalid_request_error", V1ErrorBodyMapper.errorTypeOf("/v1/embeddings", 413));
        assertEquals("invalid_request_error", V1ErrorBodyMapper.errorTypeOf("/v1/rerank", 413));
    }

    @Test
    @DisplayName("旧的一参数重载与其余状态码映射不变")
    void otherStatusesAndLegacyOverload_shouldBeUnchanged() {
        assertEquals("invalid_request_error", V1ErrorBodyMapper.errorTypeOf(413));
        assertEquals("invalid_request_error", V1ErrorBodyMapper.errorTypeOf("/v1/messages", 400));
        assertEquals("authentication_error", V1ErrorBodyMapper.errorTypeOf("/v1/messages", 401));
        assertEquals("rate_limit_error", V1ErrorBodyMapper.errorTypeOf("/v1/messages", 429));
        assertEquals("api_error", V1ErrorBodyMapper.errorTypeOf("/v1/messages", 500));
    }

    @Test
    @DisplayName("Anthropic 面 413 错误体：type=request_too_large、无 code、消息已净化")
    void anthropicBody_shouldCarryRequestTooLargeWithoutCode() {
        final Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(
            "/v1/messages", 413, "请求处理失败: 请求体超过 10MB 上限", "413");

        assertNotNull(body);
        assertEquals("error", body.get("type"), "Anthropic 外层 type 恒为 error");

        @SuppressWarnings("unchecked")
        final Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals("request_too_large", error.get("type"));
        assertEquals("请求体超过 10MB 上限", error.get("message"), "legacy 前缀必须被剥掉");
        assertFalse(error.containsKey("code"), "Anthropic 面按协议不带 code: " + error);
    }

    @Test
    @DisplayName("OpenAI 面 413 错误体：type=invalid_request_error 且保留 code")
    void openAiBody_shouldCarryInvalidRequestErrorWithCode() {
        final Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(
            "/v1/chat/completions", 413, "请求体超过 10MB 上限", "413");

        assertNotNull(body);

        @SuppressWarnings("unchecked")
        final Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals("invalid_request_error", error.get("type"));
        assertEquals("413", error.get("code"));
    }
}

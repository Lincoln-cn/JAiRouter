/*
 * Copyright 2024 JAiRouter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.router.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 下游原生响应（OpenAI 兼容 JSON 文本）→ Anthropic Message 翻译器（v3.1 PR-4b）.
 *
 * <p>{@code /v1/messages} 通过 {@code ServiceRequestHandler} 复用既有全部路由/适配器链路，
 * 在 exchange 上置原生响应标记后拿到的是<b>下游原生 JSON 字符串</b>（未包 {@code RouterResponse}），
 * 本翻译器按 OpenAI 兼容形状读取并映射为 Anthropic 协议：</p>
 * <ul>
 *   <li>{@code id} → {@code id}（缺失/空白时生成 {@code msg_<uuid>}）；</li>
 *   <li>{@code choices[0].message.content} → {@code content[0].text}；</li>
 *   <li>{@code usage.prompt_tokens} → {@code usage.input_tokens}；</li>
 *   <li>{@code usage.completion_tokens} → {@code usage.output_tokens}；</li>
 *   <li>{@code choices[0].finish_reason} → {@code stop_reason}：{@code stop→end_turn}、
 *       {@code length→max_tokens}、其他/缺失{@code →end_turn}；</li>
 *   <li>{@code stop_sequence} 恒为 {@code null}（非流式无命中的停止序列）。</li>
 * </ul>
 *
 * <p>容错：响应体为空、非合法 JSON 或缺 {@code choices} 时不抛异常，降级为「空文本 + 零用量」
 * 的 Message 并记 warn 日志——保证协议形状始终合法（避免把网关内部异常形状直接抛给 Claude Code
 * 这类严格客户端）。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@Slf4j
@Component
public class AnthropicResponseTranslator {

    private static final String MESSAGE_ID_PREFIX = "msg_";

    private static final String FINISH_REASON_STOP = "stop";

    private static final String FINISH_REASON_LENGTH = "length";

    private final ObjectMapper objectMapper;

    /**
     * 构造函数.
     *
     * @param objectMapper 全局 ObjectMapper（解析下游原生 JSON）
     */
    public AnthropicResponseTranslator(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 下游原生 JSON → Anthropic Message.
     *
     * @param nativeJson     下游原生 OpenAI 兼容响应 JSON 文本（可为 {@code null}/空白）
     * @param requestedModel 请求侧模型名（下游响应缺 {@code model} 时回填，可为 {@code null}）
     * @return Anthropic Message 响应（形状始终合法）
     */
    public AnthropicMessagesResponse toAnthropicMessage(final String nativeJson, final String requestedModel) {
        final JsonNode root = readTree(nativeJson);

        String id = null;
        String model = requestedModel;
        String text = "";
        String finishReason = null;
        int inputTokens = 0;
        int outputTokens = 0;

        if (root != null && root.isObject()) {
            id = textOrNull(root.get("id"));
            final String downstreamModel = textOrNull(root.get("model"));
            if (downstreamModel != null && !downstreamModel.isBlank()) {
                model = downstreamModel;
            }

            final JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                final JsonNode choice = choices.get(0);
                finishReason = textOrNull(choice.get("finish_reason"));
                text = extractText(choice);
            }

            final JsonNode usage = root.get("usage");
            if (usage != null && usage.isObject()) {
                inputTokens = intOrZero(usage.get("prompt_tokens"));
                outputTokens = intOrZero(usage.get("completion_tokens"));
            }
        }

        final String messageId = (id == null || id.isBlank()) ? generateMessageId() : id;
        return AnthropicMessagesResponse.text(
                messageId, model, text, mapStopReason(finishReason), inputTokens, outputTokens);
    }

    /**
     * 解析下游响应 JSON 文本.
     *
     * @param nativeJson 响应体文本
     * @return 根节点；无法解析或为空时返回 {@code null}
     */
    private JsonNode readTree(final String nativeJson) {
        if (nativeJson == null || nativeJson.isBlank()) {
            log.warn("Anthropic 响应翻译: 下游响应体为空, 已降级为空文本消息");
            return null;
        }
        try {
            return objectMapper.readTree(nativeJson);
        } catch (JsonProcessingException e) {
            log.warn("Anthropic 响应翻译: 下游响应体非合法 JSON, 已降级为空文本消息: {}", e.getOriginalMessage());
            return null;
        }
    }

    /**
     * 提取 {@code choices[i].message.content} 文本.
     *
     * @param choice 单个 choice 节点
     * @return 文本内容；缺失时返回空串
     */
    private String extractText(final JsonNode choice) {
        if (choice == null || !choice.isObject()) {
            return "";
        }
        final JsonNode message = choice.get("message");
        if (message == null || !message.isObject()) {
            return "";
        }
        final JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isArray()) {
            final StringBuilder builder = new StringBuilder();
            for (final JsonNode part : content) {
                final String partText = part != null && part.isObject() ? textOrNull(part.get("text")) : null;
                if (partText != null) {
                    builder.append(partText);
                }
            }
            return builder.toString();
        }
        return content.asText();
    }

    /**
     * 映射下游 {@code finish_reason} → Anthropic {@code stop_reason}.
     *
     * @param finishReason 下游结束原因（可为 {@code null}）
     * @return {@code max_tokens} 当且仅当下游为 {@code length}，其余一律 {@code end_turn}
     */
    private String mapStopReason(final String finishReason) {
        return switch (finishReason == null ? "" : finishReason) {
            case FINISH_REASON_STOP -> AnthropicMessagesResponse.STOP_REASON_END_TURN;
            case FINISH_REASON_LENGTH -> AnthropicMessagesResponse.STOP_REASON_MAX_TOKENS;
            default -> AnthropicMessagesResponse.STOP_REASON_END_TURN;
        };
    }

    /**
     * 生成 Anthropic 风格消息 ID.
     *
     * @return {@code msg_<32 位无连字符 UUID>}
     */
    private String generateMessageId() {
        return MESSAGE_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 读取文本字段.
     *
     * @param node JSON 节点
     * @return 文本值；节点缺失或为 null 时返回 {@code null}
     */
    private String textOrNull(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    /**
     * 读取整数字段.
     *
     * @param node JSON 节点
     * @return 数值；节点缺失或非数值时返回 0
     */
    private int intOrZero(final JsonNode node) {
        if (node == null || !node.isNumber()) {
            return 0;
        }
        return node.asInt();
    }
}

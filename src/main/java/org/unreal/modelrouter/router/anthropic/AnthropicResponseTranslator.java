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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *   <li>{@code choices[0].message.tool_calls[]}（PR-5）→ {@code content[n].tool_use}：
 *       {@code id}/{@code function.name}/{@code function.arguments}（JSON 文本 → 解析为对象）；
 *       文本与工具共存时按下游顺序产出「文本块 + 各工具块」；</li>
 *   <li>{@code usage.prompt_tokens} → {@code usage.input_tokens}；</li>
 *   <li>{@code usage.completion_tokens} → {@code usage.output_tokens}；</li>
 *   <li>{@code choices[0].finish_reason} → {@code stop_reason}：{@code stop→end_turn}、
 *       {@code length→max_tokens}、{@code tool_calls→tool_use}、其他/缺失{@code →end_turn}；</li>
 *   <li>{@code stop_sequence} 恒为 {@code null}（非流式无命中的停止序列）。</li>
 * </ul>
 *
 * <p>容错：响应体为空、非合法 JSON 或缺 {@code choices} 时不抛异常，降级为「空文本 + 零用量」
 * 的 Message 并记 warn 日志——保证协议形状始终合法（避免把网关内部异常形状直接抛给 Claude Code
 * 这类严格客户端）。工具入参 {@code arguments} 非法 JSON 或缺 {@code id} 时同样不抛异常：
 * 前者保留原文（{@code {"raw_arguments":"<原文>"}}）并记 warn，后者生成 {@code toolu_<uuid>}。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@Slf4j
@Component
public class AnthropicResponseTranslator {

    private static final String MESSAGE_ID_PREFIX = "msg_";

    /**
     * 工具调用 ID 前缀（下游缺 {@code tool_calls[].id} 时生成）.
     */
    private static final String TOOL_USE_ID_PREFIX = "toolu_";

    private static final String FINISH_REASON_STOP = "stop";

    private static final String FINISH_REASON_LENGTH = "length";

    /**
     * 下游结束原因：模型请求调用工具.
     */
    private static final String FINISH_REASON_TOOL_CALLS = "tool_calls";

    /**
     * 工具入参解析失败时的原文字段名（保证 {@code input} 仍是对象，客户端不会因形态非法而失败）.
     */
    private static final String RAW_ARGUMENTS_FIELD = "raw_arguments";

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
        List<AnthropicMessagesResponse.ContentBlock> toolUseBlocks = List.of();

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
                toolUseBlocks = extractToolUseBlocks(choice);
            }

            final JsonNode usage = root.get("usage");
            if (usage != null && usage.isObject()) {
                inputTokens = intOrZero(usage.get("prompt_tokens"));
                outputTokens = intOrZero(usage.get("completion_tokens"));
            }
        }

        final String messageId = (id == null || id.isBlank()) ? generateMessageId() : id;
        final String stopReason = mapStopReason(finishReason);
        if (toolUseBlocks.isEmpty()) {
            // 无工具调用：保持 PR-4b 的既有代码路径（单文本块，形状逐字节一致）
            return AnthropicMessagesResponse.text(messageId, model, text, stopReason, inputTokens, outputTokens);
        }

        final List<AnthropicMessagesResponse.ContentBlock> content = new ArrayList<>(toolUseBlocks.size() + 1);
        if (!text.isEmpty()) {
            content.add(AnthropicMessagesResponse.textBlock(text));
        }
        content.addAll(toolUseBlocks);
        return AnthropicMessagesResponse.of(messageId, model, content, stopReason, inputTokens, outputTokens);
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
        final JsonNode message = messageOf(choice);
        if (message == null) {
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
     * 提取 {@code choices[0].message.tool_calls[]} 并转为 Anthropic {@code tool_use} 块（PR-5）.
     *
     * @param choice 单个 choice 节点
     * @return {@code tool_use} 块列表（按下游顺序）；无工具调用时返回空列表
     */
    private List<AnthropicMessagesResponse.ContentBlock> extractToolUseBlocks(final JsonNode choice) {
        final JsonNode message = messageOf(choice);
        if (message == null) {
            return List.of();
        }
        final JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
            return List.of();
        }
        final List<AnthropicMessagesResponse.ContentBlock> blocks = new ArrayList<>(toolCalls.size());
        for (final JsonNode toolCall : toolCalls) {
            if (toolCall == null || !toolCall.isObject()) {
                continue;
            }
            final JsonNode function = toolCall.get("function");
            final String name = function == null ? null : textOrNull(function.get("name"));
            final String arguments = function == null ? null : textOrNull(function.get("arguments"));
            String callId = textOrNull(toolCall.get("id"));
            if (callId == null || callId.isBlank()) {
                callId = TOOL_USE_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
                log.warn("Anthropic 响应翻译: 下游 tool_call 缺 id, 已生成 {} (name={})", callId, name);
            }
            blocks.add(AnthropicMessagesResponse.toolUseBlock(
                    callId, name == null ? "" : name, parseArguments(arguments, callId)));
        }
        return blocks;
    }

    /**
     * {@code function.arguments}（JSON 文本）→ Anthropic {@code tool_use.input}（对象）.
     *
     * <p>缺失/空白视为「无入参」→ {@code {}}；解析失败或非对象形态时保留原文并记 warn
     * （包装为 {@code {"raw_arguments":"<原文>"}}，使 {@code input} 恒为对象，客户端不会因
     * 形态非法而整体失败）。</p>
     *
     * @param arguments 下游 {@code function.arguments} 文本（可为 {@code null}）
     * @param callId    工具调用 ID（仅用于日志定位）
     * @return 入参对象
     */
    private Object parseArguments(final String arguments, final String callId) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            final JsonNode node = objectMapper.readTree(arguments);
            if (node != null && node.isObject()) {
                return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
                });
            }
            log.warn("Anthropic 响应翻译: 工具入参不是 JSON 对象, 已保留原文: id={}, arguments={}", callId, arguments);
        } catch (JsonProcessingException e) {
            log.warn("Anthropic 响应翻译: 工具入参非法 JSON, 已保留原文: id={}, arguments={}, error={}",
                    callId, arguments, e.getOriginalMessage());
        }
        return Map.of(RAW_ARGUMENTS_FIELD, arguments);
    }

    /**
     * 读取 {@code choice.message}.
     *
     * @param choice 单个 choice 节点
     * @return message 节点；缺失时返回 {@code null}
     */
    private JsonNode messageOf(final JsonNode choice) {
        if (choice == null || !choice.isObject()) {
            return null;
        }
        final JsonNode message = choice.get("message");
        return message != null && message.isObject() ? message : null;
    }

    /**
     * 映射下游 {@code finish_reason} → Anthropic {@code stop_reason}.
     *
     * <p>包内共享：流式翻译器（{@code AnthropicStreamingTranslator}）在 {@code message_delta}
     * 中复用同一映射，避免非流式/流式两处映射规则漂移。</p>
     *
     * @param finishReason 下游结束原因（可为 {@code null}）
     * @return {@code max_tokens} 当且仅当下游为 {@code length}；{@code tool_use} 当且仅当
     *         {@code tool_calls}；其余一律 {@code end_turn}
     */
    static String mapStopReason(final String finishReason) {
        return switch (finishReason == null ? "" : finishReason) {
            case FINISH_REASON_STOP -> AnthropicMessagesResponse.STOP_REASON_END_TURN;
            case FINISH_REASON_LENGTH -> AnthropicMessagesResponse.STOP_REASON_MAX_TOKENS;
            case FINISH_REASON_TOOL_CALLS -> AnthropicMessagesResponse.STOP_REASON_TOOL_USE;
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

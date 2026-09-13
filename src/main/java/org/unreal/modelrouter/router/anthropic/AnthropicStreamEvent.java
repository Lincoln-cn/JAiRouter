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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic 流式 SSE 事件载荷（v3.1 PR-4c/5）.
 *
 * <p>{@code /v1/messages?stream=true} 的事件序列为：
 * {@code message_start} → 每个内容块的 {@code content_block_start} → 若干 {@code content_block_delta}
 * → {@code content_block_stop}（块之间不交叉，文本块为 index 0，工具块按出现顺序递增）
 * → {@code message_delta} → {@code message_stop}
 * （每个事件形如 {@code event: <name>\n data: <json>\n\n}）。本类仅承载各事件的
 * <b>data 段 JSON 形状</b>，事件名由 {@link AnthropicStreamingTranslator} 与载荷中的
 * {@code type} 字段一并写入。</p>
 *
 * <p>工具调用（PR-5）的块序列为：{@code content_block_start(content_block:{type:"tool_use",id,name})}
 * → {@code content_block_delta(delta:{type:"input_json_delta",partial_json:"<分片>"})}（0..N 次）
 * → {@code content_block_stop}。</p>
 *
 * <p>{@link JsonInclude.Include#ALWAYS} 用于抵消全局 {@code ObjectMapper} 的 {@code NON_NULL}
 * 策略（见 {@code JacksonConfig}）：Anthropic 协议要求 {@code message_start.message.stop_reason}
 * 与 {@code message_delta.delta.stop_sequence} 这类字段<b>显式存在</b>（无值时输出 {@code null}），
 * 缺字段会被严格客户端判为协议不符。{@code message_start.message} 复用
 * {@link AnthropicMessagesResponse}（其类级注解同为 {@code ALWAYS}）。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
public final class AnthropicStreamEvent {

    /**
     * 事件名：消息开始.
     */
    public static final String EVENT_MESSAGE_START = "message_start";

    /**
     * 事件名：内容块开始.
     */
    public static final String EVENT_CONTENT_BLOCK_START = "content_block_start";

    /**
     * 事件名：内容块增量.
     */
    public static final String EVENT_CONTENT_BLOCK_DELTA = "content_block_delta";

    /**
     * 事件名：内容块结束.
     */
    public static final String EVENT_CONTENT_BLOCK_STOP = "content_block_stop";

    /**
     * 事件名：消息级增量（停止原因 + 输出用量）.
     */
    public static final String EVENT_MESSAGE_DELTA = "message_delta";

    /**
     * 事件名：消息结束.
     */
    public static final String EVENT_MESSAGE_STOP = "message_stop";

    /**
     * 事件名：流式过程中的错误（Anthropic 协议定义，用于 200 已建立后的中途失败）.
     */
    public static final String EVENT_ERROR = "error";

    /**
     * 文本内容块类型.
     */
    public static final String CONTENT_BLOCK_TYPE_TEXT = "text";

    /**
     * 工具调用内容块类型（v3.1 PR-5）.
     */
    public static final String CONTENT_BLOCK_TYPE_TOOL_USE = "tool_use";

    /**
     * 文本增量类型.
     */
    public static final String DELTA_TYPE_TEXT = "text_delta";

    /**
     * 工具入参增量类型（v3.1 PR-5）.
     */
    public static final String DELTA_TYPE_INPUT_JSON = "input_json_delta";

    private AnthropicStreamEvent() {
    }

    /**
     * {@code message_start} 载荷.
     *
     * @param type    事件类型（{@code message_start}）
     * @param message 初始 Message（{@code content} 为空数组、{@code stop_reason} 为 {@code null}、
     *                {@code usage.output_tokens} 为 0）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageStart(
            String type,
            AnthropicMessagesResponse message) {
    }

    /**
     * {@code content_block_start} 载荷.
     *
     * @param type         事件类型（{@code content_block_start}）
     * @param index        内容块下标（文本块 0，其余块按出现顺序递增）
     * @param contentBlock 起始内容块（{@link ContentBlock} 文本块或 {@link ToolUseBlock} 工具块）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContentBlockStart(
            String type,
            int index,
            @JsonProperty("content_block") Object contentBlock) {
    }

    /**
     * {@code content_block_delta} 载荷.
     *
     * @param type  事件类型（{@code content_block_delta}）
     * @param index 内容块下标
     * @param delta 增量载荷（{@link TextDelta} 或 {@link InputJsonDelta}）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContentBlockDelta(
            String type,
            int index,
            DeltaPayload delta) {
    }

    /**
     * {@code content_block_stop} 载荷.
     *
     * @param type  事件类型（{@code content_block_stop}）
     * @param index 内容块下标（本版本恒为 0）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContentBlockStop(
            String type,
            int index) {
    }

    /**
     * {@code message_delta} 载荷.
     *
     * @param type  事件类型（{@code message_delta}）
     * @param delta 停止信息（{@code stop_reason} / {@code stop_sequence}）
     * @param usage 输出用量（{@code {"output_tokens":M}}）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageDelta(
            String type,
            StopDelta delta,
            DeltaUsage usage) {
    }

    /**
     * {@code message_stop} 载荷.
     *
     * @param type 事件类型（{@code message_stop}）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageStop(
            String type) {
    }

    /**
     * {@code error} 载荷（流中途失败，此时 200/SSE 已建立）.
     *
     * @param type  事件类型（{@code error}）
     * @param error 错误明细
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ErrorEvent(
            String type,
            ErrorDetail error) {
    }

    /**
     * 内容块（文本块）.
     *
     * @param type 块类型（{@code text}）
     * @param text 文本内容（起始块为空串）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContentBlock(
            String type,
            String text) {
    }

    /**
     * 工具调用内容块（{@code content_block_start} 起始块，v3.1 PR-5）.
     *
     * <p>入参不在此处给出——按 Anthropic 协议，工具入参通过后续 {@link InputJsonDelta}
     * 分片累积。</p>
     *
     * @param type 块类型（{@code tool_use}）
     * @param id   工具调用 ID（客户端回填 {@code tool_result.tool_use_id} 时使用）
     * @param name 工具名
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ToolUseBlock(
            String type,
            String id,
            String name) {
    }

    /**
     * 内容块增量载荷（{@link TextDelta} / {@link InputJsonDelta}）.
     */
    public interface DeltaPayload {
    }

    /**
     * 文本增量.
     *
     * @param type 增量类型（{@code text_delta}）
     * @param text 增量文本
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TextDelta(
            String type,
            String text) implements DeltaPayload {
    }

    /**
     * 工具入参增量（v3.1 PR-5）.
     *
     * @param type        增量类型（{@code input_json_delta}）
     * @param partialJson 工具入参 JSON 的<b>片段</b>（下游 {@code function.arguments} 分片原样透传，
     *                    客户端按到达顺序拼接后解析）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record InputJsonDelta(
            String type,
            @JsonProperty("partial_json") String partialJson) implements DeltaPayload {
    }

    /**
     * 停止信息.
     *
     * @param stopReason   停止原因（{@code end_turn} / {@code max_tokens}）
     * @param stopSequence 命中的停止序列（本版本恒为 {@code null}，字段必须存在）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StopDelta(
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("stop_sequence") String stopSequence) {
    }

    /**
     * 输出用量.
     *
     * @param outputTokens 输出 token 数
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeltaUsage(
            @JsonProperty("output_tokens") long outputTokens) {
    }

    /**
     * 错误明细.
     *
     * @param type    错误类型（{@code api_error} / {@code authentication_error} /
     *                {@code rate_limit_error} / {@code invalid_request_error}）
     * @param message 错误描述
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ErrorDetail(
            String type,
            String message) {
    }
}

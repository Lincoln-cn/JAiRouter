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

import java.util.List;

/**
 * Anthropic Messages API 响应体（Message 对象）.
 *
 * <p>形状与 Anthropic 官方一致：
 * {@code {id, type:"message", role:"assistant", model, content:[{type:"text",text}],
 * stop_reason, stop_sequence, usage:{input_tokens, output_tokens}}}。</p>
 *
 * <p>类级 {@link JsonInclude.Include#ALWAYS} 用于抵消全局 {@code ObjectMapper} 的
 * {@code NON_NULL} 策略（见 {@code JacksonConfig}）：Anthropic 客户端要求
 * {@code stop_sequence} 字段存在（无匹配时为显式 {@code null}），缺字段会被严格客户端判为协议不符。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnthropicMessagesResponse(
        String id,
        String type,
        String role,
        String model,
        List<ContentBlock> content,
        @JsonProperty("stop_reason") String stopReason,
        @JsonProperty("stop_sequence") String stopSequence,
        Usage usage) {

    /**
     * 响应类型固定值.
     */
    public static final String TYPE_MESSAGE = "message";

    /**
     * 响应角色固定值.
     */
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * stop_reason：模型自然结束（映射下游 {@code stop} / 未知值）.
     */
    public static final String STOP_REASON_END_TURN = "end_turn";

    /**
     * stop_reason：达到 max_tokens 上限（映射下游 {@code length}）.
     */
    public static final String STOP_REASON_MAX_TOKENS = "max_tokens";

    /**
     * stop_reason：模型请求调用工具（v3.1 PR-5，映射下游 {@code tool_calls}）.
     */
    public static final String STOP_REASON_TOOL_USE = "tool_use";

    /**
     * 文本内容块类型.
     */
    public static final String CONTENT_TYPE_TEXT = "text";

    /**
     * 工具调用内容块类型（v3.1 PR-5）.
     */
    public static final String CONTENT_TYPE_TOOL_USE = "tool_use";

    /**
     * 构造文本 Message（{@code type}/{@code role} 固定，{@code stop_sequence} 恒为 {@code null}）.
     *
     * @param id           消息 ID
     * @param model        模型名
     * @param text         回复文本
     * @param stopReason   停止原因（{@code end_turn} / {@code max_tokens}）
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     * @return Anthropic Message 响应
     */
    public static AnthropicMessagesResponse text(final String id,
                                                 final String model,
                                                 final String text,
                                                 final String stopReason,
                                                 final Integer inputTokens,
                                                 final Integer outputTokens) {
        return new AnthropicMessagesResponse(
                id,
                TYPE_MESSAGE,
                ROLE_ASSISTANT,
                model,
                List.of(new ContentBlock(CONTENT_TYPE_TEXT, text)),
                stopReason,
                null,
                new Usage(inputTokens, outputTokens));
    }

    /**
     * 按给定内容块构造 Message（v3.1 PR-5：文本 + 多个 {@code tool_use} 块）.
     *
     * @param id           消息 ID
     * @param model        模型名
     * @param content      内容块列表（调用方保证非空且含至少一个块）
     * @param stopReason   停止原因（{@code end_turn} / {@code max_tokens} / {@code tool_use}）
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     * @return Anthropic Message 响应
     */
    public static AnthropicMessagesResponse of(final String id,
                                               final String model,
                                               final List<ContentBlock> content,
                                               final String stopReason,
                                               final Integer inputTokens,
                                               final Integer outputTokens) {
        return new AnthropicMessagesResponse(
                id,
                TYPE_MESSAGE,
                ROLE_ASSISTANT,
                model,
                content,
                stopReason,
                null,
                new Usage(inputTokens, outputTokens));
    }

    /**
     * 构造文本内容块.
     *
     * @param text 文本内容
     * @return {@code {type:"text",text:"..."}}
     */
    public static ContentBlock textBlock(final String text) {
        return new ContentBlock(CONTENT_TYPE_TEXT, text);
    }

    /**
     * 构造工具调用内容块.
     *
     * @param id    工具调用 ID（客户端回填 {@code tool_result.tool_use_id} 时使用）
     * @param name  工具名
     * @param input 工具入参（已解析对象；解析失败时为 {@code {"raw_arguments":...}} 包装）
     * @return {@code {type:"tool_use",id,name,input}}
     */
    public static ContentBlock toolUseBlock(final String id, final String name, final Object input) {
        return new ContentBlock(CONTENT_TYPE_TOOL_USE, null, id, name, input);
    }

    /**
     * 响应内容块（文本块或工具调用块）.
     *
     * <p>类级 {@link JsonInclude.Include#ALWAYS} 保持 {@code text} 字段对文本块恒输出；
     * {@code id}/{@code name}/{@code input} 用字段级 {@code NON_NULL} 抑制，使
     * <b>文本块的 JSON 形状与 PR-4c 逐字节一致</b>（{@code {"type":"text","text":"..."}}），
     * 工具调用块则为 {@code {"type":"tool_use","id":...,"name":...,"input":{...}}}。</p>
     *
     * @param type  块类型（{@code text} / {@code tool_use}）
     * @param text  文本内容（仅文本块有值）
     * @param id    工具调用 ID（仅 {@code tool_use} 块）
     * @param name  工具名（仅 {@code tool_use} 块）
     * @param input 工具入参（仅 {@code tool_use} 块；对象，解析失败时保留原文包装）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContentBlock(
            String type,
            @JsonInclude(JsonInclude.Include.NON_NULL) String text,
            @JsonInclude(JsonInclude.Include.NON_NULL) String id,
            @JsonInclude(JsonInclude.Include.NON_NULL) String name,
            @JsonInclude(JsonInclude.Include.NON_NULL) Object input) {

        /**
         * 文本块便捷构造（保持 PR-4b 的调用兼容）.
         *
         * @param type 块类型
         * @param text 文本内容
         */
        public ContentBlock(final String type, final String text) {
            this(type, text, null, null, null);
        }
    }

    /**
     * Token 用量（Anthropic 命名）.
     *
     * @param inputTokens  输入 token 数（下游 {@code usage.prompt_tokens}）
     * @param outputTokens 输出 token 数（下游 {@code usage.completion_tokens}）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens) {
    }
}

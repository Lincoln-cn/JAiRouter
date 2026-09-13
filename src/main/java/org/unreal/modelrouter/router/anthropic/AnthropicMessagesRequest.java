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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 请求体（{@code POST /v1/messages}）.
 *
 * <p>字段与 Anthropic 官方协议保持一致（snake_case 由 {@link JsonProperty} 对齐 Java 命名）。
 * 未知字段一律忽略（{@link JsonIgnoreProperties}），以兼容 Claude Code 等客户端附带的
 * {@code thinking} / {@code cache_control} / {@code service_tier} 等扩展字段——本版本不消费
 * {@code tools} / {@code tool_choice} / {@code metadata}，仅保证其可反序列化、不报错。</p>
 *
 * <p>{@code system} 与 {@code messages[].content} 在 Anthropic 协议中均为「字符串或块数组」
 * 的联合类型，这里统一由 {@link AnthropicContentDeserializer} 归一化为块列表：纯字符串被视为
 * 单个 {@code type=text} 块，使下游翻译逻辑只需处理一种形态。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicMessagesRequest(
        String model,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonDeserialize(using = AnthropicContentDeserializer.class) List<ContentBlock> system,
        List<Message> messages,
        Boolean stream,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        @JsonProperty("stop_sequences") List<String> stopSequences,
        List<Tool> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        Map<String, Object> metadata) {

    /**
     * 对话消息（{@code role} + 归一化后的内容块列表）.
     *
     * @param role    角色（{@code user} / {@code assistant}）
     * @param content 内容块列表（由 {@link AnthropicContentDeserializer} 归一化，字符串入参转为单文本块）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            @JsonDeserialize(using = AnthropicContentDeserializer.class) List<ContentBlock> content) {
    }

    /**
     * 内容块（本版本只消费文本块，其余类型由翻译器记日志跳过）.
     *
     * @param type 块类型（{@code text} / {@code image} / {@code tool_use} / {@code tool_result} ...）
     * @param text 文本内容（仅文本块有值）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            String type,
            String text) {
    }

    /**
     * 工具定义（本版本仅接收，不转发给下游）.
     *
     * @param name        工具名
     * @param description 工具描述
     * @param inputSchema 入参 JSON Schema（协议字段名 {@code input_schema}）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tool(
            String name,
            String description,
            @JsonProperty("input_schema") Map<String, Object> inputSchema) {
    }
}

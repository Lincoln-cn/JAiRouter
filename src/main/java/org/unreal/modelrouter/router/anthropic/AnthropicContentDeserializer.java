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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「字符串或块数组」联合类型反序列化器.
 *
 * <p>Anthropic 协议中 {@code system} 与 {@code messages[].content} 既可以是纯字符串，也可以是
 * 内容块数组。本反序列化器统一归一化为 {@link AnthropicMessagesRequest.ContentBlock} 列表：</p>
 * <ul>
 *   <li>字符串 → 单个 {@code type=text} 块；</li>
 *   <li>数组 → 逐元素转为块（元素为对象时按类型取字段，为字符串时视为文本块）；</li>
 *   <li>单个对象 → 单元素列表（容错，Anthropic 协议未定义但可低成本兼容）；</li>
 *   <li>null → {@code null}（保持「字段缺失」与「空数组」的语义差异）；</li>
 *   <li>其他类型 → 抛 {@link JsonMappingException}，由 Spring 转成 400（而非静默截断用户输入）。</li>
 * </ul>
 *
 * <p>v3.1 PR-5 起额外提取 {@code tool_use}（{@code id}/{@code name}/{@code input}）与
 * {@code tool_result}（{@code tool_use_id}/{@code content}）字段，使下游翻译器能完成
 * Anthropic ↔ OpenAI 工具调用消息的双向映射。块中的其他未知键（如 {@code cache_control} /
 * {@code source}）仍不会被读取，因此天然免疫未知字段。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
public final class AnthropicContentDeserializer
        extends JsonDeserializer<List<AnthropicMessagesRequest.ContentBlock>> {

    @Override
    public List<AnthropicMessagesRequest.ContentBlock> deserialize(
            final JsonParser parser, final DeserializationContext context) throws IOException {

        final ObjectCodec codec = parser.getCodec();
        final JsonNode node = codec.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return List.of(textBlock(node.asText()));
        }
        if (node.isArray()) {
            final List<AnthropicMessagesRequest.ContentBlock> blocks = new ArrayList<>();
            for (final JsonNode element : node) {
                final AnthropicMessagesRequest.ContentBlock block = toBlock(element, codec);
                if (block != null) {
                    blocks.add(block);
                }
            }
            return blocks;
        }
        if (node.isObject()) {
            final AnthropicMessagesRequest.ContentBlock block = toBlock(node, codec);
            return block == null ? List.of() : List.of(block);
        }
        throw JsonMappingException.from(parser, "content/system 必须是字符串或内容块数组");
    }

    /**
     * 将单个 JSON 节点转为内容块.
     *
     * @param element JSON 节点
     * @param codec   编解码器（物化 {@code input} / {@code tool_result.content} 用）
     * @return 内容块；节点为空或类型不支持时返回 {@code null}（由调用方跳过）
     */
    private AnthropicMessagesRequest.ContentBlock toBlock(final JsonNode element, final ObjectCodec codec) {
        if (element == null || element.isNull()) {
            return null;
        }
        if (element.isTextual()) {
            return textBlock(element.asText());
        }
        if (!element.isObject()) {
            return null;
        }
        final String type = textOrNull(element, "type");
        final String text = textOrNull(element, "text");
        final String id = textOrNull(element, "id");
        final String name = textOrNull(element, "name");
        final String toolUseId = textOrNull(element, "tool_use_id");
        final Map<String, Object> input = objectOrNull(element.get("input"), codec);
        final Object toolResultContent = materialize(element.get("content"), codec);
        return new AnthropicMessagesRequest.ContentBlock(
                type, text, id, name, input, toolUseId, toolResultContent);
    }

    /**
     * 构造文本块.
     *
     * @param text 文本内容
     * @return {@code type=text} 块
     */
    private AnthropicMessagesRequest.ContentBlock textBlock(final String text) {
        return new AnthropicMessagesRequest.ContentBlock("text", text, null, null, null, null, null);
    }

    /**
     * 读取文本字段.
     *
     * @param node 块节点
     * @param key  字段名
     * @return 文本值；字段缺失或为 null 时返回 {@code null}
     */
    private String textOrNull(final JsonNode node, final String key) {
        final JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    /**
     * 读取对象字段（{@code tool_use.input}）.
     *
     * @param node  字段节点
     * @param codec 编解码器
     * @return 对象 Map；非对象形态返回 {@code null}（交由翻译器降级为空对象）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> objectOrNull(final JsonNode node, final ObjectCodec codec) {
        final Object value = materialize(node, codec);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /**
     * 把 JSON 节点物化为普通 Java 值（字符串 / 数字 / 布尔 / {@code List} / {@code Map}）.
     *
     * <p>{@code tool_result.content} 既可以是字符串也可以是内容块数组，这里保留原始形态，
     * 由翻译器决定如何压平成下游所需的文本。</p>
     *
     * @param node  JSON 节点
     * @param codec 编解码器
     * @return 物化值；节点缺失/null 时返回 {@code null}，物化异常时退化为节点文本
     */
    private Object materialize(final JsonNode node, final ObjectCodec codec) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return codec.treeToValue(node, Object.class);
        } catch (JsonProcessingException e) {
            return node.asText();
        }
    }
}

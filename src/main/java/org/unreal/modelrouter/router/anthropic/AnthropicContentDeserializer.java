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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 「字符串或块数组」联合类型反序列化器.
 *
 * <p>Anthropic 协议中 {@code system} 与 {@code messages[].content} 既可以是纯字符串，也可以是
 * 内容块数组。本反序列化器统一归一化为 {@link AnthropicMessagesRequest.ContentBlock} 列表：</p>
 * <ul>
 *   <li>字符串 → 单个 {@code type=text} 块；</li>
 *   <li>数组 → 逐元素转为块（元素为对象时取 {@code type} / {@code text}，为字符串时视为文本块）；</li>
 *   <li>单个对象 → 单元素列表（容错，Anthropic 协议未定义但可低成本兼容）；</li>
 *   <li>null → {@code null}（保持「字段缺失」与「空数组」的语义差异）；</li>
 *   <li>其他类型 → 抛 {@link JsonMappingException}，由 Spring 转成 400（而非静默截断用户输入）。</li>
 * </ul>
 *
 * <p>块中的未知键（如 {@code cache_control} / {@code source} / {@code id}）不会被读取，因此
 * 无需绑定到目标类型，天然免疫未知字段。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
public final class AnthropicContentDeserializer
        extends JsonDeserializer<List<AnthropicMessagesRequest.ContentBlock>> {

    @Override
    public List<AnthropicMessagesRequest.ContentBlock> deserialize(
            final JsonParser parser, final DeserializationContext context) throws IOException {

        final JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return List.of(new AnthropicMessagesRequest.ContentBlock("text", node.asText()));
        }
        if (node.isArray()) {
            final List<AnthropicMessagesRequest.ContentBlock> blocks = new ArrayList<>();
            for (final JsonNode element : node) {
                final AnthropicMessagesRequest.ContentBlock block = toBlock(element);
                if (block != null) {
                    blocks.add(block);
                }
            }
            return blocks;
        }
        if (node.isObject()) {
            final AnthropicMessagesRequest.ContentBlock block = toBlock(node);
            return block == null ? List.of() : List.of(block);
        }
        throw JsonMappingException.from(parser, "content/system 必须是字符串或内容块数组");
    }

    /**
     * 将单个 JSON 节点转为内容块.
     *
     * @param element JSON 节点
     * @return 内容块；节点为空或类型不支持时返回 {@code null}（由调用方跳过）
     */
    private AnthropicMessagesRequest.ContentBlock toBlock(final JsonNode element) {
        if (element == null || element.isNull()) {
            return null;
        }
        if (element.isTextual()) {
            return new AnthropicMessagesRequest.ContentBlock("text", element.asText());
        }
        if (!element.isObject()) {
            return null;
        }
        final String type = element.hasNonNull("type") ? element.get("type").asText() : null;
        final String text = element.hasNonNull("text") ? element.get("text").asText() : null;
        return new AnthropicMessagesRequest.ContentBlock(type, text);
    }
}

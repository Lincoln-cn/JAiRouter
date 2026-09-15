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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.unreal.modelrouter.auth.security.quota.QuotaTokenEstimator;

import java.util.List;
import java.util.Map;

/**
 * Anthropic 请求/文本 token 估算器（v3.1 PR-4c）.
 *
 * <p>{@code POST /v1/messages/count_tokens} 与流式 {@code message_start.usage.input_tokens}
 * 必须给出<b>同源</b>的估算值，因此估算逻辑集中在本组件：</p>
 * <ul>
 *   <li>统计文本量——{@code system}、{@code messages[].content} 中的文本块、
 *       {@code tool_use} 块（name + input）、{@code tool_result} 块的 content 文本、
 *       以及 {@code tools[]} 的 name + description + inputSchema 序列化文本均已计入；
 *       {@code image} 块仍不计入（不计 base64）；</li>
 *   <li>字符系数复用 {@link QuotaTokenEstimator#estimateFromText(String)}（表意文字 2 字符/token、
 *       其余非空白 4 字符/token），与配额预留、流式响应侧估算保持同一把尺子；</li>
 *   <li>偏差（设计取舍）：{@code max_tokens} 输出侧规模、
 *       图片块、与下游真实分词差异均不计入
 *       ——该估算值只用于预算/裁剪，不等于下游分词结果。</li>
 * </ul>
 *
 * <p>本类为无状态工具类：全局唯一估算实现，避免 count_tokens 与流式入口各写一份比例系数。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
public final class AnthropicTokenEstimator {

    /**
     * JSON 序列化器（线程安全，用于将 Map/List 序列化为文本再估算）.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AnthropicTokenEstimator() {
    }

    /**
     * 估算一次 Anthropic Messages 请求的输入 token 数.
     *
     * <p>已计入 tools schema 与工具块文本（tool_use / tool_result），
     * image 块仍不计。</p>
     *
     * @param request Anthropic 请求体，可为 {@code null}
     * @return 估算输入 token 数（非负）；{@code null} 请求返回 0
     */
    public static long estimateRequest(final AnthropicMessagesRequest request) {
        if (request == null) {
            return 0L;
        }
        final StringBuilder text = new StringBuilder();
        appendTextBlocks(text, request.system());
        if (request.messages() != null) {
            for (final AnthropicMessagesRequest.Message message : request.messages()) {
                if (message != null) {
                    appendTextBlocks(text, message.content());
                }
            }
        }
        appendToolsSchema(text, request.tools());
        return estimateText(text.toString());
    }

    /**
     * 估算纯文本的 token 数.
     *
     * @param text 文本，可为 {@code null}
     * @return 估算 token 数（非负）
     */
    public static long estimateText(final String text) {
        return QuotaTokenEstimator.estimateFromText(text);
    }

    /**
     * 追加内容块列表中的文本（文本块、tool_use、tool_result）.
     *
     * <p>image 块与未知类型块跳过。</p>
     *
     * @param target 累加目标
     * @param blocks 内容块列表，可为 {@code null}
     */
    private static void appendTextBlocks(final StringBuilder target,
                                         final List<AnthropicMessagesRequest.ContentBlock> blocks) {
        if (blocks == null) {
            return;
        }
        for (final AnthropicMessagesRequest.ContentBlock block : blocks) {
            if (block == null) {
                continue;
            }
            final String type = block.type();
            if ("text".equals(type) || type == null) {
                if (block.text() != null) {
                    target.append(block.text());
                }
            } else if ("tool_use".equals(type)) {
                if (block.name() != null) {
                    target.append(block.name());
                }
                if (block.input() != null) {
                    appendJsonMap(target, block.input());
                }
            } else if ("tool_result".equals(type)) {
                appendToolResultContent(target, block.toolResultContent());
            }
            // image 及其它类型跳过
        }
    }

    /**
     * 追加 {@code tool_result.content} 的文本.
     *
     * <p>{@code content} 可以是字符串（直接追加）或文本块数组（拼各 text 块）。</p>
     *
     * @param target  累加目标
     * @param content tool_result 的 content 字段，可为 {@code null}
     */
    private static void appendToolResultContent(final StringBuilder target, final Object content) {
        if (content == null) {
            return;
        }
        if (content instanceof String s) {
            target.append(s);
        } else if (content instanceof List<?> list) {
            for (final Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    final Object itemType = map.get("type");
                    if ("text".equals(itemType) || itemType == null) {
                        final Object textVal = map.get("text");
                        if (textVal instanceof String s) {
                            target.append(s);
                        }
                    }
                }
            }
        }
    }

    /**
     * 追加 {@code tools[]} 的 schema 文本（name + description + inputSchema JSON）.
     *
     * @param target 累加目标
     * @param tools  工具定义列表，可为 {@code null}
     */
    private static void appendToolsSchema(final StringBuilder target,
                                          final List<AnthropicMessagesRequest.Tool> tools) {
        if (tools == null) {
            return;
        }
        for (final AnthropicMessagesRequest.Tool tool : tools) {
            if (tool == null) {
                continue;
            }
            if (tool.name() != null) {
                target.append(tool.name());
            }
            if (tool.description() != null) {
                target.append(tool.description());
            }
            if (tool.inputSchema() != null) {
                appendJsonMap(target, tool.inputSchema());
            }
        }
    }

    /**
     * 将 Map 序列化为紧凑 JSON 文本并追加到目标.
     *
     * @param target 累加目标
     * @param map    待序列化的 Map，不为 {@code null}
     */
    private static void appendJsonMap(final StringBuilder target, final Map<String, Object> map) {
        try {
            target.append(MAPPER.writeValueAsString(map));
        } catch (Exception e) {
            // 序列化失败时跳过（估算允许偏差）
        }
    }
}

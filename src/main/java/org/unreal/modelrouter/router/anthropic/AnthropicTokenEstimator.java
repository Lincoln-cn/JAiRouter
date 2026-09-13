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

import org.unreal.modelrouter.auth.security.quota.QuotaTokenEstimator;

import java.util.List;

/**
 * Anthropic 请求/文本 token 估算器（v3.1 PR-4c）.
 *
 * <p>{@code POST /v1/messages/count_tokens} 与流式 {@code message_start.usage.input_tokens}
 * 必须给出<b>同源</b>的估算值，因此估算逻辑集中在本组件：</p>
 * <ul>
 *   <li>只统计「文本量」——{@code system} 与 {@code messages[].content} 中的文本块拼接；
 *       非文本块（{@code image} / {@code tool_use} / {@code tool_result} …）不计入；</li>
 *   <li>字符系数复用 {@link QuotaTokenEstimator#estimateFromText(String)}（表意文字 2 字符/token、
 *       其余非空白 4 字符/token），与配额预留、流式响应侧估算保持同一把尺子；</li>
 *   <li>偏差（设计取舍）：{@code tools} 的 JSON Schema 文本、{@code max_tokens} 输出侧规模、
 *       图片块与下游真实分词差异均不计入——与「tools 不映射下游」的实现边界一致。</li>
 * </ul>
 *
 * <p>本类为无状态工具类：全局唯一估算实现，避免 count_tokens 与流式入口各写一份比例系数。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
public final class AnthropicTokenEstimator {

    private AnthropicTokenEstimator() {
    }

    /**
     * 估算一次 Anthropic Messages 请求的输入 token 数.
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
     * 追加内容块列表中的文本（仅文本块）.
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
            if (block == null || block.text() == null) {
                continue;
            }
            if ("text".equals(block.type()) || block.type() == null) {
                target.append(block.text());
            }
        }
    }
}

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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.dto.ChatDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic Messages 请求 → 内部统一 {@link ChatDTO.Request} 翻译器（v3.1 PR-4b/4c）.
 *
 * <p>映射规则：</p>
 * <ul>
 *   <li>{@code system}（字符串或文本块数组）→ 首条 {@code role=system} 消息（内容为文本块拼接）；</li>
 *   <li>{@code messages[].content} 块数组 → 只取 {@code type=text} 块拼接，其他块类型记 debug 日志并跳过；</li>
 *   <li>{@code max_tokens → maxTokens}、{@code temperature → temperature}、{@code top_p → topP}；</li>
 *   <li>{@code stop_sequences → stop}（原样以列表透传；空列表转 {@code null}）；</li>
 *   <li>{@code stream} 由调用方显式指定（PR-4c）：{@code /v1/messages} 非流式分支恒 {@code FALSE}、
 *       流式分支恒 {@code TRUE}，单参入口保持 {@code FALSE} 语义。</li>
 * </ul>
 *
 * <p>本版本不转发 {@code tools} / {@code tool_choice} / {@code metadata}（内部 {@code ChatDTO.Request}
 * 无对应字段），其中工具定义会记 debug 日志以免静默丢失。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@Slf4j
@Component
public class AnthropicRequestTranslator {

    /**
     * Anthropic 内容块类型：文本.
     */
    private static final String CONTENT_TYPE_TEXT = "text";

    /**
     * 将 Anthropic 请求翻译为内部统一 Chat 请求（非流式）.
     *
     * <p>永不抛异常：{@code messages} 缺失或为空时返回仅含（可选）system 消息的请求，
     * 由下游实例/适配器决定如何响应。等价于 {@link #toChatRequest(AnthropicMessagesRequest, Boolean)}
     * 传 {@link Boolean#FALSE}（保持 PR-4b 的既有语义）。</p>
     *
     * @param request Anthropic 请求体
     * @return 内部 Chat 请求（{@code stream=FALSE}）
     */
    public ChatDTO.Request toChatRequest(final AnthropicMessagesRequest request) {
        return toChatRequest(request, Boolean.FALSE);
    }

    /**
     * 将 Anthropic 请求翻译为内部统一 Chat 请求.
     *
     * <p>永不抛异常：{@code messages} 缺失或为空时返回仅含（可选）system 消息的请求，
     * 由下游实例/适配器决定如何响应。</p>
     *
     * <p>{@code stream} 由调用方（控制器）显式给出，而非直接透传请求体字段：
     * {@code /v1/messages} 的流式分支要先完成「Anthropic 事件流」改写，因此内部
     * {@code ChatDTO.Request.stream} 必须与「走哪条链路」严格一致——非流式分支恒为
     * {@link Boolean#FALSE}，流式分支恒为 {@link Boolean#TRUE}。</p>
     *
     * @param request Anthropic 请求体（可为 {@code null}）
     * @param stream  内部请求的流式标记（{@code null} 视为非流式）
     * @return 内部 Chat 请求
     * @since v3.1 PR-4c
     */
    public ChatDTO.Request toChatRequest(final AnthropicMessagesRequest request, final Boolean stream) {
        final Boolean streamFlag = Boolean.TRUE.equals(stream) ? Boolean.TRUE : Boolean.FALSE;
        if (request == null) {
            return new ChatDTO.Request(
                    null, List.of(), streamFlag, null, null, null, null, null, null, null, null, null);
        }

        final List<ChatDTO.Message> messages = new ArrayList<>();

        // system（字符串或文本块数组）→ 首条 system 消息
        final String systemText = joinTextBlocks(request.system(), "system");
        if (systemText != null && !systemText.isEmpty()) {
            messages.add(new ChatDTO.Message("system", systemText, null));
        }

        if (request.messages() != null) {
            for (final AnthropicMessagesRequest.Message message : request.messages()) {
                if (message == null) {
                    continue;
                }
                final String content = joinTextBlocks(message.content(), "messages.content");
                messages.add(new ChatDTO.Message(message.role(), content == null ? "" : content, null));
            }
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            log.debug("Anthropic tools 暂不转发到下游（本版本仅支持纯文本对话）: size={}", request.tools().size());
        }

        return new ChatDTO.Request(
                request.model(),
                messages,
                streamFlag,
                request.maxTokens(),
                request.temperature(),
                request.topP(),
                null,
                null,
                null,
                stopOf(request.stopSequences()),
                null,
                null);
    }

    /**
     * 拼接文本块内容.
     *
     * @param blocks 内容块列表（可为 {@code null}）
     * @param where  日志定位信息（{@code system} / {@code messages.content}）
     * @return 拼接后的文本；块列表缺失时返回 {@code null}；无文本块时返回空串
     */
    private String joinTextBlocks(final List<AnthropicMessagesRequest.ContentBlock> blocks, final String where) {
        if (blocks == null) {
            return null;
        }
        final StringBuilder builder = new StringBuilder();
        for (final AnthropicMessagesRequest.ContentBlock block : blocks) {
            if (block == null) {
                continue;
            }
            if (isTextBlock(block)) {
                if (block.text() != null) {
                    builder.append(block.text());
                }
            } else {
                log.debug("Anthropic {} 中的非文本块已跳过: type={}", where, block.type());
            }
        }
        return builder.toString();
    }

    /**
     * 是否文本块.
     *
     * <p>{@code type} 缺失但带 {@code text} 的块按文本处理（容错，Anthropic 协议要求 type 显式存在）。</p>
     *
     * @param block 内容块
     * @return 文本块返回 true
     */
    private boolean isTextBlock(final AnthropicMessagesRequest.ContentBlock block) {
        if (CONTENT_TYPE_TEXT.equals(block.type())) {
            return true;
        }
        return block.type() == null && block.text() != null;
    }

    /**
     * {@code stop_sequences} → 内部 {@code stop}.
     *
     * @param stopSequences Anthropic 停止序列
     * @return 非空列表原样返回，其余返回 {@code null}
     */
    private Object stopOf(final List<String> stopSequences) {
        if (stopSequences == null || stopSequences.isEmpty()) {
            return null;
        }
        return List.copyOf(stopSequences);
    }
}

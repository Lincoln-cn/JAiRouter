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

package org.unreal.modelrouter.router.cache;

import java.util.List;

/**
 * v2.9.10: 流式 SSE 缓存值.
 *
 * <p>缓存完整的流式 SSE 响应，用于命中时的逐块回放。
 * 存储 {@code transformStreamChunk} 后的出站 data 串列表（不含 {@code data: } 前缀），
 * 回放时逐块包装为 {@code ServerSentEvent} 重新 SSE 序列化，保证字节级一致。
 *
 * <p>chunks 列表包含全部变换后块（含 {@code [DONE]} 标记），
 * 回放时按序发射即可还原完整 SSE 流。
 *
 * @param chunks         变换后的 SSE data 串列表（含 {@code [DONE]}）
 * @param model          模型名称
 * @param promptTokens   prompt token 使用量（后端未提供时为 null）
 * @param completionTokens completion token 使用量（后端未提供时为 null）
 * @param totalTokens    total token 使用量（后端未提供时为 null）
 * @param finishReason   完成原因（如 {@code stop}、{@code length}，未获取时为 null）
 * @param complete       流是否带终止标记（{@code [DONE]} / {@code finish_reason} / {@code message_stop}）；
 *                       #721 起只有 complete 的流才允许写缓存
 * @author JAiRouter Team
 * @since 2.9.10
 */
public record CachedStreamingResponse(
        List<String> chunks,
        String model,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        String finishReason,
        boolean complete
) {

    /**
     * 兼容既有调用点的六参构造器（#721 之前的形状）。
     *
     * <p>历史调用方没有终止标记信息，按「未确认完整」处理，避免把截断的血统当成完整流。</p>
     *
     * @param chunks           变换后的 SSE data 串列表
     * @param model            模型名
     * @param promptTokens     prompt token 数
     * @param completionTokens completion token 数
     * @param totalTokens      total token 数
     * @param finishReason     完成原因
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public CachedStreamingResponse(final List<String> chunks, final String model,
                                   final Long promptTokens, final Long completionTokens,
                                   final Long totalTokens, final String finishReason) {
        this(chunks, model, promptTokens, completionTokens, totalTokens, finishReason, false);
    }
}

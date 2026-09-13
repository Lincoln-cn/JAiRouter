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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 下游 OpenAI 兼容 SSE 流 → Anthropic 事件流翻译器（v3.1 PR-4c）.
 *
 * <p>{@code POST /v1/messages} 在 {@code stream=true} 时复用 {@code ServiceRequestHandler}
 * 的既有流式链路：拿到的是下游 <b>OpenAI 风格</b> chunk（{@code choices[0].delta.content}，
 * 末尾 {@code data: [DONE]}）。本翻译器把它逐块改写为 Anthropic 事件序列：</p>
 *
 * <pre>
 * event: message_start        data: {message:{id,type,role,model,content:[],stop_reason:null,usage:{...}}}
 * event: content_block_start  data: {index:0,content_block:{type:"text",text:""}}
 * event: content_block_delta  data: {index:0,delta:{type:"text_delta",text:"..."}}   （0..N 次）
 * event: content_block_stop   data: {index:0}
 * event: message_delta        data: {delta:{stop_reason,stop_sequence:null},usage:{output_tokens:M}}
 * event: message_stop         data: {type:"message_stop"}
 * </pre>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li><b>不阻塞 event loop</b>：全部用 {@link Flux#defer} + {@link Flux#concatWith} 组合，
 *       起始事件与下游流拼接，收尾事件用 {@code Flux.defer} 在<b>订阅尾段时</b>求值
 *       ——此时累计文本、{@code finish_reason}、下游 {@code usage} 均已就绪；</li>
 *   <li><b>累积状态是订阅级的</b>：状态在 {@code Flux.defer} 内创建（每次订阅一份），
 *       不共享可变字段，天然线程安全；</li>
 *   <li>{@code data: [DONE]} 块直接过滤；无法解析/非 SSE 元素记 debug 并跳过，
 *       绝不因单个脏块中断整条流；</li>
 *   <li>{@code input_tokens} 由 {@link AnthropicTokenEstimator} 在请求侧估算（与
 *       {@code count_tokens} 同源）；{@code output_tokens} 优先用下游 {@code usage.completion_tokens}
 *       （末块提供时），否则按累计文本估算；</li>
 *   <li>{@code message_start.message.id} 由网关生成 {@code msg_<uuid>}——该事件早于首个下游
 *       块发出，此时下游 id 尚不可知；{@code model} 取请求侧模型名；</li>
 *   <li>下游中途报错（HTTP 已 200、SSE 已建立）时发一个 {@code event: error} 收尾，
 *       让 Claude Code 这类客户端能显示真实错误而不是「流被截断」。</li>
 * </ul>
 *
 * <p>已知边界：本版本只产出<b>单个文本内容块</b>（index 恒为 0），不映射 {@code tools} /
 * {@code thinking}；下游 chunk 中的 {@code reasoning_content} 不进入文本增量。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@Slf4j
@Component
public class AnthropicStreamingTranslator {

    /**
     * 下游 OpenAI 风格流结束标记.
     */
    private static final String CHUNK_DONE = "[DONE]";

    /**
     * SSE 数据行前缀（{@code data:} 后可带一个空格）.
     */
    private static final String DATA_PREFIX = "data:";

    /**
     * Anthropic 消息 ID 前缀.
     */
    private static final String MESSAGE_ID_PREFIX = "msg_";

    /**
     * 文本内容块下标（本版本仅单块）.
     */
    private static final int TEXT_BLOCK_INDEX = 0;

    /**
     * Anthropic 错误事件类型：通用服务端错误.
     */
    private static final String ERROR_TYPE_API = "api_error";

    private final ObjectMapper objectMapper;

    /**
     * 构造函数.
     *
     * @param objectMapper 全局 ObjectMapper（序列化各事件 data 段）
     */
    public AnthropicStreamingTranslator(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将下游 SSE 元素流改写为 Anthropic 事件流.
     *
     * @param downstream     下游流式响应体（元素为 {@link ServerSentEvent}，非 SSE 元素被跳过）
     * @param requestedModel 请求侧模型名（{@code message_start.message.model}）
     * @param inputTokens    请求侧估算的输入 token 数（{@code message_start.message.usage.input_tokens}）
     * @return Anthropic 事件流（事件名 + data 段 JSON）
     */
    public Flux<ServerSentEvent<String>> toEventStream(final Flux<?> downstream,
                                                       final String requestedModel,
                                                       final long inputTokens) {

        return Flux.defer(() -> {
            final StreamState state = new StreamState();
            final Flux<ServerSentEvent<String>> deltaEvents = downstream
                    .filter(element -> element instanceof ServerSentEvent)
                    .concatMap(element -> Flux.fromIterable(
                            toDeltaEvents((ServerSentEvent<?>) element, state)))
                    .concatWith(Flux.defer(() -> Flux.fromIterable(
                            finishEvents(state))));

            return Flux.fromIterable(startEvents(requestedModel, inputTokens))
                    .concatWith(deltaEvents)
                    .onErrorResume(error -> {
                        log.error("Anthropic 流式翻译: 下游流异常, 以 event: error 收尾", error);
                        return Flux.just(errorEvent(error));
                    });
        });
    }

    /**
     * 构造起始事件（{@code message_start} + {@code content_block_start}）.
     *
     * @param requestedModel 请求侧模型名
     * @param inputTokens    估算输入 token 数
     * @return 起始事件列表
     */
    private List<ServerSentEvent<String>> startEvents(final String requestedModel, final long inputTokens) {
        final AnthropicMessagesResponse message = new AnthropicMessagesResponse(
                MESSAGE_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""),
                AnthropicMessagesResponse.TYPE_MESSAGE,
                AnthropicMessagesResponse.ROLE_ASSISTANT,
                requestedModel,
                List.of(),
                null,
                null,
                new AnthropicMessagesResponse.Usage(clampToInt(inputTokens), 0));

        final List<ServerSentEvent<String>> events = new ArrayList<>(2);
        events.add(event(AnthropicStreamEvent.EVENT_MESSAGE_START,
                new AnthropicStreamEvent.MessageStart(
                        AnthropicStreamEvent.EVENT_MESSAGE_START, message)));
        events.add(event(AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,
                new AnthropicStreamEvent.ContentBlockStart(
                        AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,
                        TEXT_BLOCK_INDEX,
                        new AnthropicStreamEvent.ContentBlock(
                                AnthropicStreamEvent.CONTENT_BLOCK_TYPE_TEXT, ""))));
        return events;
    }

    /**
     * 构造收尾事件（{@code content_block_stop} + {@code message_delta} + {@code message_stop}）.
     *
     * <p>仅在订阅尾段（下游流正常结束）时求值，因此可读取累计状态。</p>
     *
     * @param state 本次订阅的累积状态（累计文本、输出用量、结束原因）
     * @return 收尾事件列表
     */
    private List<ServerSentEvent<String>> finishEvents(final StreamState state) {
        final long outputTokens = state.outputTokens > 0
                ? state.outputTokens
                : AnthropicTokenEstimator.estimateText(state.text.toString());

        final List<ServerSentEvent<String>> events = new ArrayList<>(3);
        events.add(event(AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                new AnthropicStreamEvent.ContentBlockStop(
                        AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP, TEXT_BLOCK_INDEX)));
        events.add(event(AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                new AnthropicStreamEvent.MessageDelta(
                        AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                        new AnthropicStreamEvent.StopDelta(
                                AnthropicResponseTranslator.mapStopReason(state.finishReason), null),
                        new AnthropicStreamEvent.DeltaUsage(outputTokens))));
        events.add(event(AnthropicStreamEvent.EVENT_MESSAGE_STOP,
                new AnthropicStreamEvent.MessageStop(AnthropicStreamEvent.EVENT_MESSAGE_STOP)));
        return events;
    }

    /**
     * 单个下游块 → 0..1 个 {@code content_block_delta} 事件.
     *
     * @param downstreamEvent 下游 SSE 元素
     * @param state           本次订阅的累积状态（累计文本、输出用量、结束原因）
     * @return 事件列表；无文本增量/心跳/异常块返回空列表
     */
    private List<ServerSentEvent<String>> toDeltaEvents(final ServerSentEvent<?> downstreamEvent,
                                                        final StreamState state) {
        final String payload = payloadOf(downstreamEvent);
        if (payload == null || payload.isEmpty() || CHUNK_DONE.equals(payload)) {
            return List.of();
        }
        final JsonNode root = readTree(payload);
        if (root == null || !root.isObject()) {
            return List.of();
        }

        accumulateUsage(root, state);

        final JsonNode choice = firstChoice(root);
        if (choice == null) {
            return List.of();
        }
        accumulateFinishReason(choice, state);

        final String text = extractDeltaText(choice);
        if (text.isEmpty()) {
            return List.of();
        }
        state.text.append(text);

        return List.of(event(AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                new AnthropicStreamEvent.ContentBlockDelta(
                        AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                        TEXT_BLOCK_INDEX,
                        new AnthropicStreamEvent.TextDelta(
                                AnthropicStreamEvent.DELTA_TYPE_TEXT, text))));
    }

    /**
     * 取下游块的数据载荷（去掉可能的 {@code data:} 前缀）.
     *
     * @param downstreamEvent 下游 SSE 元素
     * @return 去前缀后的文本；无数据返回 {@code null}
     */
    private String payloadOf(final ServerSentEvent<?> downstreamEvent) {
        final Object data = downstreamEvent.data();
        if (data == null) {
            return null;
        }
        String payload = String.valueOf(data).trim();
        if (payload.startsWith(DATA_PREFIX)) {
            payload = payload.substring(DATA_PREFIX.length()).trim();
        }
        return payload;
    }

    /**
     * 解析块 JSON（脏块记 debug 跳过）.
     *
     * @param payload 块文本
     * @return 根节点；无法解析时返回 {@code null}
     */
    private JsonNode readTree(final String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.debug("Anthropic 流式翻译: 跳过无法解析的块: {}", e.getOriginalMessage());
            return null;
        }
    }

    /**
     * 读取 {@code choices[0]}.
     *
     * @param root 块根节点
     * @return 首个 choice；缺失时返回 {@code null}
     */
    private JsonNode firstChoice(final JsonNode root) {
        final JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        return choices.get(0);
    }

    /**
     * 累积下游 {@code usage.completion_tokens}（末块提供时生效，后写覆盖先写）.
     *
     * @param root  块根节点
     * @param state 累积状态
     */
    private void accumulateUsage(final JsonNode root, final StreamState state) {
        final JsonNode usage = root.get("usage");
        if (usage == null || !usage.isObject()) {
            return;
        }
        final JsonNode completionTokens = usage.get("completion_tokens");
        if (completionTokens != null && completionTokens.isNumber()) {
            state.outputTokens = completionTokens.asLong();
        }
    }

    /**
     * 累积下游 {@code finish_reason}.
     *
     * @param choice 首个 choice
     * @param state  累积状态
     */
    private void accumulateFinishReason(final JsonNode choice, final StreamState state) {
        final JsonNode finishReason = choice.get("finish_reason");
        if (finishReason != null && !finishReason.isNull()) {
            state.finishReason = finishReason.asText();
        }
    }

    /**
     * 提取块中的文本增量.
     *
     * <p>兼容 {@code choices[0].delta.content}（字符串或分片数组）与 {@code choices[0].text}
     * 两种形态；缺失时返回空串（该块只可能携带 usage 或 finish_reason）。</p>
     *
     * @param choice 首个 choice
     * @return 文本增量（可能为空串）
     */
    private String extractDeltaText(final JsonNode choice) {
        final JsonNode delta = choice.get("delta");
        if (delta != null && delta.isObject()) {
            final String fromContent = textOf(delta.get("content"));
            if (!fromContent.isEmpty()) {
                return fromContent;
            }
        }
        return textOf(choice.get("text"));
    }

    /**
     * 读取文本字段（字符串或分片数组中的 {@code text}）.
     *
     * @param node JSON 节点
     * @return 文本；节点缺失/非文本形态返回空串
     */
    private String textOf(final JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            final StringBuilder builder = new StringBuilder();
            for (final JsonNode part : node) {
                if (part != null && part.isObject()) {
                    final JsonNode partText = part.get("text");
                    if (partText != null && partText.isTextual()) {
                        builder.append(partText.asText());
                    }
                }
            }
            return builder.toString();
        }
        return "";
    }

    /**
     * 构造 {@code event: error}（下游中途失败）.
     *
     * @param error 下游异常
     * @return 错误事件（事件名 + OpenAI/Anthropic 风格的 {@code {type,error:{type,message}}} 载荷）
     */
    private ServerSentEvent<String> errorEvent(final Throwable error) {
        return event(AnthropicStreamEvent.EVENT_ERROR,
                new AnthropicStreamEvent.ErrorEvent(
                        AnthropicStreamEvent.EVENT_ERROR,
                        new AnthropicStreamEvent.ErrorDetail(errorTypeOf(error), errorMessageOf(error))));
    }

    /**
     * 下游异常 → 面向客户端的错误描述.
     *
     * <p>{@link ResponseStatusException#getReason()} 优先（干净的业务原因），
     * 退化顺序：{@code getMessage()} → 异常类名。</p>
     *
     * @param error 下游异常
     * @return 错误描述（非空）
     */
    private String errorMessageOf(final Throwable error) {
        if (error instanceof ResponseStatusException statusException) {
            final String reason = statusException.getReason();
            if (reason != null && !reason.isBlank()) {
                return reason;
            }
        }
        final String message = error.getMessage();
        return (message == null || message.isBlank()) ? error.getClass().getSimpleName() : message;
    }

    /**
     * 下游异常 → Anthropic 错误类型.
     *
     * @param error 下游异常
     * @return {@code authentication_error} / {@code invalid_request_error} /
     *         {@code rate_limit_error} / {@code api_error}
     */
    private String errorTypeOf(final Throwable error) {
        if (error instanceof ResponseStatusException statusException) {
            final int status = statusException.getStatusCode().value();
            if (status == 401 || status == 403) {
                return "authentication_error";
            }
            if (status == 429) {
                return "rate_limit_error";
            }
            if (status == 400 || status == 404 || status == 422) {
                return "invalid_request_error";
            }
        }
        return ERROR_TYPE_API;
    }

    /**
     * 构造单个 SSE 事件（事件名 + data 段 JSON）.
     *
     * @param name    事件名
     * @param payload 载荷对象
     * @return 事件
     */
    private ServerSentEvent<String> event(final String name, final Object payload) {
        return ServerSentEvent.<String>builder()
                .event(name)
                .data(toJson(payload))
                .build();
    }

    /**
     * 序列化事件载荷.
     *
     * @param payload 载荷对象
     * @return JSON 文本；序列化异常时退化为 {@code {}} 并记 error（保证事件序列不断裂）
     */
    private String toJson(final Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Anthropic 流式翻译: 事件载荷序列化失败, 退化为空对象", e);
            return "{}";
        }
    }

    /**
     * long → int（钳制，token 数不会溢出 int，防御非受控超大估算值）.
     *
     * @param value 原始值
     * @return 钳制后的 int
     */
    private int clampToInt(final long value) {
        if (value <= 0) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    /**
     * 单次订阅的流式累积状态（在 {@code Flux.defer} 内创建，不跨订阅共享）.
     */
    private static final class StreamState {

        /**
         * 累计文本（用于下游未给 usage 时的输出 token 估算）.
         */
        private final StringBuilder text = new StringBuilder();

        /**
         * 下游提供的输出 token 数（0 表示下游未提供）.
         */
        private long outputTokens;

        /**
         * 下游最近一次非空 {@code finish_reason}.
         */
        private String finishReason;
    }
}

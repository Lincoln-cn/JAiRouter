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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.config.core.StreamingSafetyProperties;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>工具调用（PR-5）</b>：下游 {@code choices[0].delta.tool_calls[]} 被改写为
 * {@code tool_use} 内容块（下游 {@code index} 映射为独立 Anthropic 块，文本块恒为 index 0）：</p>
 *
 * <pre>
 * event: content_block_start  data: {index:n,content_block:{type:"tool_use",id:"...",name:"..."}}
 * event: content_block_delta  data: {index:n,delta:{type:"input_json_delta",partial_json:"<分片>"}}  （0..N 次）
 * event: content_block_stop   data: {index:n}
 * </pre>
 *
 * <p>块序列规则：</p>
 * <ul>
 *   <li>文本块恒为 index 0（保持既有行为）；文本与工具交替出现时，新块取「上一个块 index + 1」，</li>
 *   <li>任意块 start/delta/stop 三件套不交叉——切到新块前先发当前块的 {@code content_block_stop}；</li>
 *   <li>{@code message_delta.stop_reason} 在本次流出现过工具块时为 {@code tool_use}
 *       （下游已给 {@code length} 时仍保留 {@code max_tokens}，截断信息更准确）；</li>
 *   <li>{@code usage.output_tokens} 口径不变：优先下游 {@code usage.completion_tokens}，
 *       否则按累计文本 <b>+ 工具 arguments 片段</b>估算（无工具时与 PR-4c 完全一致）。</li>
 * </ul>
 *
 * <p>已知边界：下游按 {@code tool_calls[].index} 顺序连续分片（OpenAI/DeepSeek 实测形态）；
 * 若同一工具分片被下游拆成多段交叉下发，交叉处会为该工具开启新的内容块并记 debug
 * （事件序始终合法，客户端按 id 关联即可）。{@code tools} / {@code thinking} 的请求侧定义
 * 由 {@link AnthropicRequestTranslator} 负责，本类不消费请求。</p>
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
     * 文本内容块下标（首个文本块恒为 0，保持 PR-4c 行为）.
     */
    private static final int TEXT_BLOCK_INDEX = 0;

    /**
     * 工具调用块索引计数起点（0 已被文本块占用）.
     */
    private static final int FIRST_DYNAMIC_BLOCK_INDEX = 1;

    /**
     * 下游缺 {@code tool_calls[].id} 时生成的调用 ID 前缀.
     */
    private static final String TOOL_USE_ID_PREFIX = "toolu_";

    /**
     * 块类型：文本.
     */
    private static final int BLOCK_KIND_TEXT = 0;

    /**
     * 块类型：工具调用.
     */
    private static final int BLOCK_KIND_TOOL = 1;

    /**
     * Anthropic 错误事件类型：通用服务端错误.
     */
    private static final String ERROR_TYPE_API = "api_error";

    /**
     * v3.2.3 (#127): text/toolArguments 累积默认字符上限（与 StreamingRequestProcessor 同量级）.
     */
    private static final int DEFAULT_MAX_ACCUMULATED_CHARS = 1_048_576;

    /**
     * 估算系数：表意文字字符/token（与 {@link AnthropicTokenEstimator} 同源）.
     */
    private static final double CHINESE_CHARS_PER_TOKEN = 2.0;

    /**
     * 估算系数：其余非空白字符/token（与 {@link AnthropicTokenEstimator} 同源）.
     */
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;

    private final ObjectMapper objectMapper;

    /**
     * v3.2.3 (#127): text/toolArguments 累积字符上限（仅用于输出 token 估算兜底；
     * 发给客户端的 content_block_delta 不受影响）.
     */
    private final int maxAccumulatedChars;

    /**
     * 构造函数（Spring 装配）.
     *
     * @param objectMapper 全局 ObjectMapper（序列化各事件 data 段）
     * @param properties   流式安全配置（可选；缺省用宽松默认上界）
     */
    @Autowired
    public AnthropicStreamingTranslator(final ObjectMapper objectMapper,
                                        final StreamingSafetyProperties properties) {
        this(objectMapper, properties != null ? properties.getMaxContentChars() : 0);
    }

    /**
     * 构造函数（单测/缺省上界）.
     *
     * @param objectMapper 全局 ObjectMapper（序列化各事件 data 段）
     */
    public AnthropicStreamingTranslator(final ObjectMapper objectMapper) {
        this(objectMapper, 0);
    }

    private AnthropicStreamingTranslator(final ObjectMapper objectMapper, final int maxAccumulatedChars) {
        this.objectMapper = objectMapper;
        this.maxAccumulatedChars = maxAccumulatedChars > 0 ? maxAccumulatedChars : DEFAULT_MAX_ACCUMULATED_CHARS;
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
     * 累积文本增量：全量计入估算计数，有界写入 {@code state.text}.
     *
     * @param text  文本增量
     * @param state 订阅状态
     */
    private void accumulateText(final String text, final StreamState state) {
        countChars(text, state);
        appendBounded(state.text, text);
    }

    /**
     * 累积工具入参增量：全量计入估算计数，有界写入 {@code state.toolArguments}.
     *
     * @param arguments 入参增量
     * @param state     订阅状态
     */
    private void accumulateToolArguments(final String arguments, final StreamState state) {
        countChars(arguments, state);
        appendBounded(state.toolArguments, arguments);
    }

    /**
     * 将增量字符计入独立估算计数（汉字 / 其余非空白）.
     *
     * @param text  增量文本
     * @param state 订阅状态
     */
    private void countChars(final String text, final StreamState state) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                state.chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                state.otherChars++;
            }
        }
    }

    /**
     * 有界追加：达到 {@link #maxAccumulatedChars} 后不再增长（保留前缀）.
     *
     * @param target 累积缓冲
     * @param text   增量文本
     */
    private void appendBounded(final StringBuilder target, final String text) {
        final int remaining = maxAccumulatedChars - target.length();
        if (remaining <= 0) {
            return;
        }
        if (text.length() <= remaining) {
            target.append(text);
        } else {
            target.append(text, 0, remaining);
        }
    }

    /**
     * 由独立字符计数估算 token 数（与 {@link AnthropicTokenEstimator#estimateText(String)} 同系数）.
     *
     * @param chineseChars 汉字数
     * @param otherChars   其余非空白字符数
     * @return 估算 token 数（非负）
     */
    private static long estimateFromCounts(final long chineseChars, final long otherChars) {
        return (long) Math.ceil(chineseChars / CHINESE_CHARS_PER_TOKEN
                + otherChars / ENGLISH_CHARS_PER_TOKEN);
    }

    /**
     * 构造起始事件（{@code message_start} + 文本块 {@code content_block_start}）.
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
        events.add(contentBlockStart(TEXT_BLOCK_INDEX, new AnthropicStreamEvent.ContentBlock(
                AnthropicStreamEvent.CONTENT_BLOCK_TYPE_TEXT, "")));
        return events;
    }

    /**
     * 构造收尾事件（{@code content_block_stop} + {@code message_delta} + {@code message_stop}）.
     *
     * <p>仅在订阅尾段（下游流正常结束）时求值，因此可读取累计状态；收尾的
     * {@code content_block_stop} 关闭的是<b>当前仍打开</b>的块（无工具时恒为 index 0，
     * 与 PR-4c 逐字节一致）。</p>
     *
     * @param state 本次订阅的累积状态（累计文本、工具入参、输出用量、结束原因、块下标）
     * @return 收尾事件列表
     */
    private List<ServerSentEvent<String>> finishEvents(final StreamState state) {
        // v3.2.3 (#127): 估算走独立字符计数（全量增量已计入），不受累积上界截断影响
        final long outputTokens = state.outputTokens > 0
                ? state.outputTokens
                : estimateFromCounts(state.chineseChars, state.otherChars);

        final List<ServerSentEvent<String>> events = new ArrayList<>(3);
        if (state.openIndex >= 0) {
            events.add(contentBlockStop(state.openIndex));
        }
        events.add(event(AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                new AnthropicStreamEvent.MessageDelta(
                        AnthropicStreamEvent.EVENT_MESSAGE_DELTA,
                        new AnthropicStreamEvent.StopDelta(stopReasonOf(state), null),
                        new AnthropicStreamEvent.DeltaUsage(outputTokens))));
        events.add(event(AnthropicStreamEvent.EVENT_MESSAGE_STOP,
                new AnthropicStreamEvent.MessageStop(AnthropicStreamEvent.EVENT_MESSAGE_STOP)));
        return events;
    }

    /**
     * 本次流的 {@code stop_reason}.
     *
     * <p>映射规则与 PR-4c 一致（{@code stop→end_turn}、{@code length→max_tokens}、
     * {@code tool_calls→tool_use}、其他→{@code end_turn}），额外规则：只要本次流产出过工具块，
     * {@code end_turn} 上修为 {@code tool_use}（Claude Code 依此决定是否执行工具调用）；
     * 下游已给出 {@code max_tokens} 时保留（截断语义优先）。</p>
     *
     * @param state 累积状态
     * @return Anthropic {@code stop_reason}
     */
    private String stopReasonOf(final StreamState state) {
        final String mapped = AnthropicResponseTranslator.mapStopReason(state.finishReason);
        if (state.hasToolBlocks && AnthropicMessagesResponse.STOP_REASON_END_TURN.equals(mapped)) {
            return AnthropicMessagesResponse.STOP_REASON_TOOL_USE;
        }
        return mapped;
    }

    /**
     * 单个下游块 → 0..N 个内容块事件.
     *
     * @param downstreamEvent 下游 SSE 元素
     * @param state           本次订阅的累积状态
     * @return 事件列表；无增量/心跳/异常块返回空列表
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

        state.pending.clear();
        appendToolCallEvents(choice, state);
        final String text = extractDeltaText(choice);
        if (!text.isEmpty()) {
            accumulateText(text, state);
            appendTextDeltaEvents(text, state);
        }
        return List.copyOf(state.pending);
    }

    /**
     * 追加「工具调用分片」对应的块事件（PR-5）.
     *
     * <p>下游 {@code delta.tool_calls[].index} 首次出现 → 关闭当前块并开启 {@code tool_use} 块
     * （{@code id}/{@code name} 缺失时生成/留空）；后续分片 → {@code input_json_delta}。</p>
     *
     * @param choice 首个 choice
     * @param state  累积状态（事件累积在 {@code state.pending}）
     */
    private void appendToolCallEvents(final JsonNode choice, final StreamState state) {
        final JsonNode delta = choice.get("delta");
        if (delta == null || !delta.isObject()) {
            return;
        }
        final JsonNode toolCalls = delta.get("tool_calls");
        if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
            return;
        }
        for (final JsonNode toolCall : toolCalls) {
            if (toolCall == null || !toolCall.isObject()) {
                continue;
            }
            appendToolCallEvent(toolCall, state);
        }
    }

    /**
     * 单个工具分片 → 起始/增量事件.
     *
     * @param toolCall 下游 {@code tool_calls[]} 元素
     * @param state    累积状态
     */
    private void appendToolCallEvent(final JsonNode toolCall, final StreamState state) {
        final int downstreamIndex = toolCall.hasNonNull("index") ? toolCall.get("index").asInt() : 0;
        final ToolBlockState tool = state.toolAt(downstreamIndex);
        final JsonNode function = toolCall.get("function");
        fillToolIdentity(tool, toolCall, function);

        if (!state.isOpenTool(downstreamIndex)) {
            closeOpenBlock(state);
            final int blockIndex = state.nextBlockIndex++;
            if (tool.opened) {
                log.debug("Anthropic 流式翻译: 工具分片交叉下发, 已为其开启新内容块: 下游 index={}, 块 index={}",
                        downstreamIndex, blockIndex);
            }
            tool.blockIndex = blockIndex;
            tool.opened = true;
            state.openIndex = blockIndex;
            state.openKind = BLOCK_KIND_TOOL;
            state.openToolIndex = downstreamIndex;
            state.hasToolBlocks = true;
            state.pending.add(contentBlockStart(blockIndex,
                    new AnthropicStreamEvent.ToolUseBlock(
                            AnthropicStreamEvent.CONTENT_BLOCK_TYPE_TOOL_USE, tool.id, tool.name)));
        }

        final String arguments = function == null ? null : textOf(function.get("arguments"));
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        accumulateToolArguments(arguments, state);
        state.pending.add(event(AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                new AnthropicStreamEvent.ContentBlockDelta(
                        AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                        state.openIndex,
                        new AnthropicStreamEvent.InputJsonDelta(
                                AnthropicStreamEvent.DELTA_TYPE_INPUT_JSON, arguments))));
    }

    /**
     * 补齐工具块的 {@code id}/{@code name}（仅在该工具首次出现时有值，后续分片通常不再携带）.
     *
     * @param tool     工具块状态
     * @param toolCall 下游 {@code tool_calls[]} 元素
     * @param function 下游 {@code tool_calls[].function}
     */
    private void fillToolIdentity(final ToolBlockState tool, final JsonNode toolCall, final JsonNode function) {
        if (tool.id == null) {
            final String id = textOrNull(toolCall.get("id"));
            tool.id = id == null || id.isBlank() ? generateToolUseId() : id;
            if (id == null || id.isBlank()) {
                log.warn("Anthropic 流式翻译: 下游 tool_calls 缺 id, 已生成 {}", tool.id);
            }
        }
        if (tool.name == null) {
            final String name = function == null ? null : textOrNull(function.get("name"));
            tool.name = name == null ? "" : name;
        }
    }

    /**
     * 读取文本字段（不 trim，{@code arguments} 片段需原样透传）.
     *
     * @param node JSON 节点
     * @return 文本；节点缺失/null 或非文本时返回 {@code null}
     */
    private String textOrNull(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    /**
     * 追加「文本分片」对应的块事件.
     *
     * <p>当前打开的是工具块时，先关闭它再以新下标开启文本块——保证块三件套不交叉。</p>
     *
     * @param text  文本增量
     * @param state 累积状态
     */
    private void appendTextDeltaEvents(final String text, final StreamState state) {
        if (state.openKind != BLOCK_KIND_TEXT) {
            closeOpenBlock(state);
            final int blockIndex = state.nextBlockIndex++;
            state.openIndex = blockIndex;
            state.openKind = BLOCK_KIND_TEXT;
            state.openToolIndex = -1;
            state.pending.add(contentBlockStart(blockIndex, new AnthropicStreamEvent.ContentBlock(
                    AnthropicStreamEvent.CONTENT_BLOCK_TYPE_TEXT, "")));
        }
        state.pending.add(event(AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                new AnthropicStreamEvent.ContentBlockDelta(
                        AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA,
                        state.openIndex,
                        new AnthropicStreamEvent.TextDelta(
                                AnthropicStreamEvent.DELTA_TYPE_TEXT, text))));
    }

    /**
     * 关闭当前打开的内容块（若已关闭则无操作）.
     *
     * @param state 累积状态
     */
    private void closeOpenBlock(final StreamState state) {
        if (state.openIndex < 0) {
            return;
        }
        state.pending.add(contentBlockStop(state.openIndex));
        state.openIndex = -1;
        state.openKind = -1;
        state.openToolIndex = -1;
    }

    /**
     * 生成 {@code tool_use} 块 ID（下游缺 {@code id} 时）.
     *
     * @return {@code toolu_<32 位无连字符 UUID>}
     */
    private String generateToolUseId() {
        return TOOL_USE_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构造文本块起始事件.
     *
     * @param index 块下标
     * @param block 起始内容块
     * @return 事件
     */
    private ServerSentEvent<String> contentBlockStart(final int index, final Object block) {
        return event(AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START,
                new AnthropicStreamEvent.ContentBlockStart(
                        AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START, index, block));
    }

    /**
     * 构造内容块结束事件.
     *
     * @param index 块下标
     * @return 事件
     */
    private ServerSentEvent<String> contentBlockStop(final int index) {
        return event(AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP,
                new AnthropicStreamEvent.ContentBlockStop(
                        AnthropicStreamEvent.EVENT_CONTENT_BLOCK_STOP, index));
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
         * 累计文本（v3.2.3 #127: 有界；仅用于输出 token 估算兜底，发给客户端的事件不受影响）.
         */
        private final StringBuilder text = new StringBuilder();

        /**
         * 累计工具入参片段（与文本一起参与输出 token 估算；v3.2.3 #127: 有界）.
         */
        private final StringBuilder toolArguments = new StringBuilder();

        /**
         * v3.2.3 (#127): 全量文本+工具参数中的汉字数（独立于截断，估算用）.
         */
        private long chineseChars;

        /**
         * v3.2.3 (#127): 全量文本+工具参数中的其余非空白字符数（独立于截断，估算用）.
         */
        private long otherChars;

        /**
         * 当前块的事件缓冲（逐下游块构建，构建完成后交由 Flux 消费）.
         */
        private final List<ServerSentEvent<String>> pending = new ArrayList<>(4);

        /**
         * 下游工具下标 → 工具块状态（{@code id}/{@code name}/{@code 块 index}）.
         */
        private final Map<Integer, ToolBlockState> tools = new HashMap<>();

        /**
         * 下游提供的输出 token 数（0 表示下游未提供）.
         */
        private long outputTokens;

        /**
         * 下游最近一次非空 {@code finish_reason}.
         */
        private String finishReason;

        /**
         * 当前打开的内容块下标（{@code -1} 表示无打开块）.
         */
        private int openIndex = TEXT_BLOCK_INDEX;

        /**
         * 当前打开的内容块类型（{@link #BLOCK_KIND_TEXT} / {@link #BLOCK_KIND_TOOL}，{@code -1} 表示无）.
         */
        private int openKind = BLOCK_KIND_TEXT;

        /**
         * 当前打开的工具块对应的下游下标（非工具块为 {@code -1}）.
         */
        private int openToolIndex = -1;

        /**
         * 下一个可分配的内容块下标（0 已被首个文本块占用）.
         */
        private int nextBlockIndex = FIRST_DYNAMIC_BLOCK_INDEX;

        /**
         * 本次流是否产出过工具块（决定 {@code stop_reason} 是否上修为 {@code tool_use}）.
         */
        private boolean hasToolBlocks;

        /**
         * 取（或创建）下游工具下标对应的块状态.
         *
         * @param downstreamIndex 下游 {@code tool_calls[].index}
         * @return 工具块状态
         */
        private ToolBlockState toolAt(final int downstreamIndex) {
            return tools.computeIfAbsent(downstreamIndex, key -> new ToolBlockState());
        }

        /**
         * 判断指定下游工具是否为当前打开的块.
         *
         * @param downstreamIndex 下游 {@code tool_calls[].index}
         * @return 是当前打开的工具块返回 true
         */
        private boolean isOpenTool(final int downstreamIndex) {
            return openKind == BLOCK_KIND_TOOL && openToolIndex == downstreamIndex;
        }
    }

    /**
     * 单个下游工具调用的块状态.
     */
    private static final class ToolBlockState {

        /**
         * 工具调用 ID（首次出现时确定，缺失则生成 {@code toolu_*}）.
         */
        private String id;

        /**
         * 工具名（首次出现时确定，缺失则空串）.
         */
        private String name;

        /**
         * 当前对应的 Anthropic 块下标（交叉下发时更新为最新块）.
         */
        private int blockIndex = -1;

        /**
         * 是否已开启过内容块（交叉下发时用于记 debug）.
         */
        private boolean opened;
    }
}

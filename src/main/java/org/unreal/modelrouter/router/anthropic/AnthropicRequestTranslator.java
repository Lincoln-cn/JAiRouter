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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.dto.ChatDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anthropic Messages 请求 → 内部统一 {@link ChatDTO.Request} 翻译器（v3.1 PR-4b/4c/5）.
 *
 * <p>映射规则：</p>
 * <ul>
 *   <li>{@code system}（字符串或文本块数组）→ 首条 {@code role=system} 消息（内容为文本块拼接）；</li>
 *   <li>{@code messages[].content} 块数组 → 只取 {@code type=text} 块拼接为内部 {@code messages}
 *       的文本视图；其他块类型记 debug 日志并跳过；</li>
 *   <li>{@code max_tokens → maxTokens}、{@code temperature → temperature}、{@code top_p → topP}；</li>
 *   <li>{@code stop_sequences → stop}（原样以列表透传；空列表转 {@code null}）；</li>
 *   <li>{@code stream} 由调用方显式指定（PR-4c）：{@code /v1/messages} 非流式分支恒 {@code FALSE}、
 *       流式分支恒 {@code TRUE}，单参入口保持 {@code FALSE} 语义。</li>
 * </ul>
 *
 * <p><b>工具调用（PR-5）</b>：{@code tools} / {@code tool_choice} 与消息中的
 * {@code tool_use} / {@code tool_result} 块映射为 <b>OpenAI 兼容 wire 形状</b>，通过
 * {@link ChatDTO.Options} 的三个透传字段交给 OpenAI 兼容适配器：</p>
 * <ul>
 *   <li>{@code tools[{name,description,input_schema}]} →
 *       {@code tools:[{type:"function",function:{name,description,parameters:<input_schema>}}]}；</li>
 *   <li>{@code tool_choice}：{@code "auto"|{"type":"auto"} → "auto"}、
 *       {@code "any"|{"type":"any"} → "required"}、
 *       {@code {"type":"tool","name":X} → {"type":"function","function":{"name":X}}}、
 *       {@code "none" → 整体不传 tools/tool_choice}；</li>
 *   <li>assistant 的 {@code tool_use{id,name,input}} → 该条 assistant 消息的
 *       {@code tool_calls:[{id,type:"function",function:{name,arguments:JSON.stringify(input)}}]}
 *       （同消息内文本进 {@code content}，多个 {@code tool_use} 保持块序）；</li>
 *   <li>user 的 {@code tool_result{tool_use_id,content}} → 独立 {@code role:"tool"} 消息
 *       （{@code tool_call_id=tool_use_id}，{@code content} 取文本），与同消息内的文本消息按块序排列。</li>
 * </ul>
 *
 * <p>只有「请求带 tools」或「会话中出现工具块」时才填充 {@link ChatDTO.Options}：
 * 纯文本请求的 {@link ChatDTO.Request} 与 PR-4c 完全一致（{@code options == null}），
 * 因此既有用例与既有面行为逐字节不变。</p>
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
     * Anthropic 内容块类型：工具调用（模型发起）.
     */
    private static final String CONTENT_TYPE_TOOL_USE = "tool_use";

    /**
     * Anthropic 内容块类型：工具结果（客户端回填）.
     */
    private static final String CONTENT_TYPE_TOOL_RESULT = "tool_result";

    /**
     * 下游 OpenAI 角色：工具结果消息.
     */
    private static final String ROLE_TOOL = "tool";

    /**
     * 下游 OpenAI 角色：助手.
     */
    private static final String ROLE_ASSISTANT = "assistant";

    /**
     * OpenAI 工具条目类型.
     */
    private static final String TOOL_WIRE_TYPE_FUNCTION = "function";

    /**
     * Anthropic {@code tool_choice}：由模型自主决定.
     */
    private static final String TOOL_CHOICE_AUTO = "auto";

    /**
     * Anthropic {@code tool_choice}：必须调用某个工具.
     */
    private static final String TOOL_CHOICE_ANY = "any";

    /**
     * Anthropic {@code tool_choice}：禁用工具.
     */
    private static final String TOOL_CHOICE_NONE = "none";

    /**
     * Anthropic {@code tool_choice}：指定工具（{@code {"type":"tool","name":...}}）.
     */
    private static final String TOOL_CHOICE_TOOL = "tool";

    /**
     * OpenAI {@code tool_choice}：必须调用工具.
     */
    private static final String OPENAI_TOOL_CHOICE_REQUIRED = "required";

    /**
     * 下游缺失 {@code tool_use.id} 时生成的调用 ID 前缀.
     */
    private static final String TOOL_CALL_ID_PREFIX = "call_";

    /**
     * {@code tools[].input_schema} 缺失时补的兜底 JSON Schema（OpenAI 要求 {@code parameters} 存在）.
     */
    private static final Map<String, Object> EMPTY_PARAMETERS =
            Map.of("type", "object", "properties", Map.of());

    /**
     * 序列化工具入参（{@code input} → {@code arguments} 字符串）.
     *
     * <p>仅做 ASCII/JSON 序列化，不依赖全局 {@code ObjectMapper} 的 NON_NULL 等策略，
     * 因此用私有静态实例（与 {@code ResponseCacheKeyBuilder} 同风格），
     * 避免为既有构造函数引入新依赖。</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

        final ChatDTO.Options options = buildToolOptions(request);

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
                options);
    }

    /**
     * 构造工具调用相关的扩展选项（PR-5）.
     *
     * <p>返回 {@code null} 表示「本次请求与工具调用无关」——此时内部 DTO 与 PR-4c 逐字段一致
     * （{@code options == null}），既有链路（配额估算、响应缓存键、下游请求体）行为不变。</p>
     *
     * @param request Anthropic 请求体
     * @return 扩展选项；无工具定义且会话中无工具块时返回 {@code null}
     */
    private ChatDTO.Options buildToolOptions(final AnthropicMessagesRequest request) {
        final boolean hasToolBlocks = containsToolBlocks(request.messages());
        final boolean toolsDisabled = isToolChoiceNone(request.toolChoice());
        final List<Map<String, Object>> tools = toolsDisabled ? List.of() : toWireTools(request.tools());
        if (tools.isEmpty() && !hasToolBlocks) {
            return null;
        }

        return ChatDTO.Options.builder()
                .tools(tools.isEmpty() ? null : tools)
                .toolChoice(tools.isEmpty() ? null : toWireToolChoice(request.toolChoice()))
                .wireMessages(buildWireMessages(request))
                .build();
    }

    /**
     * {@code tool_choice:"none"} 判定（该取值下按协议「禁用工具」，整体不向下游传 tools/tool_choice）.
     *
     * @param toolChoice Anthropic {@code tool_choice}（字符串或对象）
     * @return 显式禁用工具时返回 true
     */
    private boolean isToolChoiceNone(final Object toolChoice) {
        return TOOL_CHOICE_NONE.equals(choiceTypeOf(toolChoice));
    }

    /**
     * 会话中是否出现工具块（{@code tool_use} / {@code tool_result}）.
     *
     * @param messages 消息列表（可为 {@code null}）
     * @return 任一消息含工具块时返回 true
     */
    private boolean containsToolBlocks(final List<AnthropicMessagesRequest.Message> messages) {
        if (messages == null) {
            return false;
        }
        for (final AnthropicMessagesRequest.Message message : messages) {
            if (message == null || message.content() == null) {
                continue;
            }
            for (final AnthropicMessagesRequest.ContentBlock block : message.content()) {
                if (isToolUse(block) || isToolResult(block)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code tools} → OpenAI 顶层 {@code tools}.
     *
     * <p>{@code input_schema} 原样作为 {@code parameters}；缺失时补空的 object schema
     * （OpenAI/DeepSeek 要求 {@code parameters} 存在）。无名工具条目记 warn 并跳过。</p>
     *
     * @param tools Anthropic 工具定义（可为 {@code null}）
     * @return OpenAI 工具数组（无有效条目时为空列表）
     */
    private List<Map<String, Object>> toWireTools(final List<AnthropicMessagesRequest.Tool> tools) {
        final List<Map<String, Object>> wireTools = new ArrayList<>();
        if (tools == null) {
            return wireTools;
        }
        for (final AnthropicMessagesRequest.Tool tool : tools) {
            if (tool == null || tool.name() == null || tool.name().isBlank()) {
                log.warn("Anthropic tools 条目缺少 name, 已跳过: {}", tool);
                continue;
            }
            final Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            if (tool.description() != null) {
                function.put("description", tool.description());
            }
            function.put("parameters", tool.inputSchema() != null ? tool.inputSchema() : EMPTY_PARAMETERS);

            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", TOOL_WIRE_TYPE_FUNCTION);
            entry.put("function", function);
            wireTools.add(entry);
        }
        return wireTools;
    }

    /**
     * {@code tool_choice} → OpenAI {@code tool_choice}.
     *
     * <p>映射：{@code auto→auto}、{@code any→required}、{@code {type:tool,name}→{type:function,function:{name}}}；
     * {@code none} 由调用方在更早处短路（不传 tools）。未知取值记 warn 并按「不指定」处理（下游默认 auto）。</p>
     *
     * @param toolChoice Anthropic {@code tool_choice}（字符串或对象，可为 {@code null}）
     * @return OpenAI {@code tool_choice}；无需指定时返回 {@code null}
     */
    private Object toWireToolChoice(final Object toolChoice) {
        final String type = choiceTypeOf(toolChoice);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case TOOL_CHOICE_AUTO -> TOOL_CHOICE_AUTO;
            case TOOL_CHOICE_ANY -> OPENAI_TOOL_CHOICE_REQUIRED;
            case TOOL_CHOICE_TOOL -> functionChoice(toolChoice);
            case TOOL_CHOICE_NONE -> null;
            default -> {
                log.warn("Anthropic tool_choice 取值未知, 按未指定处理(下游默认 auto): {}", toolChoice);
                yield null;
            }
        };
    }

    /**
     * {@code {"type":"tool","name":X}} → {@code {"type":"function","function":{"name":X}}}.
     *
     * @param toolChoice Anthropic {@code tool_choice}
     * @return OpenAI 指定函数选择；缺 {@code name} 时返回 {@code null}
     */
    private Object functionChoice(final Object toolChoice) {
        final String name = namedChoiceName(toolChoice);
        if (name == null || name.isBlank()) {
            log.warn("Anthropic tool_choice 指定了工具但缺少 name, 按未指定处理: {}", toolChoice);
            return null;
        }
        final Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);

        final Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("type", TOOL_WIRE_TYPE_FUNCTION);
        choice.put("function", function);
        return choice;
    }

    /**
     * 取 {@code tool_choice} 的 {@code type} 字段（兼容字符串与对象两种形态）.
     *
     * @param toolChoice Anthropic {@code tool_choice}
     * @return 类型字符串；缺失或形态不识别时返回 {@code null}
     */
    private String choiceTypeOf(final Object toolChoice) {
        if (toolChoice instanceof String text) {
            return text.isBlank() ? null : text;
        }
        if (toolChoice instanceof Map<?, ?> map) {
            final Object type = map.get("type");
            return type == null ? null : String.valueOf(type);
        }
        return null;
    }

    /**
     * 取 {@code tool_choice} 的 {@code name} 字段（仅对象形态携带）.
     *
     * @param toolChoice Anthropic {@code tool_choice}
     * @return 工具名；缺失时返回 {@code null}
     */
    private String namedChoiceName(final Object toolChoice) {
        if (toolChoice instanceof Map<?, ?> map) {
            final Object name = map.get("name");
            return name == null ? null : String.valueOf(name);
        }
        return null;
    }

    /**
     * 构造 OpenAI 兼容的 {@code messages} 覆盖值.
     *
     * <p>与文本视图的差异只在工具块：</p>
     * <ul>
     *   <li>assistant：文本块 → {@code content}，{@code tool_use} 块 → {@code tool_calls}（保持块序，
     *       {@code arguments} 为 {@code input} 的 JSON 文本）；</li>
     *   <li>user：文本块 → {@code content}，{@code tool_result} 块 → 独立 {@code role:"tool"} 消息
     *       （按块序插入，保证「文本 + 工具结果」混排时相对顺序不变）；</li>
     *   <li>其他块类型（{@code image} / {@code thinking} …）与文本视图一致地跳过并记 debug；
     *       整条消息无可用块时补一条空文本消息，保持角色序列不被破坏。</li>
     * </ul>
     *
     * @param request Anthropic 请求体
     * @return OpenAI 兼容消息列表
     */
    private List<Map<String, Object>> buildWireMessages(final AnthropicMessagesRequest request) {
        final List<Map<String, Object>> wireMessages = new ArrayList<>();
        // 与文本视图一致：system 拼接为独立首条消息
        final String systemText = joinTextBlocks(request.system(), "system");
        if (systemText != null && !systemText.isEmpty()) {
            wireMessages.add(simpleMessage("system", systemText));
        }
        if (request.messages() == null) {
            return wireMessages;
        }
        for (final AnthropicMessagesRequest.Message message : request.messages()) {
            if (message == null) {
                continue;
            }
            final List<AnthropicMessagesRequest.ContentBlock> blocks = message.content();
            final boolean produced = ROLE_ASSISTANT.equals(message.role())
                    ? appendAssistantMessage(wireMessages, blocks)
                    : appendUserMessage(wireMessages, message.role(), blocks);
            if (!produced) {
                wireMessages.add(simpleMessage(message.role(), ""));
            }
        }
        return wireMessages;
    }

    /**
     * assistant 消息 → 单条 OpenAI assistant 消息（{@code content} + {@code tool_calls}）.
     *
     * @param target 目标列表
     * @param blocks 内容块（可为 {@code null}）
     * @return 是否产出了消息
     */
    private boolean appendAssistantMessage(final List<Map<String, Object>> target,
                                           final List<AnthropicMessagesRequest.ContentBlock> blocks) {
        if (blocks == null) {
            return false;
        }
        final StringBuilder text = new StringBuilder();
        final List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> message = null;
        for (final AnthropicMessagesRequest.ContentBlock block : blocks) {
            if (isTextBlock(block)) {
                if (message == null) {
                    message = baseMessage(ROLE_ASSISTANT);
                    target.add(message);
                }
                text.append(block.text() == null ? "" : block.text());
            } else if (isToolUse(block)) {
                if (message == null) {
                    message = baseMessage(ROLE_ASSISTANT);
                    target.add(message);
                }
                toolCalls.add(toToolCall(block));
            } else {
                logSkippedBlock(block, "messages.content");
            }
        }
        if (message == null) {
            return false;
        }
        // 只有工具调用时 content 置空串（OpenAI/DeepSeek 均接受，且比 null 更宽容）
        message.put("content", text.toString());
        if (!toolCalls.isEmpty()) {
            message.put("tool_calls", toolCalls);
        }
        return true;
    }

    /**
     * user（或其他非 assistant 角色）消息 → 工具结果消息 + 文本消息.
     *
     * <p>顺序取舍：{@code role:"tool"} 消息<b>先于</b>同消息内的文本消息发出。OpenAI 兼容下游
     * （含 DeepSeek）要求 {@code role:"tool"} 紧跟带 {@code tool_calls} 的 assistant 消息，
     * 若把用户文本插在中间，下游会以「tool 消息未紧邻对应 tool_calls」报 400——因此这里按
     * 下游协议约束排序，并保持多个 {@code tool_result} 之间的块序。</p>
     *
     * @param target 目标列表
     * @param role   消息角色
     * @param blocks 内容块（可为 {@code null}）
     * @return 是否产出了消息
     */
    private boolean appendUserMessage(final List<Map<String, Object>> target,
                                      final String role,
                                      final List<AnthropicMessagesRequest.ContentBlock> blocks) {
        if (blocks == null) {
            return false;
        }
        boolean produced = false;
        for (final AnthropicMessagesRequest.ContentBlock block : blocks) {
            if (isToolResult(block)) {
                target.add(toToolMessage(block));
                produced = true;
            }
        }

        final StringBuilder text = new StringBuilder();
        boolean hasText = false;
        for (final AnthropicMessagesRequest.ContentBlock block : blocks) {
            if (isTextBlock(block)) {
                text.append(block.text() == null ? "" : block.text());
                hasText = true;
            } else if (!isToolResult(block)) {
                logSkippedBlock(block, "messages.content");
            }
        }
        if (hasText) {
            target.add(simpleMessage(role, text.toString()));
            produced = true;
        }
        return produced;
    }

    /**
     * {@code tool_use} 块 → OpenAI {@code tool_calls} 条目.
     *
     * @param block {@code tool_use} 块
     * @return {@code {id,type:"function",function:{name,arguments}}}
     */
    private Map<String, Object> toToolCall(final AnthropicMessagesRequest.ContentBlock block) {
        final String id = (block.id() == null || block.id().isBlank())
                ? TOOL_CALL_ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
                : block.id();
        if (block.id() == null || block.id().isBlank()) {
            log.debug("Anthropic tool_use 缺 id, 已生成下游调用 ID: name={}", block.name());
        }

        final Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", block.name() == null ? "" : block.name());
        function.put("arguments", toJson(block.input() == null ? Map.of() : block.input()));

        final Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", id);
        toolCall.put("type", TOOL_WIRE_TYPE_FUNCTION);
        toolCall.put("function", function);
        return toolCall;
    }

    /**
     * {@code tool_result} 块 → OpenAI {@code role:"tool"} 消息.
     *
     * @param block {@code tool_result} 块
     * @return {@code {role:"tool",tool_call_id,content}}
     */
    private Map<String, Object> toToolMessage(final AnthropicMessagesRequest.ContentBlock block) {
        final Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", ROLE_TOOL);
        message.put("tool_call_id", block.toolUseId() == null ? "" : block.toolUseId());
        message.put("content", flattenToolResultContent(block));
        if (block.toolUseId() == null || block.toolUseId().isBlank()) {
            log.warn("Anthropic tool_result 缺 tool_use_id, 下游消息的 tool_call_id 置空串");
        }
        return message;
    }

    /**
     * {@code tool_result.content}（字符串或内容块数组）→ 下游所需文本.
     *
     * @param block {@code tool_result} 块
     * @return 压平后的文本；无内容时返回空串
     */
    private String flattenToolResultContent(final AnthropicMessagesRequest.ContentBlock block) {
        final Object content = block.toolResultContent();
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> items) {
            final StringBuilder builder = new StringBuilder();
            for (final Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    final Object partText = map.get("text");
                    if (partText != null) {
                        builder.append(partText);
                    }
                } else if (item instanceof String text) {
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        if (content instanceof Map<?, ?> map) {
            final Object partText = map.get("text");
            return partText == null ? toJson(map) : String.valueOf(partText);
        }
        return String.valueOf(content);
    }

    /**
     * 构造角色的空壳消息（{@code content} 由调用方补齐）.
     *
     * @param role 角色（{@code null} 时不写入该字段，与文本视图的非空策略一致）
     * @return 可变消息 Map（保持字段顺序：role → content → …）
     */
    private Map<String, Object> baseMessage(final String role) {
        final Map<String, Object> message = new LinkedHashMap<>();
        if (role != null) {
            message.put("role", role);
        }
        return message;
    }

    /**
     * 构造仅含 {@code role}/{@code content} 的消息.
     *
     * @param role    角色
     * @param content 文本内容
     * @return 消息 Map
     */
    private Map<String, Object> simpleMessage(final String role, final String content) {
        final Map<String, Object> message = baseMessage(role);
        message.put("content", content);
        return message;
    }

    /**
     * 序列化工具入参为 JSON 文本.
     *
     * @param value 待序列化值
     * @return JSON 文本；序列化异常时退化为空对象 {@code {}} 并记 warn
     */
    private String toJson(final Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Anthropic 工具入参序列化失败, 退化为空对象: {}", e.getOriginalMessage());
            return "{}";
        }
    }

    /**
     * 记录被跳过的非文本/非工具块.
     *
     * @param block 内容块
     * @param where 日志定位信息
     */
    private void logSkippedBlock(final AnthropicMessagesRequest.ContentBlock block, final String where) {
        log.debug("Anthropic {} 中的块已跳过: type={}", where, block == null ? null : block.type());
    }

    /**
     * 是否 {@code tool_use} 块.
     *
     * @param block 内容块（可为 {@code null}）
     * @return 是工具调用块返回 true
     */
    private boolean isToolUse(final AnthropicMessagesRequest.ContentBlock block) {
        return block != null && CONTENT_TYPE_TOOL_USE.equals(block.type());
    }

    /**
     * 是否 {@code tool_result} 块.
     *
     * @param block 内容块（可为 {@code null}）
     * @return 是工具结果块返回 true
     */
    private boolean isToolResult(final AnthropicMessagesRequest.ContentBlock block) {
        return block != null && CONTENT_TYPE_TOOL_RESULT.equals(block.type());
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
     * @param block 内容块（可为 {@code null}）
     * @return 文本块返回 true
     */
    private boolean isTextBlock(final AnthropicMessagesRequest.ContentBlock block) {
        if (block == null) {
            return false;
        }
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

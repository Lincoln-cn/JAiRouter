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

package org.unreal.modelrouter.router.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.exceptionhandler.V1ErrorBodyMapper;
import org.unreal.modelrouter.monitor.monitoring.metrics.SseConnectionMetrics;
import org.unreal.modelrouter.router.anthropic.AnthropicCountTokensResponse;
import org.unreal.modelrouter.router.anthropic.AnthropicMessagesRequest;
import org.unreal.modelrouter.router.anthropic.AnthropicMessagesResponse;
import org.unreal.modelrouter.router.anthropic.AnthropicRequestTranslator;
import org.unreal.modelrouter.router.anthropic.AnthropicResponseTranslator;
import org.unreal.modelrouter.router.anthropic.AnthropicStreamingTranslator;
import org.unreal.modelrouter.router.anthropic.AnthropicTokenEstimator;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 协议入口控制器（{@code POST /v1/messages} + {@code /v1/messages/count_tokens}）.
 *
 * <p>让 Claude Code 零改造直连本网关：客户端把 {@code ANTHROPIC_BASE_URL} 指向
 * {@code http://localhost:8080}，请求经网关鉴权/配额/路由后落到任意下游实例（OpenAI / DeepSeek /
 * vLLM / Claude …），响应再翻译回 Anthropic 形状（非流式 Message / 流式事件序列）。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>请求体翻译为内部 {@link ChatDTO.Request}（{@link AnthropicRequestTranslator}）后，
 *       经 {@link ServiceRequestHandler#handleRequest} 复用既有全部链路；</li>
 *   <li>非流式：在 exchange 上置 {@link ServiceRequestHandler#NATIVE_RESPONSE_ATTRIBUTE}，
 *       拿到<b>下游原生 JSON 字符串</b>（不包 {@code RouterResponse}），再翻译为 Anthropic Message；</li>
 *   <li>流式（PR-4c）：同一条链路返回的 {@code Flux<ServerSentEvent<String>>} 为下游
 *       OpenAI 风格 chunk，由 {@link AnthropicStreamingTranslator} 逐块改写为 Anthropic
 *       事件序列（{@code message_start → content_block_* → message_delta → message_stop}）；</li>
 *   <li>两种模式均置 {@link ServiceRequestHandler#REQUEST_DTO_ATTRIBUTE}（响应缓存键依赖原始 DTO）；</li>
 *   <li>工具调用（PR-5）：{@code tools} / {@code tool_choice} 与 {@code tool_use} / {@code tool_result}
 *       块由 {@link AnthropicRequestTranslator} 转成 OpenAI 兼容 wire（经 {@code ChatDTO.Options}），
 *       非流式响应的 {@code tool_calls} 与流式 {@code delta.tool_calls} 分片分别由
 *       {@link AnthropicResponseTranslator} / {@link AnthropicStreamingTranslator} 转回
 *       Anthropic {@code tool_use} 块（{@code stop_reason: tool_use}）——Claude Code 的工具能力因此可用；</li>
 *   <li>{@code count_tokens}（PR-4c）纯本地估算，<b>不触达下游</b>，与流式 {@code message_start}
 *       的 {@code input_tokens} 同源（{@link AnthropicTokenEstimator}）；</li>
 *   <li>缺 {@code model} 一律 400 + Anthropic 错误体。</li>
 * </ul>
 *
 * <p>鉴权：沿用 {@code /v1/**} 的既有策略（{@code X-API-Key}/{@code x-api-key} 网关凭据或
 * {@code X-Jairouter-Token} JWT）；{@code Authorization} 透传下游且<b>实例级 headers 优先</b>。
 * 客户端放在 {@code x-api-key} 的是<b>网关凭据</b>（由安全层消费），因此不向下游转发——下游鉴权
 * 依赖实例级 headers 配置。{@code anthropic-version} / {@code anthropic-beta} 头仅接收不校验。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@Tag(name = "Anthropic 原生接口",
        description = "Anthropic Messages 协议入口（Claude Code 可零改造直连，支持非流式/流式与工具调用）")
public class AnthropicMessagesController {

    /**
     * Anthropic 错误体中的对象类型.
     */
    private static final String ERROR_OBJECT_TYPE = "error";

    /**
     * Anthropic 错误类型：上游过载（Anthropic 用 529 表示；网关自身不产出该状态码，只在下游透传时保留语义）.
     */
    private static final String ERROR_TYPE_OVERLOADED = "overloaded_error";

    /**
     * Anthropic 的「上游过载」状态码（{@link V1ErrorBodyMapper#errorTypeOf(int)} 未收录，需单独映射）.
     */
    private static final int HTTP_STATUS_OVERLOADED = 529;

    /**
     * 透出给客户端的下游错误消息上限（字符数）：下游错误体可能是整页 HTML，不能原样塞进错误体.
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    /**
     * 下游错误体里可直接取作消息的字段（按优先级）.
     */
    private static final List<String> MESSAGE_FIELDS = List.of("message", "detail", "error_description");

    /**
     * 只读解析下游错误体用的 ObjectMapper（{@code readTree} 线程安全）.
     */
    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

    private final ServiceRequestHandler requestHandler;
    private final AnthropicRequestTranslator requestTranslator;
    private final AnthropicResponseTranslator responseTranslator;
    private final AnthropicStreamingTranslator streamingTranslator;

    /** #947：流式端点连接计数（可选注入；未装配时流语义完全不变） */
    @Autowired(required = false)
    private SseConnectionMetrics sseConnectionMetrics;

    /**
     * 构造函数.
     *
     * @param requestHandler      统一请求处理器（复用路由/适配器/配额链路）
     * @param requestTranslator   Anthropic → 内部 Chat 请求翻译器
     * @param responseTranslator  下游原生响应 → Anthropic Message 翻译器（非流式）
     * @param streamingTranslator 下游 SSE 流 → Anthropic 事件流翻译器（流式，PR-4c）
     */
    public AnthropicMessagesController(final ServiceRequestHandler requestHandler,
                                       final AnthropicRequestTranslator requestTranslator,
                                       final AnthropicResponseTranslator responseTranslator,
                                       final AnthropicStreamingTranslator streamingTranslator) {
        this.requestHandler = requestHandler;
        this.requestTranslator = requestTranslator;
        this.responseTranslator = responseTranslator;
        this.streamingTranslator = streamingTranslator;
    }

    /**
     * Messages 接口（Anthropic 原生格式，支持非流式与流式）.
     *
     * @param authorization      透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param anthropicVersion   客户端协议版本头（接收但不校验）
     * @param anthropicBeta      客户端 beta 特性头（接收但不校验）
     * @param request            Anthropic 请求体
     * @param exchange           当前交换对象
     * @return 非流式：Anthropic Message（{@code application/json}）；
     *         流式：Anthropic 事件序列（{@code text/event-stream}）；
     *         参数不合法：Anthropic 错误体（400）
     */
    @PostMapping("/messages")
    public Mono<ResponseEntity<?>> messages(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestHeader(value = "anthropic-version", required = false) final String anthropicVersion,
            @RequestHeader(value = "anthropic-beta", required = false) final String anthropicBeta,
            @RequestBody(required = false) final AnthropicMessagesRequest request,
            final ServerWebExchange exchange) {

        log.debug("Anthropic /v1/messages: stream={}, anthropic-version={}, anthropic-beta={}（版本/beta 头不校验）",
                request == null ? null : request.stream(), anthropicVersion, anthropicBeta);

        if (request == null || request.model() == null || request.model().isBlank()) {
            log.warn("Anthropic /v1/messages 缺少 model 字段, 拒绝请求");
            return Mono.just(anthropicError(HttpStatus.BAD_REQUEST, "model 为必填字段"));
        }
        if (Boolean.TRUE.equals(request.stream())) {
            return streamMessages(authorization, request, exchange);
        }

        final ChatDTO.Request chatRequest = requestTranslator.toChatRequest(request);

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, chatRequest);

        return requestHandler.handleRequest(
                ServiceEndpoint.CHAT,
                chatRequest.model(),
                authorization,
                exchange,
                (adapter, auth, httpRequest) -> adapter.chat(chatRequest, auth, httpRequest))
            .map(response -> toAnthropicResponse(response, chatRequest.model()));
    }

    /**
     * Token 计数接口（Anthropic 原生格式，PR-4c）.
     *
     * <p>入参与 {@code /v1/messages} 相同，但<b>忽略</b> {@code max_tokens} / {@code stream} /
     * {@code tools}：只按 {@code system} + {@code messages} 的文本量估算 {@code input_tokens}。
     * 纯本地计算，不触达下游、不消耗配额、无下游鉴权要求（仅需 {@code /v1/**} 的网关认证）。</p>
     *
     * @param request Anthropic 请求体（同 messages 体）
     * @return {@code {"input_tokens":N}}；请求体缺失时 400 + Anthropic 错误体
     */
    @PostMapping("/messages/count_tokens")
    public Mono<ResponseEntity<?>> countTokens(@RequestBody(required = false) final AnthropicMessagesRequest request) {
        if (request == null) {
            log.warn("Anthropic /v1/messages/count_tokens 缺少请求体, 拒绝请求");
            return Mono.just(anthropicError(HttpStatus.BAD_REQUEST, "请求体为必填字段"));
        }
        final long inputTokens = AnthropicTokenEstimator.estimateRequest(request);
        log.debug("Anthropic /v1/messages/count_tokens: model={}, input_tokens={}", request.model(), inputTokens);
        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AnthropicCountTokensResponse(inputTokens)));
    }

    /**
     * 流式 Messages 分支：复用既有流式链路并改写为 Anthropic 事件序列.
     *
     * <p>内部 DTO 的 {@code stream} 恒为 {@link Boolean#TRUE}；{@code input_tokens} 用请求侧估算
     * （与 {@code count_tokens} 同源），在首个下游块到达<b>之前</b>写入 {@code message_start}。</p>
     *
     * @param authorization 透传下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param request       Anthropic 请求体（{@code stream=true}）
     * @param exchange      当前交换对象
     * @return Anthropic 事件流的 {@code text/event-stream} 响应
     */
    private Mono<ResponseEntity<?>> streamMessages(final String authorization,
                                                   final AnthropicMessagesRequest request,
                                                   final ServerWebExchange exchange) {
        final ChatDTO.Request chatRequest = requestTranslator.toChatRequest(request, Boolean.TRUE);
        final long inputTokens = AnthropicTokenEstimator.estimateRequest(request);

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, chatRequest);

        return requestHandler.handleRequest(
                ServiceEndpoint.CHAT,
                chatRequest.model(),
                authorization,
                exchange,
                (adapter, auth, httpRequest) -> adapter.chat(chatRequest, auth, httpRequest))
            .map(response -> toAnthropicStreamResponse(response, chatRequest.model(), inputTokens));
    }

    /**
     * 下游流式响应 → Anthropic 事件流响应.
     *
     * <p>成功分支要求响应体为 {@code Flux<ServerSentEvent<String>>}（流式处理器产出）；非 2xx
     * 按状态码翻译为 Anthropic 错误体（{@link #toAnthropicError}，与本地错误同一张映射表）；
     * 形态异常降级为 502 + Anthropic 错误体。</p>
     *
     * @param downstream     下游响应实体（body 为 SSE 流）
     * @param requestedModel 请求侧模型名（{@code message_start.message.model}）
     * @param inputTokens    请求侧估算输入 token 数
     * @return {@code text/event-stream} 响应或错误响应
     */
    private ResponseEntity<?> toAnthropicStreamResponse(final ResponseEntity<?> downstream,
                                                        final String requestedModel,
                                                        final long inputTokens) {
        if (downstream == null) {
            log.error("Anthropic /v1/messages(stream): 上游返回空响应实体");
            return anthropicError(HttpStatus.BAD_GATEWAY, "下游服务未返回响应");
        }
        if (!downstream.getStatusCode().is2xxSuccessful()) {
            return toAnthropicError(downstream);
        }
        if (!(downstream.getBody() instanceof Flux<?> downstreamStream)) {
            final Object body = downstream.getBody();
            log.error("Anthropic /v1/messages(stream): 流式响应体形态异常(期望 SSE 流): {}",
                    body == null ? "null" : body.getClass().getName());
            return anthropicError(HttpStatus.BAD_GATEWAY, "下游响应形态异常: 期望 SSE 流");
        }
        return ResponseEntity.status(downstream.getStatusCode())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(trackStream(streamingTranslator.toEventStream(downstreamStream, requestedModel, inputTokens)));
    }

    /**
     * 下游原生响应 → Anthropic Message 响应.
     *
     * <p>成功分支要求响应体为原生 JSON 字符串（置了原生标记后由 {@code NonStreamingRequestProcessor}
     * 产出）；非 2xx 按状态码翻译为 Anthropic 错误体（{@link #toAnthropicError}）—— 下游多为 OpenAI
     * 形状（{@code {"error":{"message","type","code"}}}），原样透传会让 Anthropic 客户端解析不到错误
     * 消息（issue #685）。</p>
     *
     * @param downstream     下游响应实体
     * @param requestedModel 请求侧模型名（下游缺 {@code model} 时回填）
     * @return Anthropic Message 或错误响应
     */
    private ResponseEntity<?> toAnthropicResponse(final ResponseEntity<?> downstream, final String requestedModel) {
        if (downstream == null) {
            log.error("Anthropic /v1/messages: 上游返回空响应实体");
            return anthropicError(HttpStatus.BAD_GATEWAY, "下游服务未返回响应");
        }
        if (!downstream.getStatusCode().is2xxSuccessful()) {
            return toAnthropicError(downstream);
        }
        final Object body = downstream.getBody();
        if (!(body instanceof String nativeJson)) {
            log.error("Anthropic /v1/messages: 原生响应体形态异常(期望 JSON 文本): {}",
                    body == null ? "null" : body.getClass().getName());
            return anthropicError(HttpStatus.BAD_GATEWAY, "下游响应形态异常: 期望原生 JSON 文本");
        }
        final AnthropicMessagesResponse message =
                responseTranslator.toAnthropicMessage(nativeJson, requestedModel);
        return ResponseEntity.status(downstream.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(message);
    }

    /**
     * 构造 Anthropic 错误响应体（{@code {"type":"error","error":{"type","message"}}}）.
     *
     * @param status  HTTP 状态
     * @param message 错误描述
     * @return Anthropic 错误响应
     */
    private ResponseEntity<?> anthropicError(final HttpStatus status, final String message) {
        return anthropicError(status.value(), message);
    }

    /**
     * 构造 Anthropic 错误响应体（状态码以 {@code int} 给出，用于保留 529 等非枚举状态码）.
     *
     * <p>{@code error.type} 由<b>状态码</b>决定（{@link #errorTypeFor(int)}），不再一律写
     * {@code invalid_request_error} —— 502 却报「请求不合法」会误导客户端按参数错误重试（issue #685）。</p>
     *
     * @param statusCode HTTP 状态码
     * @param message    错误描述
     * @return Anthropic 错误响应
     */
    private ResponseEntity<?> anthropicError(final int statusCode, final String message) {
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", errorTypeFor(statusCode));
        error.put("message", message);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", ERROR_OBJECT_TYPE);
        body.put("error", error);

        return ResponseEntity.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * HTTP 状态码 → Anthropic 错误类型.
     *
     * <p>与网关其余 {@code /v1/**} 错误体共用 {@link V1ErrorBodyMapper#errorTypeOf(int)} 这张表
     * （400/401/403/404/429 逐项、其余 4xx → {@code invalid_request_error}、5xx → {@code api_error}），
     * 仅补一个共享表里没有的 529（Anthropic 的「上游过载」，只在透传下游时出现）。</p>
     *
     * @param statusCode HTTP 状态码
     * @return Anthropic 错误类型（{@code invalid_request_error} / {@code authentication_error} /
     *         {@code permission_error} / {@code not_found_error} / {@code rate_limit_error} /
     *         {@code api_error} / {@code overloaded_error}）
     */
    static String errorTypeFor(final int statusCode) {
        return statusCode == HTTP_STATUS_OVERLOADED
                ? ERROR_TYPE_OVERLOADED
                : V1ErrorBodyMapper.errorTypeOf(statusCode);
    }

    /**
     * 下游非 2xx 响应 → Anthropic 错误响应（状态码保持不变，错误体换成 Anthropic 形状）.
     *
     * @param downstream 下游响应（body 可能是下游原生 JSON 字符串、Map 或纯文本）
     * @return Anthropic 错误响应
     */
    private ResponseEntity<?> toAnthropicError(final ResponseEntity<?> downstream) {
        final int statusCode = downstream.getStatusCode().value();
        final String message = downstreamErrorMessage(downstream.getBody(), statusCode);
        log.warn("Anthropic /v1/messages: 下游返回非 2xx(status={}), 错误体已翻译为 Anthropic 形状", statusCode);
        return anthropicError(statusCode, message);
    }

    /**
     * 从下游错误体中取出可读消息（取不到时按状态码兜底）.
     *
     * <p>下游错误体至少有三类形状：OpenAI 形状 {@code {"error":{"message","type","code"}}}、Anthropic
     * 形状 {@code {"type":"error","error":{"type","message"}}}、以及网关自身的
     * {@code RouterResponse}（{@code {"message":...}}）；非 JSON 的下游正文（如 HTML 错误页）按原样取出。</p>
     *
     * @param body       下游响应体，可为 {@code null}
     * @param statusCode 下游状态码（取不到消息时用于兜底文案）
     * @return 截断后的可读消息
     */
    static String downstreamErrorMessage(final Object body, final int statusCode) {
        String message = messageOf(body);
        if (message == null || message.isBlank()) {
            final HttpStatus resolved = HttpStatus.resolve(statusCode);
            message = "下游服务返回错误: " + statusCode
                    + (resolved == null || resolved.getReasonPhrase().isBlank()
                            ? "" : " " + resolved.getReasonPhrase());
        }
        return truncateMessage(message);
    }

    /**
     * 取出下游错误体里的消息（不保证非空）.
     *
     * @param body 下游响应体，可为 {@code null}
     * @return 消息；取不到时为 {@code null}
     */
    private static String messageOf(final Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String text) {
            final JsonNode json = parseErrorJson(text);
            final String fromJson = json == null ? null : messageFromJsonNode(json);
            return fromJson != null ? fromJson : text;
        }
        return messageFromJsonNode(ERROR_BODY_MAPPER.valueToTree(body));
    }

    /**
     * 把文本解析为 JSON（失败返回 {@code null}，调用方据此按纯文本处理）.
     *
     * @param text 待解析文本
     * @return JSON 节点；非 JSON 时为 {@code null}
     */
    private static JsonNode parseErrorJson(final String text) {
        final String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            return ERROR_BODY_MAPPER.readTree(trimmed);
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 按「{@code error.message} → {@code message}/{@code detail}/{@code error_description} → 文本本身」取值.
     *
     * @param node JSON 节点，可为 {@code null}
     * @return 消息；取不到时为 {@code null}
     */
    private static String messageFromJsonNode(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        final JsonNode error = node.get("error");
        if (error != null) {
            if (error.isTextual()) {
                final String asText = blankToNull(error.asText());
                if (asText != null) {
                    return asText;
                }
            } else {
                final JsonNode nested = error.get("message");
                if (nested != null && nested.isTextual()) {
                    final String asText = blankToNull(nested.asText());
                    if (asText != null) {
                        return asText;
                    }
                }
            }
        }
        for (final String field : MESSAGE_FIELDS) {
            final JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                final String asText = blankToNull(value.asText());
                if (asText != null) {
                    return asText;
                }
            }
        }
        return null;
    }

    /**
     * 空白字符串归一为 {@code null}.
     *
     * @param value 原值
     * @return 去空白后的值；空白时为 {@code null}
     */
    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 截断超长消息（下游可能返回整页 HTML）.
     *
     * @param message 原消息
     * @return 长度不超过 {@link #MAX_ERROR_MESSAGE_LENGTH}（含省略号）的消息
     */
    /**
     * #947：给流式响应包一层活跃连接计数（未装配指标时原样返回）。
     *
     * @param stream 翻译后的 SSE 流
     * @return 计数包装后的流
     */
    private Flux<ServerSentEvent<String>> trackStream(final Flux<ServerSentEvent<String>> stream) {
        return sseConnectionMetrics == null
                ? stream
                : sseConnectionMetrics.track(stream, "anthropic-messages");
    }

    private static String truncateMessage(final String message) {
        final String trimmed = message.trim();
        return trimmed.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "…";
    }
}

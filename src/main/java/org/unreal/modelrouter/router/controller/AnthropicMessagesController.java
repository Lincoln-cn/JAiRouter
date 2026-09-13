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

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.dto.ChatDTO;
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
 *   <li>{@code count_tokens}（PR-4c）纯本地估算，<b>不触达下游</b>，与流式 {@code message_start}
 *       的 {@code input_tokens} 同源（{@link AnthropicTokenEstimator}）；</li>
 *   <li>缺 {@code model} 一律 400 + Anthropic 错误体。</li>
 * </ul>
 *
 * <p>鉴权：沿用 {@code /v1/**} 的既有策略（{@code X-API-Key}/{@code x-api-key} 网关凭据或
 * {@code Jairouter_Token} JWT）；{@code Authorization} 透传下游且<b>实例级 headers 优先</b>。
 * 客户端放在 {@code x-api-key} 的是<b>网关凭据</b>（由安全层消费），因此不向下游转发——下游鉴权
 * 依赖实例级 headers 配置。{@code anthropic-version} / {@code anthropic-beta} 头仅接收不校验。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@Tag(name = "Anthropic 原生接口", description = "Anthropic Messages 协议入口（Claude Code 可零改造直连，支持非流式与流式）")
public class AnthropicMessagesController {

    /**
     * Anthropic 错误体中的错误类型：请求不合法.
     */
    private static final String ERROR_TYPE_INVALID_REQUEST = "invalid_request_error";

    /**
     * Anthropic 错误体中的对象类型.
     */
    private static final String ERROR_OBJECT_TYPE = "error";

    private final ServiceRequestHandler requestHandler;
    private final AnthropicRequestTranslator requestTranslator;
    private final AnthropicResponseTranslator responseTranslator;
    private final AnthropicStreamingTranslator streamingTranslator;

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
     * 原样透传（与非流式分支同理）；形态异常降级为 502 + Anthropic 错误体。</p>
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
            return downstream;
        }
        if (!(downstream.getBody() instanceof Flux<?> downstreamStream)) {
            final Object body = downstream.getBody();
            log.error("Anthropic /v1/messages(stream): 流式响应体形态异常(期望 SSE 流): {}",
                    body == null ? "null" : body.getClass().getName());
            return anthropicError(HttpStatus.BAD_GATEWAY, "下游响应形态异常: 期望 SSE 流");
        }
        return ResponseEntity.status(downstream.getStatusCode())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(streamingTranslator.toEventStream(downstreamStream, requestedModel, inputTokens));
    }

    /**
     * 下游原生响应 → Anthropic Message 响应.
     *
     * <p>成功分支要求响应体为原生 JSON 字符串（置了原生标记后由 {@code NonStreamingRequestProcessor}
     * 产出）；非 2xx 原样透传（其错误体与状态码由下游/网关既有机制给出，避免此处二次包装丢失语义）。</p>
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
            return downstream;
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
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", ERROR_TYPE_INVALID_REQUEST);
        error.put("message", message);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", ERROR_OBJECT_TYPE);
        body.put("error", error);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}

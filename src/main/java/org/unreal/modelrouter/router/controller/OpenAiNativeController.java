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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelCatalogService;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 原生面控制器（{@code /v1}）.
 *
 * <p>与 {@link UniversalController}（{@code /api/v1}，控制台面，返回 {@code RouterResponse} 包裹体）
 * 分工明确：本控制器在 exchange 上置 {@link ServiceRequestHandler#NATIVE_RESPONSE_ATTRIBUTE}，
 * 非流式响应直接返回下游原生 JSON（不包 {@code RouterResponse}），流式响应直接透传原生 SSE，
 * 因此 {@code http://localhost:8080/v1} 可直接作为 OpenAI SDK / LangChain / LlamaIndex 的
 * {@code base_url}。</p>
 *
 * <p>鉴权说明：网关侧认证使用 {@code X-API-Key}（API Key）或 {@code Jairouter_Token}（JWT）；
 * {@code Authorization} 请求头保留并透传给下游 AI 服务，且<b>实例级 headers 优先</b>（详见
 * {@code StreamingRequestProcessor}/{@code NonStreamingRequestProcessor}）。安全策略由
 * {@code SecurityConfiguration} 的 {@code /v1/** authenticated()} 规则与
 * {@code ServiceRequestHandler.hasServicePermission}（{@code ROLE_ADMIN} 或
 * {@code ROLE_<SERVICE>}）共同约束。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@RestController
@RequestMapping("/v1")
@Tag(name = "OpenAI 原生接口", description = "OpenAI 原生格式接口（无 RouterResponse 包裹，可直接接入 OpenAI SDK）")
public class OpenAiNativeController {

    private final ServiceRequestHandler requestHandler;
    private final ModelCatalogService modelCatalogService;

    /**
     * 构造函数.
     *
     * @param requestHandler      统一请求处理器
     * @param modelCatalogService 模型目录服务（{@code GET /v1/models} 数据源）
     */
    public OpenAiNativeController(final ServiceRequestHandler requestHandler,
                                  final ModelCatalogService modelCatalogService) {
        this.requestHandler = requestHandler;
        this.modelCatalogService = modelCatalogService;
    }

    /**
     * 模型列表接口（OpenAI 原生格式，v3.1 PR-4a）.
     *
     * @return {@code {object:"list", data:[{id, object:"model", created, owned_by, service_type, adapter}]}}
     */
    @GetMapping("/models")
    public Mono<ResponseEntity<Map<String, Object>>> models() {
        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(modelCatalogService.listAllModelsAsOpenAiList()));
    }

    /**
     * 聊天完成接口（OpenAI 原生格式）.
     *
     * @param authorization 透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param request       聊天请求体
     * @param exchange      当前交换对象
     * @return 原生 JSON（非流式）或原生 SSE（{@code stream=true}）
     */
    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final ChatDTO.Request request,
            final ServerWebExchange exchange) {

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);
        requireRequestBody(request);
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);

        return requestHandler.handleRequest(
            ServiceEndpoint.CHAT,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.chat(request, auth, httpRequest)
        );
    }

    /**
     * 向量生成接口（OpenAI 原生格式）.
     *
     * @param authorization 透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param request       向量请求体
     * @param exchange      当前交换对象
     * @return 原生 JSON 响应
     */
    @PostMapping("/embeddings")
    public Mono<ResponseEntity<?>> embeddings(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final EmbeddingDTO.Request request,
            final ServerWebExchange exchange) {

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);
        requireRequestBody(request);
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);

        return requestHandler.handleRequest(
            ServiceEndpoint.EMBEDDING,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.embedding(request, auth, httpRequest)
        );
    }

    /**
     * 重排序接口（OpenAI 原生格式扩展）.
     *
     * @param authorization 透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param request       重排序请求体
     * @param exchange      当前交换对象
     * @return 原生 JSON 响应
     */
    @PostMapping("/rerank")
    public Mono<ResponseEntity<?>> rerank(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final RerankDTO.Request request,
            final ServerWebExchange exchange) {

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);
        requireRequestBody(request);
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);

        return requestHandler.handleRequest(
            ServiceEndpoint.RERANK,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.rerank(request, auth, httpRequest)
        );
    }

    /**
     * 媒体能力的兜底入口（issue #830）.
     *
     * <p>音频与图像四类能力落在 {@code /api/v1} 控制台面（响应被 {@code RouterResponse} 包裹），
     * 本面没有对应映射。此前这类请求得到的是默认 404（「No static resource」），
     * 对把 {@code base_url} 指过来的 SDK 用户而言等同于「网关没有这个能力」。
     * 这里显式返回 501，并在正文里给出对应的 {@code /api/v1/...} 路径。</p>
     *
     * <p>如果将来把图像端点提升为一等公民（OpenAI 原生面），直接把对应映射加在
     * 本方法之前即可，Spring 会优先匹配更具体的映射。</p>
     *
     * @param exchange 当前交换对象（用于取原始路径）
     * @return 501 + OpenAI 形状的错误体
     */
    @RequestMapping(path = {"/audio/**", "/images/**"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> mediaOnConsoleSurface(
            final ServerWebExchange exchange) {

        final String originalPath = exchange.getRequest().getPath().pathWithinApplication().value();
        final String consolePath = "/api/v1" + originalPath.substring("/v1".length());

        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", "音频与图像接口不在 /v1 下：请改用 POST " + consolePath
                + "（控制台面，响应包裹在 RouterResponse.data 里）。"
                + "详见 docs/zh/api-reference/openapi-spec.md");
        error.put("type", "invalid_request_error");
        error.put("code", "not_implemented_on_v1");

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    /**
     * 空请求体（或缺失）必须以客户端错误终止，而不是把 {@code null} 交给下游适配器.
     *
     * <p>issue #779：三个原生入口此前用 {@code @RequestBody(required = false)}，空体时把
     * {@code null} 直接透传给适配器，adapter 内部 NPE 被中央处理器归为 500，调用方拿不到
     * 「请求体为空」这一可自我修正的信息。改为在入口处抛
     * {@link ServerWebInputException}：{@code ReactiveGlobalExceptionHandler} 把它映射为
     * 400，{@code /v1/**} 再由 {@code V1ErrorBodyMapper} 渲染成
     * {@code invalid_request_error}（与既有 400 口径一致）。</p>
     *
     * @param request 请求体
     * @throws ServerWebInputException 请求体为 {@code null}
     */
    private static void requireRequestBody(final Object request) {
        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }
    }
}

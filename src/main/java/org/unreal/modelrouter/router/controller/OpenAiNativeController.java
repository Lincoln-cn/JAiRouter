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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelCatalogService;
import reactor.core.publisher.Mono;

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
        if (request != null) {
            exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.CHAT,
            request != null ? request.model() : null,
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
        if (request != null) {
            exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.EMBEDDING,
            request != null ? request.model() : null,
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
        if (request != null) {
            exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.RERANK,
            request != null ? request.model() : null,
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.rerank(request, auth, httpRequest)
        );
    }
}

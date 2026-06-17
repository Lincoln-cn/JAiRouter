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
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import reactor.core.publisher.Mono;

/**
 * 统一模型接口控制器.
 *
 * <p>提供兼容OpenAI格式的统一模型服务接口，所有请求处理逻辑委托给 {@link ServiceRequestHandler}。
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "统一模型接口", description = "提供兼容OpenAI格式的统一模型服务接口")
public class UniversalController {

    private final ServiceRequestHandler requestHandler;

    public UniversalController(final ServiceRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final ChatDTO.Request request,
            final ServerWebExchange exchange) {

        return requestHandler.handleRequest(
            ServiceEndpoint.CHAT,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.chat(request, auth, httpRequest)
        );
    }

    @PostMapping("/embeddings")
    public Mono<ResponseEntity<?>> embeddings(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final EmbeddingDTO.Request request,
            final ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.EMBEDDING,
            request.model(),
            authorization,
            httpRequest,
            (adapter, auth, req) -> adapter.embedding(request, auth, req)
        );
    }

    @PostMapping("/rerank")
    public Mono<ResponseEntity<?>> rerank(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final RerankDTO.Request request,
            final ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.RERANK,
            request.model(),
            authorization,
            httpRequest,
            (adapter, auth, req) -> adapter.rerank(request, auth, req)
        );
    }

    @PostMapping("/audio/speech")
    public Mono<ResponseEntity<?>> textToSpeech(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final TtsDTO.Request request,
            final ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.TTS,
            request.model(),
            authorization,
            httpRequest,
            (adapter, auth, req) -> adapter.tts(request, auth, req)
        );
    }

    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> speechToText(
            @RequestPart("model") final String model,
            @RequestPart("file") final FilePart file,
            @RequestPart(value = "language", required = false) final String language,
            @RequestPart(value = "prompt", required = false) final String prompt,
            @RequestPart(value = "responseFormat", required = false) final String responseFormat,
            @RequestPart(value = "temperature", required = false) final String temperatureStr,
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            final ServerHttpRequest httpRequest) {

        // 手动转换 temperature 字符串为 Double
        Double temperature = null;
        if (temperatureStr != null && !temperatureStr.isEmpty()) {
            try {
                temperature = Double.parseDouble(temperatureStr);
            } catch (NumberFormatException e) {
                // 忽略无效的温度值
            }
        }

        SttDTO.Request request = new SttDTO.Request(model, file, language, prompt, responseFormat, temperature);

        return requestHandler.handleRequest(
            ServiceEndpoint.STT,
            request.model(),
            authorization,
            httpRequest,
            (adapter, auth, req) -> adapter.stt(request, auth, req)
        );
    }

    @PostMapping("/images/generations")
    public Mono<ResponseEntity<?>> imageGenerate(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final ImageGenerateDTO.Request request,
            final ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.IMAGE_GEN,
            request.model(),
            authorization,
            httpRequest,
            (adapter, auth, req) -> adapter.imageGenerate(request, auth, req)
        );
    }

    @PostMapping("/images/edits")
    public Mono<ResponseEntity<?>> imageEdits(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final ImageEditDTO.Request request,
            final ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.IMAGE_EDIT,
            request.model(),
            authorization,
            httpRequest,
            (adapter, auth, req) -> adapter.imageEdit(request, auth, req)
        );
    }
}

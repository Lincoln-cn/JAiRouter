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

import java.util.ArrayList;
import java.util.List;

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

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        // v2.9.9: 原始 DTO 放入 exchange attribute，供 handler 认证后构建响应缓存键
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);

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
            final ServerWebExchange exchange) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        // v2.9.9: 原始 DTO 放入 exchange attribute，供 handler 认证后构建响应缓存键
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);

        return requestHandler.handleRequest(
            ServiceEndpoint.EMBEDDING,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.embedding(request, auth, httpRequest)
        );
    }

    @PostMapping("/rerank")
    public Mono<ResponseEntity<?>> rerank(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final RerankDTO.Request request,
            final ServerWebExchange exchange) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        // v2.9.9: 原始 DTO 放入 exchange attribute，供 handler 认证后构建响应缓存键
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, request);

        return requestHandler.handleRequest(
            ServiceEndpoint.RERANK,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.rerank(request, auth, httpRequest)
        );
    }

    @PostMapping("/audio/speech")
    public Mono<ResponseEntity<?>> textToSpeech(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final TtsDTO.Request request,
            final ServerWebExchange exchange) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.TTS,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.tts(request, auth, httpRequest)
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
            final ServerWebExchange exchange) {

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
            exchange,
            (adapter, auth, httpRequest) -> adapter.stt(request, auth, httpRequest)
        );
    }

    @PostMapping("/images/generations")
    public Mono<ResponseEntity<?>> imageGenerate(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final ImageGenerateDTO.Request request,
            final ServerWebExchange exchange) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        return requestHandler.handleRequest(
            ServiceEndpoint.IMAGE_GEN,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.imageGenerate(request, auth, httpRequest)
        );
    }

    /**
     * 图像编辑（{@code multipart/form-data}）.
     *
     * <p>OpenAI 的 {@code /v1/images/edits} 契约要求 multipart：图像与掩码是文件部件、其余是文本部件。
     * 单张图像用部件名 {@code image}、多张用 {@code image[]}（与官方 curl 示例一致）；掩码用 {@code mask}。
     *
     * @param authorization     授权头
     * @param image             图像部件（单张形态）
     * @param imageArray        图像部件（数组形态）
     * @param mask              可选掩码文件部件
     * @param model             模型名
     * @param prompt            编辑描述
     * @param background        背景
     * @param inputFidelity     输入保真度
     * @param n                 生成张数（字符串部件，手动解析）
     * @param outputCompression 输出压缩（字符串部件，手动解析）
     * @param outputFormat      输出格式
     * @param partialImages     部分图像数量（字符串部件，手动解析）
     * @param quality           质量
     * @param responseFormat    响应格式
     * @param size              尺寸
     * @param stream            是否流式（字符串部件，手动解析）
     * @param user              终端用户标识
     * @param exchange          当前交换对象
     * @return 上游响应
     * @since v3.5.2 起改为 multipart（#637）
     */
    @PostMapping(value = "/images/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> imageEdits(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestPart(value = "image", required = false) final List<FilePart> image,
            @RequestPart(value = "image[]", required = false) final List<FilePart> imageArray,
            @RequestPart(value = "mask", required = false) final FilePart mask,
            @RequestPart(value = "model", required = false) final String model,
            @RequestPart(value = "prompt", required = false) final String prompt,
            @RequestPart(value = "background", required = false) final String background,
            @RequestPart(value = "input_fidelity", required = false) final String inputFidelity,
            @RequestPart(value = "n", required = false) final String nPart,
            @RequestPart(value = "output_compression", required = false) final String outputCompressionPart,
            @RequestPart(value = "output_format", required = false) final String outputFormat,
            @RequestPart(value = "partial_images", required = false) final String partialImagesPart,
            @RequestPart(value = "quality", required = false) final String quality,
            @RequestPart(value = "response_format", required = false) final String responseFormat,
            @RequestPart(value = "size", required = false) final String size,
            @RequestPart(value = "stream", required = false) final String streamPart,
            @RequestPart(value = "user", required = false) final String user,
            final ServerWebExchange exchange) {

        final List<FilePart> images = new ArrayList<>();
        if (image != null) {
            images.addAll(image);
        }
        if (imageArray != null) {
            images.addAll(imageArray);
        }
        if (images.isEmpty()) {
            throw new ServerWebInputException(
                    "Multipart part 'image' (or 'image[]') is required");
        }

        final ImageEditDTO.Request request = new ImageEditDTO.Request(
                images,
                prompt,
                background,
                inputFidelity,
                mask,
                model,
                parseIntegerPart(nPart, "n"),
                parseIntegerPart(outputCompressionPart, "output_compression"),
                outputFormat,
                parseIntegerPart(partialImagesPart, "partial_images"),
                quality,
                responseFormat,
                size,
                parseBooleanPart(streamPart, "stream"),
                user);

        return requestHandler.handleRequest(
            ServiceEndpoint.IMAGE_EDIT,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.imageEdit(request, auth, httpRequest)
        );
    }

    /**
     * 解析可选的整数部件.
     *
     * @param raw       部件原始文本
     * @param fieldName 字段名（用于错误信息）
     * @return 解析结果，空文本返回 {@code null}
     */
    private static Integer parseIntegerPart(final String raw, final String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new ServerWebInputException(
                    "Multipart part '" + fieldName + "' must be an integer: " + raw);
        }
    }

    /**
     * 解析可选的布尔部件.
     *
     * @param raw       部件原始文本
     * @param fieldName 字段名（用于错误信息）
     * @return 解析结果，空文本返回 {@code null}
     */
    private static Boolean parseBooleanPart(final String raw, final String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String value = raw.trim();
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.valueOf(value);
        }
        throw new ServerWebInputException(
                "Multipart part '" + fieldName + "' must be true or false: " + raw);
    }
}

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
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.RequestPart;
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
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelCatalogService;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>鉴权说明：网关侧认证使用 {@code X-API-Key}（API Key）或 {@code X-Jairouter-Token}（JWT）；
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
     * 语音合成接口（OpenAI 原生格式，issue #835）.
     *
     * <p>返回下游的原始音频字节（透传 {@code Content-Type}，例如 {@code audio/mpeg}），
     * 与 {@code /api/v1/audio/speech} 的差别只在于不包 {@code RouterResponse}，
     * 因此 OpenAI SDK 的 {@code audio.speech.create(...)} 可以直接使用。</p>
     *
     * @param authorization 透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param request       语音合成请求体
     * @param exchange      当前交换对象
     * @return 音频字节流
     * @since v3.5.7
     */
    @PostMapping("/audio/speech")
    public Mono<ResponseEntity<?>> textToSpeech(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final TtsDTO.Request request,
            final ServerWebExchange exchange) {

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);
        requireRequestBody(request);

        return requestHandler.handleRequest(
            ServiceEndpoint.TTS,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.tts(request, auth, httpRequest)
        );
    }

    /**
     * 语音转写接口（OpenAI 原生格式 {@code multipart/form-data}，issue #835）.
     *
     * <p>部件名遵循 OpenAI 契约：{@code file}/{@code model} 必填，其余可选；
     * {@code response_format} 同时接受驼峰形式，兼容此前按控制台面写的客户端。</p>
     *
     * @param model          模型名
     * @param file           音频文件部件
     * @param language       语言
     * @param prompt         提示词
     * @param responseFormat 响应格式（蛇形部件名）
     * @param responseFormatCamel 响应格式（驼峰部件名，兼容用）
     * @param temperatureStr 温度（字符串部件，手动解析）
     * @param authorization  透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param exchange       当前交换对象
     * @return 原生 JSON 转写结果
     * @since v3.5.7
     */
    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> speechToText(
            @RequestPart("model") final String model,
            @RequestPart("file") final FilePart file,
            @RequestPart(value = "language", required = false) final String language,
            @RequestPart(value = "prompt", required = false) final String prompt,
            @RequestPart(value = "response_format", required = false) final String responseFormat,
            @RequestPart(value = "responseFormat", required = false) final String responseFormatCamel,
            @RequestPart(value = "temperature", required = false) final String temperatureStr,
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            final ServerWebExchange exchange) {

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);

        final SttDTO.Request request = new SttDTO.Request(
                model, file, language, prompt,
                responseFormat != null ? responseFormat : responseFormatCamel,
                parseDoublePart(temperatureStr, "temperature"));

        return requestHandler.handleRequest(
            ServiceEndpoint.STT,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.stt(request, auth, httpRequest)
        );
    }

    /**
     * 图像生成接口（OpenAI 原生格式，issue #835）.
     *
     * @param authorization 透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param request       图像生成请求体
     * @param exchange      当前交换对象
     * @return 原生 JSON 响应（{@code b64_json} 或 {@code url}）
     * @since v3.5.7
     */
    @PostMapping("/images/generations")
    public Mono<ResponseEntity<?>> imageGenerate(
            @RequestHeader(value = "Authorization", required = false) final String authorization,
            @RequestBody(required = false) final ImageGenerateDTO.Request request,
            final ServerWebExchange exchange) {

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);
        requireRequestBody(request);

        return requestHandler.handleRequest(
            ServiceEndpoint.IMAGE_GEN,
            request.model(),
            authorization,
            exchange,
            (adapter, auth, httpRequest) -> adapter.imageGenerate(request, auth, httpRequest)
        );
    }

    /**
     * 图像编辑接口（OpenAI 原生格式 {@code multipart/form-data}，issue #835）.
     *
     * <p>图像部件名同时接受 {@code image} 与 {@code image[]}，掩码用 {@code mask}，
     * 其余为文本部件（数字/布尔部件手动解析）。</p>
     *
     * @param authorization      透传给下游的 Authorization 头（可为空；实例级 headers 优先）
     * @param image              图像部件（单张形态）
     * @param imageArray         图像部件（数组形态）
     * @param mask               可选掩码文件部件
     * @param model              模型名
     * @param prompt             编辑描述
     * @param background         背景
     * @param inputFidelity      输入保真度
     * @param nPart              生成张数（字符串部件）
     * @param outputCompressionPart 输出压缩（字符串部件）
     * @param outputFormat       输出格式
     * @param partialImagesPart  部分图像数量（字符串部件）
     * @param quality            质量
     * @param responseFormat     响应格式
     * @param size               尺寸
     * @param streamPart         是否流式（字符串部件）
     * @param user               终端用户标识
     * @param exchange           当前交换对象
     * @return 原生 JSON 响应
     * @since v3.5.7
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

        exchange.getAttributes().put(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE, Boolean.TRUE);

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
        error.put("message", "该接口在 /v1 下未提供：请改用 POST " + consolePath
                + "（控制台面，响应包裹在 RouterResponse.data 里）。"
                + "语音合成/转写与图像生成/编辑已提供 /v1 原生映射。"
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

    /**
     * 解析可选的浮点部件.
     *
     * @param raw       部件原始文本
     * @param fieldName 字段名（用于错误信息）
     * @return 解析结果，空文本返回 {@code null}
     * @since v3.5.7
     */
    private static Double parseDoublePart(final String raw, final String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new ServerWebInputException(
                    "Multipart part '" + fieldName + "' must be a number: " + raw);
        }
    }

    /**
     * 解析可选的整数部件.
     *
     * @param raw       部件原始文本
     * @param fieldName 字段名（用于错误信息）
     * @return 解析结果，空文本返回 {@code null}
     * @since v3.5.7
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
     * @since v3.5.7
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

package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.adapter.AdapterCapabilities;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

public class NormalOpenAiAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public NormalOpenAiAdapter(ModelServiceRegistry registry, MetricsCollector metricsCollector) {
        super(registry, metricsCollector);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.all();
    }

    @Override
    protected String getAdapterType() {
        return "normal";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        // 记录适配器特定的追踪信息
        org.unreal.modelrouter.tracing.TracingContext tracingContext =
            org.unreal.modelrouter.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    // 添加OpenAI特定的属性
                    currentSpan.setAttribute("adapter.api_standard", "openai");
                    currentSpan.setAttribute("adapter.version", "v1");
                    currentSpan.setAttribute("adapter.compliance_level", "full");

                    // 根据请求类型添加特定属性
                    if (request instanceof SttDTO.Request) {
                        SttDTO.Request sttRequest = (SttDTO.Request) request;
                        currentSpan.setAttribute("request.model", sttRequest.model());
                        currentSpan.setAttribute("request.language", sttRequest.language());
                    } else if (request instanceof ImageEditDTO.Request) {
                        ImageEditDTO.Request imageEditRequest = (ImageEditDTO.Request) request;
                        currentSpan.setAttribute("request.model", imageEditRequest.model());
                        currentSpan.setAttribute("request.prompt_length",
                            imageEditRequest.prompt() != null ? imageEditRequest.prompt().length() : 0);
                    }
                }

                // 记录适配器调用开始事件
                try {
                    org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                            org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
                    enhancer.logAdapterCallStart(adapterType, null, getServiceTypeFromRequest(request),
                        getModelNameFromRequest(request), tracingContext);
                } catch (Exception e) {
                    // 忽略追踪增强错误
                }
            } catch (Exception e) {
                // 忽略追踪错误
            }
        }

        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequest(chatRequest);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return transformEmbeddingRequest(embeddingRequest);
        } else if (request instanceof RerankDTO.Request rerankRequest) {
            return transformRerankRequest(rerankRequest);
        } else if (request instanceof TtsDTO.Request ttsRequest) {
            return transformTtsRequest(ttsRequest);
        } else if (request instanceof SttDTO.Request sttRequest) {
            return transformSttRequest(sttRequest);
        } else if (request instanceof ImageEditDTO.Request imageEditRequest) {
            return transformImageEditRequestRequest(imageEditRequest);
        }
        return super.transformRequest(request, adapterType);
    }

    /**
     * 转换Chat请求格式以适配标准OpenAI API
     * 支持最新的OpenAI API参数
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            // 标准OpenAI参数
            openAiRequest.put("model", adaptModelName(request.model()));
            openAiRequest.set("messages", objectMapper.valueToTree(request.messages()));

            if (request.temperature() != null) {
                openAiRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                openAiRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                openAiRequest.put("top_p", request.topP());
            }
            if (request.stop() != null) {
                openAiRequest.set("stop", objectMapper.valueToTree(request.stop()));
            }
            if (request.stream() != null) {
                openAiRequest.put("stream", request.stream());
            }
            if (request.n() != null) {
                openAiRequest.put("n", request.n());
            }
            if (request.presencePenalty() != null) {
                openAiRequest.put("presence_penalty", request.presencePenalty());
            }
            if (request.frequencyPenalty() != null) {
                openAiRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.logprobs() != null) {
                openAiRequest.put("logprobs", request.logprobs());
            }
            if (request.topLogprobs() != null) {
                openAiRequest.put("top_logprobs", request.topLogprobs());
            }
            if (request.user() != null) {
                openAiRequest.put("user", request.user());
            }

            // OpenAI扩展参数
            ObjectNode extraBody = objectMapper.createObjectNode();
            
            // OpenAI特定参数
            if (request.useBeamSearch() != null) {
                extraBody.put("use_beam_search", request.useBeamSearch());
            }
            if (request.topK() != null) {
                extraBody.put("top_k", request.topK());
            }
            if (request.minP() != null) {
                extraBody.put("min_p", request.minP());
            }
            if (request.repetitionPenalty() != null) {
                extraBody.put("repetition_penalty", request.repetitionPenalty());
            }
            if (request.lengthPenalty() != null) {
                extraBody.put("length_penalty", request.lengthPenalty());
            }
            if (request.includeStopStrInOutput() != null) {
                extraBody.put("include_stop_str_in_output", request.includeStopStrInOutput());
            }
            if (request.ignoreEos() != null) {
                extraBody.put("ignore_eos", request.ignoreEos());
            }
            if (request.minTokens() != null) {
                extraBody.put("min_tokens", request.minTokens());
            }
            if (request.skipSpecialTokens() != null) {
                extraBody.put("skip_special_tokens", request.skipSpecialTokens());
            }
            if (request.spacesBetweenSpecialTokens() != null) {
                extraBody.put("spaces_between_special_tokens", request.spacesBetweenSpecialTokens());
            }
            if (request.truncatePromptTokens() != null) {
                extraBody.put("truncate_prompt_tokens", request.truncatePromptTokens());
            }
            if (request.echo() != null) {
                extraBody.put("echo", request.echo());
            }
            if (request.addGenerationPrompt() != null) {
                extraBody.put("add_generation_prompt", request.addGenerationPrompt());
            }
            if (request.continueFinalMessage() != null) {
                extraBody.put("continue_final_message", request.continueFinalMessage());
            }
            if (request.addSpecialTokens() != null) {
                extraBody.put("add_special_tokens", request.addSpecialTokens());
            }
            if (request.documents() != null) {
                extraBody.set("documents", objectMapper.valueToTree(request.documents()));
            }
            if (request.chatTemplate() != null) {
                extraBody.put("chat_template", request.chatTemplate());
            }
            if (request.chatTemplateKwargs() != null) {
                extraBody.set("chat_template_kwargs", objectMapper.valueToTree(request.chatTemplateKwargs()));
            }
            if (request.structuredOutputs() != null) {
                extraBody.set("structured_outputs", objectMapper.valueToTree(request.structuredOutputs()));
            }
            if (request.priority() != null) {
                extraBody.put("priority", request.priority());
            }
            if (request.requestId() != null) {
                extraBody.put("request_id", request.requestId());
            }
            if (request.returnTokensAsTokenIds() != null) {
                extraBody.put("return_tokens_as_token_ids", request.returnTokensAsTokenIds());
            }
            if (request.returnTokenIds() != null) {
                extraBody.put("return_token_ids", request.returnTokenIds());
            }
            if (request.cacheSalt() != null) {
                extraBody.put("cache_salt", request.cacheSalt());
            }
            if (request.repetitionDetection() != null) {
                extraBody.set("repetition_detection", objectMapper.valueToTree(request.repetitionDetection()));
            }

            // 如果有扩展参数，则添加到请求中
            if (extraBody.size() > 0) {
                openAiRequest.set("extra_body", extraBody);
            }

            return openAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * 转换Embedding请求格式以适配标准OpenAI API
     * 支持最新的OpenAI API参数
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", adaptModelName(request.model()));

            // 处理输入 - 支持字符串或数组
            if (request.input() instanceof String) {
                openAiRequest.put("input", (String) request.input());
            } else if (request.input() instanceof String[]) {
                openAiRequest.set("input", objectMapper.valueToTree(request.input()));
            } else if (request.input() instanceof java.util.List) {
                openAiRequest.set("input", objectMapper.valueToTree(request.input()));
            } else {
                // 默认处理
                openAiRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            // 标准参数
            if (request.encodingFormat() != null) {
                openAiRequest.put("encoding_format", request.encodingFormat());
            }
            if (request.dimensions() != null) {
                openAiRequest.put("dimensions", request.dimensions());
            }
            if (request.user() != null) {
                openAiRequest.put("user", request.user());
            }

            // OpenAI扩展参数
            ObjectNode extraBody = objectMapper.createObjectNode();
            if (request.truncatePromptTokens() != null) {
                extraBody.put("truncate_prompt_tokens", request.truncatePromptTokens());
            }
            if (request.requestId() != null) {
                extraBody.put("request_id", request.requestId());
            }
            if (request.priority() != null) {
                extraBody.put("priority", request.priority());
            }
            if (request.cacheSalt() != null) {
                extraBody.put("cache_salt", request.cacheSalt());
            }
            if (request.addSpecialTokens() != null) {
                extraBody.put("add_special_tokens", request.addSpecialTokens());
            }
            if (request.embedDtype() != null) {
                extraBody.put("embed_dtype", request.embedDtype());
            }
            if (request.endianness() != null) {
                extraBody.put("endianness", request.endianness());
            }
            if (request.useActivation() != null) {
                extraBody.put("use_activation", request.useActivation());
            }
            if (request.chatTemplate() != null) {
                extraBody.put("chat_template", request.chatTemplate());
            }
            if (request.chatTemplateKwargs() != null) {
                extraBody.set("chat_template_kwargs", objectMapper.valueToTree(request.chatTemplateKwargs()));
            }
            if (request.mediaIoKwargs() != null) {
                extraBody.set("media_io_kwargs", objectMapper.valueToTree(request.mediaIoKwargs()));
            }
            if (request.addGenerationPrompt() != null) {
                extraBody.put("add_generation_prompt", request.addGenerationPrompt());
            }
            if (request.continueFinalMessage() != null) {
                extraBody.put("continue_final_message", request.continueFinalMessage());
            }

            // 如果有扩展参数，则添加到请求中
            if (extraBody.size() > 0) {
                openAiRequest.set("extra_body", extraBody);
            }

            return openAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * 转换Rerank请求格式以适配标准OpenAI API
     * 支持最新的OpenAI API参数
     */
    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", adaptModelName(rerankRequest.model()));
            openAiRequest.put("query", rerankRequest.query());
            openAiRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                openAiRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                openAiRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            // OpenAI扩展参数
            ObjectNode extraBody = objectMapper.createObjectNode();
            if (rerankRequest.requestId() != null) {
                extraBody.put("request_id", rerankRequest.requestId());
            }
            if (rerankRequest.priority() != null) {
                extraBody.put("priority", rerankRequest.priority());
            }
            if (rerankRequest.truncatePromptTokens() != null) {
                extraBody.put("truncate_prompt_tokens", rerankRequest.truncatePromptTokens());
            }

            // 如果有扩展参数，则添加到请求中
            if (extraBody.size() > 0) {
                openAiRequest.set("extra_body", extraBody);
            }

            return openAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return rerankRequest;
        }
    }

    /**
     * 转换TTS请求格式以适配标准OpenAI API
     * 支持最新的OpenAI API参数
     */
    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", adaptModelName(ttsRequest.model()));
            openAiRequest.put("input", ttsRequest.input());
            openAiRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                openAiRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                openAiRequest.put("speed", ttsRequest.speed());
            }

            return openAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return ttsRequest;
        }
    }

    private Object transformImageEditRequestRequest(ImageEditDTO.Request imageEditRequest) {

        try {
            if (imageEditRequest.model() == null || imageEditRequest.model().isEmpty()) {
                throw new IllegalArgumentException("model is required");
            }

            if (imageEditRequest.image() == null || imageEditRequest.image().isEmpty()) {
                throw new IllegalArgumentException("At least one image file is required");
            }

            if (imageEditRequest.prompt() == null || imageEditRequest.prompt().trim().isEmpty()) {
                throw new IllegalArgumentException("Prompt is required");
            }

            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            // 添加 model 字段
            if (imageEditRequest.model() != null) {
                builder.part("model", imageEditRequest.model());
            }

            // 添加 prompt 字段
            if (imageEditRequest.prompt() != null) {
                builder.part("prompt", imageEditRequest.prompt());
            }

            // 添加 background 字段
            if (imageEditRequest.background() != null) {
                builder.part("background", imageEditRequest.background());
            }

            // 添加 input_fidelity 字段
            if (imageEditRequest.input_fidelity() != null) {
                builder.part("input_fidelity", imageEditRequest.input_fidelity());
            }

            // 添加 mask 字段
            if (imageEditRequest.mask() != null) {
                builder.part("mask", imageEditRequest.mask());
            }

            // 添加 n 字段
            if (imageEditRequest.n() != null) {
                builder.part("n", imageEditRequest.n());
            }

            // 添加 output_compression 字段
            if (imageEditRequest.output_compression() != null) {
                builder.part("output_compression", imageEditRequest.output_compression());
            }

            // 添加 output_format 字段
            if (imageEditRequest.output_format() != null) {
                builder.part("output_format", imageEditRequest.output_format());
            }

            // 添加 partial_images 字段
            if (imageEditRequest.partial_images() != null) {
                builder.part("partial_images", imageEditRequest.partial_images());
            }

            // 添加 quality 字段
            if (imageEditRequest.quality() != null) {
                builder.part("quality", imageEditRequest.quality());
            }

            // 添加 response_format 字段
            if (imageEditRequest.response_format() != null) {
                builder.part("response_format", imageEditRequest.response_format());
            }

            // 添加 size 字段
            if (imageEditRequest.size() != null) {
                builder.part("size", imageEditRequest.size());
            }

            // 添加 stream 字段
            if (imageEditRequest.stream() != null) {
                builder.part("stream", imageEditRequest.stream());
            }

            // 添加 user 字段
            if (imageEditRequest.user() != null) {
                builder.part("user", imageEditRequest.user());
            }

            // 处理 image 文件列表
            if (imageEditRequest.image() != null && !imageEditRequest.image().isEmpty()) {
                for (FilePart filePart : imageEditRequest.image()) {
                    // 动态检测文件类型
                    MediaType contentType = determineImageContentType(filePart.filename());

                    builder.asyncPart("image", filePart.content(), DataBuffer.class)
                            .filename(filePart.filename())
                            .contentType(contentType);
                }
            }
            return builder.build();
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return imageEditRequest;
        }
    }

    private MediaType determineImageContentType(String filename) {
        if (filename == null) return MediaType.IMAGE_PNG;

        String lowercaseFilename = filename.toLowerCase();
        if (lowercaseFilename.endsWith(".jpg") || lowercaseFilename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lowercaseFilename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lowercaseFilename.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (lowercaseFilename.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_PNG; // 默认
    }

    /**
     * STT请求需要特殊的multipart处理
     */
    private Object transformSttRequest(SttDTO.Request sttRequest) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("model", sttRequest.model());
            builder.part("language", sttRequest.language());

            // 使用 asyncPart 处理文件内容流
            builder.asyncPart("file", sttRequest.file().content(), DataBuffer.class)
                    .filename(sttRequest.file().filename())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);

            // 添加其他字段
            if (sttRequest.prompt() != null) {
                builder.part("prompt", sttRequest.prompt());
            }
            if (sttRequest.responseFormat() != null) {
                builder.part("response_format", sttRequest.responseFormat());
            }
            if (sttRequest.temperature() != null) {
                builder.part("temperature", sttRequest.temperature());
            }

            return builder.build();
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return sttRequest;
        }
    }

    @Override
    protected Object transformResponse(Object response, String adapterType) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                return transformStandardResponse(jsonResponse);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 转换标准OpenAI响应格式
     */
    private String transformStandardResponse(JsonNode openAiResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            // 根据OpenAI响应类型进行转换
            if (openAiResponse.has("choices")) {
                // 聊天响应转换
                standardResponse.set("id", openAiResponse.path("id"));
                standardResponse.put("object", "chat.completion");
                standardResponse.put("created", System.currentTimeMillis() / 1000);
                
                // 复制模型信息
                if (openAiResponse.has("model")) {
                    standardResponse.put("model", openAiResponse.get("model").asText());
                }
                
                // 复制选择项
                standardResponse.set("choices", openAiResponse.get("choices"));
                
                // 添加使用情况统计（如果存在）
                if (openAiResponse.has("usage")) {
                    standardResponse.set("usage", openAiResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("completion_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (openAiResponse.has("data") && openAiResponse.has("model")) {
                // 嵌入响应转换
                standardResponse.put("object", "list");
                standardResponse.set("data", openAiResponse.get("data"));
                standardResponse.put("model", openAiResponse.get("model").asText());
                
                // 添加使用情况统计（如果存在）
                if (openAiResponse.has("usage")) {
                    standardResponse.set("usage", openAiResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (openAiResponse.has("results")) {
                // 重排序响应转换
                standardResponse.set("id", objectMapper.getNodeFactory().textNode("openai-" + System.currentTimeMillis()));
                standardResponse.set("results", openAiResponse.get("results"));
                if (openAiResponse.has("model")) {
                    standardResponse.put("model", openAiResponse.get("model").asText());
                }
                
                // 添加使用情况统计（如果存在）
                if (openAiResponse.has("usage")) {
                    standardResponse.set("usage", openAiResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else {
                // 如果都不是标准格式，返回原始响应
                return openAiResponse.toString();
            }

            return standardResponse.toString();
        } catch (Exception e) {
            return openAiResponse.toString();
        }
    }

    @Override
    protected String transformStreamChunk(String chunk) {
        try {
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6).trim();
                if ("[DONE]".equals(jsonPart)) {
                    return chunk;
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                ObjectNode standardChunk = objectMapper.createObjectNode();
                
                // 设置基本字段
                standardChunk.put("id", "chatcmpl-" + System.currentTimeMillis());
                standardChunk.put("object", "chat.completion.chunk");
                standardChunk.put("created", System.currentTimeMillis() / 1000);
                
                // 复制模型信息
                if (chunkJson.has("model")) {
                    standardChunk.put("model", chunkJson.get("model").asText());
                }

                // 处理选择项
                if (chunkJson.has("choices")) {
                    standardChunk.set("choices", chunkJson.get("choices"));
                } else {
                    // 创建标准的选择项格式
                    ObjectNode choice = objectMapper.createObjectNode();
                    choice.put("index", 0);
                    
                    // 处理delta
                    ObjectNode delta = objectMapper.createObjectNode();
                    if (chunkJson.has("delta")) {
                        delta = (ObjectNode) chunkJson.get("delta");
                    } else if (chunkJson.has("content")) {
                        delta.put("content", chunkJson.get("content").asText());
                    } else if (chunkJson.has("text")) {
                        delta.put("content", chunkJson.get("text").asText());
                    }
                    
                    choice.set("delta", delta);
                    
                    // 处理finish_reason
                    if (chunkJson.has("finish_reason")) {
                        choice.put("finish_reason", chunkJson.get("finish_reason").asText());
                    }
                    
                    standardChunk.set("choices", objectMapper.createArrayNode().add(choice));
                }

                // 添加空的usage字段（如果原响应中有）
                if (chunkJson.has("usage")) {
                    standardChunk.set("usage", chunkJson.get("usage"));
                }

                return "data: " + standardChunk.toString();
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
        }
    }

    @Override
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(WebClient.RequestBodySpec requestSpec, T request) {
        // Normal adapter保持标准的OpenAI格式，不需要特殊的头部配置
        return super.configureRequestHeaders(requestSpec, request);
    }
}
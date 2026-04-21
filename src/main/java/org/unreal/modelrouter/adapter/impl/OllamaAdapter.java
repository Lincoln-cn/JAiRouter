package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.adapter.AdapterCapabilities;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.adapter.handler.ResponseHandler;

import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.repository.ModelCallStatsRepository;

/**
 * Ollama Adapter - 适配Ollama API格式的示例
 * 展示如何轻松扩展新的适配器，支持最新的Ollama API特性
 */
public class OllamaAdapter extends BaseAdapter {

    public OllamaAdapter(ModelServiceRegistry registry,
                         MetricsCollector metricsCollector,
                         ObjectMapper objectMapper,
                         ModelCallStatsRepository statsRepository,
                         RequestBuilder requestBuilder,
                         ResponseHandler responseHandler) {
        super(registry, metricsCollector, objectMapper, statsRepository, requestBuilder, responseHandler);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .build();
    }

    @Override
    protected String getAdapterType() {
        return "ollama";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        // 记录Ollama适配器特定的追踪信息
        org.unreal.modelrouter.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    // 添加Ollama特定的属性
                    currentSpan.setAttribute("adapter.local_deployment", true);
                    currentSpan.setAttribute("adapter.supports_custom_models", true);
                    currentSpan.setAttribute("adapter.deployment_type", "local");
                    currentSpan.setAttribute("adapter.model_format", "gguf");
                    currentSpan.setAttribute("adapter.version", "v1");

                    // 根据请求类型添加特定属性
                    if (request instanceof ChatDTO.Request) {
                        ChatDTO.Request chatRequest = (ChatDTO.Request) request;
                        currentSpan.setAttribute("request.model_family", inferModelFamily(chatRequest.model()));
                        currentSpan.setAttribute("request.stream", chatRequest.stream() != null ? chatRequest.stream() : false);
                        currentSpan.setAttribute("request.temperature", chatRequest.temperature() != null ? chatRequest.temperature() : 0.8);
                    } else if (request instanceof EmbeddingDTO.Request) {
                        EmbeddingDTO.Request embeddingRequest = (EmbeddingDTO.Request) request;
                        currentSpan.setAttribute("request.embedding_model", embeddingRequest.model());
                        currentSpan.setAttribute("request.input_type", embeddingRequest.input() instanceof String ? "string" : "array");
                    } else if (request instanceof RerankDTO.Request) {
                        RerankDTO.Request rerankRequest = (RerankDTO.Request) request;
                        currentSpan.setAttribute("request.query_length", rerankRequest.query() != null ? rerankRequest.query().length() : 0);
                        currentSpan.setAttribute("request.documents_count", rerankRequest.documents() != null ? rerankRequest.documents().size() : 0);
                    } else if (request instanceof TtsDTO.Request) {
                        TtsDTO.Request ttsRequest = (TtsDTO.Request) request;
                        currentSpan.setAttribute("request.voice", ttsRequest.voice());
                        currentSpan.setAttribute("request.input_length", ttsRequest.input() != null ? ttsRequest.input().length() : 0);
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

        if (request instanceof ChatDTO.Request) {
            return transformChatRequest((ChatDTO.Request) request);
        } else if (request instanceof EmbeddingDTO.Request) {
            return transformEmbeddingRequest((EmbeddingDTO.Request) request);
        } else if (request instanceof RerankDTO.Request) {
            return transformRerankRequest((RerankDTO.Request) request);
        } else if (request instanceof TtsDTO.Request) {
            return transformTtsRequest((TtsDTO.Request) request);
        } else if (request instanceof SttDTO.Request) {
            return transformSttRequest((SttDTO.Request) request);
        } else {
            return request;
        }
    }

    /**
     * 推断模型家族
     */
    private String inferModelFamily(String modelName) {
        if (modelName == null) {
            return "unknown";
        }

        String lowerName = modelName.toLowerCase();
        if (lowerName.contains("llama")) {
            return "llama";
        } else if (lowerName.contains("qwen")) {
            return "qwen";
        } else if (lowerName.contains("chatglm")) {
            return "chatglm";
        } else if (lowerName.contains("baichuan")) {
            return "baichuan";
        } else if (lowerName.contains("mistral")) {
            return "mistral";
        } else {
            return "custom";
        }
    }

    /**
     * Ollama的Chat API格式转换
     * 支持最新的Ollama API参数
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            // Ollama使用不同的字段名
            ollamaRequest.put("model", adaptModelName(request.model()));

            // Ollama的消息格式可能需要调整
            ollamaRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // Ollama特有的选项
            ObjectNode options = objectMapper.createObjectNode();
            if (request.temperature() != null) {
                options.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                options.put("num_predict", request.maxTokens()); // Ollama使用num_predict
            }
            if (request.topP() != null) {
                options.put("top_p", request.topP());
            }
            if (request.topK() != null) {
                options.put("top_k", request.topK());
            }
            if (request.frequencyPenalty() != null) {
                options.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.presencePenalty() != null) {
                options.put("presence_penalty", request.presencePenalty());
            }
            if (request.repeatPenalty() != null) {
                options.put("repeat_penalty", request.repeatPenalty());
            }
            if (request.seed() != null) {
                options.put("seed", request.seed());
            }
            if (request.numKeep() != null) {
                options.put("num_keep", request.numKeep());
            }
            if (request.tfsZ() != null) {
                options.put("tfs_z", request.tfsZ());
            }
            if (request.typicalP() != null) {
                options.put("typical_p", request.typicalP());
            }
            if (request.repeatLastN() != null) {
                options.put("repeat_last_n", request.repeatLastN());
            }
            if (request.temperature() != null) {
                options.put("temperature", request.temperature());
            }
            if (request.penalizeNewline() != null) {
                options.put("penalize_newline", request.penalizeNewline());
            }
            if (request.stop() != null) {
                options.set("stop", objectMapper.valueToTree(request.stop()));
            }
            ollamaRequest.set("options", options);

            // 流式处理
            if (request.stream() != null) {
                ollamaRequest.put("stream", request.stream());
            }

            // Ollama扩展参数
            ObjectNode extraBody = objectMapper.createObjectNode();
            if (request.useBeamSearch() != null) {
                extraBody.put("use_beam_search", request.useBeamSearch());
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
                ollamaRequest.set("extra_body", extraBody);
            }

            return ollamaRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * Ollama的Embedding API格式转换
     * 支持最新的Ollama API参数
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            ollamaRequest.put("model", adaptModelName(request.model()));

            // 处理输入 - 支持字符串或数组
            if (request.input() instanceof String) {
                ollamaRequest.put("prompt", (String) request.input());
            } else if (request.input() instanceof String[]) {
                ollamaRequest.set("prompt", objectMapper.valueToTree(request.input()));
            } else if (request.input() instanceof java.util.List) {
                ollamaRequest.set("prompt", objectMapper.valueToTree(request.input()));
            } else {
                // 默认处理
                ollamaRequest.set("prompt", objectMapper.valueToTree(request.input()));
            }

            // Ollama扩展参数
            ObjectNode options = objectMapper.createObjectNode();
            if (request.truncatePromptTokens() != null) {
                options.put("num_ctx", request.truncatePromptTokens());
            }
            ollamaRequest.set("options", options);

            // Ollama扩展参数
            ObjectNode extraBody = objectMapper.createObjectNode();
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
                ollamaRequest.set("extra_body", extraBody);
            }

            return ollamaRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * Ollama的Rerank API格式转换
     * 支持最新的Ollama API参数
     */
    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            ollamaRequest.put("model", adaptModelName(rerankRequest.model()));
            ollamaRequest.put("query", rerankRequest.query());
            ollamaRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                ollamaRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                ollamaRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            // Ollama扩展参数
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
                ollamaRequest.set("extra_body", extraBody);
            }

            return ollamaRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return rerankRequest;
        }
    }

    /**
     * Ollama的TTS API格式转换
     * 支持最新的Ollama API参数
     */
    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            ollamaRequest.put("model", adaptModelName(ttsRequest.model()));
            ollamaRequest.put("input", ttsRequest.input());
            ollamaRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                ollamaRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                ollamaRequest.put("speed", ttsRequest.speed());
            }

            return ollamaRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return ttsRequest;
        }
    }

    /**
     * Ollama的STT API格式转换
     * 支持最新的Ollama API参数
     */
    private Object transformSttRequest(SttDTO.Request sttRequest) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("model", sttRequest.model());
            builder.part("language", sttRequest.language());

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
                return transformResponseJson(jsonResponse);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 将Ollama响应转换为OpenAI格式
     */
    private String transformResponseJson(JsonNode ollamaResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            // 根据Ollama响应类型进行转换
            if (ollamaResponse.has("choices")) {
                // 聊天响应转换
                standardResponse.set("id", ollamaResponse.path("id"));
                standardResponse.put("object", "chat.completion");
                standardResponse.put("created", System.currentTimeMillis() / 1000);
                
                // 复制模型信息
                if (ollamaResponse.has("model")) {
                    standardResponse.put("model", ollamaResponse.get("model").asText());
                }
                
                // 复制选择项
                standardResponse.set("choices", ollamaResponse.get("choices"));
                
                // 添加使用情况统计（如果存在）
                if (ollamaResponse.has("usage")) {
                    standardResponse.set("usage", ollamaResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("completion_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (ollamaResponse.has("data") && ollamaResponse.has("model")) {
                // 嵌入响应转换
                standardResponse.put("object", "list");
                standardResponse.set("data", ollamaResponse.get("data"));
                standardResponse.put("model", ollamaResponse.get("model").asText());
                
                // 添加使用情况统计（如果存在）
                if (ollamaResponse.has("usage")) {
                    standardResponse.set("usage", ollamaResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (ollamaResponse.has("results")) {
                // 重排序响应转换
                standardResponse.set("id", objectMapper.getNodeFactory().textNode("ollama-" + System.currentTimeMillis()));
                standardResponse.set("results", ollamaResponse.get("results"));
                if (ollamaResponse.has("model")) {
                    standardResponse.put("model", ollamaResponse.get("model").asText());
                }
                
                // 添加使用情况统计（如果存在）
                if (ollamaResponse.has("usage")) {
                    standardResponse.set("usage", ollamaResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else {
                // 如果都不是标准格式，返回原始响应
                return ollamaResponse.toString();
            }

            return standardResponse.toString();
        } catch (Exception e) {
            return ollamaResponse.toString();
        }
    }

    @Override
    protected String transformStreamChunk(String chunk) {
        try {
            // 检查是否是标准的SSE格式
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6);
                // 对于 [DONE] 标记，直接返回纯文本（Spring WebFlux 会自动处理 SSE 格式）
                if ("[DONE]".equals(jsonPart.trim())) {
                    return "[DONE]";
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                ObjectNode standardChunk = objectMapper.createObjectNode();
                
                // 设置基本字段
                standardChunk.put("id", "ollama-" + System.currentTimeMillis());
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

                // 返回纯 JSON 字符串，Spring WebFlux 会自动添加 SSE 格式的 data: 前缀
                return standardChunk.toString();
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        // Ollama通常不需要认证，或使用简单的API Key
        if (authorization != null && !authorization.startsWith("Bearer ")) {
            return "Bearer " + authorization;
        }
        return authorization;
    }

    @Override
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(WebClient.RequestBodySpec requestSpec, T request) {
        // Ollama特有的请求头配置
        return super.configureRequestHeaders(requestSpec, request)
                .header("User-Agent", "ModelRouter-OllamaAdapter/1.0");
    }
}
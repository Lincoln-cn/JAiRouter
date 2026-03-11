package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.unreal.modelrouter.adapter.AdapterCapabilities;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

/**
 * VLLM Adapter - 适配VLLM API格式
 * VLLM (Very Large Language Model) 推理服务器适配器
 * 支持最新的vLLM OpenAI兼容API
 */
public class VllmAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VllmAdapter(ModelServiceRegistry registry, MetricsCollector metricsCollector) {
        super(registry, metricsCollector);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .rerank(true)
                .build();
    }

    @Override
    protected String getAdapterType() {
        return "vllm";
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
                    // 添加VLLM特定的属性
                    currentSpan.setAttribute("adapter.high_performance", true);
                    currentSpan.setAttribute("adapter.gpu_accelerated", true);
                    currentSpan.setAttribute("adapter.deployment_type", "vllm");
                    currentSpan.setAttribute("adapter.version", "v1");

                    // 根据请求类型添加特定属性
                    if (request instanceof ChatDTO.Request) {
                        ChatDTO.Request chatRequest = (ChatDTO.Request) request;
                        currentSpan.setAttribute("request.stream", chatRequest.stream() != null ? chatRequest.stream() : false);
                        currentSpan.setAttribute("request.max_tokens", chatRequest.maxTokens() != null ? chatRequest.maxTokens() : 0);
                        currentSpan.setAttribute("request.temperature", chatRequest.temperature() != null ? chatRequest.temperature() : 1.0);
                    } else if (request instanceof EmbeddingDTO.Request) {
                        EmbeddingDTO.Request embeddingRequest = (EmbeddingDTO.Request) request;
                        currentSpan.setAttribute("request.embedding_model", embeddingRequest.model());
                        currentSpan.setAttribute("request.input_type", embeddingRequest.input() instanceof String ? "string" : "array");
                    } else if (request instanceof RerankDTO.Request) {
                        RerankDTO.Request rerankRequest = (RerankDTO.Request) request;
                        currentSpan.setAttribute("request.query_length", rerankRequest.query() != null ? rerankRequest.query().length() : 0);
                        currentSpan.setAttribute("request.documents_count", rerankRequest.documents() != null ? rerankRequest.documents().size() : 0);
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
     * 转换Chat请求格式以适配VLLM
     * 支持最新的vLLM OpenAI兼容API参数
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            // 标准OpenAI参数
            vllmRequest.put("model", adaptModelName(request.model()));
            vllmRequest.set("messages", objectMapper.valueToTree(request.messages()));

            if (request.temperature() != null) {
                vllmRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                vllmRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                vllmRequest.put("top_p", request.topP());
            }
            if (request.stop() != null) {
                vllmRequest.set("stop", objectMapper.valueToTree(request.stop()));
            }
            if (request.stream() != null) {
                vllmRequest.put("stream", request.stream());
            }
            if (request.n() != null) {
                vllmRequest.put("n", request.n());
            }
            if (request.presencePenalty() != null) {
                vllmRequest.put("presence_penalty", request.presencePenalty());
            }
            if (request.frequencyPenalty() != null) {
                vllmRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.logprobs() != null) {
                vllmRequest.put("logprobs", request.logprobs());
            }
            if (request.topLogprobs() != null) {
                vllmRequest.put("top_logprobs", request.topLogprobs());
            }

            // vLLM扩展参数 - 通过extra_body传递
            ObjectNode extraBody = objectMapper.createObjectNode();
            
            // vLLM采样参数
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
                vllmRequest.set("extra_body", extraBody);
            }

            return vllmRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * 转换Embedding请求格式
     * 支持最新的vLLM OpenAI兼容API参数
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            vllmRequest.put("model", adaptModelName(request.model()));

            // 处理输入 - 支持字符串或数组
            if (request.input() instanceof String) {
                vllmRequest.put("input", (String) request.input());
            } else if (request.input() instanceof String[]) {
                vllmRequest.set("input", objectMapper.valueToTree(request.input()));
            } else if (request.input() instanceof java.util.List) {
                vllmRequest.set("input", objectMapper.valueToTree(request.input()));
            } else {
                // 默认处理
                vllmRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            // 标准参数
            if (request.encodingFormat() != null) {
                vllmRequest.put("encoding_format", request.encodingFormat());
            }
            if (request.dimensions() != null) {
                vllmRequest.put("dimensions", request.dimensions());
            }
            if (request.user() != null) {
                vllmRequest.put("user", request.user());
            }

            // vLLM扩展参数
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
                vllmRequest.set("extra_body", extraBody);
            }

            return vllmRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * 转换Rerank请求格式
     * 支持最新的vLLM OpenAI兼容API参数
     */
    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            vllmRequest.put("model", adaptModelName(rerankRequest.model()));
            vllmRequest.put("query", rerankRequest.query());
            vllmRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                vllmRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                vllmRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            // vLLM扩展参数
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
                vllmRequest.set("extra_body", extraBody);
            }

            return vllmRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return rerankRequest;
        }
    }

    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            String vllmModelName = adaptModelName(ttsRequest.model());
            vllmRequest.put("model", adaptModelName(vllmModelName));
            vllmRequest.put("input", ttsRequest.input());
            vllmRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                vllmRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                vllmRequest.put("speed", ttsRequest.speed());
            }

            return vllmRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return ttsRequest;
        }
    }

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
                JsonNode jsonResponse = objectMapper.readTree(adaptModelName(responseStr));
                return transformResponseJson(jsonResponse);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 转换响应格式以符合OpenAI标准
     */
    private String transformResponseJson(JsonNode vllmResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            // 根据vLLM响应类型进行转换
            if (vllmResponse.has("choices")) {
                // 聊天响应转换
                standardResponse.set("id", vllmResponse.path("id"));
                standardResponse.put("object", "chat.completion");
                standardResponse.put("created", System.currentTimeMillis() / 1000);
                
                // 复制模型信息
                if (vllmResponse.has("model")) {
                    standardResponse.put("model", vllmResponse.get("model").asText());
                }
                
                // 复制选择项
                standardResponse.set("choices", vllmResponse.get("choices"));
                
                // 添加使用情况统计（如果存在）
                if (vllmResponse.has("usage")) {
                    standardResponse.set("usage", vllmResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("completion_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (vllmResponse.has("data") && vllmResponse.has("model")) {
                // 嵌入响应转换
                standardResponse.put("object", "list");
                standardResponse.set("data", vllmResponse.get("data"));
                standardResponse.put("model", vllmResponse.get("model").asText());
                
                // 添加使用情况统计（如果存在）
                if (vllmResponse.has("usage")) {
                    standardResponse.set("usage", vllmResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (vllmResponse.has("results")) {
                // 重排序响应转换
                standardResponse.set("id", objectMapper.getNodeFactory().textNode("cmpl-" + System.currentTimeMillis()));
                standardResponse.set("results", vllmResponse.get("results"));
                if (vllmResponse.has("model")) {
                    standardResponse.put("model", vllmResponse.get("model").asText());
                }
                
                // 添加使用情况统计（如果存在）
                if (vllmResponse.has("usage")) {
                    standardResponse.set("usage", vllmResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else {
                // 如果都不是标准格式，返回原始响应
                return vllmResponse.toString();
            }

            return standardResponse.toString();
        } catch (Exception e) {
            return vllmResponse.toString();
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        if (adaptModelName(authorization) != null && adaptModelName(authorization).startsWith("Bearer ")) {
            return adaptModelName(authorization);
        } else if (adaptModelName(authorization) != null) {
            return "Bearer " + adaptModelName(authorization);
        }
        return null;
    }

    @Override
    protected String transformStreamChunk(String chunk) {
        try {
            // 检查是否是标准的SSE格式
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6);
                if ("[DONE]".equals(jsonPart.trim())) {
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
}
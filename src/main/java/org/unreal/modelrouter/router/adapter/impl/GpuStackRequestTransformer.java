package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.adapter.util.ModelUtils;

/**
 * GPUStack 请求转换器
 * 负责将各种请求格式转换为GPUStack API格式
 */
public class GpuStackRequestTransformer {

    private static final Logger log = LoggerFactory.getLogger(GpuStackRequestTransformer.class);

    private final ObjectMapper objectMapper;

    public GpuStackRequestTransformer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 根据请求类型转换请求
     */
    public Object transformRequest(final Object request, final String adapterType, final String modelFieldName) {
        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequest(chatRequest, modelFieldName);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return transformEmbeddingRequest(embeddingRequest, modelFieldName);
        } else if (request instanceof RerankDTO.Request rerankRequest) {
            return transformRerankRequest(rerankRequest, modelFieldName);
        } else if (request instanceof TtsDTO.Request ttsRequest) {
            return transformTtsRequest(ttsRequest, modelFieldName);
        } else if (request instanceof SttDTO.Request) {
            return request;
        }
        return request;
    }

    /**
     * 转换Chat请求格式以适配GPUStack
     */
    public Object transformChatRequest(final ChatDTO.Request request, final String modelFieldName) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            gpuStackRequest.put("model", modelFieldName);
            gpuStackRequest.set("messages", objectMapper.valueToTree(request.messages()));

            if (request.temperature() != null) {
                gpuStackRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                gpuStackRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                gpuStackRequest.put("top_p", request.topP());
            }
            if (request.stop() != null) {
                gpuStackRequest.set("stop", objectMapper.valueToTree(request.stop()));
            }
            if (request.stream() != null) {
                gpuStackRequest.put("stream", request.stream());
            }
            if (request.n() != null) {
                gpuStackRequest.put("n", request.n());
            }
            if (request.presencePenalty() != null) {
                gpuStackRequest.put("presence_penalty", request.presencePenalty());
            }
            if (request.frequencyPenalty() != null) {
                gpuStackRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.logprobs() != null) {
                gpuStackRequest.put("logprobs", request.logprobs());
            }
            if (request.topLogprobs() != null) {
                gpuStackRequest.put("top_logprobs", request.topLogprobs());
            }

            ObjectNode extraBody = objectMapper.createObjectNode();
            addChatExtraParams(extraBody, request);

            if (extraBody.size() > 0) {
                gpuStackRequest.set("extra_body", extraBody);
            }

            return gpuStackRequest;
        } catch (Exception e) {
            log.error("Failed to transform chat request for GPUStack", e);
            return request;
        }
    }

    private void addChatExtraParams(final ObjectNode extraBody, final ChatDTO.Request request) {
        putIfNotNull(extraBody, "use_beam_search", request.useBeamSearch());
        putIfNotNull(extraBody, "top_k", request.topK());
        putIfNotNull(extraBody, "min_p", request.minP());
        putIfNotNull(extraBody, "repetition_penalty", request.repetitionPenalty());
        putIfNotNull(extraBody, "length_penalty", request.lengthPenalty());
        putIfNotNull(extraBody, "include_stop_str_in_output", request.includeStopStrInOutput());
        putIfNotNull(extraBody, "ignore_eos", request.ignoreEos());
        putIfNotNull(extraBody, "min_tokens", request.minTokens());
        putIfNotNull(extraBody, "skip_special_tokens", request.skipSpecialTokens());
        putIfNotNull(extraBody, "spaces_between_special_tokens", request.spacesBetweenSpecialTokens());
        putIfNotNull(extraBody, "truncate_prompt_tokens", request.truncatePromptTokens());
        putIfNotNull(extraBody, "echo", request.echo());
        putIfNotNull(extraBody, "add_generation_prompt", request.addGenerationPrompt());
        putIfNotNull(extraBody, "continue_final_message", request.continueFinalMessage());
        putIfNotNull(extraBody, "add_special_tokens", request.addSpecialTokens());
        putIfNotNull(extraBody, "chat_template", request.chatTemplate());
        putIfNotNull(extraBody, "priority", request.priority());
        putIfNotNull(extraBody, "request_id", request.requestId());
        putIfNotNull(extraBody, "return_tokens_as_token_ids", request.returnTokensAsTokenIds());
        putIfNotNull(extraBody, "return_token_ids", request.returnTokenIds());
        putIfNotNull(extraBody, "cache_salt", request.cacheSalt());

        if (request.documents() != null) {
            extraBody.set("documents", objectMapper.valueToTree(request.documents()));
        }
        if (request.chatTemplateKwargs() != null) {
            extraBody.set("chat_template_kwargs", objectMapper.valueToTree(request.chatTemplateKwargs()));
        }
        if (request.structuredOutputs() != null) {
            extraBody.set("structured_outputs", objectMapper.valueToTree(request.structuredOutputs()));
        }
        if (request.repetitionDetection() != null) {
            extraBody.set("repetition_detection", objectMapper.valueToTree(request.repetitionDetection()));
        }
    }

    /**
     * 转换Embedding请求格式
     */
    public Object transformEmbeddingRequest(final EmbeddingDTO.Request request, final String modelFieldName) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            gpuStackRequest.put("model", modelFieldName);

            if (request.input() instanceof String) {
                gpuStackRequest.put("input", (String) request.input());
            } else {
                gpuStackRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            if (request.encodingFormat() != null) {
                gpuStackRequest.put("encoding_format", request.encodingFormat());
            }
            if (request.dimensions() != null) {
                gpuStackRequest.put("dimensions", request.dimensions());
            }
            if (request.user() != null) {
                gpuStackRequest.put("user", request.user());
            }

            ObjectNode extraBody = objectMapper.createObjectNode();
            putIfNotNull(extraBody, "truncate_prompt_tokens", request.truncatePromptTokens());
            putIfNotNull(extraBody, "request_id", request.requestId());
            putIfNotNull(extraBody, "priority", request.priority());
            putIfNotNull(extraBody, "cache_salt", request.cacheSalt());
            putIfNotNull(extraBody, "add_special_tokens", request.addSpecialTokens());
            putIfNotNull(extraBody, "embed_dtype", request.embedDtype());
            putIfNotNull(extraBody, "endianness", request.endianness());
            putIfNotNull(extraBody, "use_activation", request.useActivation());
            putIfNotNull(extraBody, "chat_template", request.chatTemplate());
            putIfNotNull(extraBody, "add_generation_prompt", request.addGenerationPrompt());
            putIfNotNull(extraBody, "continue_final_message", request.continueFinalMessage());

            if (request.chatTemplateKwargs() != null) {
                extraBody.set("chat_template_kwargs", objectMapper.valueToTree(request.chatTemplateKwargs()));
            }
            if (request.mediaIoKwargs() != null) {
                extraBody.set("media_io_kwargs", objectMapper.valueToTree(request.mediaIoKwargs()));
            }

            if (extraBody.size() > 0) {
                gpuStackRequest.set("extra_body", extraBody);
            }

            return gpuStackRequest;
        } catch (Exception e) {
            log.error("Failed to transform embedding request for GPUStack", e);
            return request;
        }
    }

    /**
     * 转换Rerank请求格式
     */
    public Object transformRerankRequest(final RerankDTO.Request rerankRequest, final String modelFieldName) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            gpuStackRequest.put("model", modelFieldName);
            gpuStackRequest.put("query", rerankRequest.query());
            gpuStackRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                gpuStackRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                gpuStackRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            ObjectNode extraBody = objectMapper.createObjectNode();
            putIfNotNull(extraBody, "request_id", rerankRequest.requestId());
            putIfNotNull(extraBody, "priority", rerankRequest.priority());
            putIfNotNull(extraBody, "truncate_prompt_tokens", rerankRequest.truncatePromptTokens());

            if (extraBody.size() > 0) {
                gpuStackRequest.set("extra_body", extraBody);
            }

            return gpuStackRequest;
        } catch (Exception e) {
            log.error("Failed to transform rerank request for GPUStack", e);
            return rerankRequest;
        }
    }

    /**
     * 转换TTS请求格式
     */
    public Object transformTtsRequest(final TtsDTO.Request ttsRequest, final String modelFieldName) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            gpuStackRequest.put("model", modelFieldName);
            gpuStackRequest.put("input", ttsRequest.input());
            gpuStackRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                gpuStackRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                gpuStackRequest.put("speed", ttsRequest.speed());
            }

            return gpuStackRequest;
        } catch (Exception e) {
            log.error("Failed to transform TTS request for GPUStack", e);
            return ttsRequest;
        }
    }

    /**
     * 转换STT请求格式
     */
    public Object transformSttRequest(final SttDTO.Request sttRequest) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("model", sttRequest.model());
            builder.part("language", sttRequest.language());

            builder.asyncPart("file", sttRequest.file().content(), DataBuffer.class)
                    .filename(sttRequest.file().filename())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);

            if (sttRequest.prompt() != null) {
                builder.part("prompt", sttRequest.prompt());
            }
            if (sttRequest.responseFormat() != null) {
                builder.part("response_format", sttRequest.responseFormat());
            }
            if (sttRequest.temperature() != null) {
                builder.part("temperature", sttRequest.temperature().toString());
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to transform STT request for GPUStack", e);
            return sttRequest;
        }
    }

    private void putIfNotNull(final ObjectNode node, final String field, final Object value) {
        if (value != null) {
            if (value instanceof Integer intVal) {
                node.put(field, intVal);
            } else if (value instanceof Long longVal) {
                node.put(field, longVal);
            } else if (value instanceof Double doubleVal) {
                node.put(field, doubleVal);
            } else if (value instanceof Float floatVal) {
                node.put(field, floatVal);
            } else if (value instanceof Boolean boolVal) {
                node.put(field, boolVal);
            } else if (value instanceof String strVal) {
                node.put(field, strVal);
            }
        }
    }
}

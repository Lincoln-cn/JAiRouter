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

/**
 * vLLM 请求转换器
 * 负责将各种请求格式转换为vLLM API格式
 *
 * @author JAiRouter Team
 * @since 2.9.1
 */
public final class VllmRequestTransformer {

    private static final Logger log = LoggerFactory.getLogger(VllmRequestTransformer.class);

    private final ObjectMapper objectMapper;

    public VllmRequestTransformer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 转换Chat请求格式以适配VLLM
     * 支持最新的vLLM OpenAI兼容API参数
     */
    public Object transformChatRequest(final ChatDTO.Request request, final String adaptedModelName) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            // 标准OpenAI参数
            vllmRequest.put("model", adaptedModelName);
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
            log.warn("vLLM chat request transform error: {}", e.getMessage());
            return request;
        }
    }

    /**
     * 转换Embedding请求格式
     * 支持最新的vLLM OpenAI兼容API参数
     */
    public Object transformEmbeddingRequest(final EmbeddingDTO.Request request, final String adaptedModelName) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            vllmRequest.put("model", adaptedModelName);

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
            log.warn("vLLM embedding request transform error: {}", e.getMessage());
            return request;
        }
    }

    /**
     * 转换Rerank请求格式
     * 支持最新的vLLM OpenAI兼容API参数
     */
    public Object transformRerankRequest(final RerankDTO.Request rerankRequest, final String adaptedModelName) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            vllmRequest.put("model", adaptedModelName);
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
            log.warn("vLLM rerank request transform error: {}", e.getMessage());
            return rerankRequest;
        }
    }

    /**
     * 转换TTS请求格式
     */
    public Object transformTtsRequest(final TtsDTO.Request ttsRequest, final String adaptedModelName) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            vllmRequest.put("model", adaptedModelName);
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
            log.warn("vLLM TTS request transform error: {}", e.getMessage());
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

            // 添加其他字段
            if (sttRequest.prompt() != null) {
                builder.part("prompt", sttRequest.prompt());
            }
            if (sttRequest.responseFormat() != null) {
                builder.part("response_format", sttRequest.responseFormat());
            }
            if (sttRequest.temperature() != null) {
                // 关键：temperature 必须转为字符串，否则 Content-Type 会是 application/octet-stream
                builder.part("temperature", sttRequest.temperature().toString());
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("vLLM STT request transform error: {}", e.getMessage());
            return sttRequest;
        }
    }
}

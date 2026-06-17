package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

/**
 * LocalAI请求转换器
 * 将标准请求转换为LocalAI API格式
 */
@Slf4j
@RequiredArgsConstructor
public class LocalAiRequestTransformer {

    private final ObjectMapper objectMapper;

    /**
     * 转换请求
     */
    public Object transform(final Object request) {
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
        }
        return request;
    }

    private Object transformChatRequest(final ChatDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());
            localAiRequest.set("messages", objectMapper.valueToTree(request.messages()));

            if (request.temperature() != null) {
                localAiRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                localAiRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                localAiRequest.put("top_p", request.topP());
            }
            if (request.stop() != null) {
                localAiRequest.set("stop", objectMapper.valueToTree(request.stop()));
            }
            if (request.stream() != null) {
                localAiRequest.put("stream", request.stream());
            }
            if (request.n() != null) {
                localAiRequest.put("n", request.n());
            }
            if (request.presencePenalty() != null) {
                localAiRequest.put("presence_penalty", request.presencePenalty());
            }
            if (request.frequencyPenalty() != null) {
                localAiRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.logprobs() != null) {
                localAiRequest.put("logprobs", request.logprobs());
            }
            if (request.topLogprobs() != null) {
                localAiRequest.put("top_logprobs", request.topLogprobs());
            }
            if (request.user() != null) {
                localAiRequest.put("user", request.user());
            }

            // LocalAI扩展参数
            ObjectNode extraBody = buildExtraBody(request);
            if (extraBody.size() > 0) {
                localAiRequest.set("extra_body", extraBody);
            }

            return localAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform LocalAI chat request: {}", e.getMessage());
            return request;
        }
    }

    private ObjectNode buildExtraBody(final ChatDTO.Request request) {
        ObjectNode extraBody = objectMapper.createObjectNode();

        if (request.useBeamSearch() != null) extraBody.put("use_beam_search", request.useBeamSearch());
        if (request.topK() != null) extraBody.put("top_k", request.topK());
        if (request.minP() != null) extraBody.put("min_p", request.minP());
        if (request.repetitionPenalty() != null) extraBody.put("repetition_penalty", request.repetitionPenalty());
        if (request.lengthPenalty() != null) extraBody.put("length_penalty", request.lengthPenalty());
        if (request.includeStopStrInOutput() != null) extraBody.put("include_stop_str_in_output", request.includeStopStrInOutput());
        if (request.ignoreEos() != null) extraBody.put("ignore_eos", request.ignoreEos());
        if (request.minTokens() != null) extraBody.put("min_tokens", request.minTokens());
        if (request.skipSpecialTokens() != null) extraBody.put("skip_special_tokens", request.skipSpecialTokens());
        if (request.spacesBetweenSpecialTokens() != null) extraBody.put("spaces_between_special_tokens", request.spacesBetweenSpecialTokens());
        if (request.truncatePromptTokens() != null) extraBody.put("truncate_prompt_tokens", request.truncatePromptTokens());
        if (request.echo() != null) extraBody.put("echo", request.echo());
        if (request.addGenerationPrompt() != null) extraBody.put("add_generation_prompt", request.addGenerationPrompt());
        if (request.continueFinalMessage() != null) extraBody.put("continue_final_message", request.continueFinalMessage());
        if (request.addSpecialTokens() != null) extraBody.put("add_special_tokens", request.addSpecialTokens());
        if (request.documents() != null) extraBody.set("documents", objectMapper.valueToTree(request.documents()));
        if (request.chatTemplate() != null) extraBody.put("chat_template", request.chatTemplate());
        if (request.chatTemplateKwargs() != null) extraBody.set("chat_template_kwargs", objectMapper.valueToTree(request.chatTemplateKwargs()));
        if (request.structuredOutputs() != null) extraBody.set("structured_outputs", objectMapper.valueToTree(request.structuredOutputs()));
        if (request.priority() != null) extraBody.put("priority", request.priority());
        if (request.requestId() != null) extraBody.put("request_id", request.requestId());
        if (request.returnTokensAsTokenIds() != null) extraBody.put("return_tokens_as_token_ids", request.returnTokensAsTokenIds());
        if (request.returnTokenIds() != null) extraBody.put("return_token_ids", request.returnTokenIds());
        if (request.cacheSalt() != null) extraBody.put("cache_salt", request.cacheSalt());
        if (request.repetitionDetection() != null) extraBody.set("repetition_detection", objectMapper.valueToTree(request.repetitionDetection()));

        return extraBody;
    }

    private Object transformEmbeddingRequest(final EmbeddingDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());

            if (request.input() instanceof String) {
                localAiRequest.put("input", (String) request.input());
            } else {
                localAiRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            if (request.encodingFormat() != null) localAiRequest.put("encoding_format", request.encodingFormat());
            if (request.dimensions() != null) localAiRequest.put("dimensions", request.dimensions());
            if (request.user() != null) localAiRequest.put("user", request.user());

            // LocalAI扩展参数
            ObjectNode extraBody = objectMapper.createObjectNode();
            if (request.truncatePromptTokens() != null) extraBody.put("truncate_prompt_tokens", request.truncatePromptTokens());
            if (request.requestId() != null) extraBody.put("request_id", request.requestId());
            if (request.priority() != null) extraBody.put("priority", request.priority());
            if (request.cacheSalt() != null) extraBody.put("cache_salt", request.cacheSalt());
            if (request.addSpecialTokens() != null) extraBody.put("add_special_tokens", request.addSpecialTokens());
            if (request.embedDtype() != null) extraBody.put("embed_dtype", request.embedDtype());
            if (request.endianness() != null) extraBody.put("endianness", request.endianness());
            if (request.useActivation() != null) extraBody.put("use_activation", request.useActivation());
            if (request.chatTemplate() != null) extraBody.put("chat_template", request.chatTemplate());
            if (request.chatTemplateKwargs() != null) extraBody.set("chat_template_kwargs", objectMapper.valueToTree(request.chatTemplateKwargs()));
            if (request.mediaIoKwargs() != null) extraBody.set("media_io_kwargs", objectMapper.valueToTree(request.mediaIoKwargs()));
            if (request.addGenerationPrompt() != null) extraBody.put("add_generation_prompt", request.addGenerationPrompt());
            if (request.continueFinalMessage() != null) extraBody.put("continue_final_message", request.continueFinalMessage());

            if (extraBody.size() > 0) {
                localAiRequest.set("extra_body", extraBody);
            }

            return localAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform LocalAI embedding request: {}", e.getMessage());
            return request;
        }
    }

    private Object transformRerankRequest(final RerankDTO.Request rerankRequest) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", rerankRequest.model());
            localAiRequest.put("query", rerankRequest.query());
            localAiRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) localAiRequest.put("top_n", rerankRequest.topN());
            if (rerankRequest.returnDocuments() != null) localAiRequest.put("return_documents", rerankRequest.returnDocuments());

            ObjectNode extraBody = objectMapper.createObjectNode();
            if (rerankRequest.requestId() != null) extraBody.put("request_id", rerankRequest.requestId());
            if (rerankRequest.priority() != null) extraBody.put("priority", rerankRequest.priority());
            if (rerankRequest.truncatePromptTokens() != null) extraBody.put("truncate_prompt_tokens", rerankRequest.truncatePromptTokens());

            if (extraBody.size() > 0) {
                localAiRequest.set("extra_body", extraBody);
            }

            return localAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform LocalAI rerank request: {}", e.getMessage());
            return rerankRequest;
        }
    }

    private Object transformTtsRequest(final TtsDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());
            localAiRequest.put("input", request.input());
            localAiRequest.put("voice", request.voice());

            if (request.responseFormat() != null) {
                localAiRequest.put("response_format", request.responseFormat());
            } else {
                localAiRequest.put("response_format", "mp3");
            }

            if (request.speed() != null) {
                localAiRequest.put("speed", request.speed());
            }

            return localAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform LocalAI TTS request: {}", e.getMessage());
            return request;
        }
    }

    private Object transformSttRequest(final SttDTO.Request sttRequest) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("model", sttRequest.model());
            builder.part("language", sttRequest.language());

            builder.asyncPart("file", sttRequest.file().content(), DataBuffer.class)
                    .filename(sttRequest.file().filename())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);

            if (sttRequest.prompt() != null) builder.part("prompt", sttRequest.prompt());
            if (sttRequest.responseFormat() != null) builder.part("response_format", sttRequest.responseFormat());
            if (sttRequest.temperature() != null) {
                // 关键：temperature 必须转为字符串，否则 Content-Type 会是 application/octet-stream
                builder.part("temperature", sttRequest.temperature().toString());
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("Failed to transform LocalAI STT request: {}", e.getMessage());
            return sttRequest;
        }
    }
}

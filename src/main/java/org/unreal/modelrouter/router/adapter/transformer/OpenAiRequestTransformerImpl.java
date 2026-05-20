package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

import java.util.List;

/**
 * OpenAI请求转换器实现
 * 将各种请求类型转换为标准OpenAI API格式
 *
 * @since v2.7.17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiRequestTransformerImpl implements OpenAiRequestTransformer {

    private final ObjectMapper objectMapper;

    @Override
    public Object transformChatRequest(ChatDTO.Request request, ModelNameAdapter modelNameAdapter) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            // 标准OpenAI参数
            openAiRequest.put("model", modelNameAdapter.adaptModelName(request.model()));
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

            // 扩展参数
            ObjectNode extraBody = buildChatExtraBody(request);
            if (extraBody.size() > 0) {
                openAiRequest.set("extra_body", extraBody);
            }

            return openAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform chat request: {}", e.getMessage());
            return request;
        }
    }

    private ObjectNode buildChatExtraBody(ChatDTO.Request request) {
        ObjectNode extraBody = objectMapper.createObjectNode();

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

        return extraBody;
    }

    @Override
    public Object transformEmbeddingRequest(EmbeddingDTO.Request request, ModelNameAdapter modelNameAdapter) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", modelNameAdapter.adaptModelName(request.model()));

            // 处理输入
            if (request.input() instanceof String) {
                openAiRequest.put("input", (String) request.input());
            } else if (request.input() instanceof String[]) {
                openAiRequest.set("input", objectMapper.valueToTree(request.input()));
            } else if (request.input() instanceof List) {
                openAiRequest.set("input", objectMapper.valueToTree(request.input()));
            } else {
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

            // 扩展参数
            ObjectNode extraBody = buildEmbeddingExtraBody(request);
            if (extraBody.size() > 0) {
                openAiRequest.set("extra_body", extraBody);
            }

            return openAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform embedding request: {}", e.getMessage());
            return request;
        }
    }

    private ObjectNode buildEmbeddingExtraBody(EmbeddingDTO.Request request) {
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

        return extraBody;
    }

    @Override
    public Object transformRerankRequest(RerankDTO.Request request, ModelNameAdapter modelNameAdapter) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", modelNameAdapter.adaptModelName(request.model()));
            openAiRequest.set("query", objectMapper.valueToTree(request.query()));
            openAiRequest.set("documents", objectMapper.valueToTree(request.documents()));

            if (request.topN() != null) {
                openAiRequest.put("top_n", request.topN());
            }
            if (request.returnDocuments() != null) {
                openAiRequest.put("return_documents", request.returnDocuments());
            }

            return openAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform rerank request: {}", e.getMessage());
            return request;
        }
    }

    @Override
    public Object transformTtsRequest(TtsDTO.Request request, ModelNameAdapter modelNameAdapter) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", modelNameAdapter.adaptModelName(request.model()));
            openAiRequest.put("input", request.input());
            openAiRequest.put("voice", request.voice());

            if (request.responseFormat() != null) {
                openAiRequest.put("response_format", request.responseFormat());
            }
            if (request.speed() != null) {
                openAiRequest.put("speed", request.speed());
            }

            return openAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform TTS request: {}", e.getMessage());
            return request;
        }
    }

    @Override
    public Object transformImageEditRequest(ImageEditDTO.Request request, ModelNameAdapter modelNameAdapter) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", modelNameAdapter.adaptModelName(request.model()));
            openAiRequest.put("prompt", request.prompt());

            if (request.n() != null) {
                openAiRequest.put("n", request.n());
            }
            if (request.size() != null) {
                openAiRequest.put("size", request.size());
            }
            if (request.response_format() != null) {
                openAiRequest.put("response_format", request.response_format());
            }
            if (request.user() != null) {
                openAiRequest.put("user", request.user());
            }

            return openAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform image edit request: {}", e.getMessage());
            return request;
        }
    }

    @Override
    public Object transformSttRequest(SttDTO.Request request, ModelNameAdapter modelNameAdapter) {
        try {
            ObjectNode openAiRequest = objectMapper.createObjectNode();

            openAiRequest.put("model", modelNameAdapter.adaptModelName(request.model()));

            if (request.language() != null) {
                openAiRequest.put("language", request.language());
            }
            if (request.prompt() != null) {
                openAiRequest.put("prompt", request.prompt());
            }
            if (request.responseFormat() != null) {
                openAiRequest.put("response_format", request.responseFormat());
            }
            if (request.temperature() != null) {
                openAiRequest.put("temperature", request.temperature());
            }

            return openAiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform STT request: {}", e.getMessage());
            return request;
        }
    }
}

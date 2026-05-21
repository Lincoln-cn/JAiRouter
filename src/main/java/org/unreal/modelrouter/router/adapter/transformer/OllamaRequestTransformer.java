package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Ollama请求转换器
 * 负责将标准请求转换为Ollama API格式
 */
public class OllamaRequestTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaRequestTransformer.class);

    private final ObjectMapper objectMapper;

    public OllamaRequestTransformer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 转换Chat请求为Ollama格式
     */
    public ObjectNode transformChatRequest(final ChatDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            ollamaRequest.put("model", adaptModelName(request.model()));
            ollamaRequest.set("messages", objectMapper.valueToTree(request.messages()));

            ObjectNode options = buildChatOptions(request);
            ollamaRequest.set("options", options);

            if (request.stream() != null) {
                ollamaRequest.put("stream", request.stream());
            }

            ObjectNode extraBody = buildExtraBody(request);
            if (extraBody.size() > 0) {
                ollamaRequest.set("extra_body", extraBody);
            }

            return ollamaRequest;
        } catch (Exception e) {
            LOGGER.warn("Failed to transform chat request for Ollama: {}", e.getMessage());
            return objectMapper.createObjectNode().put("model", request.model()).set("messages", objectMapper.valueToTree(request.messages()));
        }
    }

    /**
     * 转换Embedding请求为Ollama格式
     */
    public ObjectNode transformEmbeddingRequest(final EmbeddingDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();
            ollamaRequest.put("model", adaptModelName(request.model()));

            if (request.input() != null) {
                if (request.input() instanceof String) {
                    ollamaRequest.put("prompt", (String) request.input());
                } else {
                    ollamaRequest.set("input", objectMapper.valueToTree(request.input()));
                }
            }

            ObjectNode options = buildEmbeddingOptions(request);
            if (options.size() > 0) {
                ollamaRequest.set("options", options);
            }

            return ollamaRequest;
        } catch (Exception e) {
            LOGGER.warn("Failed to transform embedding request for Ollama: {}", e.getMessage());
            return objectMapper.createObjectNode().put("model", request.model());
        }
    }

    /**
     * 转换Rerank请求为Ollama格式
     */
    public ObjectNode transformRerankRequest(final RerankDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();
            ollamaRequest.put("model", adaptModelName(request.model()));

            if (request.query() != null) {
                ollamaRequest.put("query", request.query());
            }
            if (request.documents() != null) {
                ollamaRequest.set("documents", objectMapper.valueToTree(request.documents()));
            }
            if (request.topN() != null) {
                ollamaRequest.put("top_n", request.topN());
            }

            return ollamaRequest;
        } catch (Exception e) {
            LOGGER.warn("Failed to transform rerank request for Ollama: {}", e.getMessage());
            return objectMapper.createObjectNode().put("model", request.model());
        }
    }

    /**
     * 转换TTS请求为Ollama格式
     */
    public ObjectNode transformTtsRequest(final TtsDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();
            ollamaRequest.put("model", adaptModelName(request.model()));

            if (request.input() != null) {
                ollamaRequest.put("input", request.input());
            }
            if (request.voice() != null) {
                ollamaRequest.put("voice", request.voice());
            }
            if (request.responseFormat() != null) {
                ollamaRequest.put("response_format", request.responseFormat());
            }
            if (request.speed() != null) {
                ollamaRequest.put("speed", request.speed());
            }

            return ollamaRequest;
        } catch (Exception e) {
            LOGGER.warn("Failed to transform TTS request for Ollama: {}", e.getMessage());
            return objectMapper.createObjectNode().put("model", request.model());
        }
    }

    /**
     * 转换STT请求为Ollama格式
     * Ollama的STT API需要multipart/form-data格式
     */
    public Object transformSttRequest(final SttDTO.Request request) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("model", adaptModelName(request.model()));
            builder.part("language", request.language());

            builder.asyncPart("file", request.file().content(), DataBuffer.class)
                    .filename(request.file().filename())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);

            if (request.prompt() != null) {
                builder.part("prompt", request.prompt());
            }
            if (request.responseFormat() != null) {
                builder.part("response_format", request.responseFormat());
            }
            if (request.temperature() != null) {
                builder.part("temperature", request.temperature());
            }

            return builder.build();
        } catch (Exception e) {
            LOGGER.warn("Failed to transform STT request for Ollama: {}", e.getMessage());
            return request;
        }
    }

    // ==================== 私有方法 ====================

    private String adaptModelName(final String modelName) {
        if (modelName == null) {
            return "default";
        }
        // Ollama可能需要特殊的模型名称格式
        return modelName;
    }

    private ObjectNode buildChatOptions(final ChatDTO.Request request) {
        ObjectNode options = objectMapper.createObjectNode();

        if (request.temperature() != null) {
            options.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            options.put("num_predict", request.maxTokens());
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
        if (request.stop() != null) {
            options.set("stop", objectMapper.valueToTree(request.stop()));
        }

        return options;
    }

    private ObjectNode buildExtraBody(final ChatDTO.Request request) {
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
        if (request.minTokens() != null) {
            extraBody.put("min_tokens", request.minTokens());
        }
        if (request.echo() != null) {
            extraBody.put("echo", request.echo());
        }
        if (request.priority() != null) {
            extraBody.put("priority", request.priority());
        }

        return extraBody;
    }

    private ObjectNode buildEmbeddingOptions(final EmbeddingDTO.Request request) {
        ObjectNode options = objectMapper.createObjectNode();

        if (request.encodingFormat() != null) {
            options.put("encoding_format", request.encodingFormat());
        }
        if (request.dimensions() != null) {
            options.put("dimensions", request.dimensions());
        }

        return options;
    }
}

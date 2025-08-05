package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ChatDTO {

    public record Request(
            String model,
            List<Message> messages,
            Boolean stream,
            @JsonProperty("max_tokens") Integer maxTokens,
            Double temperature,
            @JsonProperty("top_p") Double topP,
            @JsonProperty("top_k") Integer topK,
            @JsonProperty("frequency_penalty") Double frequencyPenalty,
            @JsonProperty("presence_penalty") Double presencePenalty,
            Object stop,
            String user
    ) {}

    public record Message(
            String role,
            String content,
            String name
    ) {}

    public record Response(
            String id,
            String object,
            Long created,
            String model,
            List<Choice> choices,
            Usage usage,
            @JsonProperty("system_fingerprint") String systemFingerprint
    ) {}

    public record Choice(
            Integer index,
            Message message,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    public record Delta(
            String role,
            String content
    ) {}

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}
}

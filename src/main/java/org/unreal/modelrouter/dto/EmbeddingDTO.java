package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class EmbeddingDTO {

    public record Request(
            String model,
            Object input, // Can be String or String[]
            @JsonProperty("encoding_format") String encodingFormat,
            Integer dimensions,
            String user
    ) {}

    public record Response(
            String object,
            List<EmbeddingData> data,
            String model,
            Usage usage
    ) {}

    public record EmbeddingData(
            String object,
            List<Double> embedding,
            Integer index
    ) {}

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}
}
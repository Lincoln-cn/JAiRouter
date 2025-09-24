package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RerankDTO {

    public record Request(
            String model,
            String query,
            List<String> documents,
            @JsonProperty("top_n") Integer topN,
            @JsonProperty("return_documents") Boolean returnDocuments
    ) {
    }

    public record Response(
            String id,
            List<RerankResult> results,
            String model,
            Usage usage
    ) {
    }

    public record RerankResult(
            Integer index,
            Double score,
            String document
    ) {
    }

    public record Usage(
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
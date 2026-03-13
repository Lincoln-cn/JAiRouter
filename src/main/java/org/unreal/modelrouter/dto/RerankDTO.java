package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Rerank DTO - 核心字段 + 扩展选项
 */
public class RerankDTO {

    /**
     * Rerank 请求
     * 只保留核心字段，扩展参数放入 Options 中
     */
    public record Request(
            String model,
            String query,
            List<String> documents,
            @JsonProperty("top_n") Integer topN,
            @JsonProperty("return_documents") Boolean returnDocuments,
            // 扩展选项
            Options options
    ) {
        /**
         * 获取扩展参数的便捷方法
         */
        public String requestId() {
            return options != null ? options.requestId : null;
        }

        public Integer priority() {
            return options != null ? options.priority : null;
        }

        public Boolean truncatePromptTokens() {
            return options != null ? options.truncatePromptTokens : null;
        }
    }

    /**
     * 扩展选项类
     */
    @Data
    @Builder
    public static class Options {
        @JsonProperty("request_id")
        private String requestId;
        private Integer priority;
        @JsonProperty("truncate_prompt_tokens")
        private Boolean truncatePromptTokens;
    }

    public record Response(
            String id,
            List<RerankResult> results,
            String model,
            Usage usage
    ) {}

    public record RerankResult(
            Integer index,
            Double score,
            String document
    ) {}

    public record Usage(
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}
}

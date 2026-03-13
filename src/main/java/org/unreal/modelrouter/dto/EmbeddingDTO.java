package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Embedding DTO - 核心字段 + 扩展选项
 */
public class EmbeddingDTO {

    /**
     * Embedding 请求
     * 只保留核心字段，扩展参数放入 Options 中
     */
    public record Request(
            String model,
            Object input, // Can be String or String[]
            @JsonProperty("encoding_format") String encodingFormat,
            Integer dimensions,
            String user,
            // 扩展选项
            Options options
    ) {
        /**
         * 获取扩展参数的便捷方法
         */
        public Boolean truncatePromptTokens() {
            return options != null ? options.truncatePromptTokens : null;
        }

        public String requestId() {
            return options != null ? options.requestId : null;
        }

        public Integer priority() {
            return options != null ? options.priority : null;
        }

        public String cacheSalt() {
            return options != null ? options.cacheSalt : null;
        }

        public Boolean addSpecialTokens() {
            return options != null ? options.addSpecialTokens : null;
        }

        public String embedDtype() {
            return options != null ? options.embedDtype : null;
        }

        public String endianness() {
            return options != null ? options.endianness : null;
        }

        public Boolean useActivation() {
            return options != null ? options.useActivation : null;
        }

        public String chatTemplate() {
            return options != null ? options.chatTemplate : null;
        }

        public Map<String, Object> chatTemplateKwargs() {
            return options != null ? options.chatTemplateKwargs : null;
        }

        public Map<String, Object> mediaIoKwargs() {
            return options != null ? options.mediaIoKwargs : null;
        }

        public Boolean addGenerationPrompt() {
            return options != null ? options.addGenerationPrompt : null;
        }

        public Boolean continueFinalMessage() {
            return options != null ? options.continueFinalMessage : null;
        }
    }

    /**
     * 扩展选项类
     */
    @Data
    @Builder
    public static class Options {
        @JsonProperty("truncate_prompt_tokens")
        private Boolean truncatePromptTokens;
        @JsonProperty("request_id")
        private String requestId;
        private Integer priority;
        @JsonProperty("cache_salt")
        private String cacheSalt;
        @JsonProperty("add_special_tokens")
        private Boolean addSpecialTokens;
        @JsonProperty("embed_dtype")
        private String embedDtype;
        private String endianness;
        @JsonProperty("use_activation")
        private Boolean useActivation;
        @JsonProperty("chat_template")
        private String chatTemplate;
        @JsonProperty("chat_template_kwargs")
        private Map<String, Object> chatTemplateKwargs;
        @JsonProperty("media_io_kwargs")
        private Map<String, Object> mediaIoKwargs;
        @JsonProperty("add_generation_prompt")
        private Boolean addGenerationPrompt;
        @JsonProperty("continue_final_message")
        private Boolean continueFinalMessage;
    }

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

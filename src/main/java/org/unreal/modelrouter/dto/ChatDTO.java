package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Chat DTO - 核心字段 + 扩展选项
 */
public class ChatDTO {

    /**
     * Chat 请求
     * 只保留核心字段，扩展参数放入 Options 中
     */
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
            String user,
            // 扩展选项（包含所有适配器特定参数）
            Options options
    ) {
        /**
         * 获取扩展参数的便捷方法
         */
        public Integer n() {
            return options != null ? options.n : null;
        }

        public Boolean logprobs() {
            return options != null ? options.logprobs : null;
        }

        public Integer topLogprobs() {
            return options != null ? options.topLogprobs : null;
        }

        public Boolean useBeamSearch() {
            return options != null ? options.useBeamSearch : null;
        }

        public Double minP() {
            return options != null ? options.minP : null;
        }

        public Double repetitionPenalty() {
            return options != null ? options.repetitionPenalty : null;
        }

        public Double lengthPenalty() {
            return options != null ? options.lengthPenalty : null;
        }

        public Boolean includeStopStrInOutput() {
            return options != null ? options.includeStopStrInOutput : null;
        }

        public Boolean ignoreEos() {
            return options != null ? options.ignoreEos : null;
        }

        public Integer minTokens() {
            return options != null ? options.minTokens : null;
        }

        public Boolean skipSpecialTokens() {
            return options != null ? options.skipSpecialTokens : null;
        }

        public Boolean spacesBetweenSpecialTokens() {
            return options != null ? options.spacesBetweenSpecialTokens : null;
        }

        public Boolean truncatePromptTokens() {
            return options != null ? options.truncatePromptTokens : null;
        }

        public Boolean echo() {
            return options != null ? options.echo : null;
        }

        public Boolean addGenerationPrompt() {
            return options != null ? options.addGenerationPrompt : null;
        }

        public Boolean continueFinalMessage() {
            return options != null ? options.continueFinalMessage : null;
        }

        public Boolean addSpecialTokens() {
            return options != null ? options.addSpecialTokens : null;
        }

        public List<String> documents() {
            return options != null ? options.documents : null;
        }

        public String chatTemplate() {
            return options != null ? options.chatTemplate : null;
        }

        public Map<String, Object> chatTemplateKwargs() {
            return options != null ? options.chatTemplateKwargs : null;
        }

        public Object structuredOutputs() {
            return options != null ? options.structuredOutputs : null;
        }

        public Integer priority() {
            return options != null ? options.priority : null;
        }

        public String requestId() {
            return options != null ? options.requestId : null;
        }

        public Boolean returnTokensAsTokenIds() {
            return options != null ? options.returnTokensAsTokenIds : null;
        }

        public Boolean returnTokenIds() {
            return options != null ? options.returnTokenIds : null;
        }

        public String cacheSalt() {
            return options != null ? options.cacheSalt : null;
        }

        public Object repetitionDetection() {
            return options != null ? options.repetitionDetection : null;
        }

        // Ollama 特定参数
        public Double repeatPenalty() {
            return options != null ? options.repeatPenalty : null;
        }

        public Integer seed() {
            return options != null ? options.seed : null;
        }

        public Integer numKeep() {
            return options != null ? options.numKeep : null;
        }

        public Double tfsZ() {
            return options != null ? options.tfsZ : null;
        }

        public Double typicalP() {
            return options != null ? options.typicalP : null;
        }

        public Integer repeatLastN() {
            return options != null ? options.repeatLastN : null;
        }

        public Boolean penalizeNewline() {
            return options != null ? options.penalizeNewline : null;
        }
    }

    /**
     * 扩展选项类 - 包含所有适配器特定的参数
     * 使用 @Builder 方便构建，所有字段可选
     */
    @Data
    @Builder
    public static class Options {
        // 通用扩展参数
        private Integer n;
        private Boolean logprobs;
        @JsonProperty("top_logprobs")
        private Integer topLogprobs;
        @JsonProperty("use_beam_search")
        private Boolean useBeamSearch;
        @JsonProperty("min_p")
        private Double minP;
        @JsonProperty("repetition_penalty")
        private Double repetitionPenalty;
        @JsonProperty("length_penalty")
        private Double lengthPenalty;
        @JsonProperty("include_stop_str_in_output")
        private Boolean includeStopStrInOutput;
        @JsonProperty("ignore_eos")
        private Boolean ignoreEos;
        @JsonProperty("min_tokens")
        private Integer minTokens;
        @JsonProperty("skip_special_tokens")
        private Boolean skipSpecialTokens;
        @JsonProperty("spaces_between_special_tokens")
        private Boolean spacesBetweenSpecialTokens;
        @JsonProperty("truncate_prompt_tokens")
        private Boolean truncatePromptTokens;
        private Boolean echo;
        @JsonProperty("add_generation_prompt")
        private Boolean addGenerationPrompt;
        @JsonProperty("continue_final_message")
        private Boolean continueFinalMessage;
        @JsonProperty("add_special_tokens")
        private Boolean addSpecialTokens;
        private List<String> documents;
        @JsonProperty("chat_template")
        private String chatTemplate;
        @JsonProperty("chat_template_kwargs")
        private Map<String, Object> chatTemplateKwargs;
        @JsonProperty("structured_outputs")
        private Object structuredOutputs;
        private Integer priority;
        @JsonProperty("request_id")
        private String requestId;
        @JsonProperty("return_tokens_as_token_ids")
        private Boolean returnTokensAsTokenIds;
        @JsonProperty("return_token_ids")
        private Boolean returnTokenIds;
        @JsonProperty("cache_salt")
        private String cacheSalt;
        @JsonProperty("repetition_detection")
        private Object repetitionDetection;

        // Ollama 特定参数
        @JsonProperty("repeat_penalty")
        private Double repeatPenalty;
        private Integer seed;
        @JsonProperty("num_keep")
        private Integer numKeep;
        @JsonProperty("tfs_z")
        private Double tfsZ;
        @JsonProperty("typical_p")
        private Double typicalP;
        @JsonProperty("repeat_last_n")
        private Integer repeatLastN;
        @JsonProperty("penalize_newline")
        private Boolean penalizeNewline;
    }

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

package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class EmbeddingDTO {

    /**
     * Embedding 请求 DTO
     * 示例:
     * {
     *     "input": "The food was delicious and the waiter...",
     *     "model": "text-embedding-ada-002",
     *     "encoding_format": "float",
     *     "dimensions": 1536,
     *     "user": "user-123"
     * }
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            @JsonProperty("model") String model,
            @JsonProperty("input") List<String> input,
            @JsonProperty("encoding_format") String encoding_format,
            @JsonProperty("dimensions") Integer dimensions,
            @JsonProperty("user") String user,
            @JsonProperty("normalize") Boolean normalize,
            @JsonProperty("truncate") String truncate,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 简化构造方法
        public Request(String model, List<String> input) {
            this(model, input, "float", null, null, null, null, null);
        }

        public Request(String model, List<String> input, String encoding_format) {
            this(model, input, encoding_format, null, null, null, null, null);
        }

        public Request(String model, String singleInput) {
            this(model, List.of(singleInput), "float", null, null, null, null, null);
        }

        // 校验方法
        public boolean isValid() {
            return model != null && !model.trim().isEmpty()
                    && input != null && !input.isEmpty()
                    && input.stream().allMatch(text -> text != null && !text.trim().isEmpty());
        }

        // 获取有效的编码格式
        public String getValidEncodingFormat() {
            if (encoding_format == null || encoding_format.trim().isEmpty()) {
                return "float"; // 默认格式
            }
            String format = encoding_format.toLowerCase();
            // 支持的格式：float, base64
            return List.of("float", "base64").contains(format) ? format : "float";
        }

        // 获取输入文本数量
        public int getInputCount() {
            return input != null ? input.size() : 0;
        }

        // 估算总token数
        public int getEstimatedTokens() {
            if (input == null) return 0;
            return input.stream()
                    .mapToInt(text -> text.length() / 4) // 粗略估算: 1 token ≈ 4 characters
                    .sum();
        }

        // 是否需要标准化
        public boolean shouldNormalize() {
            return normalize != null && normalize;
        }

        // 获取截断策略
        public String getTruncateStrategy() {
            return truncate != null ? truncate : "end"; // 默认从末尾截断
        }
    }

    /**
     * Embedding 响应 DTO
     * 示例:
     * {
     *   "object": "list",
     *   "data": [
     *     {
     *       "object": "embedding",
     *       "embedding": [0.0023064255, -0.009327292, ...],
     *       "index": 0
     *     }
     *   ],
     *   "model": "text-embedding-ada-002",
     *   "usage": {
     *     "prompt_tokens": 8,
     *     "total_tokens": 8
     *   }
     * }
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            @JsonProperty("object") String object,
            @JsonProperty("data") List<Embedding> data,
            @JsonProperty("model") String model,
            @JsonProperty("usage") Usage usage,
            @JsonProperty("created") Long created,
            @JsonProperty("id") String id,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 简化构造方法
        public Response(List<Embedding> data, String model, Usage usage) {
            this("list", data, model, usage, System.currentTimeMillis() / 1000, generateId(), null);
        }

        private static String generateId() {
            return "emb-" + System.currentTimeMillis() + "-" +
                    Integer.toHexString((int)(Math.random() * 0xFFFF));
        }

        // 获取嵌入向量的维度
        public Integer getDimensions() {
            if (data == null || data.isEmpty() || data.get(0).embedding() == null) {
                return null;
            }
            return data.get(0).embedding().size();
        }

        // 获取响应中的嵌入数量
        public int getEmbeddingCount() {
            return data != null ? data.size() : 0;
        }
    }

    /**
     * 单个嵌入向量 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Embedding(
            @JsonProperty("object") String object,
            @JsonProperty("embedding") List<Double> embedding,
            @JsonProperty("index") Integer index,
            @JsonProperty("input_text") String inputText,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 简化构造方法
        public Embedding(List<Double> embedding, int index) {
            this("embedding", embedding, index, null, null);
        }

        public Embedding(List<Double> embedding, int index, String inputText) {
            this("embedding", embedding, index, inputText, null);
        }

        // 获取向量维度
        public int getDimensions() {
            return embedding != null ? embedding.size() : 0;
        }

        // 计算向量的L2范数
        public double getL2Norm() {
            if (embedding == null || embedding.isEmpty()) {
                return 0.0;
            }
            return Math.sqrt(embedding.stream()
                    .mapToDouble(Double::doubleValue)
                    .map(x -> x * x)
                    .sum());
        }

        // 标准化向量
        public Embedding normalize() {
            if (embedding == null || embedding.isEmpty()) {
                return this;
            }

            double norm = getL2Norm();
            if (norm == 0.0) {
                return this;
            }

            List<Double> normalized = embedding.stream()
                    .map(x -> x / norm)
                    .toList();

            return new Embedding(object, normalized, index, inputText, metadata);
        }
    }

    /**
     * 使用统计 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer prompt_tokens,
            @JsonProperty("total_tokens") Integer total_tokens,
            @JsonProperty("completion_tokens") Integer completion_tokens
    ) {

        // 简化构造方法
        public Usage(int promptTokens) {
            this(promptTokens, promptTokens, 0);
        }

        public Usage(int promptTokens, int totalTokens) {
            this(promptTokens, totalTokens, totalTokens - promptTokens);
        }
    }
}
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

    /**
     * 批量嵌入请求 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchRequest(
            @JsonProperty("model") String model,
            @JsonProperty("inputs") List<String> inputs,
            @JsonProperty("encoding_format") String encoding_format,
            @JsonProperty("dimensions") Integer dimensions,
            @JsonProperty("batch_id") String batch_id,
            @JsonProperty("normalize") Boolean normalize,
            @JsonProperty("chunk_size") Integer chunk_size,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 简化构造方法
        public BatchRequest(String model, List<String> inputs) {
            this(model, inputs, "float", null, generateBatchId(), null, null, null);
        }

        public BatchRequest(String model, List<String> inputs, int chunkSize) {
            this(model, inputs, "float", null, generateBatchId(), null, chunkSize, null);
        }

        private static String generateBatchId() {
            return "batch-emb-" + System.currentTimeMillis() + "-" +
                    Integer.toHexString((int)(Math.random() * 0xFFFF));
        }

        public boolean isValid() {
            return model != null && !model.trim().isEmpty()
                    && inputs != null && !inputs.isEmpty()
                    && inputs.stream().allMatch(text -> text != null && !text.trim().isEmpty());
        }

        // 获取有效的块大小
        public int getValidChunkSize() {
            if (chunk_size == null || chunk_size <= 0) {
                return 100; // 默认批次大小
            }
            return Math.min(chunk_size, 1000); // 限制最大批次大小
        }
    }

    /**
     * 批量嵌入响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchResponse(
            @JsonProperty("batch_id") String batch_id,
            @JsonProperty("object") String object,
            @JsonProperty("data") List<Embedding> data,
            @JsonProperty("model") String model,
            @JsonProperty("usage") Usage usage,
            @JsonProperty("total_inputs") Integer total_inputs,
            @JsonProperty("processed_inputs") Integer processed_inputs,
            @JsonProperty("failed_inputs") Integer failed_inputs,
            @JsonProperty("created") Long created,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public BatchResponse(String batchId, List<Embedding> data, String model, Usage usage) {
            this(batchId, "list", data, model, usage,
                    data != null ? data.size() : 0,
                    data != null ? data.size() : 0,
                    0,
                    System.currentTimeMillis() / 1000,
                    null);
        }
    }

    /**
     * 嵌入模型信息 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelInfo(
            @JsonProperty("model") String model,
            @JsonProperty("description") String description,
            @JsonProperty("dimensions") Integer dimensions,
            @JsonProperty("max_input_tokens") Integer max_input_tokens,
            @JsonProperty("max_batch_size") Integer max_batch_size,
            @JsonProperty("supported_languages") List<String> supported_languages,
            @JsonProperty("version") String version,
            @JsonProperty("created") Long created
    ) {

        // 预定义模型信息
        public static final ModelInfo TEXT_EMBEDDING_ADA_002 = new ModelInfo(
                "text-embedding-ada-002",
                "OpenAI's text embedding model ada-002",
                1536, 8191, 2048,
                List.of("en", "zh", "es", "fr", "de", "ja", "ko", "pt", "it", "ru", "ar"),
                "2.0",
                System.currentTimeMillis() / 1000
        );

        public static final ModelInfo NOMIC_EMBED_TEXT_V1_5 = new ModelInfo(
                "nomic-embed-text-v1.5",
                "Nomic AI's text embedding model with strong performance",
                768, 8192, 1024,
                List.of("en", "zh", "es", "fr", "de", "ja", "ko"),
                "1.5",
                System.currentTimeMillis() / 1000
        );

        public static final ModelInfo BGE_LARGE_EN_V1_5 = new ModelInfo(
                "bge-large-en-v1.5",
                "BAAI BGE large English embedding model",
                1024, 512, 512,
                List.of("en"),
                "1.5",
                System.currentTimeMillis() / 1000
        );
    }

    /**
     * 嵌入错误响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            @JsonProperty("error") Error error
    ) {

        public record Error(
                @JsonProperty("message") String message,
                @JsonProperty("type") String type,
                @JsonProperty("code") String code,
                @JsonProperty("param") String param
        ) {

            public Error(String message, String type, String code) {
                this(message, type, code, null);
            }
        }
    }

    /**
     * 相似度计算结果 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SimilarityResult(
            @JsonProperty("similarity") Double similarity,
            @JsonProperty("distance") Double distance,
            @JsonProperty("metric") String metric,
            @JsonProperty("source_index") Integer source_index,
            @JsonProperty("target_index") Integer target_index
    ) {

        public SimilarityResult(double similarity, String metric, int sourceIndex, int targetIndex) {
            this(similarity, 1.0 - similarity, metric, sourceIndex, targetIndex);
        }

        // 余弦相似度
        public static SimilarityResult cosine(double similarity, int sourceIndex, int targetIndex) {
            return new SimilarityResult(similarity, "cosine", sourceIndex, targetIndex);
        }

        // 欧几里得距离
        public static SimilarityResult euclidean(double distance, int sourceIndex, int targetIndex) {
            return new SimilarityResult(1.0 / (1.0 + distance), distance, "euclidean", sourceIndex, targetIndex);
        }
    }
}
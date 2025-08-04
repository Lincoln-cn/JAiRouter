package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class RerankDTO {

    /**
     * Rerank 请求 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            @JsonProperty("model") String model,
            @JsonProperty("query") String query,
            @JsonProperty("documents") List<String> documents,
            @JsonProperty("top_k") Integer topK,
            @JsonProperty("return_documents") Boolean returnDocuments,
            @JsonProperty("max_chunks_per_doc") Integer maxChunksPerDoc,
            @JsonProperty("overlap_tokens") Integer overlapTokens,
            @JsonProperty("normalize_scores") Boolean normalizeScores,
            @JsonProperty("language") String language,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 提供默认值的构造方法
        public Request(String model, String query, List<String> documents) {
            this(model, query, documents, null, true, null, null, true, null, null);
        }

        public Request(String model, String query, List<String> documents, Integer topK) {
            this(model, query, documents, topK, true, null, null, true, null, null);
        }

        // 校验方法
        public boolean isValid() {
            return model != null && !model.trim().isEmpty()
                    && query != null && !query.trim().isEmpty()
                    && documents != null && !documents.isEmpty()
                    && documents.stream().allMatch(doc -> doc != null && !doc.trim().isEmpty());
        }

        // 获取有效的 topK
        public int getValidTopK() {
            if (topK == null || topK <= 0) {
                return Math.min(documents != null ? documents.size() : 10, 100); // 默认返回所有，但不超过100
            }
            return Math.min(topK, documents != null ? documents.size() : topK);
        }

        // 是否返回文档内容
        public boolean shouldReturnDocuments() {
            return returnDocuments == null || returnDocuments; // 默认返回
        }

        // 是否标准化分数
        public boolean shouldNormalizeScores() {
            return normalizeScores == null || normalizeScores; // 默认标准化
        }

        // 获取每个文档的最大块数
        public Integer getValidMaxChunksPerDoc() {
            if (maxChunksPerDoc == null || maxChunksPerDoc <= 0) {
                return null; // 不分块
            }
            return Math.min(maxChunksPerDoc, 100); // 限制最大块数
        }

        // 获取重叠令牌数
        public Integer getValidOverlapTokens() {
            if (overlapTokens == null || overlapTokens < 0) {
                return 0; // 默认无重叠
            }
            return Math.min(overlapTokens, 512); // 限制最大重叠
        }
    }

    /**
     * Rerank 响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            @JsonProperty("id") String id,
            @JsonProperty("results") List<RerankResult> results,
            @JsonProperty("model") String model,
            @JsonProperty("usage") Usage usage,
            @JsonProperty("created") Long created,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public Response(List<RerankResult> results, String model, Usage usage) {
            this(generateId(), results, model, usage, System.currentTimeMillis() / 1000, null);
        }

        private static String generateId() {
            return "rerank-" + System.currentTimeMillis() + "-" +
                    Integer.toHexString((int)(Math.random() * 0xFFFF));
        }
    }

    /**
     * Rerank 结果 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RerankResult(
            @JsonProperty("index") Integer index,
            @JsonProperty("relevance_score") Double relevanceScore,
            @JsonProperty("document") Document document,
            @JsonProperty("chunks") List<Chunk> chunks,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 简单构造方法
        public RerankResult(int index, double relevanceScore, String text) {
            this(index, relevanceScore, new Document(text, null), null, null);
        }

        public RerankResult(int index, double relevanceScore, Document document) {
            this(index, relevanceScore, document, null, null);
        }

        // 获取标准化分数 (0-1)
        public double getNormalizedScore() {
            if (relevanceScore == null) {
                return 0.0;
            }
            // 假设原始分数在 -10 到 10 之间，转换为 0-1
            return Math.max(0.0, Math.min(1.0, (relevanceScore + 10.0) / 20.0));
        }
    }

    /**
     * 文档 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Document(
            @JsonProperty("text") String text,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public Document(String text) {
            this(text, null);
        }

        // 获取文档长度
        public int getLength() {
            return text != null ? text.length() : 0;
        }

        // 获取估计的token数量 (粗略估算)
        public int getEstimatedTokens() {
            if (text == null) return 0;
            // 粗略估算: 1 token ≈ 4 characters
            return text.length() / 4;
        }
    }

    /**
     * 文档块 DTO (用于长文档分块)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Chunk(
            @JsonProperty("text") String text,
            @JsonProperty("start_index") Integer startIndex,
            @JsonProperty("end_index") Integer endIndex,
            @JsonProperty("relevance_score") Double relevanceScore,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public Chunk(String text, int startIndex, int endIndex, double relevanceScore) {
            this(text, startIndex, endIndex, relevanceScore, null);
        }
    }

    /**
     * 使用统计 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
            @JsonProperty("total_tokens") Integer totalTokens,
            @JsonProperty("query_tokens") Integer queryTokens,
            @JsonProperty("document_tokens") Integer documentTokens,
            @JsonProperty("rerank_units") Integer rerankUnits
    ) {

        public Usage(int queryTokens, int documentTokens) {
            this(queryTokens + documentTokens, queryTokens, documentTokens, 1);
        }
    }

    /**
     * Rerank 批量请求 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchRequest(
            @JsonProperty("model") String model,
            @JsonProperty("requests") List<SingleRequest> requests,
            @JsonProperty("batch_id") String batchId,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public record SingleRequest(
                @JsonProperty("query") String query,
                @JsonProperty("documents") List<String> documents,
                @JsonProperty("top_k") Integer topK,
                @JsonProperty("return_documents") Boolean returnDocuments,
                @JsonProperty("request_id") String requestId
        ) {

            public SingleRequest(String query, List<String> documents) {
                this(query, documents, null, true, generateRequestId());
            }

            private static String generateRequestId() {
                return "req-" + System.currentTimeMillis() + "-" +
                        Integer.toHexString((int)(Math.random() * 0xFFFF));
            }
        }

        public BatchRequest(String model, List<SingleRequest> requests) {
            this(model, requests, generateBatchId(), null);
        }

        private static String generateBatchId() {
            return "batch-rerank-" + System.currentTimeMillis() + "-" +
                    Integer.toHexString((int)(Math.random() * 0xFFFF));
        }

        public boolean isValid() {
            return model != null && !model.trim().isEmpty()
                    && requests != null && !requests.isEmpty()
                    && requests.stream().allMatch(req ->
                    req.query() != null && !req.query().trim().isEmpty() &&
                            req.documents() != null && !req.documents().isEmpty());
        }
    }

    /**
     * Rerank 批量响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchResponse(
            @JsonProperty("batch_id") String batchId,
            @JsonProperty("results") List<BatchResult> results,
            @JsonProperty("model") String model,
            @JsonProperty("total_requests") Integer totalRequests,
            @JsonProperty("successful_requests") Integer successfulRequests,
            @JsonProperty("failed_requests") Integer failedRequests,
            @JsonProperty("total_usage") Usage totalUsage,
            @JsonProperty("created") Long created,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public record BatchResult(
                @JsonProperty("request_id") String requestId,
                @JsonProperty("success") Boolean success,
                @JsonProperty("results") List<RerankResult> results,
                @JsonProperty("usage") Usage usage,
                @JsonProperty("error") ErrorResponse.Error error
        ) {

            public BatchResult(String requestId, List<RerankResult> results, Usage usage) {
                this(requestId, true, results, usage, null);
            }

            public BatchResult(String requestId, ErrorResponse.Error error) {
                this(requestId, false, null, null, error);
            }
        }
    }

    /**
     * Rerank 错误响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            @JsonProperty("error") Error error
    ) {

        public record Error(
                @JsonProperty("message") String message,
                @JsonProperty("type") String type,
                @JsonProperty("code") String code,
                @JsonProperty("param") String param,
                @JsonProperty("details") Map<String, Object> details
        ) {

            public Error(String message, String type, String code) {
                this(message, type, code, null, null);
            }
        }
    }

    /**
     * Rerank 模型信息 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelInfo(
            @JsonProperty("model") String model,
            @JsonProperty("description") String description,
            @JsonProperty("max_query_length") Integer maxQueryLength,
            @JsonProperty("max_document_length") Integer maxDocumentLength,
            @JsonProperty("max_documents") Integer maxDocuments,
            @JsonProperty("supported_languages") List<String> supportedLanguages,
            @JsonProperty("version") String version,
            @JsonProperty("created") Long created
    ) {

        // BGE Reranker 模型信息
        public static final ModelInfo BGE_RERANKER_BASE = new ModelInfo(
                "bge-reranker-base",
                "BAAI BGE Reranker Base model for semantic text reranking",
                512, 8192, 1000,
                List.of("en", "zh", "es", "fr", "de", "ja", "ko"),
                "1.0",
                System.currentTimeMillis() / 1000
        );

        public static final ModelInfo BGE_RERANKER_LARGE = new ModelInfo(
                "bge-reranker-large",
                "BAAI BGE Reranker Large model with higher accuracy",
                512, 8192, 1000,
                List.of("en", "zh", "es", "fr", "de", "ja", "ko"),
                "1.0",
                System.currentTimeMillis() / 1000
        );
    }
}
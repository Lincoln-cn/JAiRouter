package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * vLLM 响应转换器
 * 负责将vLLM响应格式转换为OpenAI标准格式
 *
 * @author JAiRouter Team
 * @since 2.9.1
 */
public final class VllmResponseTransformer {

    private static final Logger log = LoggerFactory.getLogger(VllmResponseTransformer.class);

    private final ObjectMapper objectMapper;

    public VllmResponseTransformer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 转换响应格式
     */
    public Object transformResponse(final Object response) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                return transformResponseJson(jsonResponse);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 转换响应JSON格式以符合OpenAI标准
     */
    public String transformResponseJson(final JsonNode vllmResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            // 根据vLLM响应类型进行转换
            if (vllmResponse.has("choices")) {
                // 聊天响应转换
                standardResponse.set("id", vllmResponse.path("id"));
                standardResponse.put("object", "chat.completion");
                standardResponse.put("created", System.currentTimeMillis() / 1000);

                // 复制模型信息
                if (vllmResponse.has("model")) {
                    standardResponse.put("model", vllmResponse.get("model").asText());
                }

                // 复制选择项
                standardResponse.set("choices", vllmResponse.get("choices"));

                // 添加使用情况统计（如果存在）
                if (vllmResponse.has("usage")) {
                    standardResponse.set("usage", vllmResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("completion_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (vllmResponse.has("data") && vllmResponse.has("model")) {
                // 嵌入响应转换
                standardResponse.put("object", "list");
                standardResponse.set("data", vllmResponse.get("data"));
                standardResponse.put("model", vllmResponse.get("model").asText());

                // 添加使用情况统计（如果存在）
                if (vllmResponse.has("usage")) {
                    standardResponse.set("usage", vllmResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (vllmResponse.has("results")) {
                // 重排序响应转换
                standardResponse.set("id",
                        objectMapper.getNodeFactory().textNode("cmpl-" + System.currentTimeMillis()));
                standardResponse.set("results", vllmResponse.get("results"));
                if (vllmResponse.has("model")) {
                    standardResponse.put("model", vllmResponse.get("model").asText());
                }

                // 添加使用情况统计（如果存在）
                if (vllmResponse.has("usage")) {
                    standardResponse.set("usage", vllmResponse.get("usage"));
                } else {
                    // 创建基本的usage信息
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else {
                // 如果都不是标准格式，返回原始响应
                return vllmResponse.toString();
            }

            return standardResponse.toString();
        } catch (Exception e) {
            return vllmResponse.toString();
        }
    }

    /**
     * 转换流式响应块
     */
    public String transformStreamChunk(final String chunk) {
        try {
            // 检查是否是标准的SSE格式
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6);
                // 对于 [DONE] 标记，直接返回纯文本（Spring WebFlux 会自动处理 SSE 格式）
                if ("[DONE]".equals(jsonPart.trim())) {
                    return "[DONE]";
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                ObjectNode standardChunk = objectMapper.createObjectNode();

                // 设置基本字段
                standardChunk.put("id", "chatcmpl-" + System.currentTimeMillis());
                standardChunk.put("object", "chat.completion.chunk");
                standardChunk.put("created", System.currentTimeMillis() / 1000);

                // 复制模型信息
                if (chunkJson.has("model")) {
                    standardChunk.put("model", chunkJson.get("model").asText());
                }

                // 处理选择项
                if (chunkJson.has("choices")) {
                    standardChunk.set("choices", chunkJson.get("choices"));
                } else {
                    // 创建标准的选择项格式
                    ObjectNode choice = objectMapper.createObjectNode();
                    choice.put("index", 0);

                    // 处理delta
                    ObjectNode delta = objectMapper.createObjectNode();
                    if (chunkJson.has("delta")) {
                        delta = (ObjectNode) chunkJson.get("delta");
                    } else if (chunkJson.has("content")) {
                        delta.put("content", chunkJson.get("content").asText());
                    } else if (chunkJson.has("text")) {
                        delta.put("content", chunkJson.get("text").asText());
                    }

                    choice.set("delta", delta);

                    // 处理finish_reason
                    if (chunkJson.has("finish_reason")) {
                        choice.put("finish_reason", chunkJson.get("finish_reason").asText());
                    }

                    standardChunk.set("choices", objectMapper.createArrayNode().add(choice));
                }

                // 添加空的usage字段（如果原响应中有）
                if (chunkJson.has("usage")) {
                    standardChunk.set("usage", chunkJson.get("usage"));
                }

                // 返回纯 JSON 字符串，Spring WebFlux 会自动添加 SSE 格式的 data: 前缀
                return standardChunk.toString();
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
        }
    }
}

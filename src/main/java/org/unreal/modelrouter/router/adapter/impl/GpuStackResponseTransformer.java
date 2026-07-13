package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;

/**
 * GPUStack 响应转换器
 * 负责将GPUStack响应格式转换为OpenAI标准格式
 */
public class GpuStackResponseTransformer {

    private static final Logger log = LoggerFactory.getLogger(GpuStackResponseTransformer.class);

    private final ObjectMapper objectMapper;

    public GpuStackResponseTransformer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 转换响应格式
     */
    public Object transformResponse(final Object response) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                checkGpuStackError(jsonResponse);
                return transformResponseJson(jsonResponse);
            } catch (DownstreamServiceException e) {
                throw e;
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 检测 GPUStack 特有的错误响应格式
     */
    private void checkGpuStackError(final JsonNode jsonResponse) {
        if (jsonResponse.has("status_code") && jsonResponse.has("detail")) {
            int statusCode = jsonResponse.get("status_code").asInt();
            String detail = jsonResponse.get("detail").asText();
            String errorMessage = buildGpuStackErrorMessage(statusCode, detail);
            HttpStatus httpStatus = mapGpuStackStatusCode(statusCode);
            throw new DownstreamServiceException(errorMessage, httpStatus);
        }
    }

    private String buildGpuStackErrorMessage(final int statusCode, final String detail) {
        StringBuilder message = new StringBuilder();

        if (detail.contains("not supported") || detail.contains("Voice")) {
            message.append("模型不支持该配置: ").append(detail);
            message.append("。建议: 请检查语音类型(voice)配置是否正确，或尝试其他支持的语音。");
        } else if (detail.contains("authentication") || detail.contains("unauthorized") || statusCode == 401) {
            message.append("下游服务认证失败: ").append(detail);
            message.append("。建议: 请检查实例的认证配置，确保已正确配置 Authorization 或 X-API-Key 认证头。");
        } else if (detail.contains("model") && detail.contains("not found")) {
            message.append("模型不存在: ").append(detail);
            message.append("。建议: 请检查模型名称是否正确，或确认模型是否已部署。");
        } else if (detail.contains("timeout") || statusCode == 504) {
            message.append("下游服务超时: ").append(detail);
            message.append("。建议: 请稍后重试或检查下游服务状态。");
        } else if (statusCode >= 500) {
            message.append("下游服务内部错误: ").append(detail);
            message.append("。建议: 请检查下游服务日志或稍后重试。");
        } else if (statusCode >= 400) {
            message.append("请求参数错误: ").append(detail);
            message.append("。建议: 请检查请求参数是否正确。");
        } else {
            message.append("下游服务返回错误: ").append(detail);
        }

        return message.toString();
    }

    private HttpStatus mapGpuStackStatusCode(final int statusCode) {
        return switch (statusCode) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            case 502 -> HttpStatus.BAD_GATEWAY;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            case 504 -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 转换响应格式以符合OpenAI标准
     */
    private String transformResponseJson(final JsonNode gpuStackResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            if (gpuStackResponse.has("choices")) {
                standardResponse.set("id", gpuStackResponse.path("id"));
                standardResponse.put("object", "chat.completion");
                standardResponse.put("created", System.currentTimeMillis() / 1000);

                if (gpuStackResponse.has("model")) {
                    standardResponse.put("model", gpuStackResponse.get("model").asText());
                }

                standardResponse.set("choices", gpuStackResponse.get("choices"));

                if (gpuStackResponse.has("usage")) {
                    standardResponse.set("usage", gpuStackResponse.get("usage"));
                } else {
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("completion_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (gpuStackResponse.has("data") && gpuStackResponse.has("model")) {
                standardResponse.put("object", "list");
                standardResponse.set("data", gpuStackResponse.get("data"));
                standardResponse.put("model", gpuStackResponse.get("model").asText());

                if (gpuStackResponse.has("usage")) {
                    standardResponse.set("usage", gpuStackResponse.get("usage"));
                } else {
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else if (gpuStackResponse.has("results")) {
                standardResponse.set("id", objectMapper.getNodeFactory().textNode("cmpl-" + System.currentTimeMillis()));
                standardResponse.set("results", gpuStackResponse.get("results"));
                if (gpuStackResponse.has("model")) {
                    standardResponse.put("model", gpuStackResponse.get("model").asText());
                }

                if (gpuStackResponse.has("usage")) {
                    standardResponse.set("usage", gpuStackResponse.get("usage"));
                } else {
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", 0);
                    usage.put("total_tokens", 0);
                    standardResponse.set("usage", usage);
                }
            } else {
                return gpuStackResponse.toString();
            }

            return standardResponse.toString();
        } catch (Exception e) {
            return gpuStackResponse.toString();
        }
    }

    /**
     * 转换流式响应块
     */
    public String transformStreamChunk(final String chunk) {
        try {
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6);
                if ("[DONE]".equals(jsonPart.trim())) {
                    return "[DONE]";
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                ObjectNode standardChunk = objectMapper.createObjectNode();

                standardChunk.put("id", "chatcmpl-" + System.currentTimeMillis());
                standardChunk.put("object", "chat.completion.chunk");
                standardChunk.put("created", System.currentTimeMillis() / 1000);

                if (chunkJson.has("model")) {
                    standardChunk.put("model", chunkJson.get("model").asText());
                }

                if (chunkJson.has("choices")) {
                    standardChunk.set("choices", chunkJson.get("choices"));
                } else {
                    ObjectNode choice = objectMapper.createObjectNode();
                    choice.put("index", 0);

                    ObjectNode delta = objectMapper.createObjectNode();
                    if (chunkJson.has("delta")) {
                        delta = (ObjectNode) chunkJson.get("delta");
                    } else if (chunkJson.has("content")) {
                        delta.put("content", chunkJson.get("content").asText());
                    } else if (chunkJson.has("text")) {
                        delta.put("content", chunkJson.get("text").asText());
                    }

                    choice.set("delta", delta);

                    if (chunkJson.has("finish_reason")) {
                        choice.put("finish_reason", chunkJson.get("finish_reason").asText());
                    }

                    standardChunk.set("choices", objectMapper.createArrayNode().add(choice));
                }

                if (chunkJson.has("usage")) {
                    standardChunk.set("usage", chunkJson.get("usage"));
                }

                return standardChunk.toString();
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
        }
    }
}

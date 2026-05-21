package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * OpenAI响应转换器接口
 * 将后端响应转换为标准格式
 *
 * @since v2.7.24
 */
public interface OpenAiResponseTransformer {

    /**
     * 转换响应
     * @param response 原始响应（字符串或JsonNode）
     * @return 转换后的响应字符串
     */
    String transformResponse(Object response);

    /**
     * 转换流式响应块
     * @param chunk 原始块数据
     * @return 转换后的块数据
     */
    String transformStreamChunk(String chunk);

    /**
     * 转换标准JSON响应
     * @param jsonResponse JSON节点
     * @return 转换后的响应字符串
     */
    String transformStandardResponse(JsonNode jsonResponse);
}

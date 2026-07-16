package org.unreal.modelrouter.router.adapter.transformer;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

/**
 * OpenAI请求转换器接口
 * 负责将各种请求类型转换为OpenAI API格式
 *
 * @since v2.7.17
 */
public interface OpenAiRequestTransformer {

    /**
     * 转换Chat请求
     *
     * @param request Chat请求
     * @param modelNameAdapter 模型名称适配函数
     * @return OpenAI格式的请求对象
     */
    Object transformChatRequest(ChatDTO.Request request, ModelNameAdapter modelNameAdapter);

    /**
     * 转换Embedding请求
     *
     * @param request Embedding请求
     * @param modelNameAdapter 模型名称适配函数
     * @return OpenAI格式的请求对象
     */
    Object transformEmbeddingRequest(EmbeddingDTO.Request request, ModelNameAdapter modelNameAdapter);

    /**
     * 转换Rerank请求
     *
     * @param request Rerank请求
     * @param modelNameAdapter 模型名称适配函数
     * @return OpenAI格式的请求对象
     */
    Object transformRerankRequest(RerankDTO.Request request, ModelNameAdapter modelNameAdapter);

    /**
     * 转换TTS请求
     *
     * @param request TTS请求
     * @param modelNameAdapter 模型名称适配函数
     * @return OpenAI格式的请求对象
     */
    Object transformTtsRequest(TtsDTO.Request request, ModelNameAdapter modelNameAdapter);

    /**
     * 转换图片编辑请求
     *
     * @param request 图片编辑请求
     * @param modelNameAdapter 模型名称适配函数
     * @return OpenAI格式的请求对象
     */
    Object transformImageEditRequest(ImageEditDTO.Request request, ModelNameAdapter modelNameAdapter);

    /**
     * 转换STT请求
     *
     * @param request STT请求
     * @param modelNameAdapter 模型名称适配函数
     * @return OpenAI格式的请求对象
     */
    Object transformSttRequest(SttDTO.Request request, ModelNameAdapter modelNameAdapter);

    /**
     * 模型名称适配器接口
     */
    @FunctionalInterface
    interface ModelNameAdapter {
        String adaptModelName(String modelName);
    }
}

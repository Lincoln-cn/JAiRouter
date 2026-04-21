package org.unreal.modelrouter.adapter.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 响应转换器
 * 
 * 负责转换和适配下游服务的响应
 * 支持模型名称适配和响应格式转换
 * 
 * @author AI Assistant
 * @since v2.2.4
 */
@Component
public class ResponseTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResponseTransformer.class);

    /**
     * 适配模型名称格式
     *
     * @param originalModelName 原始模型名称
     * @return 适配后的模型名称
     */
    public String adaptModelName(final String originalModelName) {
        // 默认不进行转换，子类可以重写此方法
        if (originalModelName == null) {
            return null;
        }
        
        logger.debug("适配模型名称：{} -> {}", originalModelName, originalModelName);
        return originalModelName;
    }

    /**
     * 转换响应体
     * 
     * 注意：此方法处理的是已解析为 Object 的下游响应体
     *
     * @param responseData 下游响应数据
     * @param adapterType 适配器类型
     * @return 转换后的响应数据
     */
    public Object transformResponse(final Object responseData, final String adapterType) {
        // 默认不进行转换，子类可以重写此方法
        logger.debug("转换响应：adapterType={}, responseDataType={}",
                adapterType,
                responseData != null ? responseData.getClass().getSimpleName() : "null");
        
        return responseData;
    }

    /**
     * 转换流式响应块
     *
     * @param chunk 响应块
     * @return 转换后的响应块
     */
    public String transformStreamChunk(final String chunk) {
        // 默认不进行转换，子类可以重写此方法
        return chunk;
    }

    /**
     * 适配授权头
     *
     * @param authorization 授权信息
     * @param adapterType 适配器类型
     * @return 适配后的授权头
     */
    public String adaptAuthorizationHeader(final String authorization, final String adapterType) {
        // 默认不进行转换，子类可以重写此方法
        return authorization;
    }

    /**
     * 转换请求体
     *
     * @param request 原始请求
     * @param adapterType 适配器类型
     * @return 转换后的请求
     */
    public Object transformRequest(final Object request, final String adapterType) {
        // 默认不进行转换，子类可以重写此方法
        return request;
    }

    /**
     * 检查响应是否需要转换
     *
     * @param responseData 响应数据
     * @param adapterType 适配器类型
     * @return true 如果需要转换
     */
    public boolean needsTransformation(final Object responseData, final String adapterType) {
        // 默认需要转换
        return true;
    }

    /**
     * 记录转换日志
     *
     * @param adapterType 适配器类型
     * @param inputType 输入类型
     * @param outputType 输出类型
     */
    protected void logTransformation(
            final String adapterType,
            final String inputType,
            final String outputType) {
        
        logger.debug("响应转换：adapter={}, input={}, output={}",
                adapterType, inputType, outputType);
    }
}

package org.unreal.modelrouter.adapter;

import org.unreal.modelrouter.model.ModelServiceRegistry;

/**
 * 适配器功能类，用于定义和管理适配器支持的各种AI服务类型
 * 通过构建器模式创建实例，可以灵活配置适配器支持的功能
 */
public final class AdapterCapabilities {
    private boolean supportChat = false;
    private boolean supportEmbedding = false;
    private boolean supportRerank = false;
    private boolean supportTts = false;
    private boolean supportStt = false;
    private boolean supportImageGenerate = false;
    private boolean supportImageEdit = false;
    private boolean supportStreaming = false;

    /**
     * 检查是否支持指定的服务类型
     * 
     * @param serviceType 服务类型
     * @return 如果支持该服务类型返回true，否则返回false
     */
    public boolean contains(final ModelServiceRegistry.ServiceType serviceType) {
        return switch (serviceType) {
            case chat -> supportChat;
            case embedding -> supportEmbedding;
            case rerank -> supportRerank;
            case tts -> supportTts;
            case stt -> supportStt;
            case imgGen -> supportImageGenerate;
            case imgEdit -> supportImageEdit;
        };
    }

    /**
     * 构建器模式，用于创建AdapterCapabilities实例
     */
    public static class Builder {
        private final AdapterCapabilities capabilities = new AdapterCapabilities();

        /**
         * 设置是否支持聊天功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder chat(final boolean support) {
            capabilities.supportChat = support;
            return this;
        }

        /**
         * 设置是否支持嵌入功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder embedding(final boolean support) {
            capabilities.supportEmbedding = support;
            return this;
        }

        /**
         * 设置是否支持重排序功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder rerank(final boolean support) {
            capabilities.supportRerank = support;
            return this;
        }

        /**
         * 设置是否支持文本转语音功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder tts(final boolean support) {
            capabilities.supportTts = support;
            return this;
        }

        /**
         * 设置是否支持语音转文本功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder stt(final boolean support) {
            capabilities.supportStt = support;
            return this;
        }

        /**
         * 设置是否支持图像生成功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder imageGenerate(final boolean support) {
            capabilities.supportImageGenerate = support;
            return this;
        }

        /**
         * 设置是否支持图像编辑功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder imageEdit(final boolean support) {
            capabilities.supportImageEdit = support;
            return this;
        }

        /**
         * 设置是否支持流式传输功能
         * 
         * @param support 是否支持
         * @return Builder实例
         */
        public Builder streaming(final boolean support) {
            capabilities.supportStreaming = support;
            return this;
        }

        /**
         * 构建AdapterCapabilities实例
         * 
         * @return AdapterCapabilities实例
         */
        public AdapterCapabilities build() {
            return capabilities;
        }
    }

    /**
     * 获取构建器实例
     * 
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取是否支持聊天功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportChat() {
        return supportChat;
    }

    /**
     * 获取是否支持嵌入功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportEmbedding() {
        return supportEmbedding;
    }

    /**
     * 获取是否支持重排序功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportRerank() {
        return supportRerank;
    }

    /**
     * 获取是否支持文本转语音功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportTts() {
        return supportTts;
    }

    /**
     * 获取是否支持语音转文本功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportStt() {
        return supportStt;
    }

    /**
     * 获取是否支持图像生成功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportImageGenerate() {
        return supportImageGenerate;
    }

    /**
     * 获取是否支持图像编辑功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportImageEdit() {
        return supportImageEdit;
    }

    /**
     * 获取是否支持流式传输功能
     * 
     * @return 如果支持返回true，否则返回false
     */
    public boolean isSupportStreaming() {
        return supportStreaming;
    }

    /**
     * 创建一个支持所有功能的AdapterCapabilities实例
     * 
     * @return 支持所有功能的AdapterCapabilities实例
     */
    public static AdapterCapabilities all() {
        return AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .rerank(true)
                .tts(true)
                .stt(true)
                .imageGenerate(true)
                .imageEdit(true)
                .streaming(true)
                .build();
    }
}
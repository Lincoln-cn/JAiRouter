package org.unreal.modelrouter.adapter;

import org.unreal.modelrouter.config.ModelServiceRegistry;

public class AdapterCapabilities {
    private boolean supportChat = false;
    private boolean supportEmbedding = false;
    private boolean supportRerank = false;
    private boolean supportTts = false;
    private boolean supportStt = false;
    private boolean supportImageGenerate = false;
    private boolean supportImageEdit = false;
    private boolean supportStreaming = false;

    public boolean contains(ModelServiceRegistry.ServiceType serviceType) {
        switch (serviceType) {
            case chat:
                return supportChat;
            case embedding:
                return supportEmbedding;
            case rerank:
                return supportRerank;
            case tts:
                return supportTts;
            case stt:
                return supportStt;
            case imgGen:
                return supportImageGenerate;
            case imgEdit:
                return supportImageEdit;
            default:
                return false;
        }
    }

    // 构建器模式
    public static class Builder {
        private AdapterCapabilities capabilities = new AdapterCapabilities();

        public Builder chat(boolean support) {
            capabilities.supportChat = support;
            return this;
        }

        public Builder embedding(boolean support) {
            capabilities.supportEmbedding = support;
            return this;
        }

        public Builder rerank(boolean support) {
            capabilities.supportRerank = support;
            return this;
        }

        public Builder tts(boolean support) {
            capabilities.supportTts = support;
            return this;
        }

        public Builder stt(boolean support) {
            capabilities.supportStt = support;
            return this;
        }

        public Builder imageGenerate(boolean support) {
            capabilities.supportImageGenerate = support;
            return this;
        }

        public Builder imageEdit(boolean support) {
            capabilities.supportImageEdit = support;
            return this;
        }

        public Builder streaming(boolean support) {
            capabilities.supportStreaming = support;
            return this;
        }

        public AdapterCapabilities build() {
            return capabilities;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public boolean isSupportChat() {
        return supportChat;
    }

    public boolean isSupportEmbedding() {
        return supportEmbedding;
    }

    public boolean isSupportRerank() {
        return supportRerank;
    }

    public boolean isSupportTts() {
        return supportTts;
    }

    public boolean isSupportStt() {
        return supportStt;
    }

    public boolean isSupportImageGenerate() {
        return supportImageGenerate;
    }

    public boolean isSupportImageEdit() {
        return supportImageEdit;
    }

    public boolean isSupportStreaming() {
        return supportStreaming;
    }

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
package org.unreal.modelrouter.router.adapter.template;

import java.util.List;
import java.util.Map;

/**
 * 适配器模板定义
 * 用于预配置常见 AI 供应商的适配器参数
 */
public class AdapterTemplate {

    private String id;
    private String name;
    private String description;
    private String icon;
    private String category;
    private String type;
    private String defaultBaseUrl;
    private CapabilitiesConfig capabilities;
    private AuthConfig auth;
    private Map<String, String> additionalHeaders;
    private List<String> supportedModels;
    private String setupGuide;
    private int sortOrder;

    public AdapterTemplate() {
    }

    public AdapterTemplate(final String id, final String name, final String description,
                           final String icon, final String category, final String type,
                           final String defaultBaseUrl, final CapabilitiesConfig capabilities,
                           final AuthConfig auth, final Map<String, String> additionalHeaders,
                           final List<String> supportedModels, final String setupGuide,
                           final int sortOrder) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.category = category;
        this.type = type;
        this.defaultBaseUrl = defaultBaseUrl;
        this.capabilities = capabilities;
        this.auth = auth;
        this.additionalHeaders = additionalHeaders;
        this.supportedModels = supportedModels;
        this.setupGuide = setupGuide;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(final String icon) {
        this.icon = icon;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(final String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public void setDefaultBaseUrl(final String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public CapabilitiesConfig getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(final CapabilitiesConfig capabilities) {
        this.capabilities = capabilities;
    }

    public AuthConfig getAuth() {
        return auth;
    }

    public void setAuth(final AuthConfig auth) {
        this.auth = auth;
    }

    public Map<String, String> getAdditionalHeaders() {
        return additionalHeaders;
    }

    public void setAdditionalHeaders(final Map<String, String> additionalHeaders) {
        this.additionalHeaders = additionalHeaders;
    }

    public List<String> getSupportedModels() {
        return supportedModels;
    }

    public void setSupportedModels(final List<String> supportedModels) {
        this.supportedModels = supportedModels;
    }

    public String getSetupGuide() {
        return setupGuide;
    }

    public void setSetupGuide(final String setupGuide) {
        this.setupGuide = setupGuide;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(final int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * 能力配置
     */
    public static class CapabilitiesConfig {
        private boolean chat;
        private boolean embedding;
        private boolean rerank;
        private boolean tts;
        private boolean stt;
        private boolean imgGen;
        private boolean imgEdit;
        private boolean streaming;

        public CapabilitiesConfig() {
        }

        public CapabilitiesConfig(final boolean chat, final boolean embedding, final boolean rerank,
                                  final boolean tts, final boolean stt, final boolean imgGen,
                                  final boolean imgEdit, final boolean streaming) {
            this.chat = chat;
            this.embedding = embedding;
            this.rerank = rerank;
            this.tts = tts;
            this.stt = stt;
            this.imgGen = imgGen;
            this.imgEdit = imgEdit;
            this.streaming = streaming;
        }

        public boolean isChat() {
            return chat;
        }

        public void setChat(final boolean chat) {
            this.chat = chat;
        }

        public boolean isEmbedding() {
            return embedding;
        }

        public void setEmbedding(final boolean embedding) {
            this.embedding = embedding;
        }

        public boolean isRerank() {
            return rerank;
        }

        public void setRerank(final boolean rerank) {
            this.rerank = rerank;
        }

        public boolean isTts() {
            return tts;
        }

        public void setTts(final boolean tts) {
            this.tts = tts;
        }

        public boolean isStt() {
            return stt;
        }

        public void setStt(final boolean stt) {
            this.stt = stt;
        }

        public boolean isImgGen() {
            return imgGen;
        }

        public void setImgGen(final boolean imgGen) {
            this.imgGen = imgGen;
        }

        public boolean isImgEdit() {
            return imgEdit;
        }

        public void setImgEdit(final boolean imgEdit) {
            this.imgEdit = imgEdit;
        }

        public boolean isStreaming() {
            return streaming;
        }

        public void setStreaming(final boolean streaming) {
            this.streaming = streaming;
        }
    }

    /**
     * 认证配置
     */
    public static class AuthConfig {
        private String headerName;
        private String headerPrefix;

        public AuthConfig() {
        }

        public AuthConfig(final String headerName, final String headerPrefix) {
            this.headerName = headerName;
            this.headerPrefix = headerPrefix;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(final String headerName) {
            this.headerName = headerName;
        }

        public String getHeaderPrefix() {
            return headerPrefix;
        }

        public void setHeaderPrefix(final String headerPrefix) {
            this.headerPrefix = headerPrefix;
        }
    }
}

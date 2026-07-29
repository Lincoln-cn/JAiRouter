package org.unreal.modelrouter.router.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter定义配置属性
 * 从 adapter.yml 的 adapter-definitions 节加载配置驱动的adapter定义
 */
@Configuration
@ConfigurationProperties(prefix = "")
public class AdapterDefinitionProperties {

    private Map<String, AdapterDefinition> adapterDefinitions = new HashMap<>();

    public Map<String, AdapterDefinition> getAdapterDefinitions() {
        return adapterDefinitions;
    }

    public void setAdapterDefinitions(final Map<String, AdapterDefinition> adapterDefinitions) {
        this.adapterDefinitions = adapterDefinitions;
    }

    public static class AdapterDefinition {
        private String type = "openai-compatible";
        private CapabilitiesConfig capabilities = new CapabilitiesConfig();
        private AuthConfig auth = new AuthConfig();
        private Map<String, String> additionalHeaders = new HashMap<>();

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
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
    }

    public static class CapabilitiesConfig {
        private boolean chat;
        private boolean embedding;
        private boolean rerank;
        private boolean tts;
        private boolean stt;
        private boolean imgGen;
        private boolean imgEdit;
        private boolean streaming;

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

    public static class AuthConfig {
        private String headerName = "Authorization";
        private String headerPrefix = "Bearer ";

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

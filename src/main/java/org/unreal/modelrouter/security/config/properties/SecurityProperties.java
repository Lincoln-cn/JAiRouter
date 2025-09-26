package org.unreal.modelrouter.security.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 安全配置属性类
 * 映射application.yml中的jairouter.security配置
 */
@Component
@Validated
@ConfigurationProperties(prefix = "jairouter.security")
public class SecurityProperties {

    /**
     * 是否启用安全功能
     */
    private boolean enabled = false;

    /**
     * API Key配置
     */
    @Valid
    @NotNull
    private ApiKeyConfig apiKey = new ApiKeyConfig();

    /**
     * JWT配置
     */
    @Valid
    @NotNull
    private JwtConfig jwt = new JwtConfig();

    /**
     * 数据脱敏配置
     */
    @Valid
    @NotNull
    private SanitizationConfig sanitization = new SanitizationConfig();

    /**
     * 审计配置
     */
    @Valid
    @NotNull
    private AuditConfig audit = new AuditConfig();

    /**
     * 缓存配置
     */
    @Valid
    @NotNull
    private CacheConfig cache = new CacheConfig();

    public SecurityProperties() {
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @Valid @NotNull ApiKeyConfig getApiKey() {
        return this.apiKey;
    }

    public void setApiKey(@Valid @NotNull ApiKeyConfig apiKey) {
        this.apiKey = apiKey;
    }

    public @Valid @NotNull JwtConfig getJwt() {
        return this.jwt;
    }

    public void setJwt(@Valid @NotNull JwtConfig jwt) {
        this.jwt = jwt;
    }

    public @Valid @NotNull SanitizationConfig getSanitization() {
        return this.sanitization;
    }

    public void setSanitization(@Valid @NotNull SanitizationConfig sanitization) {
        this.sanitization = sanitization;
    }

    public @Valid @NotNull AuditConfig getAudit() {
        return this.audit;
    }

    public void setAudit(@Valid @NotNull AuditConfig audit) {
        this.audit = audit;
    }

    public @Valid @NotNull CacheConfig getCache() {
        return this.cache;
    }

    public void setCache(@Valid @NotNull CacheConfig cache) {
        this.cache = cache;
    }

    public String toString() {
        return "SecurityProperties(enabled=" + this.isEnabled() + ", apiKey=" + this.getApiKey() + ", jwt=" + this.getJwt() + ", sanitization=" + this.getSanitization() + ", audit=" + this.getAudit() + ", cache=" + this.getCache() + ")";
    }

}
package org.unreal.modelrouter.security.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.config.properties.ApiKeyConfig;
import org.unreal.modelrouter.security.config.properties.JwtConfig;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.service.ApiKeyService;
import org.unreal.modelrouter.security.service.JwtAccountService;

import java.util.List;
import java.util.Map;

/**
 * 安全配置初始化器
 * 负责在应用启动时处理安全配置的初始化和加载
 * 参考 ConfigurationService 的模式，实现配置从静态文件到动态存储的转换
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityConfigurationInitializer {

    private final SecurityProperties securityProperties;
    private final SecurityConfigMergeService configMergeService;
    private final SecurityConfigurationManager configurationManager;
    private final JwtAccountService jwtAccountService;
    private final ApiKeyService apiKeyService;
    /**
     * 应用启动完成后初始化安全配置
     * 参考 ConfigurationService 的模式，处理配置的初始化和加载
     */
    @PostConstruct
    public void initializeSecurityConfigurations() {
        log.info("开始初始化安全配置...");
        
        try {
            // 初始化安全配置
            if (!configMergeService.hasPersistedSecurityConfig()) {
                // 如果没有持久化配置，将YAML配置保存为第一个版本
                initializeFromYamlConfig();
            } else {
                // 如果有持久化配置，加载最新版本并更新SecurityProperties
                loadLatestPersistedConfig();
            }
            
            // 初始化JWT账户配置
            initializeJwtAccountConfig();

            initializeApiKeyConfig();
            
            log.info("安全配置初始化完成");
            
        } catch (Exception e) {
            log.error("安全配置初始化失败", e);
            throw new RuntimeException("Failed to initialize security configurations", e);
        }
    }

    /**
     * 从YAML配置初始化持久化存储
     */
    private void initializeFromYamlConfig() {
        log.info("首次启动，将YAML安全配置保存为版本1");
        
        try {
            // 获取YAML默认配置
            Map<String, Object> defaultConfig = configMergeService.getDefaultSecurityConfig();
            
            // 保存为第一个版本
            configurationManager.saveSecurityAsNewVersion(defaultConfig);
            
            log.info("YAML安全配置已保存为版本1，包含 {} 个配置项", defaultConfig.size());
            
        } catch (Exception e) {
            log.error("从YAML配置初始化失败", e);
            throw new RuntimeException("Failed to initialize from YAML config", e);
        }
    }

    /**
     * 加载最新的持久化配置
     */
    private void loadLatestPersistedConfig() {
        log.info("发现持久化安全配置，加载最新版本");
        
        try {
            // 获取合并后的配置（持久化配置优先）
            Map<String, Object> mergedConfig = configMergeService.getMergedSecurityConfig();
            
            // 更新SecurityProperties
            updateSecurityPropertiesFromConfig(mergedConfig);
            
            int currentVersion = configurationManager.getCurrentSecurityVersion();
            log.info("已加载安全配置版本 {}，包含 {} 个配置项", currentVersion, mergedConfig.size());
            
        } catch (Exception e) {
            log.error("加载持久化安全配置失败", e);
            throw new RuntimeException("Failed to load persisted security config", e);
        }
    }

    /**
     * 从配置Map更新SecurityProperties
     * 这里只更新关键配置，复杂的嵌套配置可以通过ObjectMapper转换
     */
    @SuppressWarnings("unchecked")
    private void updateSecurityPropertiesFromConfig(Map<String, Object> config) {
        try {
            // 更新总开关
            if (config.containsKey("enabled")) {
                securityProperties.setEnabled((Boolean) config.get("enabled"));
            }
            
            // 更新API Key配置
            if (config.containsKey("apiKey")) {
                updateApiKeyConfig((Map<String, Object>) config.get("apiKey"));
            }
            
            // 更新JWT配置
            if (config.containsKey("jwt")) {
                updateJwtConfig((Map<String, Object>) config.get("jwt"));
            }
            
            // 其他复杂配置可以根据需要添加
            
            log.debug("SecurityProperties已从持久化配置更新");
            
        } catch (Exception e) {
            log.error("更新SecurityProperties失败", e);
            throw new RuntimeException("Failed to update SecurityProperties", e);
        }
    }

    /**
     * 更新API Key配置
     */
    @SuppressWarnings("unchecked")
    private void updateApiKeyConfig(Map<String, Object> apiKeyConfig) {
        ApiKeyConfig config = securityProperties.getApiKey();
        
        if (apiKeyConfig.containsKey("enabled")) {
            config.setEnabled((Boolean) apiKeyConfig.get("enabled"));
        }
        if (apiKeyConfig.containsKey("headerName")) {
            config.setHeaderName((String) apiKeyConfig.get("headerName"));
        }
        if (apiKeyConfig.containsKey("keys")) {
            config.setKeys((List) apiKeyConfig.get("keys"));
        }
        if (apiKeyConfig.containsKey("defaultExpirationDays")) {
            config.setDefaultExpirationDays(((Number) apiKeyConfig.get("defaultExpirationDays")).longValue());
        }
        if (apiKeyConfig.containsKey("cacheExpirationSeconds")) {
            config.setCacheExpirationSeconds(((Number) apiKeyConfig.get("cacheExpirationSeconds")).longValue());
        }
    }

    /**
     * 更新JWT配置
     */
    private void updateJwtConfig(Map<String, Object> jwtConfig) {
        JwtConfig config = securityProperties.getJwt();
        
        if (jwtConfig.containsKey("enabled")) {
            config.setEnabled((Boolean) jwtConfig.get("enabled"));
        }
        if (jwtConfig.containsKey("jwtHeader")) {
            config.setJwtHeader((String) jwtConfig.get("jwtHeader"));
        }
        if (jwtConfig.containsKey("secret")) {
            config.setSecret((String) jwtConfig.get("secret"));
        }
        if (jwtConfig.containsKey("algorithm")) {
            config.setAlgorithm((String) jwtConfig.get("algorithm"));
        }
        if (jwtConfig.containsKey("expirationMinutes")) {
            config.setExpirationMinutes(((Number) jwtConfig.get("expirationMinutes")).longValue());
        }
        if (jwtConfig.containsKey("refreshExpirationDays")) {
            config.setRefreshExpirationDays(((Number) jwtConfig.get("refreshExpirationDays")).longValue());
        }
        if (jwtConfig.containsKey("issuer")) {
            config.setIssuer((String) jwtConfig.get("issuer"));
        }
        if (jwtConfig.containsKey("blacklistEnabled")) {
            config.setBlacklistEnabled((Boolean) jwtConfig.get("blacklistEnabled"));
        }
    }

    /**
     * 初始化JWT账户配置
     */
    private void initializeJwtAccountConfig() {
        log.info("开始初始化JWT账户配置...");
        
        try {
            if (!jwtAccountService.hasPersistedAccountConfig()) {
                // 如果没有持久化的JWT账户配置，将YAML配置保存为第一个版本
                jwtAccountService.initializeJwtAccountFromYaml();
            } else {
                // 如果有持久化配置，加载最新版本
                jwtAccountService.loadLatestJwtAccountConfig();
            }
            
            log.info("JWT账户配置初始化完成");
            
        } catch (Exception e) {
            log.error("JWT账户配置初始化失败", e);
            // JWT账户配置初始化失败不应该阻止整个应用启动
            log.warn("JWT账户配置初始化失败，将使用YAML默认配置");
        }
    }

    private void initializeApiKeyConfig() {
        log.info("开始初始化APIKEY配置...");

        try {
            if (!apiKeyService.hasPersistedAccountConfig()) {
                // 如果没有持久化的JWT账户配置，将YAML配置保存为第一个版本
                apiKeyService.initializeApiKeyFromYaml();
            } else {
                // 如果有持久化配置，加载最新版本
                apiKeyService.loadLatestApiKeyConfig();
            }

            log.info("JWT账户配置初始化完成");

        } catch (Exception e) {
            log.error("JWT账户配置初始化失败", e);
            // JWT账户配置初始化失败不应该阻止整个应用启动
            log.warn("JWT账户配置初始化失败，将使用YAML默认配置");
        }
    }

}
package org.unreal.modelrouter.security.config;

import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SanitizationRule;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 安全配置管理服务接口
 * 提供安全配置的动态更新和管理功能
 */
public interface SecurityConfigurationService {
    
    /**
     * 动态更新API Key配置
     * @param apiKeys API Key列表
     * @return 更新操作结果
     */
    Mono<Void> updateApiKeys(List<ApiKeyInfo> apiKeys);
    
    /**
     * 动态更新脱敏规则配置
     * @param rules 脱敏规则列表
     * @return 更新操作结果
     */
    Mono<Void> updateSanitizationRules(List<SanitizationRule> rules);
    
    /**
     * 更新JWT配置
     * @param jwtConfig JWT配置
     * @return 更新操作结果
     */
    Mono<Void> updateJwtConfig(SecurityProperties.JwtConfig jwtConfig);
    
    /**
     * 获取当前安全配置
     * @return 当前安全配置
     */
    Mono<SecurityProperties> getCurrentConfiguration();
    
    /**
     * 验证配置的有效性
     * @param properties 安全配置
     * @return 验证结果
     */
    Mono<Boolean> validateConfiguration(SecurityProperties properties);
    
    /**
     * 重新加载配置
     * @return 重新加载操作结果
     */
    Mono<Void> reloadConfiguration();
    
    /**
     * 获取配置变更历史
     * @param limit 限制数量
     * @return 配置变更历史
     */
    Mono<List<SecurityConfigurationChangeEvent>> getConfigurationHistory(int limit);
}
package org.unreal.modelrouter.security.authentication;

import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.UsageStatistics;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * API Key管理服务接口
 * 提供API Key的CRUD操作和验证功能
 */
public interface ApiKeyService {
    
    /**
     * 验证API Key是否有效
     * @param keyValue API Key值
     * @return 验证结果，包含API Key信息
     */
    Mono<ApiKeyInfo> validateApiKey(String keyValue);
    
    /**
     * 创建新的API Key
     * @param apiKeyInfo API Key信息
     * @return 创建的API Key信息
     */
    Mono<ApiKeyInfo> createApiKey(ApiKeyInfo apiKeyInfo);
    
    /**
     * 更新API Key信息
     * @param keyId API Key ID
     * @param apiKeyInfo 更新的API Key信息
     * @return 更新后的API Key信息
     */
    Mono<ApiKeyInfo> updateApiKey(String keyId, ApiKeyInfo apiKeyInfo);
    
    /**
     * 删除API Key
     * @param keyId API Key ID
     * @return 删除操作结果
     */
    Mono<Void> deleteApiKey(String keyId);
    
    /**
     * 获取所有API Key列表
     * @return API Key列表
     */
    Mono<List<ApiKeyInfo>> getAllApiKeys();
    
    /**
     * 根据ID获取API Key信息
     * @param keyId API Key ID
     * @return API Key信息
     */
    Mono<ApiKeyInfo> getApiKeyById(String keyId);
    
    /**
     * 更新API Key使用统计
     * @param keyId API Key ID
     * @param success 请求是否成功
     * @return 更新操作结果
     */
    Mono<Void> updateUsageStatistics(String keyId, boolean success);
    
    /**
     * 获取API Key使用统计
     * @param keyId API Key ID
     * @return 使用统计信息
     */
    Mono<UsageStatistics> getUsageStatistics(String keyId);
}
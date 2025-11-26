package org.unreal.modelrouter.security.service;

import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT令牌持久化服务接口
 * 提供JWT令牌的存储、查询和管理功能
 */
public interface JwtPersistenceService {
    
    /**
     * 保存JWT令牌信息
     * @param tokenInfo JWT令牌信息
     * @return 保存操作结果
     */
    Mono<Void> saveToken(JwtTokenInfo tokenInfo);
    
    /**
     * 根据令牌哈希查找令牌信息
     * @param tokenHash 令牌哈希值
     * @return 令牌信息
     */
    Mono<JwtTokenInfo> findByTokenHash(String tokenHash);
    
    /**
     * 根据用户ID查找活跃的令牌
     * @param userId 用户ID
     * @return 活跃令牌列表
     */
    Mono<List<JwtTokenInfo>> findActiveTokensByUserId(String userId);
    
    /**
     * 分页查询所有令牌
     * @param page 页码
     * @param size 页大小
     * @return 令牌列表
     */
    Mono<List<JwtTokenInfo>> findAllTokens(int page, int size);
    
    /**
     * 更新令牌状态
     * @param tokenHash 令牌哈希值
     * @param status 新状态
     * @return 更新操作结果
     */
    Mono<Void> updateTokenStatus(String tokenHash, TokenStatus status);
    
    /**
     * 统计活跃令牌数量
     * @return 活跃令牌数量
     */
    Mono<Long> countActiveTokens();
    
    /**
     * 根据状态统计令牌数量
     * @param status 令牌状态
     * @return 指定状态的令牌数量
     */
    Mono<Long> countTokensByStatus(TokenStatus status);
    
    /**
     * 删除过期的令牌
     * @return 删除操作结果
     */
    Mono<Void> removeExpiredTokens();
    
    /**
     * 根据令牌ID查找令牌信息
     * @param tokenId 令牌ID
     * @return 令牌信息
     */
    Mono<JwtTokenInfo> findByTokenId(String tokenId);
    
    /**
     * 根据用户ID查找所有令牌（包括非活跃的）
     * @param userId 用户ID
     * @param page 页码
     * @param size 页大小
     * @return 令牌列表
     */
    Mono<List<JwtTokenInfo>> findTokensByUserId(String userId, int page, int size);
    
    /**
     * 批量更新令牌状态
     * @param tokenHashes 令牌哈希列表
     * @param status 新状态
     * @param reason 更新原因
     * @param updatedBy 更新者
     * @return 更新操作结果
     */
    Mono<Void> batchUpdateTokenStatus(List<String> tokenHashes, TokenStatus status, String reason, String updatedBy);
}
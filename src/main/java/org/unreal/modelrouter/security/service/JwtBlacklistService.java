package org.unreal.modelrouter.security.service;

import org.unreal.modelrouter.dto.TokenBlacklistEntry;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * JWT令牌黑名单服务接口
 * 提供令牌黑名单管理功能
 */
public interface JwtBlacklistService {
    
    /**
     * 将令牌添加到黑名单
     * @param tokenHash 令牌哈希值
     * @param reason 加入黑名单的原因
     * @param addedBy 添加者
     * @return 操作结果
     */
    Mono<Void> addToBlacklist(String tokenHash, String reason, String addedBy);
    
    /**
     * 检查令牌是否在黑名单中
     * @param tokenHash 令牌哈希值
     * @return 是否在黑名单中
     */
    Mono<Boolean> isBlacklisted(String tokenHash);
    
    /**
     * 从黑名单中移除令牌
     * @param tokenHash 令牌哈希值
     * @return 操作结果
     */
    Mono<Void> removeFromBlacklist(String tokenHash);
    
    /**
     * 获取黑名单大小
     * @return 黑名单条目数量
     */
    Mono<Long> getBlacklistSize();
    
    /**
     * 清理过期的黑名单条目
     * @return 操作结果
     */
    Mono<Void> cleanupExpiredEntries();
    
    /**
     * 获取黑名单统计信息
     * @return 统计信息
     */
    Mono<Map<String, Object>> getBlacklistStats();
    
    /**
     * 获取黑名单条目详情
     * @param tokenHash 令牌哈希值
     * @return 黑名单条目
     */
    Mono<TokenBlacklistEntry> getBlacklistEntry(String tokenHash);
    
    /**
     * 批量添加令牌到黑名单
     * @param tokenHashes 令牌哈希列表
     * @param reason 加入黑名单的原因
     * @param addedBy 添加者
     * @return 操作结果
     */
    Mono<Void> batchAddToBlacklist(List<String> tokenHashes, String reason, String addedBy);
    
    /**
     * 检查服务是否可用
     * @return 服务可用性
     */
    Mono<Boolean> isServiceAvailable();
    
    /**
     * 获取即将过期的条目数量
     * @param hoursUntilExpiry 距离过期的小时数
     * @return 即将过期的条目数量
     */
    Mono<Long> getExpiringEntriesCount(int hoursUntilExpiry);
    
    /**
     * 清理过期的黑名单条目并返回清理数量
     * @return 清理的条目数量
     */
    Mono<Long> cleanupExpiredEntriesWithCount();
}
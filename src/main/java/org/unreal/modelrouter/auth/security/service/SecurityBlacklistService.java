package org.unreal.modelrouter.auth.security.service;

import org.springframework.data.domain.Page;
import org.unreal.modelrouter.dto.AddBlacklistRequest;
import org.unreal.modelrouter.dto.BlacklistEntryDTO;
import org.unreal.modelrouter.dto.BlacklistStatsDTO;
import org.unreal.modelrouter.jpa.entity.SecurityBlacklistEntity.BlacklistType;

/**
 * 统一安全黑名单服务接口
 */
public interface SecurityBlacklistService {

    /**
     * 添加到黑名单
     */
    BlacklistEntryDTO addToBlacklist(AddBlacklistRequest request, String addedBy);

    /**
     * 从黑名单移除
     */
    boolean removeFromBlacklist(Long id);

    /**
     * 从黑名单移除（根据类型和目标值）
     */
    boolean removeFromBlacklist(BlacklistType type, String targetValue);

    /**
     * 检查是否在黑名单中
     */
    boolean isInBlacklist(BlacklistType type, String targetValue);

    /**
     * 检查Token哈希是否在黑名单中
     */
    boolean isTokenHashInBlacklist(String tokenHash);

    /**
     * 检查IP是否在黑名单中
     */
    boolean isIpInBlacklist(String ipAddress);

    /**
     * 检查设备是否在黑名单中
     */
    boolean isDeviceInBlacklist(String deviceIdentifier);

    /**
     * 获取黑名单条目详情
     */
    BlacklistEntryDTO getBlacklistEntry(Long id);

    /**
     * 分页查询黑名单
     */
    Page<BlacklistEntryDTO> getBlacklistPage(BlacklistType type, String status, int page, int size);

    /**
     * 获取黑名单统计信息
     */
    BlacklistStatsDTO getBlacklistStats();

    /**
     * 清理过期条目
     */
    int cleanupExpiredEntries();

    /**
     * 批量添加到黑名单
     */
    int batchAddToBlacklist(Iterable<AddBlacklistRequest> requests, String addedBy);
}
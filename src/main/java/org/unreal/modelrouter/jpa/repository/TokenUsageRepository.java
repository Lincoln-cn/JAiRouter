package org.unreal.modelrouter.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.jpa.entity.TokenUsageEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Token 使用量统计仓库接口
 *
 * @author JAiRouter Team
 * @since 1.9.5
 */
@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsageEntity, Long> {

    // ========== 基本查询方法 ==========

    /**
     * 根据追踪 ID 查找
     */
    List<TokenUsageEntity> findByTraceId(String traceId);

    /**
     * 根据模型名称查找
     */
    List<TokenUsageEntity> findByModelName(String modelName);

    /**
     * 根据服务类型查找
     */
    List<TokenUsageEntity> findByServiceType(String serviceType);

    /**
     * 根据 API Key ID 查找
     */
    List<TokenUsageEntity> findByApiKeyId(String apiKeyId);

    /**
     * 根据用户 ID 查找
     */
    List<TokenUsageEntity> findByUserId(String userId);

    // ========== 时间范围查询 ==========

    /**
     * 根据时间范围查询
     */
    @Query("SELECT t FROM TokenUsageEntity t WHERE t.occurredAt BETWEEN :startTime AND :endTime ORDER BY t.occurredAt DESC")
    List<TokenUsageEntity> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计时间范围内的 token 使用量
     */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageEntity t WHERE t.occurredAt BETWEEN :startTime AND :endTime")
    long countTotalTokensByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // ========== 统计查询 ==========

    /**
     * 按模型名称统计 token 使用量
     */
    @Query("SELECT t.modelName, SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY t.modelName ORDER BY SUM(t.totalTokens) DESC")
    List<Object[]> countTokensByModel(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按服务类型统计 token 使用量
     */
    @Query("SELECT t.serviceType, SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY t.serviceType ORDER BY SUM(t.totalTokens) DESC")
    List<Object[]> countTokensByServiceType(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按提供商统计 token 使用量
     */
    @Query("SELECT t.provider, SUM(t.totalTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "AND t.provider IS NOT NULL " +
           "GROUP BY t.provider ORDER BY SUM(t.totalTokens) DESC")
    List<Object[]> countTokensByProvider(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按 API Key 统计 token 使用量
     */
    @Query("SELECT t.apiKeyId, SUM(t.totalTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "AND t.apiKeyId IS NOT NULL " +
           "GROUP BY t.apiKeyId ORDER BY SUM(t.totalTokens) DESC")
    List<Object[]> countTokensByApiKey(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按用户统计 token 使用量
     */
    @Query("SELECT t.userId, SUM(t.totalTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "AND t.userId IS NOT NULL " +
           "GROUP BY t.userId ORDER BY SUM(t.totalTokens) DESC")
    List<Object[]> countTokensByUser(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按日期统计 token 使用量（日级别）
     */
    @Query("SELECT t.usageDate, SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY t.usageDate ORDER BY t.usageDate")
    List<Object[]> countTokensByDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按小时统计 token 使用量
     */
    @Query("SELECT t.hour, SUM(t.totalTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY t.hour ORDER BY t.hour")
    List<Object[]> countTokensByHour(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按周统计 token 使用量（周级别）
     */
    @Query("SELECT t.year, t.weekOfYear, SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY t.year, t.weekOfYear ORDER BY t.year, t.weekOfYear")
    List<Object[]> countTokensByWeek(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按月统计 token 使用量（月级别）
     */
    @Query("SELECT t.year, t.month, SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens), COUNT(t) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY t.year, t.month ORDER BY t.year, t.month")
    List<Object[]> countTokensByMonth(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按模型和日期统计 token 使用量（用于热力图）
     */
    @Query("SELECT t.modelName, t.usageDate, SUM(t.totalTokens) " +
           "FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY t.modelName, t.usageDate ORDER BY t.modelName, t.usageDate")
    List<Object[]> countTokensByModelAndDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计成功率
     */
    @Query("SELECT COUNT(t) FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "AND t.isSuccess = true")
    long countSuccessByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计失败数
     */
    @Query("SELECT COUNT(t) FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "AND t.isSuccess = false")
    long countFailedByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计平均响应时间
     */
    @Query("SELECT COALESCE(AVG(t.responseTimeMs), 0) FROM TokenUsageEntity t " +
           "WHERE t.occurredAt BETWEEN :startTime AND :endTime " +
           "AND t.responseTimeMs IS NOT NULL")
    double avgResponseTimeByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // ========== 最近使用记录查询 ==========

    /**
     * 获取最近的使用记录
     */
    @Query("SELECT t FROM TokenUsageEntity t ORDER BY t.occurredAt DESC LIMIT :limit")
    List<TokenUsageEntity> findRecentUsage(@Param("limit") int limit);

    /**
     * 获取指定模型的最近使用记录
     */
    @Query("SELECT t FROM TokenUsageEntity t WHERE t.modelName = :modelName ORDER BY t.occurredAt DESC LIMIT :limit")
    List<TokenUsageEntity> findRecentUsageByModel(
            @Param("modelName") String modelName,
            @Param("limit") int limit);

    // ========== 清理过期数据 ==========

    /**
     * 删除指定时间之前的使用记录
     */
    @Modifying
    @Query("DELETE FROM TokenUsageEntity t WHERE t.occurredAt < :cutoffTime")
    int deleteByOccurredAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 统计总记录数
     */
    @Query("SELECT COUNT(t) FROM TokenUsageEntity t")
    long countAllUsage();

    /**
     * 统计总 token 使用量
     */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageEntity t")
    long sumAllTokens();
}

package org.unreal.modelrouter.auth.security.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * API Key使用统计数据模型
 */
@Data
@Builder
@NoArgsConstructor   // 这个很关键
@AllArgsConstructor
public class UsageStatistics {
    
    /**
     * 总请求次数
     */
    private long totalRequests;
    
    /**
     * 成功请求次数
     */
    private long successfulRequests;
    
    /**
     * 失败请求次数
     */
    private long failedRequests;
    
    /**
     * 最后使用时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastUsedAt;
    
    /**
     * 每日使用统计（日期 -> 使用次数）
     */
    private Map<String, Long> dailyUsage;

    /**
     * 每日 Token 使用统计（日期 -> Token 总数）
     */
    private Map<String, Long> dailyTokenUsage;

    /**
     * 获取成功率
     * @return 成功率（0-1之间的小数）
     */
    public double getSuccessRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) successfulRequests / totalRequests;
    }

    /**
     * 增加请求统计
     * @param success 请求是否成功
     */
    public void incrementRequest(final boolean success) {
        totalRequests++;
        if (success) {
            successfulRequests++;
        } else {
            failedRequests++;
        }
        lastUsedAt = LocalDateTime.now();
        String today = LocalDateTime.now().toLocalDate().toString();
        if (dailyUsage == null) {
            dailyUsage = new HashMap<>();
        }
        dailyUsage.put(today, dailyUsage.getOrDefault(today, 0L) + 1);
    }

    /**
     * 增加 Token 使用量
     * @param tokens 本次请求消耗的 Token 数
     */
    public void incrementTokens(final long tokens) {
        if (tokens <= 0) {
            return;
        }
        String today = LocalDateTime.now().toLocalDate().toString();
        if (dailyTokenUsage == null) {
            dailyTokenUsage = new HashMap<>();
        }
        dailyTokenUsage.put(today, dailyTokenUsage.getOrDefault(today, 0L) + tokens);
    }

    /**
     * 重置统计信息
     */
    public void reset() {
        totalRequests = 0L;
        successfulRequests = 0L;
        failedRequests = 0L;
        lastUsedAt = null;
        if (dailyUsage != null) {
            dailyUsage.clear();
        }
        if (dailyTokenUsage != null) {
            dailyTokenUsage.clear();
        }
    }

    /**
     * 重置每日统计（请求计数和 Token 计数）
     */
    public void resetDaily() {
        if (dailyUsage != null) {
            dailyUsage.clear();
        }
        if (dailyTokenUsage != null) {
            dailyTokenUsage.clear();
        }
    }
    
    /**
     * 获取失败率
     * @return 失败率（0-1之间的小数）
     */
    public double getFailureRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) failedRequests / totalRequests;
    }
}
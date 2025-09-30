package org.unreal.modelrouter.security.model;

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
    public void incrementRequest(boolean success) {
        totalRequests++;
        if (success) {
            successfulRequests++;
        } else {
            failedRequests++;
        }
        lastUsedAt = LocalDateTime.now();
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
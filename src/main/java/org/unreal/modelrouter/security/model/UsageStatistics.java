package org.unreal.modelrouter.security.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * API Key使用统计数据模型
 */
@Data
@Builder
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
}
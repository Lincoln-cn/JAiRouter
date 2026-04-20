package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 模型调用统计 DTO
 * 
 * v2.0.0 新增功能：
 * - 按模型名称统计分析
 * - 支持多服务类型统计
 * - 包含成功率、延迟等关键指标
 * 
 * @author JAiRouter Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallStats implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 服务类型 (CHAT, EMBEDDING, RERANK, TTS 等)
     */
    private String serviceType;

    /**
     * 总调用次数
     */
    private long totalCalls;

    /**
     * 成功次数
     */
    private long successCount;

    /**
     * 失败次数
     */
    private long failureCount;

    /**
     * 熔断次数
     */
    private long circuitBreakerCount;

    /**
     * 限流拒绝次数
     */
    private long rateLimitCount;

    /**
     * 平均响应时间 (毫秒)
     */
    private double avgResponseTime;

    /**
     * 最小响应时间 (毫秒)
     */
    private long minResponseTime;

    /**
     * 最大响应时间 (毫秒)
     */
    private long maxResponseTime;

    /**
     * P95 响应时间 (毫秒)
     */
    private double p95ResponseTime;

    /**
     * P99 响应时间 (毫秒)
     */
    private double p99ResponseTime;

    /**
     * 成功率 (0.0-1.0)
     */
    private double successRate;

    /**
     * 当前 QPS (每秒请求数)
     */
    private double currentQps;

    /**
     * 峰值 QPS
     */
    private double peakQps;

    /**
     * 最后调用时间戳
     */
    private long lastCallTime;

    /**
     * 统计开始时间戳
     */
    private long statsStartTime;

    /**
     * 统计结束时间戳
     */
    private long statsEndTime;

    /**
     * 关联的实例 ID 列表
     */
    private String[] instanceIds;

    /**
     * 关联的实例数量
     */
    private int instanceCount;

    /**
     * 是否活跃 (最近有调用)
     */
    private boolean active;

    /**
     * 健康状态 (HEALTHY, DEGRADED, UNHEALTHY)
     */
    private String healthStatus;

    /**
     * 错误码分布 {\"400\": 5, \"500\": 3, \"503\": 1}
     */
    private java.util.Map<String, Integer> errorCodeDistribution;

    /**
     * 计算成功率
     */
    public double calculateSuccessRate() {
        if (totalCalls == 0) {
            return 1.0;
        }
        return (double) successCount / totalCalls;
    }

    /**
     * 计算失败率
     */
    public double calculateFailureRate() {
        if (totalCalls == 0) {
            return 0.0;
        }
        return (double) failureCount / totalCalls;
    }

    /**
     * 计算熔断率
     */
    public double calculateCircuitBreakerRate() {
        if (totalCalls == 0) {
            return 0.0;
        }
        return (double) circuitBreakerCount / totalCalls;
    }

    /**
     * 计算限流率
     */
    public double calculateRateLimitRate() {
        if (totalCalls == 0) {
            return 0.0;
        }
        return (double) rateLimitCount / totalCalls;
    }

    /**
     * 判断健康状态
     */
    public String determineHealthStatus() {
        double successRate = calculateSuccessRate();
        double circuitBreakerRate = calculateCircuitBreakerRate();

        if (successRate >= 0.99 && circuitBreakerRate < 0.01) {
            return "HEALTHY";
        } else if (successRate >= 0.95 && circuitBreakerRate < 0.05) {
            return "DEGRADED";
        } else {
            return "UNHEALTHY";
        }
    }

    /**
     * 更新统计信息
     *
     * @param success 是否成功
     * @param responseTime 响应时间
     */
    public void updateStats(boolean success, long responseTime) {
        this.totalCalls++;
        if (success) {
            this.successCount++;
        } else {
            this.failureCount++;
        }

        // 更新响应时间统计
        if (this.totalCalls == 1) {
            this.minResponseTime = responseTime;
            this.maxResponseTime = responseTime;
            this.avgResponseTime = responseTime;
        } else {
            this.minResponseTime = Math.min(this.minResponseTime, responseTime);
            this.maxResponseTime = Math.max(this.maxResponseTime, responseTime);
            // 移动平均
            this.avgResponseTime = this.avgResponseTime + (responseTime - this.avgResponseTime) / this.totalCalls;
        }

        this.lastCallTime = System.currentTimeMillis();
        this.successRate = calculateSuccessRate();
        this.healthStatus = determineHealthStatus();
    }

    /**
     * 记录熔断事件
     */
    public void recordCircuitBreaker() {
        this.circuitBreakerCount++;
        this.totalCalls++;
        this.lastCallTime = System.currentTimeMillis();
        this.healthStatus = determineHealthStatus();
    }

    /**
     * 记录限流事件
     */
    public void recordRateLimit() {
        this.rateLimitCount++;
        this.totalCalls++;
        this.lastCallTime = System.currentTimeMillis();
    }

    /**
     * 记录错误码
     *
     * @param errorCode 错误码
     */
    public void recordErrorCode(String errorCode) {
        if (this.errorCodeDistribution == null) {
            this.errorCodeDistribution = new java.util.HashMap<>();
        }
        this.errorCodeDistribution.merge(errorCode, 1, Integer::sum);
    }
}

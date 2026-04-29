package org.unreal.modelrouter.monitor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Token 使用量统计 DTO
 * 用于 Token 使用量统计 API 的数据传输
 *
 * @author JAiRouter Team
 * @since 1.9.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageStatisticsDTO {

    /**
     * 统计开始时间
     */
    private LocalDateTime startTime;

    /**
     * 统计结束时间
     */
    private LocalDateTime endTime;

    /**
     * 总请求次数
     */
    @JsonProperty("totalRequests")
    private Long totalRequests;

    /**
     * 成功请求次数
     */
    @JsonProperty("successfulRequests")
    private Long successfulRequests;

    /**
     * 失败请求次数
     */
    @JsonProperty("failedRequests")
    private Long failedRequests;

    /**
     * 总 token 使用量
     */
    @JsonProperty("totalTokens")
    private Long totalTokens;

    /**
     * 总输入 token 数
     */
    @JsonProperty("totalPromptTokens")
    private Long totalPromptTokens;

    /**
     * 总输出 token 数
     */
    @JsonProperty("totalCompletionTokens")
    private Long totalCompletionTokens;

    /**
     * 平均响应时间 (毫秒)
     */
    @JsonProperty("avgResponseTimeMs")
    private Double avgResponseTimeMs;

    /**
     * 成功率
     */
    @JsonProperty("successRate")
    private Double successRate;

    /**
     * 按模型统计
     */
    @JsonProperty("byModel")
    private List<ModelTokenStats> byModel;

    /**
     * 按服务类型统计
     */
    @JsonProperty("byServiceType")
    private List<ServiceTypeStats> byServiceType;

    /**
     * 按提供商统计
     */
    @JsonProperty("byProvider")
    private List<ProviderStats> byProvider;

    /**
     * 按日期统计
     */
    @JsonProperty("byDay")
    private List<DailyStats> byDay;

    /**
     * 按周统计
     */
    @JsonProperty("byWeek")
    private List<WeeklyStats> byWeek;

    /**
     * 按月统计
     */
    @JsonProperty("byMonth")
    private List<MonthlyStats> byMonth;

    /**
     * 按小时统计
     */
    @JsonProperty("byHour")
    private List<HourlyStats> byHour;

    /**
     * 按 API Key 统计（可选）
     */
    @JsonProperty("byApiKey")
    private List<ApiKeyStats> byApiKey;

    /**
     * 按用户统计（可选）
     */
    @JsonProperty("byUser")
    private List<UserStats> byUser;

    /**
     * 模型 Token 统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelTokenStats {
        private String modelName;
        private Long totalTokens;
        private Long promptTokens;
        private Long completionTokens;
        private Long requestCount;
        private Double avgTokensPerRequest;
    }

    /**
     * 服务类型统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceTypeStats {
        private String serviceType;
        private Long totalTokens;
        private Long promptTokens;
        private Long completionTokens;
        private Long requestCount;
    }

    /**
     * 提供商统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderStats {
        private String provider;
        private Long totalTokens;
        private Long requestCount;
    }

    /**
     * 日统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private String date;
        private Long totalTokens;
        private Long promptTokens;
        private Long completionTokens;
        private Long requestCount;
    }

    /**
     * 周统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyStats {
        private Integer year;
        private Integer week;
        private Long totalTokens;
        private Long promptTokens;
        private Long completionTokens;
        private Long requestCount;
        private String weekLabel; // 格式化后的周标签，如 "2026-W16"
    }

    /**
     * 月统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyStats {
        private Integer year;
        private Integer month;
        private Long totalTokens;
        private Long promptTokens;
        private Long completionTokens;
        private Long requestCount;
        private String monthLabel; // 格式化后的月标签，如 "2026-04"
    }

    /**
     * 小时统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyStats {
        private Integer hour;
        private Long totalTokens;
        private Long requestCount;
        private String hourLabel; // 格式化后的小时标签，如 "08:00"
    }

    /**
     * API Key 统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyStats {
        private String apiKeyId;
        private Long totalTokens;
        private Long requestCount;
    }

    /**
     * 用户统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStats {
        private String userId;
        private Long totalTokens;
        private Long requestCount;
    }
}

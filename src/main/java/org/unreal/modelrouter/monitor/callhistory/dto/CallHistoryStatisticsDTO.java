package org.unreal.modelrouter.monitor.callhistory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * API 调用历史统计响应 DTO
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallHistoryStatisticsDTO {

    /**
     * 查询开始时间
     */
    private String startTime;

    /**
     * 查询结束时间
     */
    private String endTime;

    // ========== 汇总指标 ==========

    /**
     * 总请求数
     */
    private Long totalRequests;

    /**
     * 成功请求数
     */
    private Long successfulRequests;

    /**
     * 失败请求数
     */
    private Long failedRequests;

    /**
     * 成功率 (%)
     */
    private Double successRate;

    /**
     * 总 token 使用量
     */
    private Long totalTokens;

    /**
     * 平均响应时间 (毫秒)
     */
    private Double avgResponseTimeMs;

    /**
     * 平均 token 数/请求
     */
    private Double avgTokensPerRequest;

    // ========== 分组统计 ==========

    /**
     * 按模型统计
     */
    private List<ModelStats> byModel;

    /**
     * 按服务类型统计
     */
    private List<ServiceTypeStats> byServiceType;

    /**
     * 按日期统计趋势
     */
    private List<DailyStats> byDay;

    /**
     * 按小时统计（24小时分布）
     */
    private List<HourlyStats> byHour;

    /**
     * HTTP 状态码分布
     */
    private List<StatusCodeStats> byStatusCode;

    /**
     * 错误码分布
     */
    private List<ErrorCodeStats> byErrorCode;

    // ========== 内部类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelStats {
        private String modelName;
        private Long requestCount;
        private Long totalTokens;
        private Double avgResponseTimeMs;
        private Long successCount;
        private Double successRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceTypeStats {
        private String serviceType;
        private Long requestCount;
        private Long totalTokens;
        private Double avgResponseTimeMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private String date;
        private Long requestCount;
        private Long totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyStats {
        private Integer hour;
        private Long requestCount;
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCodeStats {
        private Integer statusCode;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorCodeStats {
        private String errorCode;
        private Long count;
    }
}

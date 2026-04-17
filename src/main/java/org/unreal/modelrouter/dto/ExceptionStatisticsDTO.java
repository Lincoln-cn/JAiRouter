package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异常统计 DTO
 * 用于异常统计 API 的数据传输
 *
 * @author JAiRouter Team
 * @since 1.9.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionStatisticsDTO {

    /**
     * 统计开始时间
     */
    private LocalDateTime startTime;

    /**
     * 统计结束时间
     */
    private LocalDateTime endTime;

    /**
     * 异常总数
     */
    @JsonProperty("totalEvents")
    private Long totalCount;

    /**
     * 异常类型数
     */
    @JsonProperty("totalTypes")
    private Integer totalTypes;

    /**
     * 按类型统计（类型名 -> 数量）
     */
    @JsonProperty("eventsByType")
    private Map<String, Long> byType;

    /**
     * 按分类统计（分类名 -> 数量）
     */
    @JsonProperty("eventsByCategory")
    private Map<String, Long> byCategory;

    /**
     * 按操作统计（操作名 -> 数量）
     */
    @JsonProperty("eventsByOperation")
    private Map<String, Long> byOperation;

    /**
     * 按 HTTP 状态统计（状态码 -> 数量）
     */
    @JsonProperty("eventsByHttpStatus")
    private Map<String, Long> byHttpStatus;

    /**
     * Top 客户端 IP
     */
    @JsonProperty("topClientIps")
    private List<ClientIpStats> topClientIps;

    /**
     * 小时分布
     */
    @JsonProperty("hourlyDistribution")
    private List<HourlyStats> hourlyDistribution;

    /**
     * 聚合异常数量
     */
    private Long aggregatedCount;

    /**
     * 未聚合异常数量
     */
    private Long unaggregatedCount;

    /**
     * 客户端 IP 统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientIpStats {
        private String ip;
        private Long count;
    }

    /**
     * 小时统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyStats {
        private String hour;
        private Long count;
    }
}

package org.unreal.modelrouter.security.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API Key 列表响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyListVO {

    /**
     * API Key 列表
     */
    private List<ApiKeyVO> items;

    /**
     * 总数量
     */
    private Integer total;

    /**
     * 启用的数量
     */
    private Integer enabledCount;

    /**
     * 禁用的数量
     */
    private Integer disabledCount;

    /**
     * 已过期的数量
     */
    private Integer expiredCount;

    /**
     * 统计信息摘要
     */
    private Summary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        /**
         * 今日总请求次数
         */
        private Long todayTotalRequests;

        /**
         * 今日成功请求次数
         */
        private Long todaySuccessfulRequests;

        /**
         * 今日失败请求次数
         */
        private Long todayFailedRequests;
    }
}
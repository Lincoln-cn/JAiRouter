package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 熔断器状态详情 DTO
 * 包含实例信息和熔断器状态
 * 
 * v2.6.12: 新增，用于前端展示完整的熔断器信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerStatusDTO {

    /**
     * 实例 ID（UUID 格式）
     */
    private String instanceId;

    /**
     * 数据库 ID（用于兼容）
     */
    private Long dbId;

    /**
     * 实例名称
     */
    private String instanceName;

    /**
     * 服务类型
     */
    private String serviceType;

    /**
     * 基础 URL (host)
     */
    private String baseUrl;

    /**
     * 路径
     */
    private String path;

    /**
     * 熔断器状态：CLOSED, OPEN, HALF_OPEN
     */
    private String state;

    /**
     * 失败次数
     */
    private Integer failureCount;

    /**
     * 成功次数（HALF_OPEN 状态使用）
     */
    private Integer successCount;

    /**
     * 最后失败时间
     */
    private Long lastFailureTime;

    /**
     * 失败阈值
     */
    private Integer failureThreshold;

    /**
     * 成功阈值
     */
    private Integer successThreshold;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;

    /**
     * 熔断器是否启用
     */
    private Boolean enabled;

    /**
     * 实例状态
     */
    private String instanceStatus;

    /**
     * 健康状态
     */
    private String healthStatus;
}

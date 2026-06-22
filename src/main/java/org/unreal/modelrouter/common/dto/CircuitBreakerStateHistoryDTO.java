package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 熔断器状态变化历史记录 DTO
 * 
 * v2.6.13: 新增，用于前端历史记录展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerStateHistoryDTO {

    /**
     * 记录 ID
     */
    private Long id;

    /**
     * 实例 ID（UUID 格式）
     */
    private String instanceId;

    /**
     * 实例名称
     */
    private String instanceName;

    /**
     * 服务类型
     */
    private String serviceType;

    /**
     * 变化前的状态
     */
    private String previousState;

    /**
     * 变化后的状态
     */
    private String currentState;

    /**
     * 触发原因
     */
    private String triggerReason;

    /**
     * 触发原因描述（中文）
     */
    private String triggerReasonDesc;

    /**
     * 状态变化时的失败计数
     */
    private Integer failureCount;

    /**
     * 状态变化时的成功计数
     */
    private Integer successCount;

    /**
     * 变化时间
     */
    private LocalDateTime changedAt;

    /**
     * 触发原因枚举
     */
    public enum TriggerReason {
        FAILURE_THRESHOLD("失败次数达到阈值"),
        SUCCESS_THRESHOLD("成功次数达到阈值"),
        TIMEOUT("超时后自动进入半开状态"),
        MANUAL_RESET("手动重置"),
        MANUAL_FORCE_OPEN("手动强制打开"),
        MANUAL_FORCE_CLOSE("手动强制关闭");

        private final String description;

        TriggerReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}

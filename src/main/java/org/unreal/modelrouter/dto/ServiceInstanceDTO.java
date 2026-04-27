package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 服务实例 DTO
 * v1.5.2: 用于替代 Map 传递数据
 * v1.7.1: 添加 adapter 和 headers 字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstanceDTO {

    private Long id;

    /**
     * 获取实例ID（兼容前端）
     *
     * @deprecated 此字段与 {@link #id} 字段功能重复，仅为兼容前端而保留。
     *             前端应迁移使用 id 字段，通过 {@link #getId()} 获取实例ID。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>instanceId 返回 String 类型（id 的字符串形式）</li>
     *               <li>id 返回 Long 类型，是数据库主键</li>
     *               <li>前端应使用 id 字段，统一数据类型</li>
     *             </ul>
     *             <p>此字段将在 v3.0 版本中移除，前端需同步更新。</p>
     * @return 实例ID字符串
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    @JsonProperty("instanceId")
    public String getInstanceId() {
        return id != null ? String.valueOf(id) : null;
    }

    private Long serviceConfigId;
    private String name;
    private String baseUrl;
    private String path;
    private Integer weight;

    // 内部存储原始状态值
    private String status;

    // 兼容前端，返回小写的状态值
    @JsonProperty("status")
    public String getStatusForJson() {
        return status != null ? status.toLowerCase() : null;
    }

    private String healthStatus;
    private String errorMessage;

    /**
     * 实例级别适配器配置（可选，覆盖服务级别配置）
     */
    private String adapter;

    /**
     * 自定义请求头配置
     */
    private Map<String, String> headers;

    // 限流器配置
    private InstanceRateLimitDTO rateLimit;

    // 熔断器配置
    private InstanceCircuitBreakerDTO circuitBreaker;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
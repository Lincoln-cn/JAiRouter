package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务实例 DTO
 * v1.5.2: 用于替代 Map 传递数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstanceDTO {

    private Long id;

    // 兼容前端，添加 instanceId 字段（与 id 相同）
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
    
    // 限流器配置
    private InstanceRateLimitDTO rateLimit;
    
    // 熔断器配置
    private InstanceCircuitBreakerDTO circuitBreaker;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
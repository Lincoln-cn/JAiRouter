package org.unreal.modelrouter.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务配置 DTO
 * v1.5.2: 用于替代 Map 传递数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfigDTO {

    private Long id;
    private String configKey;
    private String serviceType;
    private String adapter;
    private String loadBalanceType;
    private Integer version;
    private Boolean isLatest;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

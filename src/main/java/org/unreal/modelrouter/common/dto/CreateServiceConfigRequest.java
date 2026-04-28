package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建服务配置请求 DTO
 * v1.5.2: 用于替代 Map 传递数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceConfigRequest {

    private String serviceType;
    private String adapter;
    private String loadBalanceType;
    private List<CreateServiceInstanceRequest> instances;
}

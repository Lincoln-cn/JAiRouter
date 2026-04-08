package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建服务实例请求 DTO
 * v1.5.2: 用于替代 Map 传递数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceInstanceRequest {

    private String name;
    private String baseUrl;
    private String path;
    private Integer weight;
}

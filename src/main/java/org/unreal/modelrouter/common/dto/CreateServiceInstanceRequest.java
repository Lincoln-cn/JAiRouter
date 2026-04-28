package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 创建服务实例请求 DTO
 * v1.5.2: 用于替代 Map 传递数据
 * v1.7.1: 添加 adapter、headers 和 status 字段
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

    /**
     * 实例状态：active 或 inactive
     */
    private String status;

    /**
     * 实例级别适配器配置（可选，覆盖服务级别配置）
     */
    private String adapter;

    /**
     * 自定义请求头配置
     */
    private Map<String, String> headers;
}

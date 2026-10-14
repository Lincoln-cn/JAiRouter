package org.unreal.modelrouter.config.dto;

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
public final class CreateServiceInstanceRequest {

    private String name;
    private String baseUrl;
    private String path;
    private Integer weight;

    /**
     * 实例生命周期状态：只接受 {@code active} / {@code inactive}（大小写不敏感，落库归一为规范大写，
     * 词表见 {@code InstanceStatus}，issue #354）；不传则默认 {@code active}。
     *
     * <p>其他取值会被**拒收**（400 INVALID_REQUEST）—— 非 {@code active} 的取值会让实例被
     * 可用性过滤整体排除。**健康**是另一个维度，用 {@code healthStatus} 表达。</p>
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

    /**
     * v2.9.7: 实例标签配置(标签路由按键值对圈选)
     */
    private Map<String, String> tags;
}

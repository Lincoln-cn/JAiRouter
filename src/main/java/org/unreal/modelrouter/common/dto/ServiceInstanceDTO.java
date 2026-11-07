package org.unreal.modelrouter.common.dto;

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
public final class ServiceInstanceDTO {

    private Long id;

    /**
     * 实例的稳定唯一标识（UUID），与 {@code service_instance.instance_id} 列值一致。
     *
     * <p>熔断器/限流器运行时键、规则与池的「目标实例」标识、日志中的实例标识均使用该值；
     * {@code GET /api/config/instance/*} 返回的 {@code instanceId} 即此值。</p>
     *
     * <p>历史数据 {@code instance_id} 为空时回退为数据库主键 {@link #id} 的字符串形式
     * （回退逻辑见 {@code ServiceInstanceManager#convertToDTO}）。</p>
     *
     * @since v3.8.0 语义统一为 UUID。v2.5.1~v3.7.x 该字段曾返回 {@link #id} 的字符串形式
     *             （与数据库主键相同），调用方需按 UUID 语义使用；
     *             {@code id} 仍保留数据库主键，供需要主键的场景（分页、内部关联）使用。
     */
    private String instanceId;

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
     * 该实例是否解析出一个**已注册**的适配器。
     *
     * <p>为 {@code false} 时请求会在转发阶段失败（502），而端口探测仍然通 ——
     * 这是 #1004 描述的「实例健康但调用全挂」的可观测面。</p>
     *
     * @since v3.7.4
     */
    private Boolean adapterResolved;

    /**
     * 适配器未解析时的说明（给界面直接展示），解析正常时为 {@code null}。
     *
     * @since v3.7.4
     */
    private String adapterWarning;


    /**
     * 自定义请求头配置
     */
    private Map<String, String> headers;

    /**
     * v2.9.7: 实例标签配置(标签路由按键值对圈选)
     */
    private Map<String, String> tags;

    // 限流器配置
    private InstanceRateLimitDTO rateLimit;

    // 熔断器配置
    private InstanceCircuitBreakerDTO circuitBreaker;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
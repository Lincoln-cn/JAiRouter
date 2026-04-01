package org.unreal.modelrouter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 实例更新请求 DTO（扁平化格式）
 * 直接对应数据库字段，无嵌套结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实例更新请求（扁平化格式）")
public class InstanceUpdateFlatRequest {

    @Schema(description = "实例 ID", example = "5", required = true)
    private String instanceId;

    @Schema(description = "实例名称", example = "qwen3:4b", required = true)
    private String name;

    @Schema(description = "基础 URL", example = "http://172.16.30.6:9090", required = true)
    private String baseUrl;

    @Schema(description = "路径", example = "/v1/chat/completions")
    private String path;

    @Schema(description = "权重", example = "1")
    private Integer weight;

    @Schema(description = "状态：active, inactive", example = "active")
    private String status;

    @Schema(description = "适配器")
    private String adapter;

    @Schema(description = "请求头配置")
    private Map<String, String> headers;

    // ==================== 限流器配置（扁平化字段）====================

    @Schema(description = "是否启用限流", example = "true")
    private Boolean rateLimitEnabled;

    @Schema(description = "限流算法", example = "token-bucket")
    private String rateLimitAlgorithm;

    @Schema(description = "限流容量", example = "200")
    private Integer rateLimitCapacity;

    @Schema(description = "限流速率", example = "20")
    private Integer rateLimitRate;

    @Schema(description = "限流范围", example = "instance")
    private String rateLimitScope;

    @Schema(description = "限流键值", example = "")
    private String rateLimitKey;

    @Schema(description = "是否启用客户端 IP 限流", example = "true")
    private Boolean rateLimitClientIpEnable;

    // ==================== 熔断器配置（扁平化字段）====================

    @Schema(description = "是否启用熔断器", example = "true")
    private Boolean circuitBreakerEnabled;

    @Schema(description = "熔断器失败阈值", example = "10")
    private Integer circuitBreakerFailureThreshold;

    @Schema(description = "熔断器超时时间（毫秒）", example = "120000")
    private Integer circuitBreakerTimeout;

    @Schema(description = "熔断器恢复阈值", example = "5")
    private Integer circuitBreakerSuccessThreshold;
}

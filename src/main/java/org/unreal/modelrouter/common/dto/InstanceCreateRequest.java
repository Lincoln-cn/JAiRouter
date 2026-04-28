package org.unreal.modelrouter.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 实例创建请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实例创建请求")
public class InstanceCreateRequest {

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

    @Schema(description = "请求头配置")
    private Map<String, String> headers;

    @Schema(description = "是否启用限流", example = "true")
    private Boolean rateLimitEnabled;

    @Schema(description = "限流算法", example = "token-bucket")
    private String rateLimitAlgorithm;

    @Schema(description = "限流容量", example = "50")
    private Integer rateLimitCapacity;

    @Schema(description = "限流速率", example = "5")
    private Integer rateLimitRate;

    @Schema(description = "限流范围", example = "instance")
    private String rateLimitScope;

    @Schema(description = "是否启用客户端 IP 限流", example = "false")
    private Boolean rateLimitClientIpEnable;
}

package org.unreal.modelrouter.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加黑名单请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddBlacklistRequest {

    @NotNull(message = "黑名单类型不能为空")
    private String blacklistType;

    @NotBlank(message = "目标值不能为空")
    private String targetValue;

    private String userId;
    private String reason;
    private String riskLevel;
    private Long expiresInSeconds;
    private String source;
    private String metadata;
}
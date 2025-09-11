package org.unreal.modelrouter.dto;

import lombok.Data;

@Data
public class TokenRevokeRequest {
    private String token;

    private String userId; // 可选，用于权限检查
    private String reason; // 可选，撤销原因
}

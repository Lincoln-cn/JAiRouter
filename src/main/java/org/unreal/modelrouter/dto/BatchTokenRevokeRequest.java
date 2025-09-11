package org.unreal.modelrouter.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchTokenRevokeRequest {
    private List<String> tokens;

    private String reason; // 可选，撤销原因
}

package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JWT 账户 DTO
 * v1.5.2: 用于替代 Map 传递数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtAccountDTO {

    private Long id;
    private String username;
    private List<String> roles;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

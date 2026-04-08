package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建 JWT 账户请求 DTO
 * v1.5.2: 用于替代 Map 传递数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJwtAccountRequest {

    private String username;
    private String password;
    private List<String> roles;
}

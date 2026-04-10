package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建 JWT 账户请求 DTO
 * v1.5.2: 用于替代 Map 传递数据
 * v1.5.4: 添加 enabled 字段支持创建时设置初始状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJwtAccountRequest {

    private String username;
    private String password;
    private List<String> roles;
    private Boolean enabled;
}

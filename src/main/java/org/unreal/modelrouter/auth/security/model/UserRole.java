package org.unreal.modelrouter.auth.security.model;

/**
 * 用户角色枚举
 * 定义系统中的用户角色级别
 */
public enum UserRole {
    /**
     * 管理员 - 拥有所有权限
     */
    ADMIN("admin", "管理员"),
    
    /**
     * 普通用户 - 只能管理自己的API Key
     */
    USER("user", "普通用户"),
    
    /**
     * 只读用户 - 只能查看，不能修改
     */
    VIEWER("viewer", "只读用户");

    private final String code;
    private final String displayName;

    UserRole(final String code, final String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据代码查找角色
     *
     * @param code 角色代码
     * @return 对应的角色，如果未找到则返回USER
     */
    public static UserRole fromCode(final String code) {
        if (code == null) {
            return USER;
        }
        for (UserRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        return USER;
    }

    /**
     * 检查是否为管理员
     *
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 检查是否有写权限
     *
     * @return 是否有写权限
     */
    public boolean hasWritePermission() {
        return this == ADMIN || this == USER;
    }
}

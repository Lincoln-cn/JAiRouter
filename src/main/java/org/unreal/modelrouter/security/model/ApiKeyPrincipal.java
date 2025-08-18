package org.unreal.modelrouter.security.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

/**
 * API Key主体对象
 * 实现Java Security的Principal接口
 * 包含API Key的详细信息
 */
@Getter
@RequiredArgsConstructor
public class ApiKeyPrincipal implements Principal {
    
    private final String keyId;
    private final String description;
    private final List<String> permissions;
    private final ApiKeyInfo apiKeyInfo;
    
    @Override
    public String getName() {
        return keyId;
    }
    
    /**
     * 检查是否具有指定权限
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * 检查是否具有管理员权限
     */
    public boolean isAdmin() {
        return hasPermission("admin");
    }
    
    /**
     * 检查是否具有读权限
     */
    public boolean canRead() {
        return hasPermission("read") || hasPermission("admin");
    }
    
    /**
     * 检查是否具有写权限
     */
    public boolean canWrite() {
        return hasPermission("write") || hasPermission("admin");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiKeyPrincipal that = (ApiKeyPrincipal) o;
        return Objects.equals(keyId, that.keyId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(keyId);
    }
    
    @Override
    public String toString() {
        return "ApiKeyPrincipal{" +
                "keyId='" + keyId + '\'' +
                ", description='" + description + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
package org.unreal.modelrouter.security.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JWT令牌主体对象
 * 实现Java Security的Principal接口
 * 包含JWT令牌的详细信息
 */
@Getter
@RequiredArgsConstructor
public class JwtPrincipal implements Principal {
    
    private final String subject;
    private final String issuer;
    private final List<String> roles;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiresAt;
    private final Map<String, Object> claims;
    
    @Override
    public String getName() {
        return subject;
    }
    
    /**
     * 检查是否具有指定角色
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    /**
     * 检查是否具有管理员角色
     */
    public boolean isAdmin() {
        return hasRole("admin") || hasRole("ADMIN");
    }
    
    /**
     * 检查是否具有用户角色
     */
    public boolean isUser() {
        return hasRole("user") || hasRole("USER");
    }
    
    /**
     * 检查令牌是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * 获取指定的声明值
     */
    public Object getClaim(String claimName) {
        return claims != null ? claims.get(claimName) : null;
    }
    
    /**
     * 获取字符串类型的声明值
     */
    public String getStringClaim(String claimName) {
        Object claim = getClaim(claimName);
        return claim != null ? claim.toString() : null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JwtPrincipal that = (JwtPrincipal) o;
        return Objects.equals(subject, that.subject) && 
               Objects.equals(issuer, that.issuer);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(subject, issuer);
    }
    
    @Override
    public String toString() {
        return "JwtPrincipal{" +
                "subject='" + subject + '\'' +
                ", issuer='" + issuer + '\'' +
                ", roles=" + roles +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
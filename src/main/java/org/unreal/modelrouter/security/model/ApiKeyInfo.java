package org.unreal.modelrouter.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API Key信息数据模型
 */
@Data
@Builder
public class ApiKeyInfo {
    
    /**
     * API Key唯一标识符
     */
    private String keyId;
    
    /**
     * API Key值（敏感信息，序列化时忽略）
     */
    @JsonIgnore
    private String keyValue;
    
    /**
     * API Key描述信息
     */
    private String description;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 权限列表
     */
    private List<String> permissions;
    
    /**
     * 元数据信息
     */
    private Map<String, Object> metadata;
    
    /**
     * 使用统计信息
     */
    private UsageStatistics usage;
    
    /**
     * 检查API Key是否已过期
     * @return 是否过期
     */
    @JsonProperty("expired")
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    // 用于反序列化expired字段的setter方法（无实际作用，仅用于兼容旧数据）
    @JsonProperty("expired")
    public void setExpired(boolean expired) {
        // 仅用于反序列化，实际值由expiresAt字段计算得出
    }
    
    /**
     * 检查API Key是否有效（启用且未过期）
     * @return 是否有效
     */
    @JsonIgnore
    public boolean isValid() {
        return enabled && !isExpired();
    }
    
    /**
     * 检查是否具有指定权限
     * @param permission 权限名称
     * @return 是否具有权限
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * 生成安全的API Key值
     * @param prefix API Key前缀（如"sk-"）
     * @param length API Key长度（不包括前缀）
     * @return 生成的API Key
     */
    public static String generateApiKey(String prefix, int length) {
        if (prefix == null) {
            prefix = "sk-";
        }
        if (length <= 0) {
            length = 32;
        }
        
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(prefix);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * 生成默认的API Key值（前缀为"sk-"，长度为32）
     * @return 生成的API Key
     */
    public static String generateApiKey() {
        return generateApiKey("sk-", 32);
    }
}
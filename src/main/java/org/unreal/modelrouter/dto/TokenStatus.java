package org.unreal.modelrouter.dto;

/**
 * JWT令牌状态枚举
 */
public enum TokenStatus {
    /**
     * 活跃状态 - 令牌有效且可以使用
     */
    ACTIVE,
    
    /**
     * 已撤销状态 - 令牌已被手动撤销
     */
    REVOKED,
    
    /**
     * 已过期状态 - 令牌已超过有效期
     */
    EXPIRED
}
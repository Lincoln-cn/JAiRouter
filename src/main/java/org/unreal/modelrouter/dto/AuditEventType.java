package org.unreal.modelrouter.dto;

/**
 * 审计事件类型枚举
 */
public enum AuditEventType {
    // JWT令牌相关事件
    JWT_TOKEN_ISSUED,       // JWT令牌颁发
    JWT_TOKEN_REFRESHED,    // JWT令牌刷新
    JWT_TOKEN_REVOKED,      // JWT令牌撤销
    JWT_TOKEN_VALIDATED,    // JWT令牌验证
    JWT_TOKEN_EXPIRED,      // JWT令牌过期
    
    // API Key相关事件
    API_KEY_CREATED,        // API Key创建
    API_KEY_USED,           // API Key使用
    API_KEY_REVOKED,        // API Key撤销
    API_KEY_EXPIRED,        // API Key过期
    API_KEY_UPDATED,        // API Key更新
    
    // 安全事件
    SECURITY_ALERT,         // 安全告警
    SUSPICIOUS_ACTIVITY,    // 可疑活动
    AUTHENTICATION_FAILED,  // 认证失败
    AUTHORIZATION_FAILED,   // 授权失败
    
    // 系统事件
    SYSTEM_CLEANUP,         // 系统清理
    SYSTEM_MAINTENANCE      // 系统维护
}
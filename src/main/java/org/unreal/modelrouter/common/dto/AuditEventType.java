package org.unreal.modelrouter.common.dto;

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
    API_KEY_ROTATED,        // API Key轮换
    API_KEY_BATCH_IMPORT,   // API Key批量导入

    // API Key校验失败细分（由 ApiKeyService.validateApiKey 判定）
    API_KEY_MISSING,           // 请求未携带 API Key
    API_KEY_INVALID,           // API Key 无效
    API_KEY_VALIDATION_FAILED, // API Key 校验失败（其他原因）

    // 安全事件
    SECURITY_ALERT,         // 安全告警
    SUSPICIOUS_ACTIVITY,    // 可疑活动
    AUTHENTICATION_FAILED,  // 认证失败
    AUTHORIZATION_FAILED,   // 授权失败
    BRUTE_FORCE_ATTEMPT,    // 暴力破解尝试
    DATA_SANITIZATION,      // 数据脱敏处理
    RESPONSE_SANITIZATION,  // 响应体脱敏
    FULL_CONTENT_ACCESS,    // 解密查看FULL级别调用记录

    // 调用历史配置
    RECORD_LEVEL_CHANGE,    // 记录级别变更

    // SLA 接入（issue #1174）：一次性令牌的签发/消费、刷新、吊销
    SLA_BOOTSTRAP_ISSUED,   // 签发 SLA 一次性接入令牌
    SLA_BOOTSTRAP_CONSUMED, // SLA 一次性接入令牌被消费（换取长期凭据）
    SLA_REFRESH,            // SLA 用刷新凭据换新访问令牌
    SLA_CREDENTIAL_REVOKED, // SLA 凭据被吊销

    // 系统事件
    SYSTEM_CLEANUP,         // 系统清理
    MONITORING_CONFIG_CHANGED, // 监控配置变更（含应急操作，含强制降级/熔断开合/清缓存）
    SYSTEM_MAINTENANCE      // 系统维护
}
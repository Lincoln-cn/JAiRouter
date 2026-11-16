-- V15：security_audit.event_type 的库级枚举补齐到 27 个
--
-- 背景：与 V7（#868）同一问题的延续。新增 AuditEventType.MONITORING_CONFIG_CHANGED
-- 用于记录控制台对监控配置的变更与应急操作（强制降级/熔断、清缓存、重置错误状态）。
-- 若不同步本迁移，写入这类事件会在 H2 的 enum 列上报 Value not permitted，
-- 而调用方（控制器里的审计 helper）吞掉异常 ⇒ 审计轨迹静默缺项。
--
-- 与 V7 相同手法：H2 的 ENUM 是列类型本身，重设类型即可扩展取值。
alter table security_audit
    alter column event_type set data type enum (
        'JWT_TOKEN_ISSUED', 'JWT_TOKEN_REFRESHED', 'JWT_TOKEN_REVOKED',
        'JWT_TOKEN_VALIDATED', 'JWT_TOKEN_EXPIRED', 'API_KEY_CREATED',
        'API_KEY_USED', 'API_KEY_REVOKED', 'API_KEY_EXPIRED',
        'API_KEY_UPDATED', 'API_KEY_ROTATED', 'API_KEY_BATCH_IMPORT',
        'API_KEY_MISSING', 'API_KEY_INVALID', 'API_KEY_VALIDATION_FAILED',
        'SECURITY_ALERT', 'SUSPICIOUS_ACTIVITY', 'AUTHENTICATION_FAILED',
        'AUTHORIZATION_FAILED', 'BRUTE_FORCE_ATTEMPT', 'DATA_SANITIZATION',
        'RESPONSE_SANITIZATION', 'FULL_CONTENT_ACCESS', 'RECORD_LEVEL_CHANGE',
        'SYSTEM_CLEANUP', 'MONITORING_CONFIG_CHANGED', 'SYSTEM_MAINTENANCE'
    );

-- V7：security_audit.event_type 的库级枚举补齐到 26 个（issue #868）
--
-- 现象：V1 建表时 event_type 只枚举了 16 个早期值，而 AuditEventType 后来扩到 26 个。
-- 写入 API_KEY_INVALID / RESPONSE_SANITIZATION / BRUTE_FORCE_ATTEMPT 这类事件时报
-- "Value not permitted for column ... [22030-232]"，调用方（审计监听器、脱敏过滤器、
-- 暴力破解守卫）都吞掉异常，表现为审计轨迹静默缺项，不阻断业务请求。
--
-- H2 的 ENUM 是列类型本身，直接重设类型即可扩展取值（已在 H2 2.3.232 上验证）。
alter table security_audit
    alter column event_type set data type enum (
        'JWT_TOKEN_ISSUED', 'JWT_TOKEN_REFRESHED', 'JWT_TOKEN_REVOKED', 'JWT_TOKEN_VALIDATED',
        'JWT_TOKEN_EXPIRED', 'API_KEY_CREATED', 'API_KEY_USED', 'API_KEY_REVOKED',
        'API_KEY_EXPIRED', 'API_KEY_UPDATED', 'API_KEY_ROTATED', 'API_KEY_BATCH_IMPORT',
        'API_KEY_MISSING', 'API_KEY_INVALID', 'API_KEY_VALIDATION_FAILED', 'SECURITY_ALERT',
        'SUSPICIOUS_ACTIVITY', 'AUTHENTICATION_FAILED', 'AUTHORIZATION_FAILED',
        'BRUTE_FORCE_ATTEMPT', 'DATA_SANITIZATION', 'RESPONSE_SANITIZATION',
        'FULL_CONTENT_ACCESS', 'RECORD_LEVEL_CHANGE', 'SYSTEM_CLEANUP', 'SYSTEM_MAINTENANCE'
    );

-- V7：security_audit.event_type 的库级约束补齐到 26 个（issue #868）
--
-- 现象与 H2 侧相同：V1 的 check 约束只有 16 个早期值，AuditEventType 已有 26 个，
-- 写入新事件类型时抛 check violation，调用方吞掉异常 ⇒ 审计轨迹静默缺项。
--
-- PostgreSQL 的约束是匿名 check（默认名 security_audit_event_type_check），先丢弃再重建。
alter table security_audit drop constraint if exists security_audit_event_type_check;

alter table security_audit add constraint security_audit_event_type_check check (event_type in (
    'JWT_TOKEN_ISSUED', 'JWT_TOKEN_REFRESHED', 'JWT_TOKEN_REVOKED', 'JWT_TOKEN_VALIDATED',
    'JWT_TOKEN_EXPIRED', 'API_KEY_CREATED', 'API_KEY_USED', 'API_KEY_REVOKED',
    'API_KEY_EXPIRED', 'API_KEY_UPDATED', 'API_KEY_ROTATED', 'API_KEY_BATCH_IMPORT',
    'API_KEY_MISSING', 'API_KEY_INVALID', 'API_KEY_VALIDATION_FAILED', 'SECURITY_ALERT',
    'SUSPICIOUS_ACTIVITY', 'AUTHENTICATION_FAILED', 'AUTHORIZATION_FAILED',
    'BRUTE_FORCE_ATTEMPT', 'DATA_SANITIZATION', 'RESPONSE_SANITIZATION',
    'FULL_CONTENT_ACCESS', 'RECORD_LEVEL_CHANGE', 'SYSTEM_CLEANUP', 'SYSTEM_MAINTENANCE'
));

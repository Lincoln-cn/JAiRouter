-- V20：SLA 凭据的可吊销性与生命周期（issue #1210 / #1213 / #1214）
--
-- 与 H2 侧同一份逻辑，见同版本 h2 迁移。PostgreSQL 的枚举是具名 check
-- （security_audit_event_type_check），先丢弃再重建。
alter table sla_bootstrap_tokens add column if not exists revoked_at timestamp(6);
alter table sla_refresh_tokens add column if not exists absolute_expires_at timestamp(6);

alter table security_audit drop constraint if exists security_audit_event_type_check;

alter table security_audit add constraint security_audit_event_type_check check (event_type in (
        'JWT_TOKEN_ISSUED', 'JWT_TOKEN_REFRESHED', 'JWT_TOKEN_REVOKED',
        'JWT_TOKEN_VALIDATED', 'JWT_TOKEN_EXPIRED', 'API_KEY_CREATED',
        'API_KEY_USED', 'API_KEY_REVOKED', 'API_KEY_EXPIRED',
        'API_KEY_UPDATED', 'API_KEY_ROTATED', 'API_KEY_BATCH_IMPORT',
        'API_KEY_MISSING', 'API_KEY_INVALID', 'API_KEY_VALIDATION_FAILED',
        'SECURITY_ALERT', 'SUSPICIOUS_ACTIVITY', 'AUTHENTICATION_FAILED',
        'AUTHORIZATION_FAILED', 'BRUTE_FORCE_ATTEMPT', 'DATA_SANITIZATION',
        'RESPONSE_SANITIZATION', 'FULL_CONTENT_ACCESS', 'RECORD_LEVEL_CHANGE',
        'SLA_BOOTSTRAP_ISSUED', 'SLA_BOOTSTRAP_CONSUMED', 'SLA_REFRESH',
        'SLA_CREDENTIAL_REVOKED', 'LOGIN_GUARD_CONFIG_CHANGED', 'LOGIN_GUARD_UNLOCKED',
        'SYSTEM_CLEANUP', 'MONITORING_CONFIG_CHANGED', 'SYSTEM_MAINTENANCE',
        'SLA_ACCOUNT_CREATED', 'SLA_ACCOUNT_DISABLED', 'SLA_ACCOUNT_ENABLED'
));

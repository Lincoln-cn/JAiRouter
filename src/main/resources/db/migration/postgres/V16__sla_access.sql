-- V16：SLA 接入（issue #1174）
--
-- 背景与 H2 侧相同（见同版本 h2 迁移）：三张表 + security_audit.event_type 约束补到 31 个。
-- PostgreSQL 的约束是具名 check（security_audit_event_type_check），先丢弃再重建。
create table if not exists sla_service_accounts (
    account_id varchar(64) primary key,
    name varchar(64) not null,
    role_template varchar(32) not null,
    enabled boolean not null,
    created_by varchar(64),
    created_at timestamp(6) not null,
    last_seen_at timestamp(6)
);

create unique index if not exists uk_sla_service_accounts_name on sla_service_accounts (name);

create table if not exists sla_bootstrap_tokens (
    token_id varchar(64) primary key,
    account_id varchar(64) not null,
    token_hash varchar(128) not null,
    expires_at timestamp(6) not null,
    used_at timestamp(6),
    allowed_ip varchar(64),
    created_by varchar(64),
    created_at timestamp(6) not null,
    version bigint not null
);

create index if not exists idx_sla_bootstrap_tokens_account on sla_bootstrap_tokens (account_id);
create unique index if not exists uk_sla_bootstrap_tokens_hash on sla_bootstrap_tokens (token_hash);

create table if not exists sla_refresh_tokens (
    token_id varchar(64) primary key,
    account_id varchar(64) not null,
    token_hash varchar(128) not null,
    issued_at timestamp(6) not null,
    expires_at timestamp(6) not null,
    revoked_at timestamp(6),
    last_used_at timestamp(6),
    client_ip varchar(64)
);

create index if not exists idx_sla_refresh_tokens_account on sla_refresh_tokens (account_id);
create unique index if not exists uk_sla_refresh_tokens_hash on sla_refresh_tokens (token_hash);

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
        'SLA_CREDENTIAL_REVOKED', 'SYSTEM_CLEANUP', 'MONITORING_CONFIG_CHANGED',
        'SYSTEM_MAINTENANCE'
));

-- V16：SLA 接入（issue #1174）
--
-- 三张表支撑「一次性接入令牌 → 长期刷新凭据」：
--   sla_service_accounts  外部巡检工具的运行主体（角色模板只有 SLA_READONLY / SLA_OPERATOR）
--   sla_bootstrap_tokens  一次性接入令牌（只存哈希、一次性、可绑 IP、默认 15 分钟）
--   sla_refresh_tokens    长期刷新凭据（只存哈希、可吊销、滚动续期）
--
-- 同时把 security_audit.event_type 的库级枚举补到 31 个：新增
-- SLA_BOOTSTRAP_ISSUED / SLA_BOOTSTRAP_CONSUMED / SLA_REFRESH / SLA_CREDENTIAL_REVOKED。
-- 手法与 V7、V15 相同（H2 的 ENUM 是列类型本身，重设类型即可扩展取值）；不补这一段，
-- 写这几类审计会报 Value not permitted，而调用方吞掉异常 ⇒ 审计轨迹静默缺项。
-- 唯一约束写在表内（constraint ... unique）而不是 create unique index：
-- 实体的 @UniqueConstraint 会生成 UNIQUE 约束，而 unique index 不进 H2 的
-- CONSTRAINTS 视图，两边 schema 比对会报「constraints 不一致」
-- （H2BaselineMatchesEntityModelTest）。
create table if not exists sla_service_accounts (
    account_id varchar(64) primary key,
    name varchar(64) not null,
    role_template varchar(32) not null,
    enabled boolean not null,
    created_by varchar(64),
    created_at timestamp(6) not null,
    last_seen_at timestamp(6),
    constraint uk_sla_service_accounts_name unique (name)
);

create table if not exists sla_bootstrap_tokens (
    token_id varchar(64) primary key,
    account_id varchar(64) not null,
    token_hash varchar(128) not null,
    expires_at timestamp(6) not null,
    used_at timestamp(6),
    allowed_ip varchar(64),
    created_by varchar(64),
    created_at timestamp(6) not null,
    version bigint not null,
    constraint uk_sla_bootstrap_tokens_hash unique (token_hash)
);

create index if not exists idx_sla_bootstrap_tokens_account on sla_bootstrap_tokens (account_id);

create table if not exists sla_refresh_tokens (
    token_id varchar(64) primary key,
    account_id varchar(64) not null,
    token_hash varchar(128) not null,
    issued_at timestamp(6) not null,
    expires_at timestamp(6) not null,
    revoked_at timestamp(6),
    last_used_at timestamp(6),
    client_ip varchar(64),
    constraint uk_sla_refresh_tokens_hash unique (token_hash)
);

create index if not exists idx_sla_refresh_tokens_account on sla_refresh_tokens (account_id);

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
        'SLA_BOOTSTRAP_ISSUED', 'SLA_BOOTSTRAP_CONSUMED', 'SLA_REFRESH',
        'SLA_CREDENTIAL_REVOKED', 'SYSTEM_CLEANUP', 'MONITORING_CONFIG_CHANGED',
        'SYSTEM_MAINTENANCE'
    );

-- V17：登录保护配置（issue #1176）
--
-- 背景：登录暴力破解频控的四个阈值原先只来自 @Value，改一次要改配置文件 + 重启；
-- 429 响应也不带剩余时间。这里把阈值做成「YAML 提供首次初始值、之后以数据库为准」
-- 的单行配置表（与 jwt_accounts 的口径一致，见 JpaDatabaseInitializer#initializeJwtAccounts）。
--
-- 为什么是单行表而不是键值表：四项属于同一个策略，必须一起生效；键值表能存出
-- 「只改了锁定时长、阈值还是旧的」这种中间态。config_id 固定为 'default'。
--
-- 同时把 security_audit.event_type 的库级枚举补到 33 个：新增
-- LOGIN_GUARD_CONFIG_CHANGED / LOGIN_GUARD_UNLOCKED。手法与 V7、V15、V16 相同
-- （H2 的 ENUM 是列类型本身，重设类型即可扩展取值）；不补这一段，写这两类审计会报
-- Value not permitted，而调用方吞掉异常 ⇒ 审计轨迹静默缺项。
create table if not exists security_login_guard_config (
    config_id varchar(32) primary key,
    max_failures_per_principal integer not null,
    max_failures_per_ip integer not null,
    window_minutes integer not null,
    lock_minutes integer not null,
    updated_by varchar(64),
    updated_at timestamp(6)
);

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
        'SLA_CREDENTIAL_REVOKED', 'LOGIN_GUARD_CONFIG_CHANGED', 'LOGIN_GUARD_UNLOCKED',
        'SYSTEM_CLEANUP', 'MONITORING_CONFIG_CHANGED', 'SYSTEM_MAINTENANCE'
    );

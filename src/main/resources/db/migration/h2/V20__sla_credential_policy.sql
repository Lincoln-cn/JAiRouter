-- V20：SLA 凭据的可吊销性与生命周期（issue #1210 / #1213 / #1214）
--
-- 三件事：
--   1. sla_bootstrap_tokens 补 revoked_at —— 吊销账号时未消费的一次性令牌必须一并作废，
--      否则吊销之后 15 分钟内旧令牌还能换出一整套新凭据（#1210）。不删行，保留审计痕迹；
--   2. sla_refresh_tokens 补 absolute_expires_at —— 滚动续期此前没有上限，一直在用的凭据
--      等于永不过期；补一列绝对有效期，越过即要求重新签发接入令牌（#1214）；
--   3. security_audit.event_type 的库级枚举补到 36 个：新增
--      SLA_ACCOUNT_CREATED / SLA_ACCOUNT_DISABLED / SLA_ACCOUNT_ENABLED（#1220）。
--      手法与 V7、V15、V16、V17 相同：H2 的 ENUM 是列类型本身，重设类型即可扩展取值；
--      不补这一段，写这几类审计会报 Value not permitted，而调用方吞掉异常 ⇒ 审计轨迹静默缺项。
--
-- 历史行：absolute_expires_at 为空表示「按 issued_at + 365 天兜底」，由服务层在下次刷新时回填。
alter table sla_bootstrap_tokens add column if not exists revoked_at timestamp(6);
alter table sla_refresh_tokens add column if not exists absolute_expires_at timestamp(6);

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
        'SYSTEM_CLEANUP', 'MONITORING_CONFIG_CHANGED', 'SYSTEM_MAINTENANCE',
        'SLA_ACCOUNT_CREATED', 'SLA_ACCOUNT_DISABLED', 'SLA_ACCOUNT_ENABLED'
    );

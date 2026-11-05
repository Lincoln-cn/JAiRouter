-- 可逆脱敏访问审计（#987）
-- 记录「哪些请求还原了 PII、还原了几处、由谁发起」，
-- 供安全审计与事后追溯使用。不存任何原值。
CREATE TABLE IF NOT EXISTS pii_access_audit (
    id BIGSERIAL PRIMARY KEY,
    scope_id VARCHAR(64) NOT NULL,
    caller VARCHAR(128),
    path VARCHAR(512),
    restored_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pii_audit_scope ON pii_access_audit (scope_id);
CREATE INDEX IF NOT EXISTS idx_pii_audit_created_at ON pii_access_audit (created_at);

-- 可逆脱敏的占位符仓库（#987）
--
-- 与 h2/V9__pii_token_vault.sql 语义一致：存「占位符 → 原文密文」的映射。
-- 密钥不落库，密文由 RecordContentCipher（AES-256-GCM）生成。

CREATE TABLE IF NOT EXISTS pii_token_vault (
    id            BIGSERIAL PRIMARY KEY,
    scope_id      VARCHAR(64)  NOT NULL,
    placeholder   VARCHAR(64)  NOT NULL,
    ciphertext    VARCHAR(4096) NOT NULL,
    rule_name     VARCHAR(128),
    created_at    TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_pii_vault_scope_placeholder UNIQUE (scope_id, placeholder)
);

CREATE INDEX IF NOT EXISTS idx_pii_vault_expires_at ON pii_token_vault (expires_at);

-- 配置数据表
-- 使用引号确保表名和列名保持小写
CREATE TABLE IF NOT EXISTS "config_data" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "config_key" VARCHAR(255) NOT NULL,
    "config_value" TEXT NOT NULL,
    "version" INT NOT NULL,
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "is_latest" BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT "uk_config_key_version" UNIQUE ("config_key", "version")
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS "idx_config_key" ON "config_data"("config_key");
CREATE INDEX IF NOT EXISTS "idx_is_latest" ON "config_data"("is_latest");
CREATE INDEX IF NOT EXISTS "idx_config_key_latest" ON "config_data"("config_key", "is_latest");

-- 安全审计表
CREATE TABLE IF NOT EXISTS "security_audit" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "event_id" VARCHAR(255) NOT NULL UNIQUE,
    "event_type" VARCHAR(100) NOT NULL,
    "user_id" VARCHAR(255),
    "client_ip" VARCHAR(50),
    "user_agent" VARCHAR(500),
    "timestamp" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resource" VARCHAR(500),
    "action" VARCHAR(100),
    "success" BOOLEAN NOT NULL DEFAULT TRUE,
    "failure_reason" VARCHAR(1000),
    "additional_data" TEXT,
    "request_id" VARCHAR(255),
    "session_id" VARCHAR(255)
);

-- 安全审计索引
CREATE INDEX IF NOT EXISTS "idx_audit_event_id" ON "security_audit"("event_id");
CREATE INDEX IF NOT EXISTS "idx_audit_timestamp" ON "security_audit"("timestamp");
CREATE INDEX IF NOT EXISTS "idx_audit_user_id" ON "security_audit"("user_id");
CREATE INDEX IF NOT EXISTS "idx_audit_event_type" ON "security_audit"("event_type");
CREATE INDEX IF NOT EXISTS "idx_audit_client_ip" ON "security_audit"("client_ip");
CREATE INDEX IF NOT EXISTS "idx_audit_success" ON "security_audit"("success");

-- API Key 表
CREATE TABLE IF NOT EXISTS "api_keys" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "key_id" VARCHAR(255) NOT NULL UNIQUE,
    "key_value" VARCHAR(500) NOT NULL UNIQUE,
    "description" VARCHAR(1000),
    "permissions" TEXT,
    "expires_at" TIMESTAMP,
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "enabled" BOOLEAN NOT NULL DEFAULT TRUE,
    "metadata" TEXT,
    "usage_statistics" TEXT
);

-- API Key 索引
CREATE INDEX IF NOT EXISTS "idx_apikey_key_id" ON "api_keys"("key_id");
CREATE INDEX IF NOT EXISTS "idx_apikey_key_value" ON "api_keys"("key_value");
CREATE INDEX IF NOT EXISTS "idx_apikey_enabled" ON "api_keys"("enabled");
CREATE INDEX IF NOT EXISTS "idx_apikey_expires_at" ON "api_keys"("expires_at");

-- JWT 账户表
CREATE TABLE IF NOT EXISTS "jwt_accounts" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "username" VARCHAR(255) NOT NULL UNIQUE,
    "password" VARCHAR(500) NOT NULL,
    "roles" TEXT NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT TRUE,
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- JWT 账户索引
CREATE INDEX IF NOT EXISTS "idx_jwt_username" ON "jwt_accounts"("username");
CREATE INDEX IF NOT EXISTS "idx_jwt_enabled" ON "jwt_accounts"("enabled");

-- JWT 黑名单表
CREATE TABLE IF NOT EXISTS "jwt_blacklist" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "token_hash" VARCHAR(255) NOT NULL UNIQUE,
    "user_id" VARCHAR(255),
    "revoked_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expires_at" TIMESTAMP NOT NULL,
    "reason" VARCHAR(1000),
    "revoked_by" VARCHAR(255),
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- JWT 黑名单索引
CREATE INDEX IF NOT EXISTS "idx_blacklist_token_hash" ON "jwt_blacklist"("token_hash");
CREATE INDEX IF NOT EXISTS "idx_blacklist_user_id" ON "jwt_blacklist"("user_id");
CREATE INDEX IF NOT EXISTS "idx_blacklist_expires_at" ON "jwt_blacklist"("expires_at");
CREATE INDEX IF NOT EXISTS "idx_blacklist_revoked_at" ON "jwt_blacklist"("revoked_at");

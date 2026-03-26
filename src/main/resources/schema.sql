-- ========================================
-- 配置管理表结构（完全关系型）
-- ========================================

-- 配置主表 - 存储配置的整体元信息和版本控制
CREATE TABLE IF NOT EXISTS `config_main` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(255) NOT NULL UNIQUE,
    `current_version` INT NOT NULL DEFAULT 0,
    `initial_version` INT NOT NULL DEFAULT 1,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` VARCHAR(255),
    `updated_by` VARCHAR(255),
    `description` VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS `idx_config_main_key` ON `config_main`(`config_key`);

-- 配置版本表 - 存储每个版本的完整配置快照（JSON 格式）
CREATE TABLE IF NOT EXISTS `config_version` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(255) NOT NULL,
    `version` INT NOT NULL,
    `config_data` TEXT NOT NULL, -- 存储 JSON 格式的配置数据
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` VARCHAR(255),
    `description` VARCHAR(1000),
    `change_type` VARCHAR(50), -- CREATE, UPDATE, DELETE, ROLLBACK
    `is_current` BOOLEAN NOT NULL DEFAULT FALSE,
    `archive_path` VARCHAR(500), -- 归档文件路径
    `is_archived` BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY `uk_config_key_version` (`config_key`, `version`)
);

CREATE INDEX IF NOT EXISTS `idx_config_version_key_current` ON `config_version`(`config_key`, `is_current`);
CREATE INDEX IF NOT EXISTS `idx_config_version_created` ON `config_version`(`created_at`);
CREATE INDEX IF NOT EXISTS `idx_config_version_archived` ON `config_version`(`is_archived`);

-- 服务配置表 - 存储每个服务的配置
CREATE TABLE IF NOT EXISTS `service_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(255) NOT NULL,
    `service_type` VARCHAR(100) NOT NULL, -- chat, embedding, rerank, tts, stt, imgGen, imgEdit
    `load_balance_type` VARCHAR(50), -- random, round-robin, least-connections, ip-hash
    `load_balance_hash_algorithm` VARCHAR(50) DEFAULT 'md5',
    `adapter` VARCHAR(100),
    `rate_limit_enabled` BOOLEAN DEFAULT TRUE,
    `rate_limit_algorithm` VARCHAR(50) DEFAULT 'token-bucket',
    `rate_limit_capacity` INT DEFAULT 1000,
    `rate_limit_rate` INT DEFAULT 100,
    `rate_limit_scope` VARCHAR(50) DEFAULT 'service',
    `rate_limit_client_ip_enable` BOOLEAN DEFAULT TRUE,
    `circuit_breaker_enabled` BOOLEAN DEFAULT TRUE,
    `circuit_breaker_failure_threshold` INT DEFAULT 5,
    `circuit_breaker_timeout` INT DEFAULT 60000,
    `circuit_breaker_success_threshold` INT DEFAULT 2,
    `fallback_enabled` BOOLEAN DEFAULT TRUE,
    `fallback_strategy` VARCHAR(50) DEFAULT 'default',
    `fallback_cache_size` INT,
    `fallback_cache_ttl` INT,
    `version` INT NOT NULL,
    `is_latest` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_config_service_version` (`config_key`, `service_type`, `version`)
);

CREATE INDEX IF NOT EXISTS `idx_service_config_key_latest` ON `service_config`(`config_key`, `service_type`, `is_latest`);
CREATE INDEX IF NOT EXISTS `idx_service_config_type` ON `service_config`(`service_type`);

-- 服务实例表 - 存储每个服务的具体实例信息
CREATE TABLE IF NOT EXISTS `service_instance` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service_config_id` BIGINT NOT NULL,
    `instance_name` VARCHAR(255) NOT NULL,
    `base_url` VARCHAR(500) NOT NULL,
    `path` VARCHAR(500),
    `weight` INT NOT NULL DEFAULT 1,
    `headers` TEXT, -- 存储认证信息等 headers(JSON 格式)
    `rate_limit_enabled` BOOLEAN DEFAULT TRUE,
    `rate_limit_algorithm` VARCHAR(50) DEFAULT 'token-bucket',
    `rate_limit_capacity` INT,
    `rate_limit_rate` INT,
    `rate_limit_scope` VARCHAR(50) DEFAULT 'instance',
    `status` VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE, ERROR
    `last_health_check` TIMESTAMP,
    `health_status` VARCHAR(50) DEFAULT 'UNKNOWN', -- HEALTHY, UNHEALTHY, UNKNOWN
    `error_message` TEXT,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_service_config` FOREIGN KEY (`service_config_id`) 
        REFERENCES `service_config`(`id`) ON DELETE CASCADE
);

-- 服务实例表索引
CREATE INDEX IF NOT EXISTS `idx_service_config_id` ON `service_instance`(`service_config_id`);
CREATE INDEX IF NOT EXISTS `idx_instance_status` ON `service_instance`(`status`);
CREATE INDEX IF NOT EXISTS `idx_instance_health` ON `service_instance`(`health_status`);

-- 配置变更历史表 - 记录所有配置变更操作（审计日志）
CREATE TABLE IF NOT EXISTS `config_change_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(255) NOT NULL,
    `operation_type` VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE, APPLY_VERSION
    `target_type` VARCHAR(50), -- CONFIG, SERVICE, INSTANCE
    `target_id` VARCHAR(255),
    `old_value` TEXT, -- JSON 格式
    `new_value` TEXT, -- JSON 格式
    `changed_by` VARCHAR(255),
    `changed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `description` VARCHAR(1000),
    `request_id` VARCHAR(255),
    `client_ip` VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS `idx_config_change_key_time` ON `config_change_history`(`config_key`, `changed_at`);
CREATE INDEX IF NOT EXISTS `idx_config_change_operation` ON `config_change_history`(`operation_type`);
CREATE INDEX IF NOT EXISTS `idx_config_change_user` ON `config_change_history`(`changed_by`);

-- 配置归档表 - 记录归档文件信息
CREATE TABLE IF NOT EXISTS `config_archive` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(255) NOT NULL,
    `archive_path` VARCHAR(500) NOT NULL,
    `archive_type` VARCHAR(50) NOT NULL DEFAULT 'ZIP', -- ZIP, TAR.GZ
    `version_range_start` INT NOT NULL,
    `version_range_end` INT NOT NULL,
    `archived_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `archived_by` VARCHAR(255),
    `file_size_bytes` BIGINT,
    `checksum` VARCHAR(100), -- SHA-256 校验和
    `retention_days` INT DEFAULT 365,
    `expiry_date` TIMESTAMP,
    `status` VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE, EXPIRED, DELETED
);

-- 配置归档表索引
CREATE INDEX IF NOT EXISTS `idx_config_archive_key` ON `config_archive`(`config_key`);
CREATE INDEX IF NOT EXISTS `idx_config_archive_status` ON `config_archive`(`status`);
CREATE INDEX IF NOT EXISTS `idx_config_archive_expiry` ON `config_archive`(`expiry_date`);

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

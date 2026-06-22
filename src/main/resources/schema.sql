-- ========================================
-- 配置管理表结构（完全关系型）
-- ========================================

-- 配置数据表 - 存储配置的简化版本（用于 ConfigEntity）
CREATE TABLE IF NOT EXISTS config_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_latest BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_config_data_key ON config_data(config_key);
CREATE INDEX IF NOT EXISTS idx_config_data_latest ON config_data(config_key, is_latest);

-- 配置主表 - 存储配置的整体元信息和版本控制
CREATE TABLE IF NOT EXISTS config_main (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    current_version INT NOT NULL DEFAULT 0,
    initial_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    description VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_config_main_key ON config_main(config_key);

-- 配置版本表 - 存储每个版本的完整配置快照（JSON 格式）
CREATE TABLE IF NOT EXISTS config_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    config_data TEXT NOT NULL, -- 存储 JSON 格式的配置数据
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    description VARCHAR(1000),
    change_type VARCHAR(50), -- CREATE, UPDATE, DELETE, ROLLBACK
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    archive_path VARCHAR(500), -- 归档文件路径
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_config_key_version (config_key, version)
);

CREATE INDEX IF NOT EXISTS idx_config_version_key_current ON config_version(config_key, is_current);
CREATE INDEX IF NOT EXISTS idx_config_version_created ON config_version(created_at);
CREATE INDEX IF NOT EXISTS idx_config_version_archived ON config_version(is_archived);

-- 服务配置表 - 存储每个服务的配置
CREATE TABLE IF NOT EXISTS service_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    service_type VARCHAR(100) NOT NULL, -- chat, embedding, rerank, tts, stt, imgGen, imgEdit
    load_balance_type VARCHAR(50), -- random, round-robin, least-connections, ip-hash
    load_balance_hash_algorithm VARCHAR(50) DEFAULT 'md5',
    adapter VARCHAR(100),
    rate_limit_enabled BOOLEAN DEFAULT TRUE,
    rate_limit_algorithm VARCHAR(50) DEFAULT 'token-bucket',
    rate_limit_capacity INT DEFAULT 1000,
    rate_limit_rate INT DEFAULT 100,
    rate_limit_scope VARCHAR(50) DEFAULT 'service',
    rate_limit_client_ip_enable BOOLEAN DEFAULT TRUE,
    circuit_breaker_enabled BOOLEAN DEFAULT TRUE,
    circuit_breaker_failure_threshold INT DEFAULT 5,
    circuit_breaker_timeout INT DEFAULT 60000,
    circuit_breaker_success_threshold INT DEFAULT 2,
    fallback_enabled BOOLEAN DEFAULT TRUE,
    fallback_strategy VARCHAR(50) DEFAULT 'default',
    fallback_cache_size INT,
    fallback_cache_ttl INT,
    version INT NOT NULL,
    is_latest BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_service_version (config_key, service_type, version)
);

CREATE INDEX IF NOT EXISTS idx_service_config_key_latest ON service_config(config_key, service_type, is_latest);
CREATE INDEX IF NOT EXISTS idx_service_config_type ON service_config(service_type);

-- 服务实例表 - 存储每个服务的具体实例信息
CREATE TABLE IF NOT EXISTS service_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_config_id BIGINT NOT NULL,
    instance_name VARCHAR(255) NOT NULL,
    instance_id VARCHAR(255), -- UUID格式的唯一标识符 (v1.7.1)
    base_url VARCHAR(500) NOT NULL,
    path VARCHAR(500),
    weight INT NOT NULL DEFAULT 1,
    adapter VARCHAR(255), -- v1.7.1: 实例级别适配器配置
    headers JSON, -- v1.7.1: 自定义请求头配置（JSON 格式）
    -- 限流器配置字段
    rate_limit_enabled BOOLEAN DEFAULT TRUE,
    rate_limit_algorithm VARCHAR(50) DEFAULT 'token-bucket',
    rate_limit_capacity INT,
    rate_limit_rate INT,
    rate_limit_scope VARCHAR(50) DEFAULT 'instance',
    rate_limit_key VARCHAR(255),
    rate_limit_client_ip_enable BOOLEAN DEFAULT FALSE,
    -- 熔断器配置字段
    circuit_breaker_enabled BOOLEAN DEFAULT FALSE,
    circuit_breaker_failure_threshold INT DEFAULT 5,
    circuit_breaker_timeout INT DEFAULT 60000,
    circuit_breaker_success_threshold INT DEFAULT 2,
    -- 状态字段
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_health_check TIMESTAMP,
    health_status VARCHAR(50) DEFAULT 'UNKNOWN',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_config FOREIGN KEY (service_config_id)
        REFERENCES service_config(id) ON DELETE CASCADE
);

-- 服务实例表索引
CREATE INDEX IF NOT EXISTS idx_service_config_id ON service_instance(service_config_id);
CREATE INDEX IF NOT EXISTS idx_instance_status ON service_instance(status);
CREATE INDEX IF NOT EXISTS idx_instance_health ON service_instance(health_status);
CREATE INDEX IF NOT EXISTS idx_instance_instance_id ON service_instance(instance_id); -- v1.7.1

-- 配置变更历史表 - 记录所有配置变更操作（审计日志）
CREATE TABLE IF NOT EXISTS config_change_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    operation_type VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE, APPLY_VERSION
    target_type VARCHAR(50), -- CONFIG, SERVICE, INSTANCE
    target_id VARCHAR(255),
    old_value TEXT, -- JSON 格式
    new_value TEXT, -- JSON 格式
    changed_by VARCHAR(255),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(1000),
    request_id VARCHAR(255),
    client_ip VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_config_change_key_time ON config_change_history(config_key, changed_at);
CREATE INDEX IF NOT EXISTS idx_config_change_operation ON config_change_history(operation_type);
CREATE INDEX IF NOT EXISTS idx_config_change_user ON config_change_history(changed_by);

-- 配置归档表 - 记录归档文件信息
CREATE TABLE IF NOT EXISTS config_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    archive_path VARCHAR(500) NOT NULL,
    archive_type VARCHAR(50) NOT NULL DEFAULT 'ZIP', -- ZIP, TAR.GZ
    version_range_start INT NOT NULL,
    version_range_end INT NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_by VARCHAR(255),
    file_size_bytes BIGINT,
    checksum VARCHAR(100), -- SHA-256 校验和
    retention_days INT DEFAULT 365,
    expiry_date TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE, EXPIRED, DELETED
);

-- 配置归档表索引
CREATE INDEX IF NOT EXISTS idx_config_archive_key ON config_archive(config_key);
CREATE INDEX IF NOT EXISTS idx_config_archive_status ON config_archive(status);
CREATE INDEX IF NOT EXISTS idx_config_archive_expiry ON config_archive(expiry_date);

-- 安全审计表（扩展版）
CREATE TABLE IF NOT EXISTS "security_audit" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "event_id" VARCHAR(255) NOT NULL UNIQUE,
    "event_type" VARCHAR(100) NOT NULL,
    "event_category" VARCHAR(50),              -- JWT_TOKEN, API_KEY, SECURITY, AUTH, SYSTEM
    "user_id" VARCHAR(255),
    "resource_id" VARCHAR(255),                -- 令牌ID或API Key ID
    "client_ip" VARCHAR(50),
    "user_agent" VARCHAR(500),
    "timestamp" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resource" VARCHAR(500),
    "action" VARCHAR(100),
    "details" VARCHAR(1000),                   -- 详细描述
    "success" BOOLEAN NOT NULL DEFAULT TRUE,
    "failure_reason" VARCHAR(1000),
    "metadata" TEXT,                           -- JSON格式的额外元数据
    "request_id" VARCHAR(255),
    "session_id" VARCHAR(255),
    "geo_location" VARCHAR(100),               -- IP地理位置
    "device_info" VARCHAR(500),                -- 设备信息
    "risk_level" VARCHAR(20) DEFAULT 'LOW',    -- LOW, MEDIUM, HIGH, CRITICAL
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 安全审计索引
CREATE INDEX IF NOT EXISTS "idx_audit_event_id" ON "security_audit"("event_id");
CREATE INDEX IF NOT EXISTS "idx_audit_timestamp" ON "security_audit"("timestamp");
CREATE INDEX IF NOT EXISTS "idx_audit_user_id" ON "security_audit"("user_id");
CREATE INDEX IF NOT EXISTS "idx_audit_event_type" ON "security_audit"("event_type");
CREATE INDEX IF NOT EXISTS "idx_audit_client_ip" ON "security_audit"("client_ip");
CREATE INDEX IF NOT EXISTS "idx_audit_success" ON "security_audit"("success");
CREATE INDEX IF NOT EXISTS "idx_audit_category" ON "security_audit"("event_category");
CREATE INDEX IF NOT EXISTS "idx_audit_resource_id" ON "security_audit"("resource_id");
CREATE INDEX IF NOT EXISTS "idx_audit_risk_level" ON "security_audit"("risk_level");

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

-- JWT 黑名单表 (旧表，保持兼容)
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

-- 统一安全黑名单表
CREATE TABLE IF NOT EXISTS "security_blacklist" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "blacklist_type" VARCHAR(20) NOT NULL,
    "target_value" VARCHAR(500) NOT NULL,
    "target_hash" VARCHAR(255),
    "user_id" VARCHAR(255),
    "reason" VARCHAR(1000),
    "risk_level" VARCHAR(20),
    "added_by" VARCHAR(255),
    "added_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expires_at" TIMESTAMP NULL,
    "status" VARCHAR(20) NOT NULL,
    "source" VARCHAR(50),
    "metadata" VARCHAR(2000),
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY "uk_blacklist_type_value" ("blacklist_type", "target_value")
);

CREATE INDEX IF NOT EXISTS "idx_security_blacklist_type" ON "security_blacklist"("blacklist_type");
CREATE INDEX IF NOT EXISTS "idx_security_blacklist_target_hash" ON "security_blacklist"("target_hash");
CREATE INDEX IF NOT EXISTS "idx_security_blacklist_status" ON "security_blacklist"("status");
CREATE INDEX IF NOT EXISTS "idx_security_blacklist_expires_at" ON "security_blacklist"("expires_at");

-- ========================================
-- 异常管理表结构（v1.9.1 新增）
-- ========================================

-- 异常事件表 - 记录所有异常事件的详细信息
CREATE TABLE IF NOT EXISTS "exception_events" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "event_id" VARCHAR(255) NOT NULL UNIQUE,
    "exception_type" VARCHAR(500) NOT NULL,
    "exception_message" VARCHAR(1000),
    "sanitized_message" VARCHAR(1000),
    "sanitized_stack_trace" TEXT,
    "operation" VARCHAR(255) NOT NULL,
    "error_code" VARCHAR(100),
    "error_category" VARCHAR(50),
    "http_status" VARCHAR(10),
    "trace_id" VARCHAR(100),
    "span_id" VARCHAR(100),
    "request_id" VARCHAR(255),
    "client_ip" VARCHAR(50),
    "user_agent" VARCHAR(500),
    "service_name" VARCHAR(100),
    "method_name" VARCHAR(255),
    "class_name" VARCHAR(500),
    "line_number" INT,
    -- 统计字段
    "occurrence_count" BIGINT NOT NULL DEFAULT 1,
    "first_occurrence" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "last_occurrence" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 元数据（JSON 格式）
    "metadata" TEXT,
    -- 时间字段
    "occurred_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "is_aggregated" BOOLEAN NOT NULL DEFAULT FALSE
);

-- 异常事件表索引
CREATE INDEX IF NOT EXISTS "idx_exception_event_id" ON "exception_events"("event_id");
CREATE INDEX IF NOT EXISTS "idx_exception_type" ON "exception_events"("exception_type");
CREATE INDEX IF NOT EXISTS "idx_exception_occurred_at" ON "exception_events"("occurred_at");
CREATE INDEX IF NOT EXISTS "idx_exception_operation" ON "exception_events"("operation");
CREATE INDEX IF NOT EXISTS "idx_exception_error_code" ON "exception_events"("error_code");
CREATE INDEX IF NOT EXISTS "idx_exception_error_category" ON "exception_events"("error_category");
CREATE INDEX IF NOT EXISTS "idx_exception_trace_id" ON "exception_events"("trace_id");
CREATE INDEX IF NOT EXISTS "idx_exception_aggregated" ON "exception_events"("is_aggregated");
CREATE INDEX IF NOT EXISTS "idx_exception_last_occurrence" ON "exception_events"("last_occurrence");

-- 异常统计小时表 - 按小时聚合异常统计信息
CREATE TABLE IF NOT EXISTS "exception_stats_hourly" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "hour_timestamp" TIMESTAMP NOT NULL,
    "exception_type" VARCHAR(500) NOT NULL,
    "error_code" VARCHAR(100),
    "error_category" VARCHAR(50),
    "operation" VARCHAR(255),
    "service_name" VARCHAR(100),
    -- 统计字段
    "total_count" BIGINT NOT NULL DEFAULT 0,
    "success_count" BIGINT NOT NULL DEFAULT 0,
    "failure_count" BIGINT NOT NULL DEFAULT 0,
    "unique_trace_ids" BIGINT NOT NULL DEFAULT 0,
    "unique_client_ips" BIGINT NOT NULL DEFAULT 0,
    -- 时间字段
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY "uk_exception_stats_hour" ("hour_timestamp", "exception_type", "error_code", "operation")
);

-- 异常统计小时表索引
CREATE INDEX IF NOT EXISTS "idx_exception_stats_hour_ts" ON "exception_stats_hourly"("hour_timestamp");
CREATE INDEX IF NOT EXISTS "idx_exception_stats_hour_type" ON "exception_stats_hourly"("exception_type");
CREATE INDEX IF NOT EXISTS "idx_exception_stats_hour_category" ON "exception_stats_hourly"("error_category");
CREATE INDEX IF NOT EXISTS "idx_exception_stats_hour_operation" ON "exception_stats_hourly"("operation");

-- ========================================
-- Token 使用量统计表
-- ========================================
CREATE TABLE IF NOT EXISTS token_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 追踪信息
    trace_id VARCHAR(100),
    -- 服务信息
    service_type VARCHAR(50) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    provider VARCHAR(100),
    instance_name VARCHAR(255),
    instance_url VARCHAR(500),
    -- Token 使用量
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    -- 认证信息
    api_key_id VARCHAR(255),
    user_id VARCHAR(255),
    -- 请求信息
    client_ip VARCHAR(50),
    is_success BOOLEAN,
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    response_time_ms BIGINT,
    -- 时间信息
    occurred_at TIMESTAMP NOT NULL,
    day_of_week INT, -- 0-6, 0=周日
    week_of_year INT, -- ISO-8601 周数
    month_num INT, -- 1-12 (避免与 H2 保留字冲突)
    year_num INT, -- 年份 (避免与 H2 保留字冲突)
    usage_date VARCHAR(10), -- YYYY-MM-DD
    hour_num INT, -- 0-23 (避免与 H2 保留字冲突)
    -- 元数据
    metadata TEXT,
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Token 使用量表索引
CREATE INDEX IF NOT EXISTS idx_token_usage_model ON token_usage(model_name);
CREATE INDEX IF NOT EXISTS idx_token_usage_service_type ON token_usage(service_type);
CREATE INDEX IF NOT EXISTS idx_token_usage_occurred_at ON token_usage(occurred_at);
CREATE INDEX IF NOT EXISTS idx_token_usage_api_key ON token_usage(api_key_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_trace_id ON token_usage(trace_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_user ON token_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_date ON token_usage(usage_date);
CREATE INDEX IF NOT EXISTS idx_token_usage_week ON token_usage(week_of_year, year_num);
CREATE INDEX IF NOT EXISTS idx_token_usage_month ON token_usage(month_num, year_num);
CREATE INDEX IF NOT EXISTS idx_token_usage_hour ON token_usage(hour_num);

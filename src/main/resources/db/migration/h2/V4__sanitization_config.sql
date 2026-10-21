-- ========================================
-- V4：脱敏配置持久化（H2 路径，v3.4.0 TA2）
-- ========================================
-- 背景：控制台「安全 → PII 脱敏管理」保存的热更新此前只改内存；重启后
-- config/auth/sanitization.yml 重新加载，管理员保存的配置与其派生规则静默回退。
--
-- 本表只存**配置快照**，不存逐条规则 —— 规则由 SanitizationConfigStore 装载出的配置
-- 确定性派生（敏感词 → SENSITIVE_WORD、pii-patterns → PII_PATTERN，规则 ID 从 1 顺序生成），
-- 所以配置一致即可保证重启后规则集与 ID 一致。
--
-- 形状：一行一个 scope（request / response），主键就是 config_scope，天然「每个 scope 最多一行」，
-- 不用自增主键，也就不存在重启后 ID 复用。
-- 两个 scope 字段集不同，差异列可空：
--   * whitelist_users        仅 request 有
--   * preserve_json_structure 仅 response 有
-- 列表列用 TEXT 存 JSON 数组文本（由 SanitizationConfigStore 用 ObjectMapper 序列化）。
--
-- 与 postgres/V4__sanitization_config.sql 同一语义、同一列集合与类型。
--
-- 幂等性：IF NOT EXISTS，空库启动与重复执行都安全。
-- 回滚：DROP TABLE IF EXISTS sanitization_config;

create table if not exists sanitization_config (
    config_scope varchar(16) not null,
    enabled boolean not null,
    sensitive_words TEXT not null,
    pii_patterns TEXT not null,
    masking_char varchar(5) not null,
    whitelist_users TEXT,
    preserve_json_structure boolean,
    log_sanitization boolean not null,
    fail_on_error boolean not null,
    updated_at timestamp(6) not null,
    updated_by varchar(255),
    primary key (config_scope)
);

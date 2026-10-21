-- ========================================
-- V4：脱敏配置持久化（PostgreSQL 路径，v3.4.0 TA2）
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
-- 与 h2/V4__sanitization_config.sql 同一语义：本表没有自增列、没有 jsonb、没有序列，
-- PG 与 H2 的物理类型在此表上完全一致（varchar / boolean / TEXT / timestamp(6)），
-- 但仍按方言分目录维护，原因见文件末注释。
--
-- 幂等性：CREATE TABLE IF NOT EXISTS 要求 PG >= 9.6（项目支持的最低版本之上），
-- 空库启动与重复执行都安全。
-- 回滚：DROP TABLE IF EXISTS sanitization_config;

CREATE TABLE IF NOT EXISTS sanitization_config (
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

-- 为什么两个方言各留一份（内容目前逐字相同）：
--   1) Flyway 的 location 由方言决定（db/migration/postgres 或 db/migration/h2），
--      缺一侧会让该方言的启动直接失败；
--   2) 两份脚本的校验和是各自独立记录的 —— 共用一份会让「只改 H2 侧类型」变得不可能；
--   3) 列类型一旦需要方言特化（例如将来把列表列换成 jsonb / json），差异就落在这里，
--      不需要再动 Flyway 配置。

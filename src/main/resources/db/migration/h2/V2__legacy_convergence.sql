-- ========================================
-- V2：把《启动期兼容补丁》表达成版本化迁移（H2 路径，issue #192）
-- ========================================
-- 与 postgres/V2__legacy_convergence.sql 同一语义，但用 H2 方言与 H2 的类型名。
--
-- 改造前，老 H2 库的兼容性修补由两个启动期组件承担（补列、以及把 security_blacklist.expires_at
-- 改可空）—— 它们已随 issue #192 删除。当时那两个组件的执行时机都在
-- Hibernate 建 EntityManagerFactory **之后**，而 ddl-auto: validate 就在那一步 —— 所以缺列的
-- 老库会在补丁跑到之前就启动失败。本文件把同一批语义搬到 Flyway（在 validate 之前执行）。
--
-- 语义（对应 postgres 侧的 A1–A6 + B1）：
--   * A1–A3：api_call_history 的三个补丁列（类型按 H2 取：varchar(20) / TEXT / TEXT）
--   * A4–A5：service_instance 的 tags / headers（H2 上是 json）
--   * B1   ：security_blacklist.expires_at 允许为 NULL（NULL = 永久黑名单）
--
-- 与 PG 侧的唯一差别：H2 **不做 JSON 类型收敛**。PG 需要收敛是因为实体曾硬编码
-- columnDefinition="JSON"，让新库在 PG 上建成 json、而旧库升级路径补成 TEXT/CLOB，出现分歧；
-- H2 上实体当时的声明就是 "JSON"，旧库的 tags/headers 一直是 json，与实体一致，没有分歧可收敛。
--
-- 幂等性：全部语句都是「已满足即空操作」，在任何状态下重复执行都安全 —— 已在 H2 2.3.232 上
-- 逐条实测（重复 ADD COLUMN IF NOT EXISTS、ALTER TABLE IF EXISTS 指向不存在的表、
-- 对已可空的列重复 DROP NOT NULL，均无错）。
-- ========================================

-- A1–A3：api_call_history 的三个补丁列
ALTER TABLE IF EXISTS api_call_history ADD COLUMN IF NOT EXISTS record_level varchar(20);
ALTER TABLE IF EXISTS api_call_history ADD COLUMN IF NOT EXISTS request_body_encrypted TEXT;
ALTER TABLE IF EXISTS api_call_history ADD COLUMN IF NOT EXISTS response_body_encrypted TEXT;

-- A4–A5：service_instance 的 JSON 列
ALTER TABLE IF EXISTS service_instance ADD COLUMN IF NOT EXISTS tags json;
ALTER TABLE IF EXISTS service_instance ADD COLUMN IF NOT EXISTS headers json;

-- B1：security_blacklist.expires_at 允许为 NULL
-- Hibernate 的 ddl-auto 只补缺失的表/列，不会修改已存在列的 NOT NULL 约束。
ALTER TABLE IF EXISTS security_blacklist ALTER COLUMN expires_at DROP NOT NULL;

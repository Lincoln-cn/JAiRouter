-- ========================================
-- V2：把《启动期兼容补丁》表达成版本化迁移（PostgreSQL，issue #192）
-- ========================================
-- 改造前这些补丁由两个 Java ApplicationRunner 在启动期执行：
--   1) 补列 + 把 JSON 列收敛为 jsonb
--   2) 把 security_blacklist.expires_at 改为可空
-- （这两个组件已随 issue #192 删除 —— 本文件接管了它们的语义，且执行时机更早）
-- 两者的执行时机都晚于 ddl-auto: validate（ApplicationRunner 在 EntityManagerFactory 之后），
-- 因此它们救不回「缺列的老库」。本脚本把同一语义搬到 Flyway（在 validate 之前执行）。
--
-- 幂等性：全部语句都是「已满足即空操作」，所以在任何状态下重复执行都安全 ——
--   * 空库：V1 已建好一切，本脚本每一步都是空操作
--   * 已收敛的库（含 #190 之后的 Java 补丁已跑过的）：同样空操作
--   * 老库：补齐缺列并把 JSON 列收敛为 jsonb
--
-- 本目录只给 PostgreSQL 用：H2 有一套等价但类型不同的脚本，见 ../h2/V2__legacy_convergence.sql
-- （两侧都必须存在，因为两种方言的物理类型不同：PG 是 jsonb / TEXT，H2 是 json / TEXT）。
-- ========================================

-- A1–A3：api_call_history 的三个补丁列
-- （原补丁在 PG 上把加密正文列补成 TEXT）
ALTER TABLE IF EXISTS api_call_history ADD COLUMN IF NOT EXISTS request_body_encrypted TEXT;
ALTER TABLE IF EXISTS api_call_history ADD COLUMN IF NOT EXISTS response_body_encrypted TEXT;
ALTER TABLE IF EXISTS api_call_history ADD COLUMN IF NOT EXISTS record_level varchar(20);

-- A4/A5：service_instance 的 JSON 列。PG 约定 jsonb（与实体 @JdbcTypeCode(SqlTypes.JSON) 的方言选择一致）
ALTER TABLE IF EXISTS service_instance ADD COLUMN IF NOT EXISTS tags jsonb;
ALTER TABLE IF EXISTS service_instance ADD COLUMN IF NOT EXISTS headers jsonb;

-- A6：把早期补成 text / json 的 JSON 列收敛为 jsonb
-- （原 convergeJsonColumn：仅 PG 自动收敛，非 PG 只告警）。列已存在时上面的 ADD 会跳过，
-- 所以这里必须独立判定一次类型，否则老库会永远停留在 text。
DO $$
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_schema = current_schema()
                 AND lower(table_name) = 'service_instance'
                 AND lower(column_name) = 'tags'
                 AND udt_name <> 'jsonb') THEN
        ALTER TABLE service_instance ALTER COLUMN tags TYPE jsonb USING tags::jsonb;
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_schema = current_schema()
                 AND lower(table_name) = 'service_instance'
                 AND lower(column_name) = 'headers'
                 AND udt_name <> 'jsonb') THEN
        ALTER TABLE service_instance ALTER COLUMN headers TYPE jsonb USING headers::jsonb;
    END IF;
END
$$;

-- B1：security_blacklist.expires_at 允许为 NULL（NULL = 永久黑名单）
-- 必须显式执行：Hibernate 的 ddl-auto 只补缺失的表/列，不会修改已存在列的 NOT NULL 约束。
-- 已是可空时 PostgreSQL 会静默接受同一条语句，因此可重复执行。
ALTER TABLE IF EXISTS security_blacklist ALTER COLUMN expires_at DROP NOT NULL;

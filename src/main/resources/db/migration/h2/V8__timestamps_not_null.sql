-- V8：审计时间戳列补上 NOT NULL 约束（issue #973）
--
-- 现象：V1 建表时 21 个 created_at / updated_at 列是可空的，而实体上这些字段由
-- @CreationTimestamp / @UpdateTimestamp 自动填值、并标了 nullable = false。
-- 也就是说同一个模型有两套写法：Hibernate 从实体建出的表是 NOT NULL，V1 建出的是可空。
-- H2 路径用的是 update 模式，不会修改已存在的表，所以空库启动不报错、也不会补上约束；
-- H2BaselineMatchesEntityModelTest 是唯一会发现这种漂移的地方（升级 Hibernate 7.4
-- 时正是它报出的 21 列）。
--
-- 修法：先用当前时间回填空值（原本就是 NULL 的行没有真实时间可还原），再加约束。
-- 不可改已发布的 V1（Flyway 按校验和判定），因此走增量脚本。

UPDATE circuit_breaker_global_config SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE circuit_breaker_global_config ALTER COLUMN updated_at SET NOT NULL;

UPDATE circuit_breaker_metrics SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE circuit_breaker_metrics ALTER COLUMN updated_at SET NOT NULL;

UPDATE exception_events SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE exception_events ALTER COLUMN created_at SET NOT NULL;

UPDATE exception_stats_hourly SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE exception_stats_hourly ALTER COLUMN created_at SET NOT NULL;

UPDATE exception_stats_hourly SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE exception_stats_hourly ALTER COLUMN updated_at SET NOT NULL;

UPDATE instance_circuit_breaker SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE instance_circuit_breaker ALTER COLUMN created_at SET NOT NULL;

UPDATE instance_circuit_breaker SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE instance_circuit_breaker ALTER COLUMN updated_at SET NOT NULL;

UPDATE instance_rate_limit SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE instance_rate_limit ALTER COLUMN created_at SET NOT NULL;

UPDATE instance_rate_limit SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE instance_rate_limit ALTER COLUMN updated_at SET NOT NULL;

UPDATE jwt_accounts SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE jwt_accounts ALTER COLUMN created_at SET NOT NULL;

UPDATE jwt_accounts SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE jwt_accounts ALTER COLUMN updated_at SET NOT NULL;

UPDATE role_permissions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE role_permissions ALTER COLUMN created_at SET NOT NULL;

UPDATE role_permissions SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE role_permissions ALTER COLUMN updated_at SET NOT NULL;

UPDATE security_audit SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE security_audit ALTER COLUMN created_at SET NOT NULL;

UPDATE security_blacklist SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE security_blacklist ALTER COLUMN created_at SET NOT NULL;

UPDATE security_blacklist SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE security_blacklist ALTER COLUMN updated_at SET NOT NULL;

UPDATE service_config SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE service_config ALTER COLUMN created_at SET NOT NULL;

UPDATE service_config SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE service_config ALTER COLUMN updated_at SET NOT NULL;

UPDATE service_instance SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE service_instance ALTER COLUMN created_at SET NOT NULL;

UPDATE service_instance SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
ALTER TABLE service_instance ALTER COLUMN updated_at SET NOT NULL;

UPDATE token_usage SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE token_usage ALTER COLUMN created_at SET NOT NULL;

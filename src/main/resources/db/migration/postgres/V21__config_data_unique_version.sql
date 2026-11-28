-- #1249：config_data 缺 (config_key, version) 唯一约束。
-- 并发保存同一份配置时有两条路径会写入 version=1 的行：saveConfig 的
-- 「查 latest 不存在 → 插入」是 check-then-act，两个请求同时走到插入分支就会各写一行；
-- 之后每次启动都会在 VersionSyncService 里按 (key, version) 查单行，
-- 命中两行即抛 NonUniqueResultException，进程以退出码 1 结束（只能人工删数据）。
--
-- 建约束前必须先清理存量重复行，否则本脚本会直接失败。保留规则：
-- 同一 (config_key, version) 只留 id 最大的一行（即最后写入的那一行）。

DELETE FROM config_data
WHERE id NOT IN (
    SELECT keep_id FROM (
        SELECT MAX(id) AS keep_id FROM config_data GROUP BY config_key, version
    ) AS keepers
);

-- 去重可能删掉某个 key 的 is_latest 行（若重复行覆盖了不同的 is_latest 组合），
-- 重建「每个 key 恰有一行 is_latest=true」：最新插入的那一行（id 最大）。
UPDATE config_data SET is_latest = FALSE;

UPDATE config_data
SET is_latest = TRUE
WHERE id IN (
    SELECT keep_id FROM (
        SELECT MAX(id) AS keep_id FROM config_data GROUP BY config_key
    ) AS newest
);

ALTER TABLE config_data
    ADD CONSTRAINT uk_config_data_key_version UNIQUE (config_key, version);

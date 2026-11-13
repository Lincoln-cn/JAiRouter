-- #1135：配置版本号改为数据库原子分配，避免「先读最大值再加一」在并发写入时撞号。
-- next_version 语义 = 该配置键「已分配到的最大版本号」；分配时在事务内 +1 后读回。
CREATE TABLE IF NOT EXISTS config_sequences (
    config_key   VARCHAR(255) PRIMARY KEY,
    next_version BIGINT NOT NULL
);

-- 用库内已有的版本历史初始化，避免升级后从 1 重新开始与历史版本撞号。
INSERT INTO config_sequences (config_key, next_version)
SELECT config_key, MAX(version)
FROM config_data
GROUP BY config_key;

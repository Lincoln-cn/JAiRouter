-- 语义缓存跨用户命中审计（#996 后续）。
-- 只记录元数据：谁命中、命中哪个池、条目年龄、桶大小。
-- 不记录命中的正文 —— 正文可能含敏感内容，审计表只需要能回答「有没有跨租户读取」。
CREATE TABLE IF NOT EXISTS cache_cross_tenant_audit (
    id BIGSERIAL PRIMARY KEY,
    scope_id VARCHAR(255) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    entry_age_ms BIGINT NOT NULL,
    bucket_size INT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cache_audit_scope ON cache_cross_tenant_audit (scope_id);
CREATE INDEX IF NOT EXISTS idx_cache_audit_created_at ON cache_cross_tenant_audit (created_at);

COMMENT ON TABLE cache_cross_tenant_audit IS '语义缓存跨用户命中审计（只含元数据）';

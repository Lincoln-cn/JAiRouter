-- 异常链路归档（#1155）：只存失败链路，滚动 7 天后清理
CREATE TABLE IF NOT EXISTS trace_archive (
    trace_id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    service VARCHAR(64),
    operation VARCHAR(255),
    duration_ms DOUBLE PRECISION,
    status_code VARCHAR(16),
    error_count INTEGER NOT NULL,
    spans_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trace_archive_created_at ON trace_archive (created_at);

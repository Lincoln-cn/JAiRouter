ALTER TABLE config_audit_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_config_audit_trace_id ON config_audit_log (trace_id);

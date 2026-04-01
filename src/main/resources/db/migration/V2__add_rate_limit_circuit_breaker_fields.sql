-- ========================================
-- 数据库迁移脚本：添加限流器和熔断器字段到 service_instance 表
-- ========================================

-- 添加限流器相关字段
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS rate_limit_client_ip_enable BOOLEAN DEFAULT FALSE;

-- 添加熔断器相关字段
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_failure_threshold INT DEFAULT 5;
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_timeout INT DEFAULT 60000;
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_success_threshold INT DEFAULT 2;

-- 添加注释说明
COMMENT ON COLUMN service_instance.rate_limit_client_ip_enable IS '是否启用客户端 IP 限流';
COMMENT ON COLUMN service_instance.circuit_breaker_enabled IS '是否启用熔断器';
COMMENT ON COLUMN service_instance.circuit_breaker_failure_threshold IS '熔断器失败阈值';
COMMENT ON COLUMN service_instance.circuit_breaker_timeout IS '熔断器超时时间 (毫秒)';
COMMENT ON COLUMN service_instance.circuit_breaker_success_threshold IS '熔断器成功阈值';

-- ========================================
-- 数据库迁移脚本：添加 adapter 和 headers 字段到 service_instance 表
-- v1.7.1: 支持实例级别的适配器和自定义请求头配置
-- ========================================

-- 添加 adapter 字段（实例级别适配器配置，可选覆盖服务级别配置）
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS adapter VARCHAR(255);

-- 添加 headers 字段（自定义请求头配置，JSON 格式存储）
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS headers JSON;

-- 添加注释说明
COMMENT ON COLUMN service_instance.adapter IS '实例级别适配器配置（可选，覆盖服务级别配置）';
COMMENT ON COLUMN service_instance.headers IS '自定义请求头配置（JSON格式）';
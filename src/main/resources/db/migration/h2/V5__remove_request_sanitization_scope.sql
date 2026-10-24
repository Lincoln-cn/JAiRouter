-- ========================================
-- V5：移除请求侧脱敏配置面（H2 路径，issue #601）
-- ========================================
-- 背景：入站链路从未调用 sanitizeRequest（零生产调用方），请求侧开关/白名单/
-- 失败中断/日志四项均无读取方。为避免配置面长期误导，request 作用域整体移除，
-- 只保留 response 作用域。
--
-- 数据处置：删除历史 request 行（默认 yml 从未配置请求侧规则；曾手工保存过
-- request 配置的部署，其规则不再参与落库脱敏，需要把保留的规则迁到 response）。
-- whitelist_users 只服务于 request 白名单，一并删除该列。
--
-- 与 postgres/V5__remove_request_sanitization_scope.sql 同一语义。
-- 幂等性：DELETE 天然幂等；DROP COLUMN IF EXISTS 重复执行安全。
-- 回滚：手工恢复 request 行与 whitelist_users 列（数据不可自动恢复）。

DELETE FROM sanitization_config WHERE config_scope = 'request';
ALTER TABLE sanitization_config DROP COLUMN IF EXISTS whitelist_users;

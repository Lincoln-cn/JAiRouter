-- ========================================
-- V3：黑名单"原因"的预设值从中文文本改成稳定码（PostgreSQL 路径，issue #455）
-- ========================================
-- 与 h2/V3__blacklist_reason_codes.sql 同一语义（纯 UPDATE，方言无差异）。
-- 背景见那个文件的注释：`security_blacklist.reason` 是自由文本，前端下拉曾把中文文案当 value 提交。
--
-- 幂等：WHERE 匹配旧中文文本，重跑不会二次命中；只改 7 个已知预设值，不动自由文本。

UPDATE security_blacklist SET reason = 'MALICIOUS_LOGIN'      WHERE reason = '恶意登录尝试';
UPDATE security_blacklist SET reason = 'ABNORMAL_ACCESS'      WHERE reason = '异常访问行为';
UPDATE security_blacklist SET reason = 'COMPROMISED_ACCOUNT'  WHERE reason = '账户被盗';
UPDATE security_blacklist SET reason = 'VIOLATION'            WHERE reason = '违规操作';
UPDATE security_blacklist SET reason = 'SECURITY_RISK'        WHERE reason = '安全风险';
UPDATE security_blacklist SET reason = 'USER_REQUEST'         WHERE reason = '用户请求封禁';
UPDATE security_blacklist SET reason = 'OTHER'                WHERE reason = '其他';

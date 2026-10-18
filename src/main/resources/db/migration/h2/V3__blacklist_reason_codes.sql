-- ========================================
-- V3：黑名单"原因"的预设值从中文文本改成稳定码（H2 路径，issue #455）
-- ========================================
-- 背景：`security_blacklist.reason` 是自由文本（varchar 1000），前端下拉把**中文文案本身**当 value 提交，
-- 于是切到英文界面后，列表里显示的历史原因仍是中文。issue #455 的裁定是「根治」：
-- 预设值改用与语言无关的稳定码，展示时按语言翻译；用户自填的自由文本不受影响。
--
-- 两边同一语义（见 postgres/V3__blacklist_reason_codes.sql）。
--
-- 幂等性：WHERE 条件匹配的是**旧的中文文本**，重跑不会二次命中（改完已不含这些字面量）。
-- 只改这 7 个已知预设值；**不动**用户自填或后端写入的自由文本（如 JWT 撤销的「手动撤销」）。
--
-- 回滚（如需）：把 = 'CODE' 的行改回对应中文，等价语句在 PR 描述里给出。

UPDATE security_blacklist SET reason = 'MALICIOUS_LOGIN'      WHERE reason = '恶意登录尝试';
UPDATE security_blacklist SET reason = 'ABNORMAL_ACCESS'      WHERE reason = '异常访问行为';
UPDATE security_blacklist SET reason = 'COMPROMISED_ACCOUNT'  WHERE reason = '账户被盗';
UPDATE security_blacklist SET reason = 'VIOLATION'            WHERE reason = '违规操作';
UPDATE security_blacklist SET reason = 'SECURITY_RISK'        WHERE reason = '安全风险';
UPDATE security_blacklist SET reason = 'USER_REQUEST'         WHERE reason = '用户请求封禁';
UPDATE security_blacklist SET reason = 'OTHER'                WHERE reason = '其他';

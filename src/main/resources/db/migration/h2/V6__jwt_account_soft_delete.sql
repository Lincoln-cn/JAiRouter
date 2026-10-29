-- V6：JWT 账户软删除标记（issue #789）
--
-- 背景：默认 admin 同时存在于 YAML 静态账户（config/auth/jwt.yml）与 jwt_accounts 表里。
-- 控制台「删除账户」此前是物理 DELETE，行没了之后登录与令牌解析会回落到 YAML 分支，
-- 于是「控制台删掉了却还能登录」。新增软删除列后，删除 = 置 deleted = true 且行保留，
-- 账户状态一概以库里的行为准（YAML 只在库里没有该账户时兜底）。
--
-- DEFAULT FALSE 是为存量行回填；实体侧见 JwtAccountEntity#deleted。
ALTER TABLE jwt_accounts ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- V18：控制台页面访问记录（B4，2026-11-18）
--
-- 背景与取舍见同版本 h2 迁移（为什么不复用 security_audit）。
create table if not exists ui_page_views (
    id bigserial primary key,
    user_id varchar(128) not null,
    path varchar(200) not null,
    role varchar(32),
    viewed_at timestamp not null
);

create index if not exists idx_ui_page_view_viewed_at on ui_page_views (viewed_at);

create index if not exists idx_ui_page_view_path on ui_page_views (path, viewed_at);

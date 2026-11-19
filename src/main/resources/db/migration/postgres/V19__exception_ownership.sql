-- V19：异常事件补「归属」两列（issue #1189）
--
-- 与 H2 侧同一份逻辑，见同版本 h2 迁移。PostgreSQL 的 update 支持相关子查询 + limit 1，
-- 语法无需改写；索引名也保持一致，便于两边 schema 对照。
--
-- 背景：exception_events 一直没有「这条异常属于谁」，导致异常列表与异常统计无法按账号
-- 切割 —— PermissionCodes.MONITORING_EXCEPTIONS_SELF_READ 只能是死码（码表里有、没有
-- 任何查询能按它过滤）。而异常本来就有归属：它是某个 API Key / 账号的请求触发的，
-- api_call_history 里同一 trace_id 就记着 user_id 与 api_key_id。
--
-- 两步：
--   1. 加两列（新异常由 ReactiveGlobalExceptionHandler 从 exchange 属性写入，
--      该属性由 ServiceRequestHandler 在认证通过后写入）；
--   2. 回填历史：按 trace_id 关联 api_call_history。同一 trace 可能有多行（重试/失败转移），
--      取最早一行即可 —— 归属由调用主体决定，与第几次尝试无关。
--      关联不上的行留空，这是事实上的「不知道属于谁」，不要瞎猜。
alter table exception_events add column user_id varchar(255);
alter table exception_events add column api_key_id varchar(255);

update exception_events e
   set user_id = (select h.user_id
                    from api_call_history h
                   where h.trace_id = e.trace_id
                     and h.user_id is not null
                   order by h.id
                   limit 1)
 where e.trace_id is not null;

update exception_events e
   set api_key_id = (select h.api_key_id
                       from api_call_history h
                      where h.trace_id = e.trace_id
                        and h.api_key_id is not null
                      order by h.id
                      limit 1)
 where e.trace_id is not null;

create index idx_exception_user on exception_events (user_id);
create index idx_exception_api_key on exception_events (api_key_id);

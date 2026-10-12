-- 由 sysdba 在每个业务数据库执行，为 szh 开放只读语句统计。
-- shared_preload_libraries 必须已包含 pg_stat_statements，否则需要先修改参数并重启实例。

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
GRANT SELECT ON TABLE public.pg_stat_statements TO :"monitor_user";

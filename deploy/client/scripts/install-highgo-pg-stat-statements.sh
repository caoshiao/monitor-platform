#!/bin/bash
# 为瀚高安全版 V4.5 实例中的全部业务数据库部署 pg_stat_statements 只读采集权限。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/../sql/highgo-pg-stat-statements.sql"
MONITOR_USER="${1:-}"
CONTROL_DATABASE="${2:-kcc_main}"
EXECUTION_USER="${HIGHGO_EXECUTION_USER:-sysdba}"
EXCLUDED_DATABASES="${HIGHGO_EXCLUDED_DATABASES:-highgo}"
PSQL_BIN="${PSQL_BIN:-/opt/highgo/hgdb-4.5/bin/psql}"
DATABASE_LIST_SQL="SELECT datname FROM pg_database WHERE datallowconn AND NOT datistemplate ORDER BY datname"

if [ -z "${MONITOR_USER}" ]; then
    echo "用法: $0 <监控账号> [入口数据库]"
    echo "示例: HIGHGO_EXECUTION_USER=sysdba $0 szh kcc_main"
    exit 1
fi

if [ ! -x "${PSQL_BIN}" ]; then
    echo "[错误] 瀚高命令行客户端不可执行: ${PSQL_BIN}" >&2
    exit 1
fi

echo "检查 pg_stat_statements 预加载配置, database=${CONTROL_DATABASE}, user=${EXECUTION_USER}"
PRELOAD_LIBRARIES=$("${PSQL_BIN}" -X -v ON_ERROR_STOP=1 -U "${EXECUTION_USER}" \
    -d "${CONTROL_DATABASE}" -Atqc "SHOW shared_preload_libraries")
NORMALIZED_PRELOAD_LIBRARIES="${PRELOAD_LIBRARIES//[[:space:]]/}"
if [[ ",${NORMALIZED_PRELOAD_LIBRARIES}," != *",pg_stat_statements,"* ]]; then
    echo "[错误] shared_preload_libraries 未包含 pg_stat_statements。" >&2
    echo "请由 DBA 配置后重启瀚高实例，再重新运行本脚本。当前值: ${PRELOAD_LIBRARIES}" >&2
    exit 1
fi

DATABASE_OUTPUT=$("${PSQL_BIN}" -X -v ON_ERROR_STOP=1 -U "${EXECUTION_USER}" \
    -d "${CONTROL_DATABASE}" -Atqc "${DATABASE_LIST_SQL}")
mapfile -t DATABASES <<< "${DATABASE_OUTPUT}"

for database in "${DATABASES[@]}"; do
    skip_database=false
    IFS=',' read -ra excluded_array <<< "${EXCLUDED_DATABASES}"
    for excluded_database in "${excluded_array[@]}"; do
        if [ "${database}" = "${excluded_database}" ]; then
            skip_database=true
            break
        fi
    done
    if [ "${skip_database}" = true ]; then
        echo "跳过排除数据库: ${database}"
        continue
    fi
    echo "正在部署语句统计扩展: database=${database}, monitor_user=${MONITOR_USER}"
    "${PSQL_BIN}" -X -v ON_ERROR_STOP=1 -U "${EXECUTION_USER}" -d "${database}" \
        -v "monitor_user=${MONITOR_USER}" -f "${SQL_FILE}"
done

echo "pg_stat_statements 部署完成，共扫描 ${#DATABASES[@]} 个数据库"

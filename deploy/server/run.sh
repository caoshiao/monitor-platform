#!/bin/bash
# ============================================================
# Monitor Server 构建+启动（HTTP 模式）
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
JAR_SRC="../../monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar"

# ---- 1. 拷贝 JAR ----
if [ ! -f "$JAR_SRC" ]; then
    echo "[错误] 未找到 JAR: $JAR_SRC"
    echo "请先执行: cd monitor-platform && mvn clean package -DskipTests"
    exit 1
fi
cp "$JAR_SRC" .

# ---- 2. 构建并启动 ----
docker-compose up -d --build

echo ""
echo "============================================"
echo "  Monitor Server 启动成功!"
echo "  仪表盘: http://localhost:8080"
echo "  查看日志: docker logs -f monitor-server"
echo "============================================"

#!/bin/bash
# ============================================================
# Monitor Client 一键安装脚本
# 将客户端 JAR 部署到目标服务器并注册 systemd 服务
# ============================================================
#
# 用法（在部署服务器上以 root 执行）:
#   bash install-client.sh
#
# 前置条件:
#   - 已将 monitor-client-1.0.0-SNAPSHOT.jar 放在当前目录
#   - 已按需编辑 config/application.yml
# ============================================================

set -e

APP_DIR="/opt/monitor-platform/monitor-client"
CONFIG_DIR="${APP_DIR}/config"
LOGS_DIR="/opt/monitor-platform/logs"
SERVICE_FILE="/etc/systemd/system/monitor-client.service"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="${SCRIPT_DIR}/monitor-client-1.0.0-SNAPSHOT.jar"

echo "============================================"
echo "  Monitor Client 部署安装"
echo "============================================"

# ---------- 1. 检查 JAR ----------
if [ ! -f "${JAR_FILE}" ]; then
    echo "[错误] 未找到 JAR: ${JAR_FILE}"
    echo "请先执行 mvn clean package -DskipTests 并将 JAR 复制到当前目录"
    exit 1
fi

# ---------- 2. 创建目录 ----------
echo "[1/4] 创建目录结构..."
mkdir -p "${APP_DIR}"
mkdir -p "${CONFIG_DIR}"
mkdir -p "${LOGS_DIR}"

# ---------- 3. 部署 JAR 和配置 ----------
echo "[2/4] 部署 JAR 和配置文件..."
cp -f "${JAR_FILE}" "${APP_DIR}/"
if [ -f "${SCRIPT_DIR}/config/application.yml" ]; then
    cp -f "${SCRIPT_DIR}/config/application.yml" "${CONFIG_DIR}/"
fi
chmod -R 755 "${APP_DIR}"
chmod -R 755 "${LOGS_DIR}"

# ---------- 4. 注册 systemd 服务 ----------
echo "[3/4] 注册 systemd 服务..."
cp -f "${SCRIPT_DIR}/monitor-client.service" "${SERVICE_FILE}"
systemctl daemon-reload
systemctl enable monitor-client

# ---------- 5. 启动服务 ----------
echo "[4/4] 启动 Monitor Client..."
systemctl restart monitor-client

# 等待启动
sleep 3

echo ""
echo "============================================"
echo "  Monitor Client 部署完成!"
echo "============================================"
echo "  安装路径:     ${APP_DIR}"
echo "  配置文件:     ${CONFIG_DIR}/application.yml"
echo "  日志文件:     ${LOGS_DIR}/monitor-client.log"
echo ""
echo "  常用命令:"
echo "    查看状态:   systemctl status monitor-client"
echo "    查看日志:   journalctl -u monitor-client -f"
echo "    重启服务:   systemctl restart monitor-client"
echo "    停止服务:   systemctl stop monitor-client"
echo "============================================"

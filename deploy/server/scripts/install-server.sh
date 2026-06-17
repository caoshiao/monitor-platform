#!/bin/bash
# ============================================================
# Monitor Server 一键安装脚本（宿主机部署）
# ============================================================
#
# 用法（root 执行）:
#   bash install-server.sh
#
# 前置条件:
#   - 已将 monitor-server-1.0.0-SNAPSHOT.jar 放在当前目录
#   - 已按需编辑 config/application.yml
# ============================================================

set -e

APP_DIR="/usr/local/monitor-platform/monitor-server"
CONFIG_DIR="${APP_DIR}/config"
LOGS_DIR="${APP_DIR}/logs"
SERVICE_FILE="/etc/systemd/system/monitor-server.service"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="${SCRIPT_DIR}/monitor-server-1.0.0-SNAPSHOT.jar"

echo "============================================"
echo "  Monitor Server 部署安装（宿主机）"
echo "============================================"

# ---------- 1. 检查 JAR ----------
if [ ! -f "${JAR_FILE}" ]; then
    echo "[错误] 未找到 JAR: ${JAR_FILE}"
    echo "请先执行 mvn clean package -DskipTests 并将 JAR 复制到当前目录"
    exit 1
fi

# ---------- 2. 创建目录 ----------
echo "[1/3] 创建目录结构..."
mkdir -p "${APP_DIR}"
mkdir -p "${CONFIG_DIR}"
mkdir -p "${LOGS_DIR}"

# ---------- 3. 部署文件 ----------
echo "[2/3] 部署 JAR 和配置文件..."
cp -f "${JAR_FILE}" "${APP_DIR}/"
if [ -f "${SCRIPT_DIR}/config/application.yml" ]; then
    cp -f "${SCRIPT_DIR}/config/application.yml" "${CONFIG_DIR}/"
fi
chmod -R 755 "${APP_DIR}"

# ---------- 4. 注册服务 ----------
echo "[3/3] 注册 systemd 服务..."
cp -f "${SCRIPT_DIR}/monitor-server.service" "${SERVICE_FILE}"
systemctl daemon-reload
systemctl enable monitor-server

echo ""
echo "============================================"
echo "  Monitor Server 安装完成!"
echo "============================================"
echo "  安装路径:     ${APP_DIR}"
echo "  配置文件:     ${CONFIG_DIR}/application.yml"
echo "  日志目录:     ${LOGS_DIR}"
echo ""
echo "  启动服务:     systemctl start monitor-server"
echo "  查看状态:     systemctl status monitor-server"
echo "  查看日志:     journalctl -u monitor-server -f"
echo ""
echo "  执行启动:"
read -p "是否立即启动? [Y/n] " yn
case $yn in
    [Nn]* ) echo "跳过启动，请稍后手动执行: systemctl start monitor-server";;
    * ) systemctl start monitor-server;;
esac
echo "============================================"

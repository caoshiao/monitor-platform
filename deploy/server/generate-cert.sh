# ============================================================
# 生成自签名证书脚本（用于测试环境）
# 生产环境请使用正规 CA 签发的证书
# ============================================================
# 用法: 在 deploy/server/certs/ 目录下执行
#   bash ../generate-cert.sh

#!/bin/bash
set -e

CERT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEYSTORE="${CERT_DIR}/monitor-server.p12"
PASSWORD="${SSL_KEY_STORE_PASSWORD:-changeit}"
ALIAS="${SSL_KEY_ALIAS:-monitor-server}"
VALIDITY_DAYS=3650

echo "============================================"
echo "  生成自签名 SSL 证书"
echo "  密钥库: ${KEYSTORE}"
echo "============================================"

# 生成 PKCS12 密钥库（Java 可直接使用）
keytool -genkeypair \
    -alias "${ALIAS}" \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore "${KEYSTORE}" \
    -storepass "${PASSWORD}" \
    -keypass "${PASSWORD}" \
    -validity "${VALIDITY_DAYS}" \
    -dname "CN=monitor-server, OU=Monitor, O=MyOrg, L=Beijing, ST=Beijing, C=CN" \
    -ext "SAN=DNS:localhost,IP:127.0.0.1"

echo "============================================"
echo "  证书生成成功!"
echo "  密钥库: ${KEYSTORE}"
echo "  密钥库密码: ${PASSWORD}"
echo "============================================"

#!/usr/bin/env bash
# ============================================================
# 切换 Nginx 为 Cloudflare 代理生产配置
# - 使用 CF-Connecting-IP 作为真实客户端 IP
# - 要求 seaunit.site 的 Let's Encrypt 证书已存在
# ============================================================
set -Eeuo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "请用 root 或 sudo 执行"
  exit 1
fi

CERT="/etc/letsencrypt/live/seaunit.site/fullchain.pem"
KEY="/etc/letsencrypt/live/seaunit.site/privkey.pem"
CONF="/etc/nginx/conf.d/shuati.conf"
TMP="$(mktemp)"
BACKUP="${CONF}.before-cloudflare"

if [ ! -s "$CERT" ] || [ ! -s "$KEY" ]; then
  echo "缺少 seaunit.site 的 Let's Encrypt 证书，请先执行 enable-https.sh"
  exit 1
fi

curl -fsSL \
  "https://raw.githubusercontent.com/seaunit/shuati/main/deploy/nginx/shuati-prod.conf" \
  -o "$TMP"

if [ -s "$CONF" ]; then
  cp "$CONF" "$BACKUP"
fi

install -m 0644 "$TMP" "$CONF"
rm -f "$TMP"

if ! nginx -t; then
  if [ -s "$BACKUP" ]; then
    cp "$BACKUP" "$CONF"
  fi
  nginx -t
  echo "新配置校验失败，已恢复原配置"
  exit 1
fi

systemctl reload nginx

echo
echo "==================== Cloudflare 代理配置完成 ===================="
echo "真实客户端 IP：CF-Connecting-IP"
echo "HTTPS：Full (strict) 源站证书"
echo "限流：按真实用户 IP 计算"
echo "================================================================"

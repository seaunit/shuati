#!/usr/bin/env bash
set -euo pipefail

# 用法：deploy.sh user@server
if [ $# -lt 1 ]; then
  echo "用法：deploy.sh user@server"
  exit 1
fi

TARGET="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/deploy/scripts/build.sh"

echo "==> 上传后端"
scp "$ROOT/backend/target/shuati-backend.jar" "$TARGET:/tmp/shuati-backend.jar"

echo "==> 上传前端（先打包）"
tar -C "$ROOT/frontend" -czf /tmp/shuati-frontend.tar.gz dist
scp /tmp/shuati-frontend.tar.gz "$TARGET:/tmp/shuati-frontend.tar.gz"

echo "==> 上传支付侧车（不含 node_modules 与 .env）"
tar -C "$ROOT" --exclude='payments/node_modules' --exclude='payments/.env' \
  -czf /tmp/shuati-payments.tar.gz payments
scp /tmp/shuati-payments.tar.gz "$TARGET:/tmp/shuati-payments.tar.gz"

echo "==> 远端替换并重启"
ssh "$TARGET" 'bash -s' <<'REMOTE'
set -euo pipefail
sudo mkdir -p /opt/shuati/frontend
sudo mv /tmp/shuati-backend.jar /opt/shuati/shuati-backend.jar
sudo rm -rf /opt/shuati/frontend/*
sudo tar -C /opt/shuati/frontend -xzf /tmp/shuati-frontend.tar.gz --strip-components=1
sudo rm -rf /opt/shuati/payments
sudo tar -C /opt/shuati -xzf /tmp/shuati-payments.tar.gz
cd /opt/shuati/payments && sudo npm ci --omit=dev
sudo chown -R shuati:shuati /opt/shuati
sudo systemctl restart shuati
sudo systemctl restart shuati-payments
sudo systemctl reload nginx
sudo systemctl status shuati --no-pager
sudo systemctl status shuati-payments --no-pager
REMOTE

echo "==> 完成"

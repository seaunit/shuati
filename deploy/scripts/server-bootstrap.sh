#!/usr/bin/env bash
# ============================================================
# 全新 Ubuntu 22.04 / 24.04 服务器初始化（用 root 或 sudo 执行）
# 安装：JDK 17、Node 20、MySQL 8、Redis、Nginx，并建好运行用户与目录
# 用法： sudo bash deploy/scripts/server-bootstrap.sh
# ============================================================
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 root 或 sudo 执行"
  exit 1
fi

echo "==> 安装系统依赖"
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jre-headless nginx redis-server mysql-server curl ca-certificates gnupg

echo "==> 安装 Node.js 20"
if ! command -v node >/dev/null 2>&1 || [ "$(node -v | cut -c2- | cut -d. -f1)" -lt 20 ]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

echo "==> 启动基础服务"
systemctl enable --now nginx redis-server mysql

echo "==> 创建运行用户与目录"
id -u shuati >/dev/null 2>&1 || useradd -r -m -s /usr/sbin/nologin shuati
mkdir -p /opt/shuati /etc/shuati
chown shuati:shuati /opt/shuati
chmod 750 /etc/shuati

echo
echo "======================================================"
echo "初始化完成。接下来："
echo "  1) 建库建账号（见 deploy/README.md 第 2 步）"
echo "  2) 执行 db/mysql/01_schema.sql 与 03_billing_seed.sql"
echo "  3) 写 /etc/shuati/env 和 /etc/shuati/payments.env"
echo "  4) 上传 jar / dist / payments，启用 systemd + Nginx"
echo "详细步骤： deploy/README.md"
echo "======================================================"

#!/usr/bin/env bash
# ============================================================
# 为正式域名申请 Let's Encrypt 证书并切换安全 Cookie
# 用法：
#   curl -fsSL .../enable-https.sh | sudo bash
# ============================================================
set -Eeuo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "请用 root 或 sudo 执行"
  exit 1
fi

DOMAIN="${SHUATI_DOMAIN:-seaunit.site}"
EMAIL="${SHUATI_CERTBOT_EMAIL:-956115846@qq.com}"

echo "==> 安装 certbot"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y certbot python3-certbot-nginx

echo "==> 为 ${DOMAIN} 和 www.${DOMAIN} 申请证书"
certbot --nginx \
  --non-interactive \
  --redirect \
  --agree-tos \
  --no-eff-email \
  --email "$EMAIL" \
  -d "$DOMAIN" \
  -d "www.${DOMAIN}"

echo "==> 打开安全 Cookie"
if [ -f /etc/shuati/env ]; then
  if grep -q '^SHUATI_COOKIE_SECURE=' /etc/shuati/env; then
    sed -i 's/^SHUATI_COOKIE_SECURE=.*/SHUATI_COOKIE_SECURE=true/' /etc/shuati/env
  else
    printf '\nSHUATI_COOKIE_SECURE=true\n' >>/etc/shuati/env
  fi
  chown shuati:shuati /etc/shuati/env
  chmod 600 /etc/shuati/env
  systemctl restart shuati
fi

nginx -t
systemctl reload nginx

echo
echo "==================== HTTPS 完成 ===================="
echo "https://${DOMAIN}"
echo "https://www.${DOMAIN}"
echo "证书自动续期由 certbot systemd timer 负责。"
echo "===================================================="

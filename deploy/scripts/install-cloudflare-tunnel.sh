#!/usr/bin/env bash
# ============================================================
# 安装 Cloudflare Named Tunnel 连接器
# 用法：
#   sudo bash install-cloudflare-tunnel.sh <TUNNEL_TOKEN>
# 或：
#   CLOUDFLARE_TUNNEL_TOKEN=... sudo -E bash install-cloudflare-tunnel.sh
# ============================================================
set -Eeuo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "请用 root 或 sudo 执行"
  exit 1
fi

TOKEN="${1:-${CLOUDFLARE_TUNNEL_TOKEN:-}}"
if [ -z "$TOKEN" ]; then
  echo "缺少 Tunnel Token。"
  echo "用法：sudo bash install-cloudflare-tunnel.sh <TUNNEL_TOKEN>"
  exit 1
fi

echo "==> 安装 cloudflared"
if ! command -v cloudflared >/dev/null 2>&1; then
  ARCH="$(dpkg --print-architecture)"
  case "$ARCH" in
    amd64) DEB_ARCH="amd64" ;;
    arm64) DEB_ARCH="arm64" ;;
    *)
      echo "暂不支持架构：$ARCH"
      exit 1
      ;;
  esac
  curl -fL \
    "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${DEB_ARCH}.deb" \
    -o /tmp/cloudflared.deb
  apt-get install -y /tmp/cloudflared.deb
fi

echo "==> 停止临时 Quick Tunnel"
systemctl disable --now shuati-quick-tunnel.service 2>/dev/null || true

echo "==> 安装并启动 Named Tunnel"
cloudflared service install "$TOKEN"
systemctl enable --now cloudflared

echo
echo "==================== Named Tunnel 已启动 ===================="
echo "请在 Cloudflare 控制台确认 Tunnel 状态为 Healthy。"
echo "Public Hostname 的源站建议配置："
echo "  Service:            https://127.0.0.1:443"
echo "  No TLS Verify:      true"
echo "  HTTP Host Header:   seaunit.site"
echo "  Hostnames:          seaunit.site / www.seaunit.site"
echo "=============================================================="

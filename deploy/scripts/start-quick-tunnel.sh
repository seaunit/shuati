#!/usr/bin/env bash
# ============================================================
# 临时公网 HTTPS 入口（Cloudflare Quick Tunnel）
# 用途：域名 clientHold / HTTPS 尚未配置时，让手机走 HTTPS 访问应用。
# 用法： curl -fsSL .../start-quick-tunnel.sh | sudo bash
# ============================================================
set -Eeuo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "请用 root 或 sudo 执行"
  exit 1
fi

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

cat >/etc/systemd/system/shuati-quick-tunnel.service <<'EOF'
[Unit]
Description=Shuati temporary Cloudflare quick tunnel
After=network-online.target nginx.service
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/bin/cloudflared tunnel --no-autoupdate --url https://127.0.0.1:443 --no-tls-verify --http-host-header seaunit.site
Restart=always
RestartSec=5
StandardOutput=append:/var/log/shuati-quick-tunnel.log
StandardError=append:/var/log/shuati-quick-tunnel.log

[Install]
WantedBy=multi-user.target
EOF

: >/var/log/shuati-quick-tunnel.log
systemctl daemon-reload
systemctl enable shuati-quick-tunnel.service
systemctl restart shuati-quick-tunnel.service

echo "==> 等待 Cloudflare 分配临时 HTTPS 地址"
URL=""
for _ in $(seq 1 45); do
  URL="$(grep -Eo 'https://[-a-z0-9]+\.trycloudflare\.com' /var/log/shuati-quick-tunnel.log | tail -1 || true)"
  if [ -n "$URL" ]; then
    break
  fi
  sleep 2
done

if [ -z "$URL" ]; then
  echo "==> 隧道已启动，但暂未拿到地址，请查看日志："
  echo "journalctl -u shuati-quick-tunnel -n 80 --no-pager"
  exit 1
fi

printf '%s\n' "$URL" >/etc/shuati/tunnel-url.txt
chmod 600 /etc/shuati/tunnel-url.txt

echo
echo "=================================================="
echo "临时 HTTPS 地址：${URL}"
echo "手机浏览器直接打开这个地址即可测试。"
echo "该地址由 Cloudflare Quick Tunnel 生成，服务重启后可能变化。"
echo "查看地址： cat /etc/shuati/tunnel-url.txt"
echo "停止隧道： systemctl disable --now shuati-quick-tunnel"
echo "=================================================="

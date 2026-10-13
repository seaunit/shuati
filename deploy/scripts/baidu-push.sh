#!/usr/bin/env bash
# ============================================================
# 把站点 URL 主动推送给百度（普通收录 API）
#
# 用法：
#   sudo bash deploy/scripts/baidu-push.sh
#
# token 从百度搜索资源平台 →「普通收录 → API 提交」获取，放到 /etc/shuati/env：
#   SHUATI_BAIDU_PUSH_TOKEN='你的 token'
#
# 建议改完题库或新增单元后跑一次；也可以挂到 crontab 每周跑：
#   0 6 * * 1 cd /opt/shuati && bash deploy/scripts/baidu-push.sh >>/var/log/shuati-baidu-push.log 2>&1
# ============================================================
set -Eeuo pipefail

SITE="${SHUATI_SITE_URL:-https://www.seaunit.site}"
ENV_FILE="${SHUATI_ENV_FILE:-/etc/shuati/env}"
SITEMAP="${SITE}/sitemap.xml"
URLS="$(mktemp)"
trap 'rm -f "$URLS" "$URLS.xml"' EXIT

TOKEN="${SHUATI_BAIDU_PUSH_TOKEN:-}"
if [ -z "$TOKEN" ] && [ -r "$ENV_FILE" ]; then
  TOKEN="$(sed -n "s/^SHUATI_BAIDU_PUSH_TOKEN=['\"]\{0,1\}\(.*\)['\"]\{0,1\}$/\1/p" "$ENV_FILE" | head -1)"
fi

if [ -z "$TOKEN" ]; then
  cat <<'EOF'
没有找到百度推送 token。

在「百度搜索资源平台 → 普通收录 → API 提交」里复制 token，然后执行：
  sudo sh -c "echo \"SHUATI_BAIDU_PUSH_TOKEN='你的token'\" >> /etc/shuati/env"
再重新跑本脚本。
EOF
  exit 1
fi

echo "==> 拉取 sitemap：${SITEMAP}"
curl -fsS --max-time 30 "$SITEMAP" -o "$URLS.xml"

grep -o '<loc>[^<]*</loc>' "$URLS.xml" | sed -e 's|<loc>||' -e 's|</loc>||' >"$URLS"

COUNT="$(wc -l <"$URLS" | tr -d ' ')"
if [ "$COUNT" -eq 0 ]; then
  echo "sitemap 里没有 URL，先确认后端 /sitemap.xml 正常" >&2
  exit 1
fi
echo "==> 共 ${COUNT} 条 URL，推送给百度"

# 刻意不用 -f：百度把错误原因放在返回体里（比如 over quota / site is not valid），
# 加了 -f 只会看到 "error: 400"，排查不了问题。
push() {
  local site="$1"
  curl -sS --max-time 30 -w '\n[http %{http_code}]' \
    -H 'Content-Type: text/plain' \
    --data-binary "@${URLS}" \
    "http://data.zz.baidu.com/urls?site=${site}&token=${TOKEN}"
}

echo "==> 推送站点：${SITE}"
RESPONSE="$(push "$SITE")"
echo "==> 百度返回：${RESPONSE}"

case "$RESPONSE" in
  *'"success"'*)
    exit 0
    ;;
esac

# 百度文档里 site 参数写的是「比如 www.example.com」，后台生成的地址却带协议头，
# 两种形态都试一次，省得排查到底是哪种。
HOST="${SITE#*://}"
if [ "$HOST" != "$SITE" ]; then
  echo "==> 上面没推成功，改用不带协议的站点名再试一次：${HOST}"
  echo "==> 百度返回：$(push "$HOST")"
fi

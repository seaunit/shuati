#!/usr/bin/env bash
# ============================================================
# Ubuntu 24.04 一键部署：环境 + MySQL 空库 + 前端 + 后端 + 支付侧车
#
# 用法（登录服务器后由 root / sudo 执行）：
#   curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/provision-ubuntu.sh | sudo bash
#
# 说明：
# - 生产库从空库开始，表结构由 Flyway 首次启动自动创建。
# - 本脚本不会导入 Supabase 业务数据，也不会覆盖已有 /etc/shuati/env。
# - 首次部署默认用 HTTP + 公网 IP，所以 SHUATI_COOKIE_SECURE=false；
#   域名与 HTTPS 配好后再改为 true。
# - 支付侧车先写占位私钥，待 Waffo 真实密钥写入 /etc/shuati/payments.env
#   后重启 shuati-payments 即可。
# ============================================================
set -Eeuo pipefail

trap 'echo "[provision] 执行失败，请把上方错误发给我 (line $LINENO)" >&2' ERR

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "请用 root 或 sudo 执行"
  exit 1
fi

REPO_URL="${SHUATI_REPO_URL:-https://github.com/seaunit/shuati.git}"
REPO_REF="${SHUATI_REPO_REF:-main}"
BUILD_DIR="${SHUATI_BUILD_DIR:-/opt/shuati-build}"
APP_DIR="/opt/shuati"
ENV_DIR="/etc/shuati"
DB_NAME="shuati"
DB_USER="shuati"
ADMIN_EMAIL="${SHUATI_BOOTSTRAP_ADMIN_EMAIL:-shuati.admin@gmail.com}"
ADMIN_PASSWORD="${SHUATI_BOOTSTRAP_ADMIN_PASSWORD:-Shuati@2026}"
COOKIE_SECURE="${SHUATI_COOKIE_SECURE:-false}"

echo "==> 1/9 安装系统依赖"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y \
  ca-certificates curl git gnupg maven nginx openjdk-17-jre-headless openssl \
  redis-server mysql-server

echo "==> 2/9 安装 Node.js 20"
if ! command -v node >/dev/null 2>&1 || [ "$(node -v | cut -c2- | cut -d. -f1)" -lt 20 ]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

if [ "$(node -v | cut -c2- | cut -d. -f1)" -lt 20 ]; then
  echo "Node.js 版本低于 20，部署中止"
  exit 1
fi

echo "==> 3/9 启动基础服务"
systemctl enable --now nginx redis-server mysql

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
fi

echo "==> 4/9 创建运行用户与目录"
if ! id -u shuati >/dev/null 2>&1; then
  useradd -r -m -s /usr/sbin/nologin shuati
fi
install -d -o shuati -g shuati -m 0755 "$APP_DIR"
install -d -m 0750 "$ENV_DIR"

echo "==> 5/9 拉取源码并构建"
rm -rf "$BUILD_DIR"
git clone --depth 1 --branch "$REPO_REF" "$REPO_URL" "$BUILD_DIR"
bash "$BUILD_DIR/deploy/scripts/build.sh"

echo "==> 6/9 部署应用产物"
install -m 0644 "$BUILD_DIR/backend/target/shuati-backend.jar" \
  "$APP_DIR/shuati-backend.jar"

rm -rf "$APP_DIR/frontend"
install -d -o shuati -g shuati -m 0755 "$APP_DIR/frontend"
cp -a "$BUILD_DIR/frontend/dist/." "$APP_DIR/frontend/"

rm -rf "$APP_DIR/payments"
cp -a "$BUILD_DIR/payments" "$APP_DIR/payments"
(
  cd "$APP_DIR/payments"
  npm ci --omit=dev
)

install -d -o shuati -g shuati -m 0755 "$APP_DIR/deploy"
cp -a "$BUILD_DIR/deploy/." "$APP_DIR/deploy/"

echo "==> 7/9 初始化 MySQL 空库"
DB_PASSWORD_FILE="$ENV_DIR/db-password"
if [ -s "$DB_PASSWORD_FILE" ]; then
  DB_PASSWORD="$(cat "$DB_PASSWORD_FILE")"
else
  DB_PASSWORD="$(openssl rand -hex 24)"
  printf '%s\n' "$DB_PASSWORD" >"$DB_PASSWORD_FILE"
fi
chmod 600 "$DB_PASSWORD_FILE"

mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

# 交互读取 DirectMail SMTP 密码（输入不回显；直接回车即跳过）。
# curl | bash 这类拿不到控制终端的环境会自动跳过，不会卡住部署。
prompt_mail_password() {
  # 注意：没有控制终端时 /dev/tty 依然存在，-r/-w 也会通过，必须真正尝试打开
  if ! { : >/dev/tty; } 2>/dev/null; then
    # 走 stderr：stdout 会被调用方 $(...) 捕获成密码值
    echo "[配置] 未检测到可交互终端，跳过 SMTP 密码输入" >&2
    printf '%s' ""
    return 0
  fi
  printf '\n[配置] 阿里云 DirectMail 邮箱 SMTP 密码（账号 no-reply@mail.seaunit.site）\n' >/dev/tty
  printf '[配置] 输入不会回显，直接回车可跳过：' >/dev/tty
  local input=""
  IFS= read -rs input </dev/tty || input=""
  printf '\n' >/dev/tty
  if [ -z "$input" ]; then
    printf '[配置] 已跳过 SMTP 密码输入\n' >/dev/tty
  else
    printf '[配置] SMTP 密码已记录，将写入 /etc/shuati/env\n' >/dev/tty
  fi
  printf '%s' "$input"
}

# 覆盖或追加单个 env 变量（保持单引号写法，只动这一行）
set_env_var() {
  local key="$1" value="$2" tmp="$ENV_DIR/env.tmp"
  awk -v key="$key" -v val="$value" -v q="'" '
    $0 ~ "^" key "=" { print key "=" q val q; done = 1; next }
    { print }
    END { if (!done) print key "=" q val q }
  ' "$ENV_DIR/env" >"$tmp" && mv "$tmp" "$ENV_DIR/env"
}

echo "==> 8/9 写入配置"

# 优先级：环境变量 > 已有配置 > 交互输入。已有密码时不再重复打扰。
MAIL_PASSWORD="${SHUATI_MAIL_PASSWORD:-}"
if [ -z "$MAIL_PASSWORD" ] && [ -s "$ENV_DIR/env" ]; then
  MAIL_PASSWORD="$(
    sed -n "s/^SHUATI_MAIL_PASSWORD='\\(.*\\)'$/\\1/p" "$ENV_DIR/env" | head -1
  )"
fi
if [ -z "$MAIL_PASSWORD" ]; then
  MAIL_PASSWORD="$(prompt_mail_password)"
fi

if [ ! -s "$ENV_DIR/env" ]; then
  JWT_SECRET="$(openssl rand -hex 32)"
  APP_AES_SECRET="$(openssl rand 32 | base64 -w0)"
  CAPTCHA_SECRET="$(openssl rand -hex 32)"
  EMAIL_SECRET="$(openssl rand -hex 32)"
  PAYMENTS_SECRET="$(openssl rand -hex 32)"

  umask 077
  cat >"$ENV_DIR/env" <<EOF
SPRING_PROFILES_ACTIVE=prod
SHUATI_DB_URL='jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false'
SHUATI_DB_USER='${DB_USER}'
SHUATI_DB_PASSWORD='${DB_PASSWORD}'
SHUATI_JWT_SECRET='${JWT_SECRET}'
APP_AES_SECRET='${APP_AES_SECRET}'
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL='https://api.deepseek.com'
DEEPSEEK_MODEL='deepseek-flash'
SHUATI_REDIS_HOST='127.0.0.1'
SHUATI_REDIS_PORT=6379
SHUATI_REDIS_PASSWORD=
SHUATI_REDIS_DB=0
SHUATI_CAPTCHA_SECRET='${CAPTCHA_SECRET}'
SHUATI_COOKIE_SECURE=${COOKIE_SECURE}
SHUATI_PAYMENTS_ENABLED=false
SHUATI_PAYMENTS_URL='http://127.0.0.1:8090'
SHUATI_PAYMENTS_SECRET='${PAYMENTS_SECRET}'
SHUATI_PAYMENTS_CURRENCY='CNY'
SHUATI_PAYMENTS_SUBSCRIPTION_CURRENCY='USD'
SHUATI_BOOTSTRAP_ADMIN_EMAIL='${ADMIN_EMAIL}'
SHUATI_BOOTSTRAP_ADMIN_PASSWORD='${ADMIN_PASSWORD}'
# 注册 / 找回密码邮箱验证码（阿里云 DirectMail）
SHUATI_MAIL_ENABLED='true'
SHUATI_MAIL_HOST='smtpdm.aliyun.com'
SHUATI_MAIL_PORT=465
SHUATI_MAIL_USERNAME='no-reply@mail.seaunit.site'
# SMTP 密码：部署时交互输入；留空则稍后在 /etc/shuati/env 手动补填
SHUATI_MAIL_PASSWORD='${MAIL_PASSWORD}'
SHUATI_MAIL_FROM='no-reply@mail.seaunit.site'
SHUATI_MAIL_FROM_NAME='拾题'
SHUATI_EMAIL_SECRET='${EMAIL_SECRET}'
SHUATI_MAIL_SSL=true
SHUATI_MAIL_CONNECT_TIMEOUT_MS=10000
SHUATI_MAIL_READ_TIMEOUT_MS=10000
SHUATI_MAIL_WRITE_TIMEOUT_MS=10000
SHUATI_MAIL_CODE_TTL_SECONDS=300
SHUATI_MAIL_COOLDOWN_SECONDS=60
SHUATI_MAIL_EMAIL_DAILY_LIMIT=10
SHUATI_MAIL_IP_HOURLY_LIMIT=20
SHUATI_MAIL_DEVICE_DAILY_LIMIT=30
SHUATI_MAIL_GLOBAL_DAILY_LIMIT=2000
EOF
fi

if [ ! -s "$ENV_DIR/payments.env" ]; then
  PAYMENTS_SECRET_VALUE="$(
    sed -n "s/^SHUATI_PAYMENTS_SECRET='\\(.*\\)'$/\\1/p" "$ENV_DIR/env" | head -1
  )"
  if [ -z "$PAYMENTS_SECRET_VALUE" ]; then
    PAYMENTS_SECRET_VALUE="$(openssl rand -hex 32)"
  fi

  PLACEHOLDER_KEY="$ENV_DIR/waffo-placeholder.pem"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PLACEHOLDER_KEY"
  PLACEHOLDER_B64="$(base64 -w0 "$PLACEHOLDER_KEY")"

  umask 077
  cat >"$ENV_DIR/payments.env" <<EOF
WAFFO_MERCHANT_ID='MER_0000000000000000000000'
WAFFO_PRIVATE_KEY_BASE64='${PLACEHOLDER_B64}'
WAFFO_ENV='test'
WAFFO_STORE_ID='STO_0000000000000000000000'
WAFFO_CURRENCY='CNY'
WAFFO_PRODUCT_MAP=
PORT=8090
JAVA_BASE_URL='http://127.0.0.1:8080'
INTERNAL_SECRET='${PAYMENTS_SECRET_VALUE}'
WAFFO_DRY_RUN=true
EOF
fi

# 旧版 env 可能缺少邮件验证码配置：只补缺失的键，绝不覆盖已有值。
ensure_env_line() {
  local key="$1" value="$2"
  if ! grep -q "^${key}=" "$ENV_DIR/env"; then
    printf "%s='%s'\n" "$key" "$value" >>"$ENV_DIR/env"
    echo "[env] 补充缺失配置 ${key}"
  fi
}

ensure_env_line SHUATI_MAIL_ENABLED true
ensure_env_line SHUATI_MAIL_HOST smtpdm.aliyun.com
ensure_env_line SHUATI_MAIL_PORT 465
ensure_env_line SHUATI_MAIL_USERNAME no-reply@mail.seaunit.site
ensure_env_line SHUATI_MAIL_FROM no-reply@mail.seaunit.site
ensure_env_line SHUATI_MAIL_FROM_NAME 拾题
ensure_env_line SHUATI_EMAIL_SECRET "$(openssl rand -hex 32)"
ensure_env_line SHUATI_MAIL_SSL true
ensure_env_line SHUATI_MAIL_CONNECT_TIMEOUT_MS 10000
ensure_env_line SHUATI_MAIL_READ_TIMEOUT_MS 10000
ensure_env_line SHUATI_MAIL_WRITE_TIMEOUT_MS 10000
ensure_env_line SHUATI_MAIL_CODE_TTL_SECONDS 300
ensure_env_line SHUATI_MAIL_COOLDOWN_SECONDS 60
ensure_env_line SHUATI_MAIL_EMAIL_DAILY_LIMIT 10
ensure_env_line SHUATI_MAIL_IP_HOURLY_LIMIT 20
ensure_env_line SHUATI_MAIL_DEVICE_DAILY_LIMIT 30
ensure_env_line SHUATI_MAIL_GLOBAL_DAILY_LIMIT 2000

# 老部署里 SMTP 密码还是空值时，用本次输入的值补上；已有非空值不会被改动
if [ -n "$MAIL_PASSWORD" ]; then
  set_env_var SHUATI_MAIL_PASSWORD "$MAIL_PASSWORD"
fi

MAIL_PASSWORD_VALUE="$(
  grep -E '^SHUATI_MAIL_PASSWORD=' "$ENV_DIR/env" \
    | tail -1 | cut -d= -f2- | tr -d "'\""
)"
if [ -z "$MAIL_PASSWORD_VALUE" ]; then
  echo
  echo "==================== 告警 ===================="
  echo "未设置 SMTP 密码，邮箱验证码、注册和找回密码暂时不可用。"
  echo "补填方式：编辑 /etc/shuati/env 里的 SHUATI_MAIL_PASSWORD，保存后执行："
  echo "  sudo systemctl restart shuati"
  echo "=============================================="
  echo
fi

chown -R shuati:shuati "$ENV_DIR"
chmod 600 "$ENV_DIR/env" "$ENV_DIR/payments.env"
chown -R shuati:shuati "$APP_DIR"

echo "==> 9/9 安装 systemd / Nginx 并启动"
install -m 0644 "$APP_DIR/deploy/systemd/shuati.service" /etc/systemd/system/shuati.service
install -m 0644 "$APP_DIR/deploy/systemd/shuati-payments.service" \
  /etc/systemd/system/shuati-payments.service
if [ ! -f /etc/nginx/conf.d/shuati.conf ] \
  || ! grep -q 'ssl_certificate' /etc/nginx/conf.d/shuati.conf; then
  install -m 0644 "$APP_DIR/deploy/nginx/shuati.conf" /etc/nginx/conf.d/shuati.conf
fi
rm -f /etc/nginx/sites-enabled/default

systemctl daemon-reload
systemctl enable shuati shuati-payments
systemctl restart shuati shuati-payments
nginx -t
systemctl reload nginx

echo "==> 等待后端健康检查"
HEALTH_OK=0
for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    HEALTH_OK=1
    break
  fi
  sleep 2
done

if [ "$HEALTH_OK" -ne 1 ]; then
  echo
  echo "后端未通过健康检查，请查看："
  echo "  journalctl -u shuati -n 120 --no-pager"
  echo "  journalctl -u shuati-payments -n 80 --no-pager"
  exit 1
fi

echo "==> 导入软考真题库（已存在时自动跳过）"
if [ -s "$BUILD_DIR/db/mysql/soft_exam.sql" ]; then
  mysql --protocol=socket -uroot "$DB_NAME" <"$BUILD_DIR/db/mysql/soft_exam.sql"
  SOFT_EXAM_COUNTS="$(
    mysql --protocol=socket -uroot --batch --raw --skip-column-names "$DB_NAME" -e "
      select
        (select count(*) from bank where owner_id is null and name = '软考真题'),
        (select count(*) from unit u join bank b on b.id = u.bank_id
          where b.owner_id is null and b.name = '软考真题'),
        (select count(*) from question q join unit u on u.id = q.unit_id
          join bank b on b.id = u.bank_id
          where b.owner_id is null and b.name = '软考真题');
    "
  )"
  echo "软考真题库：题库/单元/题目 = ${SOFT_EXAM_COUNTS}"
else
  echo "未找到 db/mysql/soft_exam.sql，跳过题库导入"
fi

echo
echo "==================== 部署完成 ===================="
echo "后端：http://127.0.0.1:8080/actuator/health"
echo "支付：http://127.0.0.1:8090/health"
echo "管理员：${ADMIN_EMAIL}"
echo "密码：${ADMIN_PASSWORD}"
echo
echo "当前是 HTTP + IP 临时模式，SHUATI_COOKIE_SECURE=${COOKIE_SECURE}"
echo "域名与证书配好后，把 /etc/shuati/env 改成 SHUATI_COOKIE_SECURE=true 并重启 shuati。"
echo "Waffo 真实密钥配置在 /etc/shuati/payments.env，配置后重启 shuati-payments。"
echo "=================================================="

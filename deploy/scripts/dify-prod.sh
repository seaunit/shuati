#!/usr/bin/env bash
set -euo pipefail

DIFY_VERSION="${DIFY_VERSION:-1.13.3}"
DIFY_ROOT="${DIFY_ROOT:-/opt/dify}"
DIFY_REPO_ROOT="${DIFY_REPO_ROOT:-/opt/shuati}"
ENV_FILE="${ENV_FILE:-/etc/shuati/env}"
SWAP_FILE="${SWAP_FILE:-/swapfile-dify}"
NODE_VERSION="${NODE_VERSION:-22.23.4}"
PLUGIN_DAEMON_VERSION="${PLUGIN_DAEMON_VERSION:-0.5.3}"
CONSOLE_URL="${DIFY_CONSOLE_URL:-http://127.0.0.1:8501}"

log() {
  printf '[Dify生产] %s\n' "$*"
}

die() {
  printf '[Dify生产] 错误：%s\n' "$*" >&2
  exit 1
}

require_root() {
  [[ "${EUID}" -eq 0 ]] || die "请使用 root 执行"
}

ensure_env_file() {
  install -d -m 0750 /etc/shuati
  touch "$ENV_FILE"
  chown shuati:shuati "$ENV_FILE" 2>/dev/null || true
  chmod 600 "$ENV_FILE"
}

get_env() {
  local key="$1"
  awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE"
}

set_env() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$ENV_FILE"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

ensure_secret() {
  local key="$1"
  local value="${2:-}"
  if [[ -z "$(get_env "$key")" ]]; then
    if [[ -z "$value" ]]; then
      value="$(openssl rand -hex 32)"
    fi
    set_env "$key" "$value"
  fi
}

ensure_swap() {
  if swapon --show=NAME --noheadings | grep -qx "$SWAP_FILE"; then
    log "Swap 已启用：$SWAP_FILE"
    return
  fi
  if [[ ! -f "$SWAP_FILE" ]]; then
    log "创建 2 GiB Swap：$SWAP_FILE"
    fallocate -l 2G "$SWAP_FILE" || dd if=/dev/zero of="$SWAP_FILE" bs=1M count=2048 status=progress
    chmod 600 "$SWAP_FILE"
    mkswap "$SWAP_FILE" >/dev/null
  fi
  swapon "$SWAP_FILE"
  if ! grep -q "^${SWAP_FILE}[[:space:]]" /etc/fstab; then
    printf '%s none swap sw 0 0\n' "$SWAP_FILE" >> /etc/fstab
  fi
}

install_system_packages() {
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y \
    build-essential ca-certificates curl ffmpeg git libgomp1 libmagic1 libpq-dev \
    nginx postgresql-client postgresql-18-pgvector python3.12 python3.12-dev \
    python3.12-venv redis-tools xz-utils
  install -d -m 0755 /opt/dify/bin /opt/dify/api /opt/dify/web /opt/dify/plugin_storage
  if ! id dify >/dev/null 2>&1; then
    useradd --system --home "$DIFY_ROOT" --shell /usr/sbin/nologin dify
  fi
}

prepare_environment() {
  ensure_env_file
  ensure_secret SECRET_KEY
  ensure_secret INIT_PASSWORD
  ensure_secret DIFY_ADMIN_PASSWORD
  ensure_secret DB_PASSWORD
  ensure_secret PLUGIN_DAEMON_KEY
  ensure_secret PLUGIN_DIFY_INNER_API_KEY
  ensure_secret SANDBOX_API_KEY

  local db_password
  db_password="$(get_env DB_PASSWORD)"
  set_env DEPLOY_ENV PRODUCTION
  set_env EDITION SELF_HOSTED
  set_env CONSOLE_API_URL "$CONSOLE_URL"
  set_env CONSOLE_WEB_URL "$CONSOLE_URL"
  set_env SERVICE_API_URL "$CONSOLE_URL"
  set_env APP_API_URL "$CONSOLE_URL/api"
  set_env APP_WEB_URL "$CONSOLE_URL"
  set_env FILES_URL "$CONSOLE_URL"
  set_env INTERNAL_FILES_URL http://127.0.0.1:5001
  set_env TRIGGER_URL http://127.0.0.1:5001
  set_env DB_TYPE postgresql
  set_env DB_HOST 127.0.0.1
  set_env DB_PORT 5432
  set_env DB_USERNAME dify
  set_env DB_DATABASE dify
  set_env DB_PLUGIN_DATABASE dify_plugin
  set_env REDIS_HOST 127.0.0.1
  set_env REDIS_PORT 6379
  set_env REDIS_PASSWORD ""
  set_env REDIS_DB 3
  set_env REDIS_USE_SSL false
  set_env CELERY_BROKER_URL redis://127.0.0.1:6379/4
  set_env CELERY_BACKEND redis
  set_env STORAGE_TYPE opendal
  set_env OPENDAL_SCHEME fs
  set_env OPENDAL_FS_ROOT "$DIFY_ROOT/api/storage"
  set_env VECTOR_STORE pgvector
  set_env PGVECTOR_HOST 127.0.0.1
  set_env PGVECTOR_PORT 5432
  set_env PGVECTOR_USER dify
  set_env PGVECTOR_PASSWORD "$db_password"
  set_env PGVECTOR_DATABASE dify
  set_env PGVECTOR_MIN_CONNECTION 1
  set_env PGVECTOR_MAX_CONNECTION 5
  set_env PLUGIN_DAEMON_URL http://127.0.0.1:5002
  set_env PLUGIN_DIFY_INNER_API_URL http://127.0.0.1:5001
  set_env INNER_API_KEY_FOR_PLUGIN "$(get_env PLUGIN_DIFY_INNER_API_KEY)"
  set_env INNER_API_KEY "$(get_env PLUGIN_DIFY_INNER_API_KEY)"
  set_env PLUGIN_STORAGE_TYPE local
  set_env PLUGIN_STORAGE_LOCAL_ROOT "$DIFY_ROOT/plugin_storage"
  set_env PLUGIN_WORKING_PATH "$DIFY_ROOT/plugin_storage/cwd"
  set_env PLUGIN_INSTALLED_PATH plugin
  set_env PLUGIN_PACKAGE_CACHE_PATH plugin_packages
  set_env PLUGIN_MEDIA_CACHE_PATH assets
  set_env PLUGIN_REMOTE_INSTALLING_HOST 127.0.0.1
  set_env PLUGIN_REMOTE_INSTALLING_PORT 5003
  set_env FORCE_VERIFYING_SIGNATURE true
  set_env CODE_EXECUTION_ENDPOINT http://127.0.0.1:8194
  set_env CODE_EXECUTION_API_KEY "$(get_env SANDBOX_API_KEY)"
  set_env SERVER_WORKER_AMOUNT 1
  set_env SERVER_WORKER_CONNECTIONS 2
  set_env CELERY_WORKER_AMOUNT 1
  set_env CELERY_WORKER_CONCURRENCY 1
  set_env MIGRATION_ENABLED true
  set_env LOG_LEVEL INFO

  if [[ -z "$(get_env DIFY_ADMIN_EMAIL)" ]]; then
    set_env DIFY_ADMIN_EMAIL dify-admin@seaunit.site
  fi
  chown shuati:shuati "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  umask 077
  {
    printf 'Dify 控制台：通过 SSH 隧道访问 http://127.0.0.1:8501\n'
    printf '管理员邮箱：%s\n' "$(get_env DIFY_ADMIN_EMAIL)"
    printf '管理员密码：%s\n' "$(get_env DIFY_ADMIN_PASSWORD)"
    printf '初始化密码：%s\n' "$(get_env INIT_PASSWORD)"
  } > /root/dify-admin.txt
}

setup_postgres() {
  local db_password
  db_password="$(get_env DB_PASSWORD)"
  sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dify') THEN
    CREATE ROLE dify LOGIN PASSWORD '${db_password}';
  ELSE
    ALTER ROLE dify WITH LOGIN PASSWORD '${db_password}';
  END IF;
END
\$\$;
SELECT 'CREATE DATABASE dify OWNER dify'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'dify')\gexec
SELECT 'CREATE DATABASE dify_plugin OWNER dify'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'dify_plugin')\gexec
SQL
  sudo -u postgres psql -v ON_ERROR_STOP=1 -d dify -c "CREATE EXTENSION IF NOT EXISTS vector;"
}

install_source() {
  if [[ -f "$DIFY_ROOT/.native" ]]; then
    log "Dify 原生源码已存在"
    return
  fi
  if [[ -d "$DIFY_ROOT" ]] && [[ -n "$(find "$DIFY_ROOT" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    local backup="${DIFY_ROOT}-docker-partial-$(date +%Y%m%d-%H%M%S)"
    log "保留旧的 Docker 半成品到 $backup"
    mv "$DIFY_ROOT" "$backup"
  fi
  install -d -m 0755 "$DIFY_ROOT"
  local archive="/tmp/dify-${DIFY_VERSION}-native.tar.gz"
  local workdir="/tmp/dify-${DIFY_VERSION}-native"
  curl -fL --retry 3 --connect-timeout 15 \
    "https://github.com/langgenius/dify/archive/refs/tags/${DIFY_VERSION}.tar.gz" \
    -o "$archive"
  rm -rf "$workdir"
  mkdir -p "$workdir"
  tar -xzf "$archive" -C "$workdir" --strip-components=1
  cp -a "$workdir/api" "$DIFY_ROOT/api"
  cp -a "$workdir/web" "$DIFY_ROOT/web"
  printf '%s\n' "$DIFY_VERSION" > "$DIFY_ROOT/.native"
  rm -rf "$workdir" "$archive"
}

install_uv_and_python() {
  if ! command -v uv >/dev/null 2>&1; then
    curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR=/usr/local/bin sh
  fi
  chown -R dify:dify "$DIFY_ROOT"
  sudo -u dify env PATH="/usr/local/bin:$PATH" HOME="$DIFY_ROOT" \
    bash -lc "cd '$DIFY_ROOT/api' && uv sync --frozen --no-dev"
}

install_node_and_web() {
  if [[ ! -x /opt/node22/bin/node ]]; then
    local node_dir="node-v${NODE_VERSION}-linux-x64"
    local archive="/tmp/${node_dir}.tar.xz"
    curl -fL --retry 3 "https://nodejs.org/dist/v${NODE_VERSION}/${node_dir}.tar.xz" -o "$archive"
    tar -xJf "$archive" -C /opt
    ln -sfn "/opt/${node_dir}" /opt/node22
    rm -f "$archive"
  fi
  if [[ ! -x /opt/node22/bin/pnpm ]]; then
    /opt/node22/bin/npm install -g pnpm@10.32.1 --prefix /opt/node22
  fi
  cat > "$DIFY_ROOT/web/.env.production" <<EOF
NEXT_PUBLIC_DEPLOY_ENV=PRODUCTION
NEXT_PUBLIC_EDITION=SELF_HOSTED
NEXT_PUBLIC_API_PREFIX=$CONSOLE_URL/console/api
NEXT_PUBLIC_PUBLIC_API_PREFIX=$CONSOLE_URL/api
NEXT_PUBLIC_MARKETPLACE_API_PREFIX=https://marketplace.dify.ai/api/v1
NEXT_PUBLIC_MARKETPLACE_URL_PREFIX=https://marketplace.dify.ai
NEXT_TELEMETRY_DISABLED=1
EOF
  chown -R dify:dify "$DIFY_ROOT/web"
  sudo -u dify env \
    PATH="/opt/node22/bin:$PATH" \
    HOME="$DIFY_ROOT" \
    NODE_ENV=production \
    NODE_OPTIONS=--max-old-space-size=768 \
    bash -lc "cd '$DIFY_ROOT/web' && pnpm install --frozen-lockfile && pnpm run build"
}

install_plugin_daemon() {
  local binary="$DIFY_ROOT/bin/dify-plugin"
  if [[ ! -x "$binary" ]]; then
    curl -fL --retry 3 \
      "https://github.com/langgenius/dify-plugin-daemon/releases/download/${PLUGIN_DAEMON_VERSION}/dify-plugin-linux-amd64" \
      -o "$binary"
    chmod 0755 "$binary"
  fi
  install -d -m 0750 "$DIFY_ROOT/plugin_storage"
  chown -R dify:dify "$DIFY_ROOT/bin" "$DIFY_ROOT/plugin_storage"
}

install_services() {
  local source_root
  source_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  install -m 0644 "$source_root/systemd/dify-api.service" /etc/systemd/system/dify-api.service
  install -m 0644 "$source_root/systemd/dify-worker.service" /etc/systemd/system/dify-worker.service
  install -m 0644 "$source_root/systemd/dify-worker-beat.service" /etc/systemd/system/dify-worker-beat.service
  install -m 0644 "$source_root/systemd/dify-plugin-daemon.service" /etc/systemd/system/dify-plugin-daemon.service
  install -m 0644 "$source_root/systemd/dify-web.service" /etc/systemd/system/dify-web.service
  install -m 0644 "$source_root/dify/nginx-internal.conf" /etc/nginx/conf.d/dify-internal.conf
  nginx -t
  systemctl daemon-reload
  systemctl enable dify-plugin-daemon dify-api dify-worker dify-worker-beat dify-web
  systemctl restart dify-plugin-daemon
  systemctl restart dify-api
  systemctl restart dify-worker
  systemctl restart dify-worker-beat
  systemctl restart dify-web
  systemctl reload nginx
}

wait_http() {
  local deadline=$((SECONDS + 300))
  while (( SECONDS < deadline )); do
    if curl -fsS --max-time 5 "$CONSOLE_URL/console/api/setup" >/dev/null 2>&1; then
      log "Dify 控制台已就绪"
      return
    fi
    sleep 5
  done
  systemctl --no-pager --full status dify-api dify-worker dify-plugin-daemon dify-web || true
  die "Dify 控制台 300 秒内未就绪"
}

install_all() {
  install_system_packages
  prepare_environment
  ensure_swap
  setup_postgres
  install_source
  install_uv_and_python
  install_node_and_web
  install_plugin_daemon
  install_services
  wait_http
  status
}

status() {
  systemctl --no-pager --full status \
    dify-plugin-daemon dify-api dify-worker dify-worker-beat dify-web >/dev/null
  curl -fsS --max-time 15 "$CONSOLE_URL/console/api/setup"
  printf '\n'
  ss -ltnp | grep -E ':(3000|5001|5002|8501)\b' || true
  free -m
}

start() {
  systemctl start redis-server postgresql
  systemctl start dify-plugin-daemon dify-api dify-worker dify-worker-beat dify-web
  wait_http
  status
}

stop() {
  systemctl stop dify-web dify-worker-beat dify-worker dify-api dify-plugin-daemon
  log "Dify 已停止，数据与插件目录保留"
}

case "${1:-status}" in
  install)
    require_root
    install_all
    ;;
  start)
    require_root
    start
    ;;
  stop)
    require_root
    stop
    ;;
  status)
    require_root
    status
    ;;
  *)
    die "用法：$0 {install|start|stop|status}"
    ;;
esac

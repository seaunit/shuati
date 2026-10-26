#!/usr/bin/env bash
set -euo pipefail

DIFY_VERSION="${DIFY_VERSION:-1.13.3}"
DIFY_ROOT="${DIFY_ROOT:-/opt/dify}"
DIFY_REPO_ROOT="${DIFY_REPO_ROOT:-/opt/shuati}"
ENV_FILE="${ENV_FILE:-/etc/shuati/env}"
SWAP_FILE="${SWAP_FILE:-/swapfile-dify}"
CONSOLE_URL="${DIFY_CONSOLE_URL:-http://127.0.0.1:8501}"
COMPOSE_FILE="$DIFY_ROOT/docker-compose.yaml"
OVERRIDE_FILE="$DIFY_ROOT/docker-compose.prod.yaml"

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
  log "Swap 已启用并写入 fstab"
}

install_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "Docker 与 Compose 已安装"
  else
    log "安装 Docker Engine 与 Compose 插件"
    apt-get update
    apt-get install -y ca-certificates curl gnupg
    curl -fsSL https://get.docker.com | sh
  fi
  systemctl enable --now docker
  docker info >/dev/null
}

install_dify() {
  ensure_env_file
  ensure_swap
  install_docker

  if [[ ! -f "$COMPOSE_FILE" ]]; then
    local archive="/tmp/dify-${DIFY_VERSION}.tar.gz"
    local workdir="/tmp/dify-${DIFY_VERSION}"
    log "下载 Dify $DIFY_VERSION"
    curl -fL --retry 3 --connect-timeout 15 \
      "https://github.com/langgenius/dify/archive/refs/tags/${DIFY_VERSION}.tar.gz" \
      -o "$archive"
    rm -rf "$workdir"
    mkdir -p "$workdir"
    tar -xzf "$archive" -C "$workdir" --strip-components=1
    install -d -m 0750 "$DIFY_ROOT"
    cp -a "$workdir/docker/." "$DIFY_ROOT/"
    rm -rf "$workdir" "$archive"
  fi

  local override_source
  override_source="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/dify/docker-compose.prod.yaml"
  [[ -f "$override_source" ]] || die "缺少生产 Compose 覆盖文件：$override_source"
  install -m 0644 "$override_source" "$OVERRIDE_FILE"

  ensure_runtime_secrets
  docker compose \
    --project-directory "$DIFY_ROOT" \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    -f "$OVERRIDE_FILE" \
    config --quiet

  log "启动 Dify"
  compose up -d
  wait_http
  status
}

ensure_runtime_secrets() {
  local db_password redis_password
  db_password="$(get_env DB_PASSWORD)"
  redis_password="$(get_env REDIS_PASSWORD)"
  if [[ -z "$db_password" ]]; then
    db_password="$(openssl rand -hex 24)"
    set_env DB_PASSWORD "$db_password"
  fi
  if [[ -z "$redis_password" ]]; then
    redis_password="$(openssl rand -hex 24)"
    set_env REDIS_PASSWORD "$redis_password"
  fi

  ensure_secret SECRET_KEY
  ensure_secret INIT_PASSWORD
  ensure_secret PLUGIN_DAEMON_KEY
  ensure_secret PLUGIN_DIFY_INNER_API_KEY
  ensure_secret SANDBOX_API_KEY
  ensure_secret DIFY_ADMIN_PASSWORD

  set_env DB_USERNAME dify
  set_env DB_HOST db_postgres
  set_env DB_PORT 5432
  set_env DB_DATABASE dify
  set_env REDIS_HOST redis
  set_env REDIS_PORT 6379
  set_env REDIS_DB 0
  set_env REDIS_USE_SSL false
  set_env CELERY_BROKER_URL "redis://:${redis_password}@redis:6379/1"
  set_env CODE_EXECUTION_ENDPOINT http://sandbox:8194
  set_env CODE_EXECUTION_API_KEY "$(get_env SANDBOX_API_KEY)"
  set_env PLUGIN_DAEMON_URL http://plugin_daemon:5002
  set_env PLUGIN_DIFY_INNER_API_URL http://api:5001
  set_env EXPOSE_NGINX_PORT 127.0.0.1:8501
  set_env CONSOLE_API_URL "$CONSOLE_URL"
  set_env CONSOLE_WEB_URL "$CONSOLE_URL"
  set_env SERVICE_API_URL "$CONSOLE_URL"
  set_env FILES_URL "$CONSOLE_URL"
  set_env SERVER_WORKER_AMOUNT 1
  set_env SERVER_WORKER_CONNECTIONS 2
  set_env CELERY_WORKER_AMOUNT 1
  set_env CELERY_WORKER_CONCURRENCY 1
  set_env SQLALCHEMY_POOL_SIZE 10
  set_env SQLALCHEMY_MAX_OVERFLOW 5
  set_env POSTGRES_SHARED_BUFFERS 64MB
  set_env POSTGRES_EFFECTIVE_CACHE_SIZE 256MB
  set_env DEPLOY_ENV PRODUCTION

  if [[ -z "$(get_env DIFY_ADMIN_EMAIL)" ]]; then
    set_env DIFY_ADMIN_EMAIL dify-admin@seaunit.site
  fi
  chown shuati:shuati "$ENV_FILE"
  chmod 600 "$ENV_FILE"

  local admin_file=/root/dify-admin.txt
  umask 077
  {
    printf 'Dify 控制台：通过 SSH 隧道访问 http://127.0.0.1:8501\n'
    printf '管理员邮箱：%s\n' "$(get_env DIFY_ADMIN_EMAIL)"
    printf '管理员密码：%s\n' "$(get_env DIFY_ADMIN_PASSWORD)"
    printf '初始化密码：%s\n' "$(get_env INIT_PASSWORD)"
  } > "$admin_file"
}

compose() {
  docker compose \
    --project-directory "$DIFY_ROOT" \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    -f "$OVERRIDE_FILE" \
    "$@"
}

wait_http() {
  log "等待 Dify 控制台就绪"
  local deadline=$((SECONDS + 300))
  while (( SECONDS < deadline )); do
    if curl -fsS --max-time 5 "$CONSOLE_URL/console/api/setup" >/dev/null 2>&1; then
      log "Dify 控制台已就绪"
      return
    fi
    sleep 5
  done
  compose ps
  die "Dify 控制台 300 秒内未就绪"
}

status() {
  [[ -f "$COMPOSE_FILE" ]] || die "Dify 尚未安装"
  local expected=(api worker worker_beat web nginx db_postgres redis sandbox ssrf_proxy plugin_daemon)
  local running
  running="$(compose ps --services --status running)"
  local missing=()
  local service
  for service in "${expected[@]}"; do
    if ! grep -qx "$service" <<<"$running"; then
      missing+=("$service")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    compose ps
    die "未运行服务：${missing[*]}"
  fi
  curl -fsS --max-time 15 "$CONSOLE_URL/console/api/setup"
  printf '\n'
  free -m
  docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}' \
    $(docker ps --filter 'name=dify' --format '{{.Names}}')
}

start() {
  [[ -f "$COMPOSE_FILE" ]] || die "Dify 尚未安装"
  wait_swap_enabled
  systemctl start docker
  compose up -d
  wait_http
  status
}

wait_swap_enabled() {
  if [[ -f "$SWAP_FILE" ]] && ! swapon --show=NAME --noheadings | grep -qx "$SWAP_FILE"; then
    swapon "$SWAP_FILE"
  fi
}

stop() {
  [[ -f "$COMPOSE_FILE" ]] || die "Dify 尚未安装"
  compose stop
  log "Dify 已停止，数据卷保留"
}

case "${1:-status}" in
  install)
    require_root
    install_dify
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

#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
COMPOSE_DIR="$APP_DIR/deploy"
ENV_FILE="$APP_DIR/.env"
CONTAINER_NAME="zdmj-backend"
HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=runtime.sh
source "$SCRIPT_DIR/runtime.sh"
# shellcheck source=health.sh
source "$SCRIPT_DIR/health.sh"

REQUIRED_ENV_KEYS=(
  PG_USER PG_PASSWORD PG_DB REDIS_PASSWORD
  DATASOURCE_URL DASHSCOPE_API_KEY
  COS_SECRET_ID COS_SECRET_KEY COS_REGION COS_BUCKET
  MAIL_USERNAME MAIL_PASSWORD JWT_SECRET
)

require_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "错误: 未找到 $ENV_FILE"
    echo "请在服务器执行: cp $APP_DIR/.env.example $ENV_FILE && nano $ENV_FILE"
    exit 1
  fi
}

require_env_keys() {
  local missing=()
  local key
  for key in "${REQUIRED_ENV_KEYS[@]}"; do
    if ! grep -Eq "^[[:space:]]*${key}=" "$ENV_FILE"; then
      missing+=("$key")
      continue
    fi
    if grep -Eq "^[[:space:]]*${key}=[[:space:]]*$" "$ENV_FILE"; then
      missing+=("$key")
    fi
  done
  if ((${#missing[@]} > 0)); then
    echo "错误: $ENV_FILE 缺少或未填写的变量: ${missing[*]}"
    echo "参考模板: $APP_DIR/.env.example"
    exit 1
  fi
}

compose() {
  docker compose --env-file "$ENV_FILE" "$@"
}

sync_release_commit() {
  local sync="$APP_DIR/deploy/sync_server_code.sh"
  if [[ ! -f "$sync" ]]; then
    echo "错误: 缺少公共同步脚本 $sync" >&2
    exit 1
  fi
  APP_DIR="$APP_DIR" DEPLOY_SHA="$DEPLOY_SHA" GIT_ORIGIN_URL="${GIT_ORIGIN_URL:-}" bash "$sync"
}

require_deploy_sha
if [[ "${SKIP_SYNC:-}" != "1" ]]; then
  echo "== 1) 同步代码 =="
  sync_release_commit
else
  echo "== 1) 使用本次发布已同步的代码 =="
fi
require_head_matches_sha

echo "== 2) 检查环境变量 =="
require_env_file
require_env_keys

echo "== 3) 构建并部署 backend =="
cd "$COMPOSE_DIR"
compose build backend
# 只重建应用容器，保留已有数据库和 Redis 数据卷。
compose up -d --no-deps --force-recreate backend

echo "== 4) 确认后端健康 =="
if ! wait_for_http_body "$HEALTH_URL" '"status":"UP"'; then
  docker logs --tail 50 "$CONTAINER_NAME" >&2 || true
  exit 1
fi

echo "Backend deploy done."

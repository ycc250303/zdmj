#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/zdmj/zdmj"
COMPOSE_DIR="$APP_DIR/deploy"
ENV_FILE="$APP_DIR/.env"
CONTAINER_NAME="zdmj-backend"

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

cd "$APP_DIR"

echo "== 1) 更新代码 =="
git fetch --prune origin
git checkout main
git reset --hard origin/main

echo "== 2) 检查环境变量 =="
require_env_file
require_env_keys

echo "== 3) 构建并部署 backend =="
cd "$COMPOSE_DIR"
compose build backend
compose up -d --no-deps --force-recreate backend
compose ps backend

echo "== 4) 健康检查 =="
sleep 10
compose ps backend
docker logs --tail 50 "$CONTAINER_NAME"

echo "Backend deploy done."

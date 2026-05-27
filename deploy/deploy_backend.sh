#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/zdmj/zdmj"
COMPOSE_DIR="$APP_DIR/deploy"

cd "$APP_DIR"

echo "== 1) 更新代码 =="
git fetch --prune origin
git checkout main
git reset --hard origin/main

echo "== 2) 构建并部署 backend =="
cd "$COMPOSE_DIR"
ENV_FILE="$APP_DIR/.env"
docker compose --env-file "$ENV_FILE" build backend
docker compose --env-file "$ENV_FILE" up -d --no-deps --force-recreate backend
docker compose --env-file "$ENV_FILE" ps backend

echo "== 3) 健康检查 =="
sleep 10
docker compose --env-file "$ENV_FILE" ps backend
docker compose --env-file "$ENV_FILE" logs --tail 50 zdmj-backend

echo "Backend deploy done."

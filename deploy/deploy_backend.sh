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
docker compose build backend
docker compose up -d --no-deps --force-recreate backend
docker compose ps backend

echo "== 3) 健康检查 =="
sleep 10
docker compose ps backend
docker logs --tail 50 zdmj-backend

echo "Backend deploy done."

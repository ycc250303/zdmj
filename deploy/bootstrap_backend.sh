#!/usr/bin/env bash
# 首次部署或需要重建全部 compose 服务时使用（含 postgres / redis）
set -euo pipefail

APP_DIR="/opt/zdmj/zdmj"
COMPOSE_FILE="$APP_DIR/deploy/docker-compose.yml"

echo "== 1) 更新代码 =="
cd "$APP_DIR"
git fetch origin
git checkout main
git pull --ff-only origin main

echo "== 2) 更新基础服务 =="
docker compose -f "$COMPOSE_FILE" up -d postgres redis

echo "== 3) 迁移到 compose 管理（首次执行需要） =="
docker stop zdmj-backend || true
docker rm zdmj-backend || true

echo "== 4) 一键构建并部署所有服务 =="
docker compose -f "$COMPOSE_FILE" up -d --build

echo "== 5) 健康检查 =="
docker ps | grep -E "zdmj-backend|pgsql|redis"
docker logs --tail 30 zdmj-backend || true

echo "Bootstrap deploy done."

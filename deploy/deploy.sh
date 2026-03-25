#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/opt/zdmj/zdmj"
COMPOSE_FILE="$PROJECT_ROOT/deploy/docker-compose.yml"

echo "== 1) 更新代码 =="
cd "$PROJECT_ROOT"
git fetch origin
git checkout main
git pull --ff-only origin main

echo "== 2) 更新基础服务和 Python =="
docker compose -f "$COMPOSE_FILE" up -d postgres redis

echo "== 3) 迁移到 compose 管理（首次执行需要） =="
docker stop zdmj-python zdmj-backend || true
docker rm zdmj-python zdmj-backend || true

echo "== 4) 一键构建并部署所有服务 =="
docker compose -f "$COMPOSE_FILE" up -d --build

echo "== 5) 健康检查 =="
docker ps | grep -E "zdmj-backend|zdmj-python|pgsql|redis"
docker logs --tail 30 zdmj-backend || true
docker logs --tail 30 zdmj-python || true

echo "Deploy done."
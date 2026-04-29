#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/zdmj/zdmj"
DIST_DIR="/usr/share/nginx/html"

cd "$APP_DIR"

echo "== 1) 更新代码 =="
git fetch --prune origin
git checkout main
git reset --hard origin/main

echo "== 2) 安装依赖 =="
cd client
corepack enable
corepack prepare pnpm@latest --activate
pnpm install --frozen-lockfile
pnpm build

echo "== 3) 部署前端 =="
sudo rsync -av --delete dist/ "$DIST_DIR"/
sudo nginx -t
sudo systemctl reload nginx

echo "Frontend deploy done."
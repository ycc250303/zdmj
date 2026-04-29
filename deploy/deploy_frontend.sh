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

# SSH Action 默认是非交互 shell，nvm 可能不会自动加载
if ! command -v node >/dev/null 2>&1; then
  if [ -s "/root/.nvm/nvm.sh" ]; then
    # shellcheck disable=SC1091
    . "/root/.nvm/nvm.sh"
    nvm use 20 || true
  fi
fi

if command -v corepack >/dev/null 2>&1; then
  corepack enable
  corepack prepare pnpm@latest --activate
fi

if ! command -v pnpm >/dev/null 2>&1; then
  npm install -g pnpm@10
  hash -r
fi

node -v
pnpm -v
pnpm install --frozen-lockfile
pnpm build

echo "== 3) 部署前端 =="
sudo rsync -av --delete dist/ "$DIST_DIR"/
sudo nginx -t
sudo systemctl reload nginx

echo "Frontend deploy done."
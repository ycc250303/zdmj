#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
DIST_DIR="${DIST_DIR:-/usr/share/nginx/html}"
INSTALL_NGINX_NEW_F="${INSTALL_NGINX_NEW_F:-false}"

cd "$APP_DIR"

echo "== 1) 更新代码 =="
if [[ -f "$APP_DIR/deploy/sync_server_code.sh" ]]; then
  chmod +x "$APP_DIR/deploy/sync_server_code.sh"
  APP_DIR="$APP_DIR" DEPLOY_BRANCH="$DEPLOY_BRANCH" "$APP_DIR/deploy/sync_server_code.sh"
else
  git fetch --prune origin
  git checkout "$DEPLOY_BRANCH"
  git reset --hard "origin/$DEPLOY_BRANCH"
fi

echo "== 2) 安装依赖 =="
cd client

# SSH Action 默认是非交互 shell，nvm 可能不会自动加载
PNPM_VERSION="10"
if [ -s "/root/.nvm/nvm.sh" ]; then
  # shellcheck disable=SC1091
  . "/root/.nvm/nvm.sh"
  nvm use 20
fi

if command -v corepack >/dev/null 2>&1; then
  corepack enable
  # pnpm 11+ 需要 Node >= 22.13（依赖 node:sqlite），服务器当前为 Node 20
  corepack prepare "pnpm@${PNPM_VERSION}" --activate
elif ! command -v pnpm >/dev/null 2>&1; then
  npm install -g "pnpm@${PNPM_VERSION}"
  hash -r
fi

node -v
pnpm -v
pnpm install --frozen-lockfile
pnpm build

echo "== 3) 部署前端 =="
sudo mkdir -p "$DIST_DIR"
sudo rsync -av --delete dist/ "$DIST_DIR"/

if [[ "$INSTALL_NGINX_NEW_F" == "true" ]]; then
  echo "== 3.1) 安装 new-f Nginx 站点 (:1234) =="
  sudo cp "$APP_DIR/deploy/nginx-new-f.conf" /etc/nginx/sites-available/zdmj-new-f
  sudo ln -sf /etc/nginx/sites-available/zdmj-new-f /etc/nginx/sites-enabled/zdmj-new-f
fi

sudo nginx -t
sudo systemctl reload nginx

echo "Frontend deploy done (branch=${DEPLOY_BRANCH}, dist=${DIST_DIR})."

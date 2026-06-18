#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
DIST_DIR="${DIST_DIR:-/usr/share/nginx/html}"
NGINX_SITE_CONF="${NGINX_SITE_CONF:-}"
NGINX_SITE_NAME="${NGINX_SITE_NAME:-}"
# 兼容旧变量
if [[ "${INSTALL_NGINX_NEW_F:-false}" == "true" ]]; then
  NGINX_SITE_CONF=deploy/nginx-new-f.conf
  NGINX_SITE_NAME=zdmj-new-f
fi

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

if [[ -n "$NGINX_SITE_CONF" && -n "$NGINX_SITE_NAME" ]]; then
  echo "== 3.1) 安装 Nginx 站点 (${NGINX_SITE_NAME}) =="
  sudo cp "$APP_DIR/$NGINX_SITE_CONF" "/etc/nginx/sites-available/$NGINX_SITE_NAME"
  sudo ln -sf "/etc/nginx/sites-available/$NGINX_SITE_NAME" "/etc/nginx/sites-enabled/$NGINX_SITE_NAME"
fi

sudo nginx -t
sudo systemctl reload nginx

echo "Frontend deploy done (branch=${DEPLOY_BRANCH}, dist=${DIST_DIR})."

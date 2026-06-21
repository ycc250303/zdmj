#!/usr/bin/env bash
# 方案 B：Docker 多阶段构建前端镜像并由 Nginx 容器提供服务。
# 暂停宿主机 nginx（保留配置与安装，不卸载），避免端口冲突。
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
FRONTEND_SERVICE="${FRONTEND_SERVICE:-frontend-main}"
COMPOSE_DIR="${COMPOSE_DIR:-$APP_DIR/deploy}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"

case "$FRONTEND_SERVICE" in
  frontend-main|frontend-new-f|frontend-xhr) ;;
  *)
    echo "错误: 未知 FRONTEND_SERVICE=$FRONTEND_SERVICE"
    exit 1
    ;;
esac

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

pause_host_nginx() {
  if systemctl is-active --quiet nginx 2>/dev/null; then
    echo "== 2) 暂停宿主机 nginx（保留 /etc/nginx 配置，可随时 systemctl start nginx 回滚） =="
    systemctl stop nginx
  else
    echo "== 2) 宿主机 nginx 未运行，跳过 =="
  fi
}

pause_host_nginx

echo "== 3) 构建并启动 Docker 前端 (${FRONTEND_SERVICE}) =="
cd "$COMPOSE_DIR"

compose() {
  if [[ -f "$ENV_FILE" ]]; then
    docker compose --env-file "$ENV_FILE" "$@"
  else
    docker compose "$@"
  fi
}

compose build "$FRONTEND_SERVICE"
compose up -d --no-deps --force-recreate "$FRONTEND_SERVICE"
compose ps "$FRONTEND_SERVICE"

CONTAINER_NAME="zdmj-${FRONTEND_SERVICE}"

echo "== 4) 容器日志 =="
sleep 3
docker logs --tail 30 "$CONTAINER_NAME"

echo "Frontend docker deploy done (branch=${DEPLOY_BRANCH}, service=${FRONTEND_SERVICE})."

#!/usr/bin/env bash
# Docker 多阶段构建前端镜像，并由 Nginx 容器提供服务。
# 若宿主机 nginx 正在占用 80 端口，先停止该服务并保留其配置。
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
FRONTEND_SERVICE="${FRONTEND_SERVICE:-frontend-main}"
COMPOSE_DIR="${COMPOSE_DIR:-$APP_DIR/deploy}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1/}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=runtime.sh
source "$SCRIPT_DIR/runtime.sh"
# shellcheck source=health.sh
source "$SCRIPT_DIR/health.sh"

case "$FRONTEND_SERVICE" in
  frontend-main) ;;
  *)
    echo "错误: 未知 FRONTEND_SERVICE=$FRONTEND_SERVICE" >&2
    exit 1
    ;;
esac

sync_release_commit() {
  local sync="$APP_DIR/deploy/sync_server_code.sh"
  if [[ ! -f "$sync" ]]; then
    echo "错误: 缺少公共同步脚本 $sync" >&2
    exit 1
  fi
  APP_DIR="$APP_DIR" DEPLOY_SHA="$DEPLOY_SHA" GIT_ORIGIN_URL="${GIT_ORIGIN_URL:-}" bash "$sync"
}

require_deploy_sha
if [[ "${SKIP_SYNC:-}" != "1" ]]; then
  echo "== 1) 同步代码 =="
  sync_release_commit
else
  echo "== 1) 使用本次发布已同步的代码 =="
fi
require_head_matches_sha

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

CONTAINER_NAME="zdmj-${FRONTEND_SERVICE}"

echo "== 4) 确认前端就绪并连通后端 =="
if ! wait_for_http_ok "$FRONTEND_URL"; then
  docker logs --tail 30 "$CONTAINER_NAME" >&2 || true
  exit 1
fi

backend_health="$(docker exec "$CONTAINER_NAME" wget -qO- http://zdmj-backend:8080/actuator/health)" || {
  echo "错误: 前端容器无法访问后端健康检查" >&2
  docker logs --tail 30 "$CONTAINER_NAME" >&2 || true
  exit 1
}
compact="${backend_health//[[:space:]]/}"
if [[ "$compact" != *'"status":"UP"'* ]]; then
  echo "错误: 前后端连通检查未得到健康状态: $backend_health" >&2
  exit 1
fi

echo "前后端连通检查通过"
echo "Frontend docker deploy done (sha=${DEPLOY_SHA}, service=${FRONTEND_SERVICE})."

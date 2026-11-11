#!/usr/bin/env bash
# 一次发布：同步已通过 CI 的固定提交，再按目标构建后端和前端。
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
DEPLOY_JAVA="${DEPLOY_JAVA:-false}"
DEPLOY_FRONTEND="${DEPLOY_FRONTEND:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=runtime.sh
source "$SCRIPT_DIR/runtime.sh"

require_target() {
  case "$1" in
    true|false) ;;
    *)
      echo "错误: $2 必须是 true 或 false" >&2
      exit 1
      ;;
  esac
}

require_deploy_sha
require_target "$DEPLOY_JAVA" "DEPLOY_JAVA"
require_target "$DEPLOY_FRONTEND" "DEPLOY_FRONTEND"

if [[ "$DEPLOY_JAVA" != "true" && "$DEPLOY_FRONTEND" != "true" ]]; then
  echo "错误: 没有部署目标" >&2
  exit 1
fi

sync_script="$SCRIPT_DIR/sync_server_code.sh"
if [[ ! -f "$sync_script" ]]; then
  echo "错误: 缺少公共同步脚本 $sync_script" >&2
  exit 1
fi

APP_DIR="$APP_DIR" DEPLOY_SHA="$DEPLOY_SHA" GIT_ORIGIN_URL="${GIT_ORIGIN_URL:-}" bash "$sync_script"
require_head_matches_sha

if [[ "$DEPLOY_JAVA" == "true" ]]; then
  backend="$APP_DIR/deploy/deploy_backend.sh"
  if [[ ! -f "$backend" ]]; then
    echo "错误: 缺少 $backend" >&2
    exit 1
  fi
  SKIP_SYNC=1 APP_DIR="$APP_DIR" DEPLOY_SHA="$DEPLOY_SHA" bash "$backend"
fi

if [[ "$DEPLOY_FRONTEND" == "true" ]]; then
  frontend="$APP_DIR/deploy/deploy_frontend.sh"
  if [[ ! -f "$frontend" ]]; then
    echo "错误: 缺少 $frontend" >&2
    exit 1
  fi
  SKIP_SYNC=1 APP_DIR="$APP_DIR" DEPLOY_SHA="$DEPLOY_SHA" FRONTEND_SERVICE=frontend-main \
    bash "$frontend"
fi

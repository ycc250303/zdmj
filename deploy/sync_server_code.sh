#!/usr/bin/env bash
# 在服务器上串行拉取代码，避免前后端 CD 并发 git fetch 导致 ref 锁冲突。
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
LOCK_FILE="${APP_DIR}/.git/deploy.lock"
WAIT_SECONDS="${GIT_SYNC_LOCK_WAIT_SECONDS:-300}"

ensure_repo() {
  if [[ -d "$APP_DIR/.git" ]]; then
    return 0
  fi

  local parent origin
  parent="$(dirname "$APP_DIR")"
  mkdir -p "$parent"

  origin="${GIT_ORIGIN_URL:-}"
  if [[ -z "$origin" && -d /opt/zdmj/zdmj/.git ]]; then
    origin="$(git -C /opt/zdmj/zdmj remote get-url origin)"
  fi
  if [[ -z "$origin" ]]; then
    echo "错误: $APP_DIR 不存在且无法推断 git origin"
    exit 1
  fi

  echo "初始化仓库: git clone $origin -> $APP_DIR"
  git clone "$origin" "$APP_DIR"
  LOCK_FILE="${APP_DIR}/.git/deploy.lock"
}

ensure_repo
mkdir -p "$(dirname "$LOCK_FILE")"

(
  flock -w "$WAIT_SECONDS" 9 || {
    echo "错误: 等待 git 同步锁超时（${WAIT_SECONDS}s），可能有其他部署正在进行"
    exit 1
  }
  cd "$APP_DIR"
  git fetch --prune origin
  git checkout "$DEPLOY_BRANCH"
  git reset --hard "origin/$DEPLOY_BRANCH"
  git rev-parse --short HEAD
) 9>"$LOCK_FILE"

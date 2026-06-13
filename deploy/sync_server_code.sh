#!/usr/bin/env bash
# 在服务器上串行拉取代码，避免前后端 CD 并发 git fetch 导致 ref 锁冲突。
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
LOCK_FILE="${APP_DIR}/.git/deploy.lock"
WAIT_SECONDS="${GIT_SYNC_LOCK_WAIT_SECONDS:-300}"

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

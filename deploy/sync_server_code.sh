#!/usr/bin/env bash
# 将服务器仓库同步到已通过 CI 的固定提交。
# 工作区使用游离提交，避免把本地分支移动到另一提交。
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
LOCK_WAIT_SECONDS="${GIT_SYNC_LOCK_WAIT_SECONDS:-300}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=runtime.sh
source "$SCRIPT_DIR/runtime.sh"

export GIT_TERMINAL_PROMPT=0
require_deploy_sha

ensure_repo() {
  local parent origin
  if [[ -d "$APP_DIR/.git" ]]; then
    return 0
  fi
  if [[ -e "$APP_DIR" ]]; then
    echo "错误: $APP_DIR 已存在但不是 git 仓库" >&2
    exit 1
  fi

  parent="$(dirname "$APP_DIR")"
  mkdir -p "$parent"
  origin="${GIT_ORIGIN_URL:-}"
  if [[ -z "$origin" && "$APP_DIR" != "/opt/zdmj/zdmj" && -d /opt/zdmj/zdmj/.git ]]; then
    origin="$(git -C /opt/zdmj/zdmj remote get-url origin)"
  fi
  if [[ -z "$origin" ]]; then
    echo "错误: $APP_DIR 不存在且无法推断 git origin" >&2
    exit 1
  fi

  echo "初始化仓库: $origin -> $APP_DIR"
  git clone "$origin" "$APP_DIR"
}

ensure_repo

lock_file="$APP_DIR/.git/deploy.lock"
mkdir -p "$(dirname "$lock_file")"

(
  flock -w "$LOCK_WAIT_SECONDS" 9 || {
    echo "错误: 等待 git 同步锁超时（${LOCK_WAIT_SECONDS}s），可能有其他部署正在进行" >&2
    exit 1
  }
  cd "$APP_DIR"
  git fetch --prune origin
  if ! git rev-parse --verify --quiet "${DEPLOY_SHA}^{commit}" >/dev/null; then
    echo "错误: 远端没有提交 $DEPLOY_SHA" >&2
    exit 1
  fi
  git checkout --force --detach "$DEPLOY_SHA"
  actual="$(git rev-parse HEAD)"
  if [[ "$actual" != "$DEPLOY_SHA" ]]; then
    echo "错误: 同步后的提交是 $actual，已通过 CI 的提交是 $DEPLOY_SHA" >&2
    exit 1
  fi
  echo "已同步到 $actual"
) 9>"$lock_file"

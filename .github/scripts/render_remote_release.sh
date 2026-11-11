#!/usr/bin/env bash
# 把本次提交的发布脚本送到服务器执行。输出远端 bash，由 ssh_run.sh 读取。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${DEPLOY_SHA:?错误: 缺少 DEPLOY_SHA}"
: "${APP_DIR:?错误: 缺少 APP_DIR}"
: "${DEPLOY_JAVA:?错误: 缺少 DEPLOY_JAVA}"
: "${DEPLOY_FRONTEND:?错误: 缺少 DEPLOY_FRONTEND}"

case "$DEPLOY_JAVA" in
  true|false) ;;
  *)
    echo "错误: DEPLOY_JAVA 必须是 true 或 false" >&2
    exit 1
    ;;
esac
case "$DEPLOY_FRONTEND" in
  true|false) ;;
  *)
    echo "错误: DEPLOY_FRONTEND 必须是 true 或 false" >&2
    exit 1
    ;;
esac
if [[ ! "$DEPLOY_SHA" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "错误: DEPLOY_SHA 必须是 40 位提交" >&2
  exit 1
fi

encode() {
  base64 < "$1" | tr -d '\n'
}

runtime_b64="$(encode "$ROOT/deploy/runtime.sh")"
sync_b64="$(encode "$ROOT/deploy/sync_server_code.sh")"
release_b64="$(encode "$ROOT/deploy/release.sh")"
quoted_app="$(printf '%q' "$APP_DIR")"
quoted_sha="$(printf '%q' "$DEPLOY_SHA")"
quoted_java="$(printf '%q' "$DEPLOY_JAVA")"
quoted_frontend="$(printf '%q' "$DEPLOY_FRONTEND")"
quoted_origin="$(printf '%q' "${GIT_ORIGIN_URL:-}")"

cat <<EOF
set -euo pipefail
stage="/opt/zdmj/release-scripts"
mkdir -p "\$stage"
printf '%s' '${runtime_b64}' | base64 -d > "\$stage/runtime.sh"
printf '%s' '${sync_b64}' | base64 -d > "\$stage/sync_server_code.sh"
printf '%s' '${release_b64}' | base64 -d > "\$stage/release.sh"
chmod +x "\$stage/sync_server_code.sh" "\$stage/release.sh"
export APP_DIR=${quoted_app}
export DEPLOY_SHA=${quoted_sha}
export DEPLOY_JAVA=${quoted_java}
export DEPLOY_FRONTEND=${quoted_frontend}
export GIT_ORIGIN_URL=${quoted_origin}
"\$stage/release.sh"
EOF

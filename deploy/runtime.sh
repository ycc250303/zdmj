#!/usr/bin/env bash
# 发布脚本共用的提交校验。由 release、同步和构建脚本加载。

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "错误: runtime.sh 由部署脚本加载" >&2
  exit 1
fi

# 校验调用方传入的固定提交，并规范为小写。
require_deploy_sha() {
  if [[ -z "${DEPLOY_SHA:-}" || ! "$DEPLOY_SHA" =~ ^[0-9a-fA-F]{40}$ ]]; then
    echo "错误: DEPLOY_SHA 必须是已通过 CI 的 40 位提交" >&2
    exit 1
  fi
  DEPLOY_SHA="$(printf '%s' "$DEPLOY_SHA" | tr '[:upper:]' '[:lower:]')"
}

# 确认工作区已经是该提交，构建前再次拒绝分支头或其他提交。
require_head_matches_sha() {
  local actual
  require_deploy_sha
  actual="$(git -C "$APP_DIR" rev-parse HEAD)"
  if [[ "$actual" != "$DEPLOY_SHA" ]]; then
    echo "错误: 工作区提交是 $actual，已通过 CI 的提交是 $DEPLOY_SHA" >&2
    exit 1
  fi
}

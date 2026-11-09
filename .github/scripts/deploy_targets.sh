#!/usr/bin/env bash
# 集中判断一次发布要构建的目标，并核对 CI 与 CD 的路径清单。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# glob|匹配前缀。前缀以 / 结尾时按目录匹配，否则按完整相对路径匹配。
BACKEND_RULES=(
  "backend/zdmj/**|backend/zdmj/"
  "sql/**|sql/"
  "deploy/deploy_backend.sh|deploy/deploy_backend.sh"
  "deploy/sync_server_code.sh|deploy/sync_server_code.sh"
  "deploy/release.sh|deploy/release.sh"
  "deploy/runtime.sh|deploy/runtime.sh"
  "deploy/health.sh|deploy/health.sh"
  "deploy/docker-compose.yml|deploy/docker-compose.yml"
  ".github/workflows/ci_java.yml|.github/workflows/ci_java.yml"
  ".github/workflows/cd.yml|.github/workflows/cd.yml"
  ".github/scripts/ssh_run.sh|.github/scripts/ssh_run.sh"
  ".github/scripts/deploy_targets.sh|.github/scripts/deploy_targets.sh"
  ".github/scripts/render_remote_release.sh|.github/scripts/render_remote_release.sh"
)

FRONTEND_RULES=(
  "client/**|client/"
  "deploy/deploy_frontend.sh|deploy/deploy_frontend.sh"
  "deploy/docker-compose.yml|deploy/docker-compose.yml"
  "deploy/nginx.conf|deploy/nginx.conf"
  "deploy/sync_server_code.sh|deploy/sync_server_code.sh"
  "deploy/release.sh|deploy/release.sh"
  "deploy/runtime.sh|deploy/runtime.sh"
  "deploy/health.sh|deploy/health.sh"
  ".dockerignore|.dockerignore"
  ".github/workflows/cd.yml|.github/workflows/cd.yml"
  ".github/scripts/ssh_run.sh|.github/scripts/ssh_run.sh"
  ".github/scripts/deploy_targets.sh|.github/scripts/deploy_targets.sh"
  ".github/scripts/render_remote_release.sh|.github/scripts/render_remote_release.sh"
)

usage() {
  echo "用法: deploy_targets.sh --java true|false --frontend true|false | --from-file FILE | --check-workflows" >&2
  exit 2
}

file_matches() {
  local file="$1"
  local rule prefix
  shift
  for rule in "$@"; do
    prefix="${rule#*|}"
    if [[ "$prefix" == */ ]]; then
      [[ "$file" == "$prefix"* ]] && return 0
    elif [[ "$file" == "$prefix" ]]; then
      return 0
    fi
  done
  return 1
}

write_result() {
  local java="$1"
  local frontend="$2"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "deploy_java=${java}" >> "$GITHUB_OUTPUT"
    echo "deploy_frontend=${frontend}" >> "$GITHUB_OUTPUT"
  fi
  printf 'deploy_java=%s\n' "$java"
  printf 'deploy_frontend=%s\n' "$frontend"
}

print_unique_globs() {
  local rule glob seen=""
  for rule in "$@"; do
    glob="${rule%%|*}"
    if printf '%s\n' "$seen" | grep -F -x -q -- "$glob"; then
      continue
    fi
    printf '%s\n' "$glob"
    seen="${seen}"$'\n'"${glob}"
  done
}

extract_workflow_paths() {
  awk '
    $1 == "paths:" { capture = 1; next }
    capture && $1 == "-" {
      value = $2
      gsub(/'\''/, "", value)
      gsub(/"/, "", value)
      print value
      next
    }
    capture { capture = 0 }
  ' "$1"
}

check_workflows() {
  local expected_backend expected_cd actual_ci actual_cd ci_once
  expected_backend="$(print_unique_globs "${BACKEND_RULES[@]}")"
  expected_cd="$(print_unique_globs "${BACKEND_RULES[@]}" "${FRONTEND_RULES[@]}")"
  ci_once="$expected_backend"
  actual_ci="$(extract_workflow_paths "$ROOT/.github/workflows/ci_java.yml")"
  actual_cd="$(extract_workflow_paths "$ROOT/.github/workflows/cd.yml")"
  if [[ "$actual_ci" != "$(printf '%s\n%s' "$ci_once" "$ci_once")" ]]; then
    echo "错误: CI 的 push 与 pull_request 路径必须各包含一次后端部署路径" >&2
    echo "期望:" >&2
    printf '%s\n%s\n' "$ci_once" "$ci_once" >&2
    echo "实际:" >&2
    printf '%s\n' "$actual_ci" >&2
    exit 1
  fi
  if [[ "$actual_cd" != "$expected_cd" ]]; then
    echo "错误: CD 路径与部署目标不一致" >&2
    echo "期望:" >&2
    printf '%s\n' "$expected_cd" >&2
    echo "实际:" >&2
    printf '%s\n' "$actual_cd" >&2
    exit 1
  fi
  echo "工作流路径与部署目标一致"
}

if [[ $# -eq 0 ]]; then
  usage
fi

case "$1" in
  --check-workflows)
    check_workflows
    ;;
  --java)
    [[ $# -eq 4 && "$3" == "--frontend" ]] || usage
    case "$2" in true|false) ;; *) usage ;; esac
    case "$4" in true|false) ;; *) usage ;; esac
    write_result "$2" "$4"
    ;;
  --from-file)
    [[ $# -eq 2 && -f "$2" ]] || usage
    deploy_java=false
    deploy_frontend=false
    while IFS= read -r file || [[ -n "${file:-}" ]]; do
      [[ -z "$file" ]] && continue
      if file_matches "$file" "${BACKEND_RULES[@]}"; then
        deploy_java=true
      fi
      if file_matches "$file" "${FRONTEND_RULES[@]}"; then
        deploy_frontend=true
      fi
    done < "$2"
    write_result "$deploy_java" "$deploy_frontend"
    ;;
  *)
    usage
    ;;
esac

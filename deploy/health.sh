#!/usr/bin/env bash
# HTTP 就绪检查。由前后端构建脚本加载。

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "错误: health.sh 由部署脚本加载" >&2
  exit 1
fi

require_curl() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "错误: 未安装 curl，无法确认服务就绪" >&2
    return 1
  fi
}

# 在限定次数内等待 URL 返回成功状态。
wait_for_http_ok() {
  local url="$1"
  local attempts="${HEALTH_ATTEMPTS:-30}"
  local interval="${HEALTH_INTERVAL_SECONDS:-2}"
  local attempt
  require_curl || return 1
  for attempt in $(seq 1 "$attempts"); do
    if curl -fsS --max-time 5 -o /dev/null "$url"; then
      echo "就绪: $url"
      return 0
    fi
    if [[ "$attempt" -lt "$attempts" ]]; then
      sleep "$interval"
    fi
  done
  echo "错误: $url 在 ${attempts} 次检查后仍不可用" >&2
  return 1
}

# 在限定次数内等待响应正文包含期望片段。空白差异忽略。
wait_for_http_body() {
  local url="$1"
  local expected="$2"
  local attempts="${HEALTH_ATTEMPTS:-30}"
  local interval="${HEALTH_INTERVAL_SECONDS:-2}"
  local attempt body compact
  require_curl || return 1
  for attempt in $(seq 1 "$attempts"); do
    if body="$(curl -fsS --max-time 5 "$url" 2>/dev/null)"; then
      compact="${body//[[:space:]]/}"
      if [[ "$compact" == *"$expected"* ]]; then
        echo "就绪: $url"
        return 0
      fi
    fi
    if [[ "$attempt" -lt "$attempts" ]]; then
      sleep "$interval"
    fi
  done
  echo "错误: $url 在 ${attempts} 次检查后仍未包含 $expected" >&2
  return 1
}

#!/usr/bin/env bash
# 通过 SSH 在远程服务器执行脚本，连接失败时自动重试。
# 用法: SSH_PRIVATE_KEY=... bash ssh_run.sh <host> <user> <port> <max_attempts> <wait_seconds> < command_timeout_seconds
set -euo pipefail

HOST="${1:?host required}"
USER="${2:?user required}"
PORT="${3:-22}"
MAX_ATTEMPTS="${4:-4}"
WAIT_SECONDS="${5:-20}"
COMMAND_TIMEOUT="${6:-1800}"

if [[ -z "${SSH_PRIVATE_KEY:-}" ]]; then
  echo "错误: 未设置 SSH_PRIVATE_KEY 环境变量" >&2
  exit 1
fi

install -m 700 -d ~/.ssh
KEY_FILE="$(mktemp)"
trap 'rm -f "$KEY_FILE"' EXIT
printf '%s\n' "$SSH_PRIVATE_KEY" > "$KEY_FILE"
chmod 600 "$KEY_FILE"

ssh-keyscan -T 15 -p "$PORT" -H "$HOST" >> ~/.ssh/known_hosts 2>/dev/null || true

SSH_CMD=(
  ssh
  -i "$KEY_FILE"
  -o BatchMode=yes
  -o ConnectTimeout=30
  -o ServerAliveInterval=15
  -o ServerAliveCountMax=6
  -o StrictHostKeyChecking=yes
  -p "$PORT"
  "${USER}@${HOST}"
)

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  echo "SSH 连接尝试 ${attempt}/${MAX_ATTEMPTS} -> ${USER}@${HOST}:${PORT}"
  if timeout "$COMMAND_TIMEOUT" "${SSH_CMD[@]}" 'bash -s'; then
    exit 0
  fi
  if (( attempt < MAX_ATTEMPTS )); then
    echo "SSH 失败，${WAIT_SECONDS}s 后重试..."
    sleep "$WAIT_SECONDS"
  fi
done

echo "SSH 在 ${MAX_ATTEMPTS} 次尝试后仍失败" >&2
exit 1

#!/usr/bin/env bash
# 获取、清理JavaGuide，并启动同版本Java镜像执行一次性增量向量化。
set -Eeuo pipefail
umask 027

TOOLS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ROOT="${JAVAGUIDE_APP_ROOT:-/opt/zdmj/zdmj}"
DATA_ROOT="${JAVAGUIDE_DATA_ROOT:-/opt/zdmj/data/knowledge-sync/javaguide}"
PYTHON="${JAVAGUIDE_PYTHON:-$TOOLS_ROOT/.venv/bin/python}"
ACTION="${1:-update}"
CONTAINER_NAME=zdmj-javaguide-import
[[ $# -le 1 && ( "$ACTION" == update || "$ACTION" == import ) ]] || { echo "用法：$0 [update|import]" >&2; exit 1; }
[[ "$DATA_ROOT" == /* && "$APP_ROOT" == /* ]] || { echo "错误：目录必须为绝对路径" >&2; exit 1; }
[[ -x "$PYTHON" ]] || { echo "错误：未安装专用Python环境 $PYTHON" >&2; exit 1; }
export GIT_TERMINAL_PROMPT=0
export JAVAGUIDE_DATA_ROOT="$DATA_ROOT"

mkdir -p "$DATA_ROOT"
exec 9>"$DATA_ROOT/update.lock"
flock -n 9 || { echo "错误：JavaGuide更新任务正在执行" >&2; exit 75; }
chgrp 10001 "$DATA_ROOT"
chmod 0750 "$DATA_ROOT"
LOG_FILE="$DATA_ROOT/latest-attempt.log"
echo "log=$LOG_FILE"
exec >"$LOG_FILE" 2>&1
trap 'status=$?; printf "status=failed line=%s exitCode=%s\n" "$LINENO" "$status"; exit "$status"' ERR
trap 'echo "status=terminated"; exit 143' TERM
trap 'echo "status=interrupted"; exit 130' INT

# 固定容器名称使脚本连接中断后仍运行的导入阻止下一次刷新。
EXISTING_CONTAINER="$(docker ps -aq --filter "name=^/${CONTAINER_NAME}$")"
[[ -z "$EXISTING_CONTAINER" ]] || { echo "错误：前次导入容器仍存在，请检查执行结果" >&2; exit 75; }
COMPOSE=(docker compose --env-file "$APP_ROOT/.env" -f "$APP_ROOT/deploy/docker-compose.yml")
# 使用已部署后端容器的不可变镜像编号，确保导入和在线业务运行同一份代码。
IMAGE_ID="$(docker inspect --format '{{.Image}}' zdmj-backend)"
[[ "$IMAGE_ID" == sha256:* ]] || { echo "错误：无法确认后端已部署镜像" >&2; exit 1; }
export JAVAGUIDE_IMPORT_IMAGE="$IMAGE_ID"
if [[ "$ACTION" == update ]]; then
  "$PYTHON" -u "$TOOLS_ROOT/sql/clean_javaguide.py" \
    --refresh-root "$DATA_ROOT" --config "$TOOLS_ROOT/sql/javaguide_sources.json"
fi
chgrp -R 10001 "$DATA_ROOT/versions"
find "$DATA_ROOT/versions" -type d -exec chmod 0750 {} +
find "$DATA_ROOT/versions" -type f -exec chmod 0640 {} +
RUN_ID="$(basename "$(readlink -f "$DATA_ROOT/current")")"
MANIFEST_HASH="$(sha256sum "$DATA_ROOT/current/manifest.json" | cut -d' ' -f1)"
"${COMPOSE[@]}" run --rm --no-deps -T --name "$CONTAINER_NAME" knowledge-import \
  "--run-id=$RUN_ID" "--manifest-hash=$MANIFEST_HASH"

"$PYTHON" - "$DATA_ROOT" "$LOG_FILE" "$RUN_ID" "$MANIFEST_HASH" <<'PYTHON'
import json, os, sys
from datetime import datetime, timezone
from pathlib import Path
root, log, run_id, manifest_hash = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], sys.argv[4]
results = [json.loads(line.split("=", 1)[1]) for line in log.read_text(encoding="utf-8").splitlines()
           if line.startswith("SYSTEM_KNOWLEDGE_IMPORT_RESULT=")]
if len(results) != 1:
    raise ValueError("Java进程必须输出一条成功摘要")
result = results[0]
if (result["runId"] != run_id or result["manifestHash"] != manifest_hash
        or type(result["documents"]) is not int or result["documents"] <= 0
        or type(result["chunks"]) is not int or result["chunks"] < result["documents"]):
    raise ValueError("Java成功摘要与本次资料不一致")
path = root / "import-success-history.json"
history = json.loads(path.read_text(encoding="utf-8")) if path.exists() else []
if not isinstance(history, list) or len(history) > 2:
    raise ValueError("入库成功摘要格式非法")
if not history or history[-1]["runId"] != run_id:
    temporary = path.with_suffix(".json.part")
    with temporary.open("w", encoding="utf-8") as output:
        json.dump([*history[-1:], {**result, "completedAt": datetime.now(timezone.utc).isoformat()}],
                  output, ensure_ascii=False, indent=2)
        output.write("\n"); output.flush(); os.fsync(output.fileno())
    temporary.replace(path)
print("status=success operationId=" + run_id, flush=True)
PYTHON

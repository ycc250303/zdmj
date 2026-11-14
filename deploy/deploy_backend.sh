#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/zdmj/zdmj}"
COMPOSE_DIR="$APP_DIR/deploy"
ENV_FILE="$APP_DIR/.env"
CONTAINER_NAME="zdmj-backend"
HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=runtime.sh
source "$SCRIPT_DIR/runtime.sh"
# shellcheck source=health.sh
source "$SCRIPT_DIR/health.sh"

REQUIRED_ENV_KEYS=(
  PG_USER PG_PASSWORD PG_DB REDIS_PASSWORD
  DATASOURCE_URL DASHSCOPE_API_KEY
  KNOWLEDGE_STORAGE_ROOT
  MAIL_USERNAME MAIL_PASSWORD JWT_SECRET
)

require_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "错误: 未找到 $ENV_FILE"
    echo "请在服务器执行: cp $APP_DIR/.env.example $ENV_FILE && nano $ENV_FILE"
    exit 1
  fi
}

require_env_keys() {
  local missing=()
  local key
  for key in "${REQUIRED_ENV_KEYS[@]}"; do
    if ! grep -Eq "^[[:space:]]*${key}=" "$ENV_FILE"; then
      missing+=("$key")
      continue
    fi
    if grep -Eq "^[[:space:]]*${key}=[[:space:]]*$" "$ENV_FILE"; then
      missing+=("$key")
    fi
  done
  if ((${#missing[@]} > 0)); then
    echo "错误: $ENV_FILE 缺少或未填写的变量: ${missing[*]}"
    echo "参考模板: $APP_DIR/.env.example"
    exit 1
  fi
}

compose() {
  docker compose --env-file "$ENV_FILE" "$@"
}

sync_release_commit() {
  local sync="$APP_DIR/deploy/sync_server_code.sh"
  if [[ ! -f "$sync" ]]; then
    echo "错误: 缺少公共同步脚本 $sync" >&2
    exit 1
  fi
  APP_DIR="$APP_DIR" DEPLOY_SHA="$DEPLOY_SHA" GIT_ORIGIN_URL="${GIT_ORIGIN_URL:-}" bash "$sync"
}

# 离线数据维护复用当前发布入口；迁移和备份期间应用必须停止。
storage_maintenance() {
  require_env_file
  python3 - "$APP_DIR" "$ENV_FILE" "$1" <<'PYTHON'
import hashlib, json, os, pathlib, re, stat, subprocess, sys, tarfile
from datetime import datetime, timezone

app, env_file, action = sys.argv[1:]
compose = ["docker", "compose", "--env-file", env_file, "-f", app + "/deploy/docker-compose.yml"]
def run(command, data=None):
    return subprocess.run(command, input=data, text=True, capture_output=True, check=True).stdout
config = json.loads(run(compose + ["config", "--format", "json"]))
mounts = config["services"]["backend"]["volumes"]
root = pathlib.Path(next(m["source"] for m in mounts if m["target"] == "/app/data/knowledge"))
if not root.is_absolute() or root != root.resolve():
    raise SystemExit("错误: 持久化目录必须为无符号链接的绝对路径")
if action == "init":
    if root.exists():
        raise SystemExit("错误: 目录已经存在，请检查现有所有权和权限")
    root.mkdir(parents=True, mode=0o750)
    os.chown(root, 10001, 10001)
if not root.is_dir() or (root.stat().st_uid, root.stat().st_gid) != (10001, 10001) or stat.S_IMODE(root.stat().st_mode) & 0o027:
    raise SystemExit("错误: 目录必须属于 UID/GID 10001，权限建议 0750，禁止组写入及其他用户访问")
if action == "init":
    print("持久化目录初始化完成:", root)
    sys.exit(0)
if action in ("migrate", "backup"):
    if run(compose + ["ps", "--status", "running", "-q", "backend"]).strip():
        raise SystemExit("错误: 数据维护前必须停止后端写入")
psql = compose + ["exec", "-T", "postgres", "sh", "-c",
    'exec psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
def sql(statement):
    return run(psql, "SET standard_conforming_strings=on;\n" + statement).removeprefix("SET\n").strip()
def literal(value):
    return "'" + str(value).replace("'", "''") + "'"
def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()
def safe_path(base, key):
    target = base / key
    if target.resolve() != target or not target.is_relative_to(base):
        raise SystemExit("错误: 文件路径越界或包含符号链接")
    return target
if action == "backup":
    backup_root = pathlib.Path(os.environ["KNOWLEDGE_BACKUP_ROOT"])
    if not backup_root.is_absolute() or backup_root.resolve() != backup_root or backup_root.is_relative_to(root):
        raise SystemExit("错误: 备份目录必须为持久化目录外的绝对路径")
    backup = backup_root / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    backup.mkdir(parents=True, mode=0o700)
    with (backup / "database.dump").open("xb") as output:
        subprocess.run(compose + ["exec", "-T", "postgres", "sh", "-c",
            'exec pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdout=output, check=True)
    with tarfile.open(backup / "knowledge.tar", "x") as archive:
        archive.add(root, arcname="knowledge")
    print("备份完成:", backup)
    sys.exit(0)
if action == "migrate":
    migration = pathlib.Path(app) / "sql/migrations/20261112_drop_knowledge_vector_tasks.sql"
    sql(migration.read_text())
rows = json.loads(sql("SELECT coalesce(json_agg(d), '[]') FROM (SELECT id,user_id,type,content,metadata,content_hash,file_status FROM knowledge_documents ORDER BY id) d;"))
references = set()
for row in rows:
    key = row["content"]
    meta = row["metadata"] or {}
    if row["type"] != 1:
        continue
    if not re.fullmatch(r"user-" + str(row["user_id"]) + r"/[a-f0-9]{32}\.(pdf|md)", key):
        raise SystemExit("错误: 原文件标识无效，文档编号=" + str(row["id"]))
    for field in ("pendingKey", "retiredFile"):
        if meta.get(field):
            references.update((meta[field], meta[field] + ".part"))
    references.update((key, key + ".part"))
    if row["file_status"] == 1:
        target = safe_path(root, key)
        if not target.is_file() or target.stat().st_size != meta.get("fileSize") or digest(target) != row["content_hash"]:
            raise SystemExit("错误: 原文件缺失或摘要不符，文档编号=" + str(row["id"]))
for path in root.rglob("*"):
    if path.is_symlink():
        raise SystemExit("错误: 持久化目录包含符号链接")
    if path.is_file() and path.relative_to(root).as_posix() not in references:
        print("待人工核查的未引用文件:", path.relative_to(root))
print("原文件检查通过，文档数量=", len(rows))
PYTHON
}

if [[ -n "${STORAGE_ACTION:-}" ]]; then
  case "$STORAGE_ACTION" in
    init|check|migrate|backup) storage_maintenance "$STORAGE_ACTION" ;;
    *) echo "错误: 不支持的文件维护操作" >&2; exit 1 ;;
  esac
  exit 0
fi

require_deploy_sha
if [[ "${SKIP_SYNC:-}" != "1" ]]; then
  echo "== 1) 同步代码 =="
  sync_release_commit
else
  echo "== 1) 使用本次发布已同步的代码 =="
fi
require_head_matches_sha

echo "== 2) 检查环境变量 =="
require_env_file
require_env_keys

echo "== 3) 检查知识库原文件 =="
storage_maintenance check

echo "== 4) 构建并部署 backend =="
cd "$COMPOSE_DIR"
compose build backend
# 只重建应用容器，保留已有数据库和 Redis 数据卷。
compose up -d --no-deps --force-recreate backend

echo "== 5) 确认后端健康 =="
if ! wait_for_http_body "$HEALTH_URL" '"status":"UP"'; then
  docker logs --tail 50 "$CONTAINER_NAME" >&2 || true
  exit 1
fi

echo "Backend deploy done."

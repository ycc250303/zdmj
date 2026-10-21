# CI/CD

GitHub Actions 上 **CI 与 CD 分离**：测试不部署。后端部署必须等同一提交的 CI 通过。另有一条服务器例行清理，与 CD 共用并发组，不发版。

| 流水线 | 文件 | 作用 |
|--------|------|------|
| CI | [`.github/workflows/ci_java.yml`](../../.github/workflows/ci_java.yml) | `./mvnw -B -ntp clean verify`：单元测试、契约测试、集成测试、JaCoCo 和 ArchUnit |
| CD | [`.github/workflows/cd.yml`](../../.github/workflows/cd.yml) | SSH 到服务器，按变更部署后端和/或前端 |
| Ops | [`.github/workflows/ops_server_housekeeping.yml`](../../.github/workflows/ops_server_housekeeping.yml) | 每周清理 Docker/pnpm/Git 占用 |

**没有前端 CI**（无 `pnpm typecheck` / `lint`）。发往 `backend` 或 `main` 的推送和 Pull Request，只要命中后端、SQL 或本工作流，就会跑 CI。密钥配置见 [`deploy/GitHub密钥配置.md`](../../deploy/GitHub密钥配置.md)。

```
push/PR → backend 或 main（改了后端、SQL 或 CI 工作流） → CI（ubuntu-latest，不碰服务器）
push    → main（改了前后端/部署）  →  CD（后端部署先确认同一提交 CI 成功）
cron    → 每周一 11:30 CST         →  Ops housekeeping
```

---

## 触发条件

### CI（`CI - Backend Tests`）

| 事件 | 分支 | 路径（任一命中） |
|------|------|------------------|
| `push`、`pull_request`（目标） | `backend`、`main` | `backend/zdmj/**`、`sql/**`、本 yml |

只改前端不会跑后端 CI。

### CD（`CD - Deploy`）

| 事件 | 条件 |
|------|------|
| `push` | 分支 `main`，且路径命中 `backend/zdmj/**`、`client/**`、`deploy/**`、本 yml、`.github/scripts/ssh_run.sh` |
| `workflow_dispatch` | Actions 页面手动勾选「部署后端」和/或「部署 main 前端」 |

workflow 触发后还有二次过滤（`dorny/paths-filter@v3`）：只改 yml/脚本、但未改业务或 `deploy_*.sh` / `docker-compose.yml` 时，**deploy job 会被 skip**（整次 run 仍算成功）。

| 检测输出 | 命中路径 | 随后动作 |
|----------|----------|----------|
| `java=true` | `backend/zdmj/**`、`deploy/deploy_backend.sh`、`sync_server_code.sh`、`docker-compose.yml` | 跑 `deploy_backend.sh` |
| `frontend=true` | `client/**`、`client/Dockerfile`、`deploy_frontend.sh`、`docker-compose.yml`、`nginx.conf`、`sync_server_code.sh`、`.dockerignore` | 跑 `deploy_frontend.sh` |

### Ops

`schedule: cron '30 3 * * 1'`（北京时间周一 11:30）或 `workflow_dispatch`。

---

## 通过条件

### CI 通过 = `./mvnw -B -ntp clean verify` 退出码 0

Runner 使用 Temurin JDK 21，只调用仓库内的 `./mvnw`。`verify` 依次执行 Surefire、Failsafe 和 JaCoCo `check`；ArchUnit 由 Surefire 执行。

| 门禁 | 判定 |
|------|------|
| 测试 | Surefire 与 Failsafe 均无 failures/errors |
| JaCoCo | `common` 行 ≥ 80%、分支 ≥ 70%；七个业务模块合计行 ≥ 70%、分支 ≥ 55% |
| 架构 | `ModuleArchitectureTest` 通过；冻结记录不允许在 CI 中更新 |

失败时直接查看工作流日志以及 Surefire、Failsafe 和 JaCoCo 报告。

### CD 通过

- SSH 在 4 次重试内连上，远端脚本退出码 0。
- 后端：`.env` 存在且必填键非空 → `compose build/up backend` 成功。**无 HTTP 探活**，只 `sleep 10` + `docker logs`。
- 前端：`compose build/up frontend-main` 成功。宿主机 nginx 若在跑会先 `systemctl stop nginx`（不卸载）。

后端部署前轮询同一 `GITHUB_SHA` 的 `ci_java.yml`，最多约 20 分钟。结论不是 `success` 则不部署后端。只部署前端时不要求这次 CI。Docker prune 失败被 `|| true` 忽略，不导致 CD 失败。

### Ops 通过

SSH 成功即可。清理命令均 `|| true`，磁盘回收失败不影响 run 结果。

---

## CI 步骤（命令级）

Runner：`ubuntu-latest`。工作目录除注明外为 `backend/zdmj`。shell 默认 `bash -eo pipefail`。

| # | 步骤 | 命令 / 动作 | 角色 |
|---|------|-------------|------|
| 1 | Checkout | `actions/checkout@v4` | 拉代码 |
| 2 | JDK 21 | `actions/setup-java@v4`（Temurin 21，`cache: maven`） | 环境 |
| 3 | 验收 | `./mvnw -B -ntp clean verify` | 硬门禁 |
| 4 | 上传 | Surefire、Failsafe、JaCoCo | 始终上传 |

```bash
./mvnw -B -ntp clean verify
```

`verify` 在测试结束后生成 JaCoCo 报告并执行覆盖率检查。测试质量由明确的业务断言、代码审查、契约测试、容器集成测试和 ArchUnit 共同保证。

集成测试在同一 JVM 内共享 `pgvector/pgvector:pg15` 与 `redis:7`，Failsafe `forkCount=1`。Schema 只执行仓库根目录 `sql/pgsql.sql`。测试进程把 `ZDMJ_ENV_FILE` 指到空文件。`*ContractTest` 在 `test` 阶段执行，`*IT` 在 `verify` 阶段执行。ArchUnit 冻结文件在 `src/test/resources/archunit_store`，随仓库提交。`allowStoreCreation=false`，`allowStoreUpdate=false`。

---

## CD 步骤（命令级）

并发组 `zdmj-server-deploy`，`cancel-in-progress: false`（与 Ops 互斥排队，不取消进行中的部署）。超时：detect 5 min，deploy 45 min。

| # | Job / 步骤 | 作用 |
|---|------------|------|
| 1 | `detect-changes` | checkout + 路径过滤，输出 `java` / `frontend` |
| 2 | `Resolve deploy targets` | push 用过滤结果；`workflow_dispatch` 用勾选 |
| 3 | `Require CI success` | 部署后端时等待同一提交的 CI `success` |
| 4 | `Deploy on server` | 见下；两目标都 false 则跳过本 step |

Runner 侧 SSH 封装（密钥来自 `secrets.SSH_PRIVATE_KEY`）：

```bash
bash .github/scripts/ssh_run.sh 111.229.81.45 root 22 4 20 2700
# 参数：host user port 最大重试 重试间隔秒 远端超时秒
```

`ssh_run.sh`：把私钥写入临时文件 → `ssh-keyscan` → `ssh -i ... -o BatchMode=yes` 执行 stdin 里的 bash；失败则最多再试 3 次（间隔 20s）。

远端固定目录 `/opt/zdmj/zdmj`。**同一 SSH 会话内先后端后前端**，避免两次 git fetch 抢锁。

### 后端（`deploy/deploy_backend.sh`）

```bash
# 1) 同步代码（有锁；等价于）
flock .git/deploy.lock git fetch --prune origin
git checkout main && git reset --hard origin/main

# 2) 校验 /opt/zdmj/zdmj/.env 存在，且下列键非空：
#    PG_USER PG_PASSWORD PG_DB REDIS_PASSWORD DATASOURCE_URL
#    DASHSCOPE_API_KEY COS_SECRET_ID COS_SECRET_KEY COS_REGION COS_BUCKET
#    MAIL_USERNAME MAIL_PASSWORD JWT_SECRET

# 3) 构建并替换容器（cwd=deploy/）
docker compose --env-file /opt/zdmj/zdmj/.env build backend
docker compose --env-file /opt/zdmj/zdmj/.env up -d --no-deps --force-recreate backend
docker compose --env-file /opt/zdmj/zdmj/.env ps backend

# 4) 观察启动（非 HTTP 探活）
sleep 10
docker logs --tail 50 zdmj-backend
```

镜像内（[`backend/zdmj/Dockerfile`](../../backend/zdmj/Dockerfile)）：`mvn -B -ntp clean package -Dmaven.test.skip=true`（失败再试一次），JRE 21 Alpine 跑 `java -jar app.jar`，映射 `:8080`。

CD 在后端部署后再做磁盘回收（失败忽略）：

```bash
docker builder prune -af --filter "until=168h" || true
docker image prune -af --filter "until=168h" || true
```

### 前端（`deploy/deploy_frontend.sh`）

环境：`APP_DIR=/opt/zdmj/zdmj` `DEPLOY_BRANCH=main` `FRONTEND_SERVICE=frontend-main`。

```bash
# 1) 同上 git 同步（分支 main）
# 2) 若宿主机 nginx 在跑：systemctl stop nginx
# 3)
docker compose --env-file /opt/zdmj/zdmj/.env build frontend-main
docker compose --env-file /opt/zdmj/zdmj/.env up -d --no-deps --force-recreate frontend-main

sleep 3
docker logs --tail 30 zdmj-frontend-main
```

镜像内（[`client/Dockerfile`](../../client/Dockerfile)，构建上下文为仓库根）：`pnpm install --frozen-lockfile --ignore-scripts` → `pnpm build` → Nginx 托管 `dist`，`:80`。构建时把 `nginx.conf` 里的 `127.0.0.1:8080` 换成 Docker 网络名 `zdmj-backend:8080`。

---

## 例行清理（Ops）

同一 `ssh_run.sh`（超时 1200s），在 `/opt/zdmj/zdmj`：

```bash
df -h; free -h; docker system df
docker builder prune -af --filter "until=168h" || true
docker image prune -af --filter "until=168h" || true
docker container prune -f || true
docker network prune -f || true
# 若有 nvm/pnpm：
pnpm store prune || true
git gc --prune=now || true
df -h; free -h; docker system df
```

**不**执行 `docker system prune`（仓库运维约定禁止未授权删除）。

---

## 已知限制

| 项 | 现状 |
|----|------|
| 前端质量 | 无 CI；构建错误只在 CD 的 Docker 阶段暴露 |
| 探活 | 无 `curl` / Spring Actuator HTTP 检查，进程挂了可能仍报 CD 成功 |
| 架构冻结 | 已有跨模块内部依赖记在 `archunit_store`；新增违规会失败，修复后要删掉对应冻结行 |
| 并发 | CD 与 Ops 排队同一组；高峰时部署会等清理结束 |

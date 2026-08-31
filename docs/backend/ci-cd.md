# CI/CD

GitHub Actions 上 **CI 与 CD 分离**：测试不部署，部署不等待测试。另有一条服务器例行清理，与 CD 共用并发组，不发版。

| 流水线 | 文件 | 作用 |
|--------|------|------|
| CI | [`.github/workflows/ci_java.yml`](../../.github/workflows/ci_java.yml) | 后端编译 + 全量单测 + 鉴权模块质量门禁 |
| CD | [`.github/workflows/cd.yml`](../../.github/workflows/cd.yml) | SSH 到服务器，按变更部署后端和/或前端 |
| Ops | [`.github/workflows/ops_server_housekeeping.yml`](../../.github/workflows/ops_server_housekeeping.yml) | 每周清理 Docker/pnpm/Git 占用 |

**没有前端 CI**（无 `pnpm typecheck` / `lint`）。合入 `main` **不会**自动跑 CI（`main` 触发已注释）。密钥配置见 [`deploy/GitHub密钥配置.md`](../../deploy/GitHub密钥配置.md)。

```
push/PR → backend 分支（改了后端）  →  CI（ubuntu-latest，不碰服务器）
push    → main（改了前后端/部署）  →  CD（SSH → /opt/zdmj/zdmj）
cron    → 每周一 11:30 CST         →  Ops housekeeping
```

---

## 触发条件

### CI（`CI - Backend Tests`）

| 事件 | 分支 | 路径（任一命中） |
|------|------|------------------|
| `push` | `backend` | `backend/zdmj/**`、`.cursor/skills/ai-unit-test-loop/**`、本 yml |
| `pull_request`（目标） | `backend` | 同上 |

`main` 的 push/PR 已注释，改前端或只合主干都 **不跑 CI**。

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

### CI 通过 = 下列硬门禁全部 `success`

各业务 step 写了 `continue-on-error: true`，中间失败不立刻红；**最后一步**读 `outcome`，任一硬门禁非 success 则 `exit 1`。

| 门禁 | 判定 |
|------|------|
| 编译 | `mvn clean compile` 退出码 0 |
| 单测 | `mvn test` 退出码 0（Surefire 有 failures/errors 即失败） |
| 覆盖率 | 仅 `userAuthService` 的 `service/impl` + `util`：行 ≥ 70%、分支/条件 ≥ 55%；匹配到 0 个类也失败 |
| 路径 | `spec-user-auth.json` 生成矩阵后，必测 path 全部命中，且覆盖条数 ≥ 6 |
| 反投机 | 仅扫 `src/test/java/com/zdmj/userAuthService`：`riskScore ≥ 80` 且无 `mustFix` |

**不决定红绿**：步骤 6「失败归因」（只在单测失败时跑）。门禁脚本在 `.cursor/skills/ai-unit-test-loop/scripts/`（该目录 gitignore），runner checkout **默认没有这些文件**；脚本缺失时覆盖率/路径/审计 step 失败，CI 红。

### CD 通过

- SSH 在 4 次重试内连上，远端脚本退出码 0。
- 后端：`.env` 存在且必填键非空 → `compose build/up backend` 成功。**无 HTTP 探活**，只 `sleep 10` + `docker logs`。
- 前端：`compose build/up frontend-main` 成功。宿主机 nginx 若在跑会先 `systemctl stop nginx`（不卸载）。

CD **不检查 CI 是否绿**。镜像能构建、容器能起来即过。Docker prune 失败被 `|| true` 忽略，不导致 CD 失败。

### Ops 通过

SSH 成功即可。清理命令均 `|| true`，磁盘回收失败不影响 run 结果。

---

## CI 步骤（命令级）

Runner：`ubuntu-latest`。工作目录除注明外为 `backend/zdmj`。shell 默认 `bash -eo pipefail`。

| # | 步骤 | 命令 / 动作 | 角色 |
|---|------|-------------|------|
| 1 | Checkout | `actions/checkout@v4` | 拉代码 |
| 2 | JDK 21 | `actions/setup-java@v4`（Temurin 21，`cache: maven`） | 环境 |
| 3 | 编译 | `mvn -B -ntp clean compile` | 硬门禁 |
| 4 | 单测 | 见下 | 硬门禁；编译失败则跳过 |
| 5 | 覆盖率/路径 | 见下 | 硬门禁；编译或单测失败则跳过 |
| 6 | 归因 | `python3 ../../.cursor/skills/ai-unit-test-loop/scripts/triage_failures.py --surefire-dir target/surefire-reports` | 仅单测失败时；不影响红绿 |
| 7 | 审计 | 见下 | 硬门禁；须编译+单测已过 |
| 8 | 汇总 | 写 `$GITHUB_STEP_SUMMARY`；`failed=1` 则 `exit 1` | 最终拦截 |

步骤 4：

```bash
mvn -B -ntp test
# 随后从 target/surefire-reports/TEST-*.xml 打印 failures/errors≠0 的摘要（仅日志）
```

`pom.xml` 在 `test` 阶段同时跑 JaCoCo `report`，产出 `target/site/jacoco/jacoco.xml`。

步骤 5（两段必须都成功）：

```bash
python3 ../../.cursor/skills/ai-unit-test-loop/scripts/coverage_gate.py \
  --jacoco-xml target/site/jacoco/jacoco.xml \
  --class-prefix com/zdmj/userAuthService/service/impl/,com/zdmj/userAuthService/util/ \
  --line-min 0.70 --branch-min 0.55 --condition-min 0.55

python3 ../../.cursor/skills/ai-unit-test-loop/scripts/path_gate.py \
  --spec src/test/resources/test-loop/spec-user-auth.json \
  --target-classes UserServiceImpl,VerificationCodeServiceImpl,EmailServiceImpl,JwtUtil,VerificationCodeSceneResolver \
  --scan-tests src/test/java/com/zdmj/userAuthService \
  --manifest-out target/testloop/path-matrix.json \
  --path-count-min 6 \
  --surefire-dir target/surefire-reports
```

步骤 7：

```bash
python3 ../../.cursor/skills/ai-unit-test-loop/scripts/audit_quality.py \
  --test-root src/test/java/com/zdmj/userAuthService --risk-pass 80
```

审计扣分规则（本地脚本）：弱断言占比 >20% −15；happy-path 主导 >70% −15；无 `@Test` −30；断言数 < 测试方法数 −5。存在 `mustFix` 或分数 < 80 即失败。

---

## CD 步骤（命令级）

并发组 `zdmj-server-deploy`，`cancel-in-progress: false`（与 Ops 互斥排队，不取消进行中的部署）。超时：detect 5 min，deploy 45 min。

| # | Job / 步骤 | 作用 |
|---|------------|------|
| 1 | `detect-changes` | checkout + 路径过滤，输出 `java` / `frontend` |
| 2 | `Resolve deploy targets` | push 用过滤结果；`workflow_dispatch` 用勾选 |
| 3 | `Deploy on server` | 见下；两目标都 false 则跳过本 step |

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
| CI 覆盖范围 | 只拦后端；质量门禁只卡鉴权模块，不是全仓覆盖率 |
| CI ↔ CD | 独立；`main` 部署不要求 CI 绿 |
| 前端质量 | 无 CI；构建错误只在 CD 的 Docker 阶段暴露 |
| 探活 | 无 `curl` / Spring Actuator HTTP 检查，进程挂了可能仍报 CD 成功 |
| 门禁脚本 | `.cursor/skills` gitignore，远程 runner 默认缺失 |
| 并发 | CD 与 Ops 排队同一组；高峰时部署会等清理结束 |

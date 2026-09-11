# LLM 长耗时任务：Redis Stream 异步方案

对齐 [interview-guide](https://github.com/Snailclimb/interview-guide) 的 **Producer/Consumer 模板方法**（先落库 PENDING 再 `XADD`、失败也 ACK、`MAXLEN`、前端轮询）。不引入 Redisson；对话保持 SSE。单实例；**不做**自动重入队与 `XCLAIM`（失败标 FAILED，用户再点；重启靠固定 `consumerName` 排空本 PEL）。

**落地进度**：一期骨架 + 二期域执行器 + 三期生成 POST 改 `Result<AsyncTaskDTO>` 与前端 2s 轮询 + 四期向量化走 `zdmj:embed:stream`（仍写 `knowledge_vector_tasks`，不双写 `async_llm_tasks`）。失败即 FAILED，无自动重试 / XCLAIM。

任务状态落在 `async_llm_tasks`（相当于参考文的 `vectorStatus`），**不**写入匹配/报告等业务表。

## 1 现状与做法

| 项 | 做法 |
| --- | --- |
| 画像 / 匹配 / 报告 / 简历解析 | POST 入队，消费者调现有 generate*，HTTP 立即返回 taskId |
| 知识库向量化 | `knowledge_vector_tasks` + Embed Stream，与 LLM 流隔离限流 |
| 前端 | PENDING/RUNNING 时 2s 轮询任务，终态再拉业务 GET |

## 2 范围

HTTP 立即返回 `taskId`；消费者调现有 `executeXxx`（无 HTTP）。

| 类型 | `bizKey` | 接口 |
| --- | --- | --- |
| `STUDENT_PROFILE` | `user:{userId}` | 学生画像 generate |
| `JOB_PROFILE` | `user:{userId}:job:{jobId}` | 岗位画像 generate（**按用户隔离**） |
| `JOB_GRAPH` | `user:{userId}:job:{jobId}` | 按用户隔离 |
| `JOB_MATCH` | `user:{userId}:job:{jobId}` | 人岗匹配 |
| `CAREER_REPORT` | `user:{userId}:job:{jobId}` | 报告生成 |
| `REPORT_POLISH` / `REPORT_CHECK` | `report:{reportId}` | 润色 / 完整性 |
| `RESUME_PARSE` | `user:{userId}` | 简历识别 |
| `KB_EMBED` / `KB_DELETE` | 仍用 `knowledge_vector_tasks`（不写 async_llm_tasks） | Embed Stream |

**不入队**：SSE 对话；RAG 查询 Embedding；纯 GET / 权重查询 / 报告手动保存。

报告缺依赖：消费者内 **同步** 调现有 Service（不再 enqueue）。读/写岗位画像必须带 **当前任务的 userId**，禁止复用他人行。一期不做任务编排。

## 3 选型

不选 Kafka/RabbitMQ：规模小，Redis 已在 Compose。不用 PG `SKIP LOCKED` 做主队列：向量化写放大不打业务库。

| 项 | 参考文 | 本项目 |
| --- | --- | --- |
| 客户端 | Redisson | `RedisUtil` + `StringRedisTemplate` |
| Stream | 每类一条（向量化 / 简历 / 面试） | **两条**：`zdmj:llm:stream`、`zdmj:embed:stream`（限流不同） |
| 消息 | 业务 id + content | **仅标识**：`taskId`；正文在 `payload` JSONB |
| 状态 | 写在知识库/简历实体 | `async_llm_tasks` |
| 去重 | 简历内容哈希 | 同一用户同一 `bizKey`：部分唯一索引（连点不双跑） |
| 消费 | 组 + ACK + 限次重入队 | 组 + ACK；失败即 FAILED；`claim` 防 at-least-once 双跑 LLM |
| 轮询 | 5s 实体状态 | 2s `GET /async-tasks/{id}`，完成后再拉业务 GET |

`BATCH_SIZE`：LLM 流 `count=1`；Embedding 可大于 1。

## 4 流程

```
POST 生成（Controller 只调 enqueueXxx）
  → INSERT async_llm_tasks PENDING（payload JSONB）
       唯一索引冲突 → 返回已有 taskId
  → XADD taskId（MAXLEN ~ 1000）
       失败 → 标 FAILED（禁止孤儿 PENDING 占坑）
  → HTTP 200，Result.code=0，{ taskId, status: PENDING }

AbstractStreamConsumer
  → 启动：ensureConsumerGroup；consumerName 固定为 llm-consumer（单实例）
  → 非阻塞 XREADGROUP 偏移 0-0，排空本消费者 PEL
  → 循环 XREADGROUP BLOCK（>）
  → claim PENDING|RUNNING→RUNNING；0 行 → ACK 丢弃（终态或重复投递）
  → 行不存在 → ACK 丢弃
  → UserContext.of(userId) + processBusiness
  → SUCCESS 或 FAILED；XACK（不自动重入队）
```

## 5 模板方法（学习对齐）

`com.zdmj.common.async`：

```
AsyncTaskType / AsyncTaskStatus
AsyncLlmTask + Mapper
AsyncTaskService          # enqueue（INSERT+XADD）与 GET
AsyncTaskDTO / AsyncTaskController
LlmStreamProducer / knowledgeService.support.EmbedStreamProducer
AbstractStreamConsumer
LlmStreamConsumer / knowledgeService.support.EmbedStreamConsumer
AsyncTaskExecutor         # 域 support 适配器
```

Consumer 只填 claim / processClaimed / 流名。不要为每类任务各写一套生命周期。

域 Service 提供 `enqueueXxx`（廉价校验 + 入队）；消费者禁止再 enqueue。登录态：`UserContext.of(userId, "async-task")`，`finally` 清理。

## 6 数据与权限

表已有（`sql/migrations/20260901_create_async_llm_tasks.sql`）。`uk_async_llm_tasks_inflight`：`UNIQUE (task_type, biz_key) WHERE status IN (1,2)`。SUCCESS/FAILED 不占坑，再点 = 新任务（参考文手动重试）。互斥只靠该索引，不再另加 Redis 锁。

**无共享任务。** `GET /async-tasks/{id}`：仅本人；否则 `ASYNC_TASK_NOT_FOUND`（13001）。失败任务返回 DTO（`status=FAILED` + `errorMessage`），轮询不抛 13002。

`JOB_PROFILE` 产物已按用户落库：`user_id` + `UNIQUE(user_id, job_id)`，与异步 `bizKey` 一致。

匹配 `bizKey` 不含 weights：进行中改权重仍返回第一次任务（产品选择）。

`KB_*` 不写入 `async_llm_tasks`。向量化走 `knowledge_vector_tasks` + `zdmj:embed:stream`；启动时补 XADD 仍为 PENDING/RUNNING 的行。

## 7 可靠性

- `XACK` 不删 Stream 条目；靠 `MAXLEN ~ 1000` 近似裁剪。
- 成功 / 失败 **都 ACK**；不自动重入队。LLM 失败标 FAILED，用户再点（终态不占 `uk_inflight`）。
- **单实例**：`consumerName=llm-consumer` / `embed-consumer`。多实例共用该名会抢同一 PEL，不要水平扩消费者。
- 启动时 `XREADGROUP` 偏移 `0-0` 排空本 PEL；`claim` 允许 PENDING 与 RUNNING（崩溃重启时行可能已是 RUNNING）。
- 不做 `XCLAIM`、不做滞留扫描。Stream 被 `MAXLEN` 裁掉未读消息时，DB 可能残留 PENDING，需人工处理或用户换 bizKey。

作业（不做）：死信流、SSE 推任务状态、Python Worker、多实例抢消息。

## 8 契约与前端

生成 POST：`Result<AsyncTaskDTO>`，HTTP **200**。业务 DTO 仍走原 GET；未完成 `data=null`。`RESUME_PARSE` 成功结果在任务 `result`。

前端：仅当任务 PENDING/RUNNING 时 2s 静默轮询 `GET /async-tasks/{id}`；终态再拉业务 GET。FAILED 展示 `errorMessage`（HTTP 仍成功）。知识库页仍用文档 `embeddingStatus`。

## 9 实施切分

| 部分 | 改动 |
| --- | --- |
| 数据库 | 任务表已有。岗位画像已按用户隔离 |
| 后端 | 入队 + GET 任务 + LLM/Embed 消费者 + 域 enqueueXxx |
| 前端 | POST 后 2s 轮询任务，完成拉业务结果 |
| 不改 | SSE 对话；不引入 Redisson；岗位图谱生成仍同步 |

顺序：模板 → 执行器 → 改 POST/前端轮询 → 迁向量化（已完成）。

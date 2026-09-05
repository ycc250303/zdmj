# LLM 长耗时任务：Redis Stream 异步方案

对齐 [interview-guide](https://github.com/Snailclimb/interview-guide) 的 **Producer/Consumer 模板方法**（先落库 PENDING 再 `XADD`、失败也 ACK、限次重入队、`MAXLEN`、前端轮询）。不引入 Redisson；对话保持 SSE。本文为设计；**一期骨架已落地**（Producer/Consumer、enqueue、GET 任务）。域 enqueue 与画像按用户隔离见第 9 节。

任务状态落在 `async_llm_tasks`（相当于参考文的 `vectorStatus`），**不**写入匹配/报告等业务表。

## 1 现状与问题

| 现状 | 问题 |
| --- | --- |
| 画像 / 匹配 / 报告 / 简历解析：Controller 同步 `chatStructuredOnce` | HTTP 被占数十秒～数分钟，易 504 |
| 知识库向量化：`knowledge_vector_tasks` + `@Async` | 进程内线程池，多实例不共享，重启靠扫 PENDING |
| 前端 `generatingXxx` | 只挡本页；多标签每次都打 LLM |
| 岗位画像已按 `(user_id, job_id)` 隔离 | 异步 `bizKey` 须与产物一致，禁止做成共享任务 |

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
| `KB_EMBED` / `KB_DELETE` | `doc:{documentId}` | 四期替换 `@Async` |

**不入队**：SSE 对话；RAG 查询 Embedding；纯 GET / 权重查询 / 报告手动保存。

报告缺依赖：消费者内 **同步** 调现有 Service（不再 enqueue）。读/写岗位画像必须带 **当前任务的 userId**，禁止复用他人行。一期不做任务编排。

## 3 选型

不选 Kafka/RabbitMQ：规模小，Redis 已在 Compose。不用 PG `SKIP LOCKED` 做主队列：向量化写放大不打业务库。

| 项 | 参考文 | 本项目 |
| --- | --- | --- |
| 客户端 | Redisson | `RedisUtil` + `StringRedisTemplate` |
| Stream | 每类一条（向量化 / 简历 / 面试） | **两条**：`zdmj:llm:stream`、`zdmj:embed:stream`（限流不同） |
| 消息 | 业务 id + content | **仅标识**：`taskId, type, userId, bizKey, retryCount`；正文在 `payload` JSONB |
| 状态 | 写在知识库/简历实体 | `async_llm_tasks` |
| 去重 | 简历内容哈希 | 同一用户同一 `bizKey`：`SET NX` + 部分唯一索引（对齐「连点不双跑」） |
| 消费 | 组 + ACK + 限次重入队 | 同左；`claim` 防 at-least-once 双跑 LLM |
| 轮询 | 5s 实体状态 | 2s `GET /async-tasks/{id}`，完成后再拉业务 GET |

`BATCH_SIZE`：LLM 流 `count=1`；Embedding 可大于 1。

## 4 流程

```
POST 生成（Controller 只调 enqueueXxx）
  → SET zdmj:tasklock:{type}:{bizKey} NX EX 600
       未抢到 → 查进行中任务，返回同一 taskId（不报错）
  → INSERT async_llm_tasks PENDING（payload JSONB）
       唯一索引冲突 → 返回已有 taskId
  → XADD（MAXLEN ~ 1000）
       失败 → 标 FAILED（禁止孤儿 PENDING 占坑）
  → HTTP 200，Result.code=0，{ taskId, status: PENDING }

AbstractStreamConsumer
  → 启动：ensureConsumerGroup；consumerName = 前缀 + UUID
  → XREADGROUP BLOCK（>）
  → claim PENDING→RUNNING；0 行 → ACK 丢弃（重复投递）
  → 行不存在 → ACK 丢弃（参考文坑 2）
  → UserContext.of(userId) + executeXxx
  → SUCCESS/FAILED；DEL lock；XACK
  → 可恢复失败：先打回 PENDING，再 XADD retryCount+1，然后 ACK 原消息（参考文坑 3）
```

## 5 模板方法（学习对齐）

`com.zdmj.common.async`：

```
AsyncTaskType / AsyncTaskStatus
AsyncLlmTask + Mapper（claim / success / fail）
AsyncTaskService          # enqueue：锁 + INSERT + XADD
AbstractStreamProducer<T> # sendTask：xaddTask；失败 onSendFailed
AbstractStreamConsumer<T> # init / consumeLoop / processMessage / ACK / retry
LlmStreamProducer / LlmStreamConsumer     # streamKind=LLM
EmbedStreamProducer / EmbedStreamConsumer # 四期；streamKind=EMBED
AsyncTaskController       # GET /async-tasks/{id}
```

Producer 子类只填 `streamKey`、`buildMessage`（五字段）、`onSendFailed`。  
Consumer 子类只填 `parsePayload`、`processBusiness`（按 `type` 调域 `executeXxx`）、`mark*` 走 Mapper。  
不要为九类任务各写一套生命周期。

域 Service 拆 `enqueueXxx` / `executeXxx`。Consumer 禁止再 enqueue。登录态：`UserContext.of(userId, "async-task")`，`finally` 清理。

## 6 数据与权限

表已有（`sql/migrations/20260901_create_async_llm_tasks.sql`）。`uk_async_llm_tasks_inflight`：`UNIQUE (task_type, biz_key) WHERE status IN (1,2)`。SUCCESS/FAILED 不占坑，再点 = 新任务（参考文手动重试）。

锁 TTL 600s 须大于单次 LLM；锁只是快路径，互斥以唯一索引为准。Redis 异常 fail-open。

**无共享任务。** `GET /async-tasks/{id}`：仅本人；否则 `ASYNC_TASK_NOT_FOUND`（13001）。失败任务返回 DTO（`status=FAILED` + `errorMessage`），轮询不抛 13002。

`JOB_PROFILE` 产物已按用户落库：`user_id` + `UNIQUE(user_id, job_id)`，与异步 `bizKey` 一致。读/写必须带当前任务的 `userId`。

匹配 `bizKey` 不含 weights：进行中改权重仍返回第一次任务（产品选择）。

`KB_*` 一期仍用 `knowledge_vector_tasks`，不双写；四期再迁。

## 7 可靠性

- `XACK` 不删 Stream 条目；靠 `MAXLEN ~ 1000` 近似裁剪（参考文坑 1）。
- 成功 / 失败 / 不可恢复 **都 ACK**；重试是新消息，不是留 PEL。
- 超 3 次标 FAILED。LLM 超时沿用 `ChatUtil`。
- 单机 1 个 LLM 消费者 + 1 个 embed 消费者；多实例组名相同、`consumerName` 带 UUID。
- PEL 空闲 ≥700s（> 锁 TTL）再 `XCLAIM`：认领超时 **RUNNING** 并重跑，避免占坑假死。窗口内原消费者若仍存活，可能双跑（用时间换）。
- 重入队前必须回到 PENDING，否则 claim 永远 0 行。

作业（一期不做）：死信流、SSE 推任务状态、Python Worker。

## 8 契约与前端

`POST` 生成：`Result<AsyncTaskDTO>`，HTTP **200**。业务 DTO 仍走原 GET；未完成 `data=null`。

前端：仅当任务 PENDING/RUNNING 时 2s 静默轮询任务接口；终态再拉业务 GET。对齐参考文「条件轮询、不闪 loading」。

## 9 实施切分

| 部分 | 改动 |
| --- | --- |
| 数据库 | 任务表已有。`job_capability_profiles` 已按用户隔离 |
| 后端 | Producer/Consumer 模板；`AsyncTaskService.enqueue`；GET 任务；九类 enqueue；四期下线 embedding `@Async` |
| 前端 | POST 后轮询任务，完成拉业务结果 |
| 不改 | SSE 对话；不引入 Redisson |

顺序：模板 + 去重/claim 单测 → 匹配与两类画像 → 报告/解析 → 迁向量化。

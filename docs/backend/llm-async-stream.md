# LLM 长耗时任务：Redis Stream 异步方案

相对 [`redis-stream.md`](./redis-stream.md) 的调整：不引入 Redisson；补**业务去重**；只覆盖「一次请求等一轮结构化 LLM / Embedding」的接口；流式对话保持 SSE。本文为设计，尚未落地。

## 1 现状与问题

| 现状 | 问题 |
| --- | --- |
| 画像 / 匹配 / 图谱 / 报告 / 简历解析：Controller 同步调 `chatStructuredOnce` | HTTP 线程被占 数十秒～数分钟；axios 5～10 min、Nginx 600s 仍可能 504 |
| 知识库向量化：`knowledge_vector_tasks` + `@Async("embeddingExecutor")` | 进程内线程池，多实例不共享、重启靠 `@PostConstruct` 扫 PENDING |
| Redis | 已有 `StringRedisTemplate`（限流、验证码），无 Stream 消费者 |
| 连点 | 前端 `generatingXxx` 挡同页；后端每次都打 LLM，upsert 后写覆盖 |

## 2 范围

**入队（HTTP 立即返回任务）**

| 任务类型 | 业务键 `bizKey` | 说明 |
| --- | --- | --- |
| `STUDENT_PROFILE` | `user:{userId}` | `POST /student-capability-profiles/generate` |
| `JOB_PROFILE` | `job:{jobId}` | 岗位画像，全站共享 |
| `JOB_GRAPH` | `job:{jobId}` | 职业图谱，全站共享 |
| `JOB_MATCH` | `user:{userId}:job:{jobId}` | 人岗匹配 |
| `CAREER_REPORT` | `user:{userId}:job:{jobId}` | 报告生成 |
| `REPORT_POLISH` | `report:{reportId}` | 润色 |
| `REPORT_CHECK` | `report:{reportId}` | 完整性检查 |
| `RESUME_PARSE` | `user:{userId}` | `POST /resumes/import/parse` |
| `KB_EMBED` / `KB_DELETE` | `doc:{documentId}` | 替换现有 `@Async` |

**不入队**

- 对话 / 学习答疑：SSE 逐 token，入队会毁掉流式体验。
- RAG 查询 Embedding：毫秒～1s，同步即可。
- 纯读写 GET、权重查询、报告手动编辑保存。

报告若缺匹配/图谱：消费者内**同步调用现有 Service**（不再走 HTTP）。第一期不把报告拆成任务编排；耗时仍在消费者，但已释放 Tomcat 线程。

## 3 选型（相对参考文）

| 项 | 参考文 | 本项目 |
| --- | --- | --- |
| 客户端 | Redisson | `StringRedisTemplate` Stream API，不新增中间件 |
| Stream 条数 | 向量化 / 简历 / 面试 三条 | **两条**：`zdmj:llm:stream`、`zdmj:embed:stream`（LLM 与向量化限流不同） |
| 去重 | 无（点两次 = 两条消息） | Redis `SET NX` + DB `claim` |
| 状态 | 轮询 5s | 复用现有 GET；另提供任务查询，前端 2s 轮询 |
| 消费 | at-least-once + ACK | 同左；失败 ACK 原消息再限次重入队（最多 3 次） |

不选 Kafka/RabbitMQ：规模小，Redis 已在 Compose 中。不用 PG `SKIP LOCKED` 做主队列：避免向量化写放大打到业务库；DB 只做任务真相与抢占。

## 4 流程

```
POST 生成
  → 校验入参 / 登录
  → SET zdmj:tasklock:{type}:{bizKey} NX EX 600
       失败 → 查进行中任务，返回同一 taskId（去重）
  → INSERT async_llm_tasks PENDING
  → XADD stream {taskId, type, userId, bizKey, payload}
  → 返回 202 + { taskId, status: PENDING }

消费者（组内一名）
  → XREADGROUP
  → UPDATE ... SET RUNNING WHERE id=? AND PENDING   -- claim，0 行则 ACK 丢弃
  → 实体已删 → ACK 丢弃（不可恢复）
  → 调现有 *Service.generate（无 HTTP）
  → SUCCESS / FAILED；DEL lock；XACK
前端：GET 任务 或 GET 业务结果（已完成后与现契约一致）
```

## 5 去重（参考文缺口）

必须同时防两类重复：

1. **用户连点 / 多标签**：同一 `bizKey` 在 PENDING/RUNNING 时不 `XADD`，返回已有 `taskId`。
2. **Stream at-least-once**：同一 `taskId` 被再投递时 `claim` 失败（已非 PENDING）→ ACK 跳过，不跑第二次 LLM。

`JOB_PROFILE` / `JOB_GRAPH` 的 `bizKey` 不含 userId：甲在生成时乙点击，乙拿到同一任务，完成后两人 GET 都看到同一份画像。

锁 TTL 600s 须大于单次 LLM 上限；消费者 SUCCESS/FAILED 时主动 `DEL`。进程宕机靠 TTL 与 PEL 超时 `XCLAIM`（作业，可二期）。

## 6 数据

新建 `async_llm_tasks`（增量脚本放 `sql/migrations/`），**不**把状态塞进匹配/报告业务表。

| 列 | 说明 |
| --- | --- |
| `id` | 任务 ID，即对外 taskId |
| `user_id` | 发起人；消费者无 `UserHolder`，权限与写库用此字段 |
| `task_type` | 上表枚举 |
| `biz_key` | 去重键 |
| `status` | 1 PENDING / 2 RUNNING / 3 SUCCESS / 4 FAILED（与向量任务一致） |
| `payload` | JSONB，如 match 的 weights |
| `error_message` | 失败摘要，不堆栈 |
| `started_at` / `completed_at` | |

部分唯一索引：`UNIQUE (task_type, biz_key) WHERE status IN (1, 2)`，保证进行中只有一条。`KB_*` 可继续用 `knowledge_vector_tasks`，消费者只改投递方式；或逐步迁入本表。

错误码建议 `13xxx`：`ASYNC_TASK_NOT_FOUND`、`ASYNC_TASK_FAILED`；进行中重复提交视为成功返回已有任务，不报错。

## 7 包结构与契约

```
com.zdmj.common.async
  AsyncTaskType / AsyncTaskStatus
  AsyncLlmTask (entity) + Mapper（claim / success / fail 与向量任务 XML 同款）
  RedisStreamProducer / AbstractStreamConsumer
  AsyncTaskController   GET /async-tasks/{id}
```

各域 Service 拆 `enqueueXxx`（入队）与 `executeXxx`（现 generate 主体）。Controller 只调 enqueue。Consumer 调 execute 时自行设置 `UserHolder` 或把 `userId` 传入（与现 `runEmbeddingTaskByUser` 一致，避免异步丢登录态）。

`POST` 生成类接口：`Result<AsyncTaskDTO>`，HTTP 200 或 202，`data.status` 为 PENDING/RUNNING。业务 DTO 仍由原 `GET` 提供，未完成时 `data=null`（匹配已有惯例）。

## 8 可靠性

- MAXLEN ≈ 1000，防止 Stream 撑爆。
- 成功/失败/不可恢复均 `XACK`；可恢复错误限次重入队（`retryCount`），避免 PEL 堆积（参考文坑 3）。
- 消费者内 LLM 超时按现有 ChatUtil；超 3 次标 FAILED，用户再点视为新任务（SUCCESS/FAILED 不占唯一索引）。
- 单机先 1 个 LLM 消费者 + 1 个 embed 消费者；多实例靠组名相同、consumerName 带 UUID。

## 9 实施切分

| 部分 | 改动 |
| --- | --- |
| 数据库 | `async_llm_tasks` + 部分唯一索引；向量任务表可暂不改 |
| 后端 | common 队列骨架；九类 enqueue；下线 embedding `@Async` 与启动扫库线程池投递；单测 claim/去重/ACK |
| 前端 | 生成按钮：POST 后轮询任务或原 GET；展示排队/生成中；完成再拉业务结果 |
| 不改 | SSE 对话、Nginx 600s 可随后缩短 |

建议顺序：骨架 + 去重单测 → 匹配/画像（痛点最大）→ 报告/图谱/解析 → 迁向量化。

### 接口总体设计思路（异步任务 + 结果查询）

考虑到“向量化时间较长”，整体采用异步任务模型，且 **Python 服务只负责向量化本身，不做业务与权限校验**：

- Java → Python：提交向量化任务（按知识库维度创建 / 重跑 / 删除），Python 立即返回任务 ID（taskId）和当前状态 `PENDING`，由 Java 记录在业务表中（如 `knowledge_bases.vector_task_id`）
- Python 内部：将任务放入队列或后台协程执行，从 PostgreSQL 中读取 `knowledge_bases`、`knowledge_vectors`、`project_code_vectors` 等表进行分块与向量化
- Java 轮询“任务查询接口”获取状态及结果（`vectorIds`），更新 `knowledge_bases.vector_ids`

下面所有接口都放在 `app/api/knowledge.py`，统一前缀建议为：

`/ai/knowledge`

---

## 一、知识库向量化接口（创建 / 重跑，异步）

### 1.1 接口基本信息

- **方法**: `POST`
- **路径**: `/ai/knowledge/embedding`
- **说明**: 按 `knowledgeId` 为指定知识库执行向量化：
  - 若该知识库尚无向量：等价于“首次创建向量”
  - 若该知识库已有向量：先删除旧向量，再按当前内容重新向量化（幂等重跑）

> **调用时机约定（由 Java 决定）**  
> 仅当 `content / type` 等会影响分块和向量语义的字段发生变化时，Java 才调用本接口。  
> 修改名称（`name`）、标签（`tag`）、项目绑定（`project_id`）等元数据时，不调用本向量化接口，由 Java 直接更新数据库。

### 1.2 请求体：`KnowledgeEmbeddingRequest`（JSON）

```json
{
  "knowledgeId": 123,
  "userId": 10001
}
```

- `knowledgeId`: 知识库主键 ID（`knowledge_bases.id`）
- `userId`: 所属用户 ID

> 说明：Python 通过 `knowledgeId` 从 `knowledge_bases` 表读取向量化所需的全部信息（如 `type / content / project_id / tag` 等），  
> **Java 无需在请求体中重复传递这些字段**。  
> 当 `type` 表示：
> - 1 / 3 / 4（项目文档 / 技术文档 / 其他等）：向量化结果写入表 `knowledge_vectors`
> - 2（项目代码 / GitHub 仓库）：向量化结果写入表 `project_code_vectors`，外键统一使用 `knowledge_id`

### 1.3 返回体：`KnowledgeEmbeddingResponse`（JSON）

> 实际返回遵循统一结构：`code/msg/data`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "taskId": "9f8a7c6b5d4e3210a1b2c3d4e5f67890",
    "status": "PENDING",
    "message": "知识库向量化任务已创建"
  }
}
```

> 注意：不在本接口中直接返回 `vectorIds`，而是等任务完成后，通过“任务查询接口”获取。

---

## 二、删除知识库向量接口（按知识库全量删除，异步）

> 适用于删除整个知识库时的“整库向量清理”场景（例如 `KnowledgeBasesServiceImpl.delete()` 中的 TODO）。  
> 若后续需要“部分向量删除”的更细粒度能力，可另行设计单独接口。

### 2.1 接口基本信息（异步版，推荐）

- **方法**: `POST`
- **路径**: `/ai/knowledge/vectors/delete`
- **说明**: 根据 `knowledgeId` 批量删除该知识库下的全部向量（无论在 `knowledge_vectors` 还是 `project_code_vectors`）

### 2.2 请求体：`DeleteVectorsRequest`（JSON）

```json
{
  "knowledgeId": 123,
  "userId": 10001
}
```

- `knowledgeId`: 知识库 ID
- `userId`: 所属用户 ID

> 说明：本接口按知识库维度全量删除，**不需要也不接收 `vectorIds`**。

### 2.3 返回体：`DeleteVectorsResponse`（JSON）

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "taskId": "a13c2e4f6b8d9012c3e4f5a6b7d8e9f0",
    "status": "PENDING",
    "message": "整库向量删除任务已创建"
  }
}
```

> 向量删除成功后，`knowledge_bases.vector_ids` 的更新由 Java 服务根据自身记录（或任务查询结果中的 `vectorIds`）自行更新，  
> Python 服务不直接修改 `knowledge_bases` 表。

---

## 三、向量化任务查询接口（异步核心）

### 3.1 接口基本信息

- **方法**: `GET`
- **路径**: `/ai/knowledge/embedding/tasks/{taskId}`
- **说明**: 查询向量化任务状态以及最终结果

### 3.2 返回体：`KnowledgeEmbeddingTaskStatusResponse`（JSON）

#### 成功示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "taskId": "9f8a7c6b5d4e3210a1b2c3d4e5f67890",
    "knowledgeId": 123,
    "status": "SUCCESS",
    "vectorIds": [101, 102, 103],
    "errorMessage": null,
    "startTime": "2026-04-02T10:01:02.123456",
    "endTime": "2026-04-02T10:02:35.987654"
  }
}
```

#### 运行中示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "taskId": "9f8a7c6b5d4e3210a1b2c3d4e5f67890",
    "knowledgeId": 123,
    "status": "RUNNING",
    "vectorIds": null,
    "errorMessage": "向量化中: 8/20 (40.0%)",
    "startTime": "2026-04-02T10:01:02.123456",
    "endTime": "2026-04-02T10:01:30.111222"
  }
}
```

#### 失败示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "taskId": "9f8a7c6b5d4e3210a1b2c3d4e5f67890",
    "knowledgeId": 123,
    "status": "FAILED",
    "vectorIds": null,
    "errorMessage": "千问 API 调用失败: timeout",
    "startTime": "2026-04-02T10:01:02.123456",
    "endTime": "2026-04-02T10:01:45.666777"
  }
}
```

#### 任务不存在示例

```json
{
  "code": 404,
  "msg": "任务不存在",
  "data": null
}
```

> 说明：由于 Java 不关心分块数量，`chunkCount` 不再作为对外契约字段；如有需要，可在任务表中内部记录。  
> Java 侧调用流程示例：
>
> - 创建 / 重跑 / 删除接口返回 `taskId`
> - 按一定间隔 `GET /ai/knowledge/embedding/tasks/{taskId}` 轮询
> - 当 `status = SUCCESS` 时取出 `vectorIds`，更新 `knowledge_bases.vector_ids` 和相关状态字段

---

## 四、可选辅助接口（便于 Java 管理和监控）

> 以下接口为可选扩展，当前版本可以视情况实现。

### 4.1 批量任务查询接口（可选）

- **方法**: `POST`
- **路径**: `/ai/knowledge/embedding/tasks/batch`
- **请求体（JSON）**：

```json
{
  "taskIds": [
    "9f8a7c6b5d4e3210a1b2c3d4e5f67890",
    "a13c2e4f6b8d9012c3e4f5a6b7d8e9f0"
  ]
}
```

- **返回体（JSON）**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "tasks": [
      {
        "taskId": "9f8a7c6b5d4e3210a1b2c3d4e5f67890",
        "knowledgeId": 123,
        "status": "SUCCESS",
        "vectorIds": [101, 102, 103],
        "errorMessage": null,
        "startTime": "2026-04-02T10:01:02.123456",
        "endTime": "2026-04-02T10:02:35.987654"
      }
    ]
  }
}
```

适用于 Java 侧批量检查多个知识库当前向量化任务的状态。

### 4.2 知识库当前向量简要信息查询（可选）

- **方法**: `GET`
- **路径**: `/ai/knowledge/{knowledgeId}/vectors/summary`
- **说明**: 便于 Java 快速获知该知识库目前有多少向量，方便对比 / 校验
- **返回体（JSON 示例）**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "knowledgeId": 123,
    "vectorCount": 356,
    "lastTaskId": "9f8a7c6b5d4e3210a1b2c3d4e5f67890",
    "lastTaskStatus": "SUCCESS",
    "lastUpdateTime": "2026-04-02T10:02:35.987654"
  }
}
```

---

## 五、Java 调用流程建议（结合 pgsql.sql 结构）

结合现有 `pgsql.sql` 中的 `knowledge_bases`、`knowledge_vectors` 表设计，以及 `KnowledgeBasesServiceImpl` 中的业务逻辑，推荐的 Java 调用流程如下：

- **创建知识库**
  - Java：创建 `knowledge_bases` 记录（不立即写 `vector_ids`）
  - Java：在创建成功后调用 `POST /ai/knowledge/embedding`，请求体仅包含 `knowledgeId + userId`
  - Python：返回 `taskId`，Java 存到 `knowledge_bases.vector_task_id` 或类似字段
  - Java：轮询 `GET /ai/knowledge/embedding/tasks/{taskId}`
  - Python：完成后写入向量表（`knowledge_vectors` 或 `project_code_vectors`），并在任务结果中返回 `vectorIds`
  - Java：任务成功时，更新 `knowledge_bases.vector_ids` 和相关状态（如 `vector_status = SUCCESS`）

- **更新知识库**
  - Java：在 `update()` 中仅当 `content / fileType / type` 等内容字段变化时，认为需要重新向量化
  - Java：调用同一个 `POST /ai/knowledge/embedding` 接口（重跑语义），请求体为 `knowledgeId + userId`
  - Python：根据 `knowledgeId` 删除旧向量，再按最新内容重新向量化
  - Java：通过任务查询接口获取新的 `vectorIds`，重写 `knowledge_bases.vector_ids`

- **删除知识库**
  - Java：删除 `knowledge_bases` 记录前，从自身记录中取出该知识库对应的 `knowledgeId`
  - Java：调用 `POST /ai/knowledge/vectors/delete` 提交“整库向量删除任务”
  - 若采用异步删除：同样通过任务查询接口确认删除完成后，再删除或标记 `knowledge_bases` 记录为已删除

---

### 小结

- **关键接口**：
  - 知识库向量化（创建 / 重跑）：`POST /ai/knowledge/embedding`
  - 按知识库全量删除向量：`POST /ai/knowledge/vectors/delete`
  - 查询任务：`GET /ai/knowledge/embedding/tasks/{taskId}`
- **请求参数**：只围绕 `knowledgeId` 和 `userId` 两个核心字段，其他业务字段全部由 Python 从数据库中读取，职责清晰。
- **返回数据**：创建 / 重跑 / 删除均返回 `taskId + status`，真正的 `vectorIds` 通过任务查询接口返回，Java 再回写 `knowledge_bases.vector_ids`。


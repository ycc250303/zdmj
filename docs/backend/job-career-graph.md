# 岗位关联图谱（Job Career Graph）

本文档描述「岗位间的关联图谱」功能的设计、存储方案、接口契约与工程细节。功能完全覆盖智能体开发要求中以下两点：

> **b) 建立岗位间的关联图谱**，能够使用者清晰了解岗位未来发展路径，包括
> 1. **垂直岗位图谱**：涵盖岗位描述、岗位晋升路径关联信息。
> 2. **换岗路径图谱**：将相关岗位进行血缘关系关联，规划岗位转换路径，至少提供 **5 个岗位的换岗路径**，每个岗位的换岗路径 **不少于 2 条**。

## 1. 设计概览

采用「每个岗位一份结构化图谱」的模型，每条记录由 LLM 基于岗位上下文一次性产出：

| 组成 | 含义 | 硬性约束 |
| :--- | :--- | :--- |
| `currentNode` | 岗位在发展阶梯中的起点节点 | - |
| `verticalPath` | 垂直晋升路径（岗位未来发展路径） | 节点数 ≥ 3，按 `level` 升序 |
| `transitionPaths` | 换岗路径图谱（跨岗位血缘） | 路径条数 ≥ 5，每条 `nodes` ≥ 2 |
| `summary` | 一句话总结 | - |

服务侧会在生成后对图谱做强校验，不满足约束直接抛出 `JOB_CAREER_GRAPH_INVALID`（10005），避免脏数据落库。

## 2. 存储方案（关系型数据库，一岗一图谱）

与岗位能力画像 `JobCapabilityProfile` 保持完全一致的风格，持久化到 PostgreSQL 表 `job_career_graphs`，业务上 1 岗位 = 最多 1 条记录。

| 列 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | `BIGSERIAL PRIMARY KEY` | 图谱ID |
| `job_id` | `BIGINT NOT NULL` | 岗位ID（逻辑外键 `jobs.id`） |
| `role_confidence` | `NUMERIC(5,4)` | 岗位分类置信度（0~1） |
| `prompt_name` | `VARCHAR(128)` | 实际使用的提示词（如 `job-career-graph/java-backend`） |
| `target_role_type` | `VARCHAR(64)` | 岗位类型展示值（`java-backend`/`frontend`/`ai-agent`/`default`） |
| `current_node` | `JSONB` | 当前节点对象 `{ level, title, roleType, description }` |
| `vertical_path` | `JSONB` | 垂直晋升路径数组（`VerticalPathNode[]`，≥3 节点） |
| `transition_paths` | `JSONB` | 换岗路径数组（`TransitionPath[]`，≥5 条，每条 ≥2 节点） |
| `summary` | `TEXT` | 一句话总结 |
| `created_at` / `updated_at` | `TIMESTAMP` | 审计时间戳 |

索引：

```sql
CREATE UNIQUE INDEX uk_job_career_graphs_job_id ON job_career_graphs(job_id);
CREATE INDEX idx_job_career_graphs_role_type ON job_career_graphs(target_role_type);
```

- **为什么用 DB**：图谱是团队共享资产，一个人生成后所有同学都能看到；DB 可导入导出、答辩前可预生成；不受 Redis 数据丢失影响。
- **为什么不加 Redis 缓存层**：单行按 `job_id` 唯一索引命中毫秒级，暂无需额外缓存；与 `job_capability_profiles` 的做法保持一致，简化代码。

## 3. 接口契约

统一前缀：`/api/zdmj`。

### 3.1 查询岗位关联图谱

```
GET /jobs/{id}/career-graph
```

- 仅查询，不触发生成；命中时同步返回完整 DTO，未命中返回 `data:null`。
- 响应：`Result<JobCareerGraphResponse>`，`msg = "查询岗位关联图谱成功"`。

### 3.2 生成岗位关联图谱

```
POST /jobs/{id}/career-graph
```

- 触发一次 LLM 生成（耗时 ~30-60s），若已有则覆盖写入。
- 需认证（JWT，同 `POST /jobs/{id}/capability-profile`）。
- 响应：`Result<JobCareerGraphResponse>`，`msg = "生成岗位关联图谱成功"`。

### 3.3 DTO 结构（`JobCareerGraphResponse`）

```jsonc
{
    "jobId": 9,
    "targetRoleType": "java-backend",
    "currentNode": {
        "level": 2,
        "title": "中级 Java 工程师",
        "roleType": "java_backend",
        "description": "..."
    },
    "verticalPath": [
        {
            "level": 1,
            "title": "初级 Java 工程师",
            "description": "...",
            "responsibilities": ["..."],
            "keyRequirements": ["..."],
            "typicalYears": "0-2",
            "current": false
        }
        /* ≥3 个节点 */
    ],
    "transitionPaths": [
        {
            "name": "DevOps / SRE 方向",
            "targetRole": "SRE/DevOps 工程师",
            "difficulty": "medium",
            "reason": "...",
            "bridgingSkills": ["..."],
            "nodes": [
                { "title": "中级 Java 工程师", "roleType": "java_backend", "description": "..." },
                { "title": "SRE/DevOps 工程师", "roleType": "devops_sre", "description": "..." }
            ]
        }
        /* ≥5 条路径，每条 nodes ≥2 */
    ],
    "summary": "..."
}
```

## 4. 错误码

| Code | 名称 | 说明 |
| :--- | :--- | :--- |
| `10001` | `JOB_NOT_FOUND` | 岗位不存在 |
| `10004` | `JOB_CAREER_GRAPH_GENERATION_FAILED` | LLM 调用或解析失败 |
| `10005` | `JOB_CAREER_GRAPH_INVALID` | LLM 返回不符合硬性要求（节点数不足） |

## 5. 技术实现

### 5.1 调用流程

```
岗位详情 (DB)
    ↓
getJobCapabilityProfileOrNull；缺失则 getJobCapabilityProfile（内含一次 JobRoleDetector）
    ↓
JobRole.fromString(profile.targetRoleType)
    ↓
PromptUtil.resolve(JOB_CAREER_GRAPH, role) — `{slug}.md`，缺文件回退 default
    ↓
ChatUtil.chatStructuredOnce() — 通义 qwen-plus 按 JSON Schema 结构化输出
    ↓
validateGraph() — 强校验：垂直≥3、换岗≥5、每条≥2；不合规抛 10005
markCurrentNode() — 自动打 current=true 高亮
    ↓
toEntity() + JSONB 序列化 → DB upsert（getOne → updateById / save）
图谱 `role_confidence` 直接拷贝岗位画像，不再二次估计
```

### 5.2 提示词组织

```
src/main/resources/prompts/job-career-graph/
├── default.md
├── java-backend.md / frontend.md / cpp.md / ...
└── （每个 JobRole.slug 一份；缺文件则 resolve 回退 default.md）
```

路由见 [`job-role-prompt.md`](job-role-prompt.md)：`PromptUtil.resolve(PromptScenario.JOB_CAREER_GRAPH, role)`，不再手写「哪些方向有专属图谱提示词」。

### 5.3 核心类

| 层 | 类 |
| :--- | :--- |
| Controller | `JobController#queryJobCareerGraph` / `#generateJobCareerGraph` |
| Service | `JobCareerGraphService` / `JobCareerGraphServiceImpl extends ServiceImpl<JobCareerGraphMapper, JobCareerGraph>` |
| Mapper / Entity | `JobCareerGraphMapper` / `JobCareerGraph`（`@TableName("job_career_graphs", autoResultMap=true)`） |
| DTO | `JobCareerGraphResponse` 及嵌套静态类 `CurrentNode` / `VerticalPathNode` / `TransitionPath` / `TransitionNode` |
| JSONB 处理 | 标量数组 `JacksonTypeHandler`；图谱结构字段 `JsonbStringTypeHandler` |

### 5.4 与岗位能力画像的一致性

- 岗位类型以岗位画像 `target_role_type` 为唯一来源（见 [`job-role-prompt.md`](job-role-prompt.md)）；图谱生成时若画像缺失会先生成画像，不再自行 `JobRoleDetector.detect`；
- 两者都采用 `ServiceImpl<Mapper, Entity>` 的 MyBatis-Plus 单表访问范式；
- JSONB：同质标量数组用 `List` + `JacksonTypeHandler`；图谱 `current_node` / 路径等结构化 JSON 仍为 `String` + `JsonbStringTypeHandler`，Service 转 DTO（见 [`jsonb-scalar-array.md`](jsonb-scalar-array.md)）；
- 出错返回约定：`Result.error(code, message)` 统一格式。

## 6. 扩展建议

- **批量导出/预生成**：答辩前通过脚本调用 `POST /jobs/{id}/career-graph` 为若干热门岗位预先入库，无需每位同学首次打开岗位详情时都等一次 LLM。
- **图谱可视化**：`verticalPath` 天然是线性时间轴，`transitionPaths[].nodes` 是 2~N 节点图，前端可用 G6 / React Flow 渲染。
- **多岗位合并视图**：若未来要在岗位列表页做"某公司内部晋升网"大图，可在 `JobCareerGraphMapper` 上新增批查询方法聚合。
- **Redis 缓存（可选）**：若未来发现图谱详情高频查询成瓶颈，再在 `getOrNull` 外层叠加 Redis 缓存即可，无需改动数据模型。

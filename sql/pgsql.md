## `pgsql.sql` 全表字段字典（含枚举/JSON说明）

> 数据库：`zdmj`（PostgreSQL）  
> 说明：以下内容按 `pgsql.sql` 当前建表语句整理；“约束”列包含 `PK/NOT NULL/UNIQUE/DEFAULT/CHECK` 及逻辑外键说明。

## 1 用户模块

### 1.1 表 `users`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 用户ID | `PK` | - |
| `username` | `VARCHAR(50)` | 用户名 | `UNIQUE, NOT NULL` | - |
| `password` | `VARCHAR(500)` | 加密密码 | `NOT NULL` | - |
| `email` | `VARCHAR(100)` | 邮箱 | `NOT NULL` | - |
| `name` | `VARCHAR(50)` | 姓名 | 可空 | - |
| `phone` | `VARCHAR(20)` | 电话 | 可空 | - |
| `website` | `VARCHAR(500)` | 个人主页 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 1.2 表 `user_profiles`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 画像ID | `PK` | - |
| `user_id` | `BIGINT` | 关联用户ID | `UNIQUE, NOT NULL`，逻辑外键 `users.id` | - |
| `basic_info` | `JSONB` | 基础信息 | `NOT NULL` | 示例：`{"major":"软件工程","grade":"大三","school":"XX大学"}` |
| `skills` | `JSONB` | 技能画像 | `NOT NULL` | 示例：`{"languages":["Java","Python"],"frameworks":["Spring Boot"],"level":"中级"}` |
| `job_intention` | `JSONB` | 求职意向 | `NOT NULL` | 示例：`{"position":"后端开发","city":"北京","salary_min":15,"salary_max":25}` |
| `stage` | `SMALLINT` | 求职阶段 | `NOT NULL` | `1=基础积累, 2=项目强化, 3=投递准备, 4=面试冲刺` |
| `constraints` | `JSONB` | 求职约束条件 | 可空 | 示例：`{"type":"日常实习/暑期实习/校招","prepare_time":"3"}` |
| `preferences` | `JSONB` | 求职偏好 | 可空 | 示例：`{"company_type":["互联网"],"industry":["科技"],"learning_style":"在线学习"}` |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 1.3 表 `user_behavior_logs`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 行为日志ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `type` | `SMALLINT` | 行为类型 | `NOT NULL` | `1=learn学习, 2=project项目, 3=resume简历, 4=job岗位`（可扩展） |
| `detail` | `JSONB` | 行为详情 | `NOT NULL` | 示例：`{"action":"创建项目","object_id":123,"object_type":"project","before":{},"after":{"name":"项目名称"}}` |
| `result` | `JSONB` | 行为结果 | 可空 | 示例：`{"status":"success","score":85,"feedback":"项目分析完成","passed":true}` |
| `created_at` | `TIMESTAMP` | 行为发生时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

## 2 简历模块

### 2.1 表 `educations`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 教育经历ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `school` | `VARCHAR(255)` | 学校名称 | `NOT NULL` | - |
| `major` | `VARCHAR(255)` | 专业名称 | `NOT NULL` | - |
| `degree` | `SMALLINT` | 学历层次 | `NOT NULL` | `1=博士, 2=硕士, 3=本科, 4=大专, 5=高中, 6=其他` |
| `start_date` | `DATE` | 入学时间 | `NOT NULL` | - |
| `end_date` | `DATE` | 毕业时间 | 可空 | - |
| `visible` | `BOOLEAN` | 是否展示在简历中 | `DEFAULT true` | - |
| `gpa` | `VARCHAR(50)` | 绩点 | 可空 | - |
| `description` | `TEXT` | 课程/奖项等描述 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 2.2 表 `skills`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 技能清单ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `name` | `VARCHAR(255)` | 技能清单名称 | `NOT NULL` | - |
| `content` | `JSONB` | 技能内容数组 | `NOT NULL, DEFAULT '[]'::jsonb, CHECK jsonb_typeof(content)='array'` | 数组项结构：`{"type":"前端框架","content":["React","Vue.js"]}` |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 2.3 表 `careers`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 工作/实习经历ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `company` | `VARCHAR(255)` | 公司名称 | `NOT NULL` | - |
| `position` | `VARCHAR(255)` | 职位名称 | `NOT NULL` | - |
| `start_date` | `DATE` | 入职时间 | `NOT NULL` | - |
| `end_date` | `DATE` | 离职时间 | 可空 | - |
| `visible` | `BOOLEAN` | 是否展示在简历中 | `DEFAULT true` | - |
| `details` | `TEXT` | 工作职责/业绩 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 2.4 表 `project_experiences`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 项目经历ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `name` | `VARCHAR(255)` | 项目名称 | `NOT NULL` | - |
| `start_date` | `DATE` | 开始时间 | `NOT NULL` | - |
| `end_date` | `DATE` | 结束时间 | 可空 | - |
| `role` | `VARCHAR(255)` | 项目角色 | 可空 | - |
| `description` | `TEXT` | 项目描述 | 可空 | - |
| `contribution` | `VARCHAR(500)` | 核心贡献 | 可空 | - |
| `tech_stack` | `JSONB` | 技术栈 | `DEFAULT '[]'::jsonb` | 示例：`["React","TypeScript","Node.js"]` |
| `highlights` | `JSONB` | 项目亮点 | `DEFAULT '[]'::jsonb` | 数组项结构：`{"type":"技术难点","content":"实现了分布式锁"}` |
| `url` | `VARCHAR(500)` | 项目链接 | 可空 | - |
| `visible` | `BOOLEAN` | 是否展示在简历中 | `DEFAULT true` | - |
| `status` | `SMALLINT` | AI分析状态 | `NOT NULL, DEFAULT 1` | `1=committed已提交, 2=mining挖掘中, 3=polishing打磨中, 4=completed已完成` |
| `lookup_result` | `JSONB` | AI分析结果 | 可空 | 示例：`{"problem":[...],"solution":[...],"score":85}` |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 2.5 表 `resumes`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 简历ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `name` | `VARCHAR(255)` | 简历名称 | `NOT NULL` | - |
| `skill_id` | `BIGINT` | 技能清单ID | 可空，逻辑外键 `skills.id` | - |
| `projects` | `JSONB` | 项目经历ID数组 | `DEFAULT '[]'::jsonb` | 示例：`[1,2,3]`（对应 `project_experiences.id`） |
| `careers` | `JSONB` | 工作经历ID数组 | `DEFAULT '[]'::jsonb` | 示例：`[1,2]`（对应 `careers.id`） |
| `educations` | `JSONB` | 教育经历ID数组 | `DEFAULT '[]'::jsonb` | 示例：`[1]`（对应 `educations.id`） |
| `resume_matched_ids` | `JSONB` | 专用简历ID数组 | `DEFAULT '[]'::jsonb` | 示例：`[1,2]`（对应 `resume_matches.id`） |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 2.6 表 `resume_matches`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 专用简历ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `resume_id` | `BIGINT` | 原始简历ID | 可空，逻辑外键 `resumes.id` | - |
| `name` | `VARCHAR(255)` | 简历名称 | `NOT NULL` | - |
| `skill` | `JSONB` | 优化后的技能对象 | `NOT NULL` | 示例：`{"name":"技能清单","content":[{"type":"开发语言","content":["Java"]}]}` |
| `projects` | `JSONB` | 优化后的项目对象数组 | `DEFAULT '[]'::jsonb` | 数组项含 `id/name/description/tech_stack/highlights` 等 |
| `job_id` | `BIGINT` | 岗位ID | 可空，逻辑外键 `jobs.id` | - |
| `status` | `SMALLINT` | 专用简历状态 | `DEFAULT 1` | `1=committed已提交, 2=generated已生成, 3=optimized已优化` |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

## 3 岗位模块

### 3.1 表 `jobs`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 岗位ID | `PK` | - |
| `job_name` | `VARCHAR(255)` | 岗位名称 | `NOT NULL` | - |
| `company_id` | `BIGINT` | 公司ID | `NOT NULL`，逻辑外键 `companies.id` | - |
| `company_name` | `VARCHAR(255)` | 公司名称（冗余） | `NOT NULL` | - |
| `description` | `TEXT` | 岗位描述 | `NOT NULL` | - |
| `embedding` | `VECTOR(1024)` | 岗位描述向量 | 可空 | 向量维度 `1024`，用于语义检索 |
| `location` | `VARCHAR(255)` | 工作地点 | `NOT NULL` | - |
| `salary` | `VARCHAR(100)` | 薪资范围 | `NOT NULL` | - |
| `link` | `VARCHAR(500)` | 岗位链接 | `NOT NULL` | - |
| `content` | `TEXT` | 工作内容 | 可空 | - |
| `requirements` | `TEXT` | 岗位要求 | 可空 | - |
| `recall` | `JSONB` | 简历召回记录 | 可空 | 示例：`[{"resumeId":1,"reason":"匹配原因"}]` |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 3.2 表 `companies`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 公司ID | `PK` | - |
| `name` | `VARCHAR(255)` | 公司名称 | `NOT NULL` | - |
| `industries` | `JSONB` | 公司行业列表 | `DEFAULT '[]'::jsonb` | 示例：`["计算机软件","IT服务"]` |
| `size` | `SMALLINT` | 公司规模 | 可空 | `1=20人以下, 2=20-99人, 3=100-299人, 4=300-499人, 5=500-999人, 6=1000-9999人, 7=10000人以上` |
| `type` | `SMALLINT` | 融资阶段 | 可空 | `1=A轮, 2=B轮, 3=C轮, 4=D轮及以上, 5=不需要融资, 6=天使轮, 7=已上市, 8=未融资` |
| `introduction` | `TEXT` | 公司详情 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

## 4 知识库模块

### 4.1 表 `knowledge_bases`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 知识库ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `name` | `VARCHAR(255)` | 知识库名称 | `NOT NULL` | - |
| `project_id` | `BIGINT` | 关联项目ID | 可空，逻辑外键 `project_experiences.id` | - |
| `tag` | `JSONB` | 标签数组 | `DEFAULT '[]'::jsonb` | 示例：`["技术文档","API文档"]` |
| `type` | `SMALLINT` | 知识类型 | `NOT NULL` | `1=项目文档(含txt/pdf/md/普通URL), 2=GitHub链接, 3=项目DeepWiki文档(预留)` |
| `content` | `TEXT` | 文档内容或URL | `NOT NULL` | - |
| `vector_task_id` | `BIGINT` | 最近向量任务ID | 可空 | - |
| `vector_task_status` | `VARCHAR(20)` | 最近任务状态 | 可空 | 字面值：`PENDING/RUNNING/SUCCESS/FAILED/CANCELLED` |
| `content_hash` | `VARCHAR(64)` | 内容哈希 | 可空 | - |
| `embedding_status` | `VARCHAR(20)` | 向量化状态 | `NOT NULL, DEFAULT 'PENDING'` | 字面值：`PENDING/EMBEDDING/READY/FAILED` |
| `chunk_count` | `INTEGER` | 已写入块数量 | `NOT NULL, DEFAULT 0` | - |
| `last_embedded_at` | `TIMESTAMP` | 最近向量化完成时间 | 可空 | - |
| `last_error` | `TEXT` | 最近错误信息 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

## 5 向量检索模块

### 5.1 表 `knowledge_vectors`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 向量ID | `PK` | - |
| `knowledge_id` | `BIGINT` | 知识库ID | `NOT NULL`，逻辑外键 `knowledge_bases.id` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `embedding` | `VECTOR(1024)` | 文档块向量 | `NOT NULL` | 向量维度 `1024` |
| `content` | `TEXT` | 文档块内容 | 可空 | - |
| `metadata` | `JSONB` | 文档块元数据 | 可空 | 示例：`{"knowledgeId":"1","source":"xxx.md"}` |
| `chunk_index` | `INTEGER` | 分块序号 | 可空，且 `(knowledge_id, chunk_index)` 唯一索引 | - |
| `chunk_hash` | `VARCHAR(64)` | 分块哈希 | 可空 | - |
| `token_count` | `INTEGER` | Token数量 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 5.2 表 `knowledge_vector_tasks`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 任务ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `knowledge_id` | `BIGINT` | 知识库ID | 可空，逻辑外键 `knowledge_bases.id` | - |
| `task_type` | `SMALLINT` | 任务类型 | `NOT NULL` | `1=创建向量, 2=更新向量, 3=删除向量` |
| `status` | `SMALLINT` | 任务状态 | `NOT NULL` | `1=pending, 2=running, 3=success, 4=failed` |
| `error_message` | `TEXT` | 错误信息 | 可空 | - |
| `started_at` | `TIMESTAMP` | 开始时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `completed_at` | `TIMESTAMP` | 完成时间 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

## 6 AI对话模块

### 6.1 表 `conversations`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 会话ID | `PK` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `project_id` | `BIGINT` | 关联项目ID | 可空，逻辑外键 `project_experiences.id` | - |
| `title` | `VARCHAR(255)` | 会话标题 | 可空 | - |
| `config` | `JSONB` | 对话配置 | `DEFAULT '{}'::jsonb` | 示例：`{"ragEnabled":true,"knowledgeIds":[1,2],"topK":10,"minScore":0.5}` |
| `context` | `JSONB` | RAG上下文列表 | `DEFAULT '[]'::jsonb` | 示例：`[{"type":"knowledge_base","id":456,"name":"知识库名称"}]` |
| `message_count` | `INTEGER` | 消息总数 | `DEFAULT 0` | - |
| `last_message_at` | `TIMESTAMP` | 最后一条消息时间 | 可空 | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 6.2 表 `messages`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 消息ID | `PK` | - |
| `conversation_id` | `BIGINT` | 会话ID | `NOT NULL`，逻辑外键 `conversations.id` | - |
| `user_id` | `BIGINT` | 用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `role` | `SMALLINT` | 消息角色 | `NOT NULL` | `1=user用户, 2=assistant助手, 3=system系统` |
| `content` | `TEXT` | 消息内容 | `NOT NULL` | - |
| `sequence` | `INTEGER` | 会话内顺序号 | `NOT NULL` | - |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | - |

### 6.3 表 `SPRING_AI_CHAT_MEMORY`

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `conversation_id` | `VARCHAR(36)` | 会话ID（UUID） | `NOT NULL` | - |
| `content` | `TEXT` | 消息内容 | `NOT NULL` | - |
| `type` | `VARCHAR(10)` | 消息类型 | `NOT NULL` | 常见值为 `USER/ASSISTANT/SYSTEM`（由 Spring AI 控制） |
| `timestamp` | `TIMESTAMP` | 时间戳 | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | - |

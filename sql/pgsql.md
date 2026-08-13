## `pgsql.sql` 全表字段字典（含枚举/JSON说明）

> 数据库：`zdmj`（PostgreSQL）
> 说明：以下内容按 `pgsql.sql` 当前建表语句整理；“约束”列包含 `PK/NOT NULL/UNIQUE/DEFAULT/CHECK` 及逻辑外键说明。

### 脚本约定（与 `pgsql.sql` 一致）

- **扩展**：`vector`（pgvector）；`pg_trgm`（岗位/公司名称模糊搜索 GIN 索引）；`hnsw` 在部分环境不存在独立扩展，若初始化失败可注释 `CREATE EXTENSION hnsw`。
- **删表顺序**（与脚本 `DROP TABLE` 自上而下一致）：`users` → `user_profiles` → `user_behavior_logs` → `educations` → `skills` → `careers` → `project_experiences` → `resumes` → `resume_matches` → `job_student_matches` / 相关岗位侧表 → `jobs` → `companies` → `knowledge_documents` → `knowledge_bases` → `knowledge_vectors` → `knowledge_vector_tasks` → `conversations` → `messages` → `SPRING_AI_CHAT_MEMORY`。
- **知识库模型（当前）**：每用户**一个** `scope=1` 的用户私有库；全系统**一个** `scope=2` 的系统默认库。`knowledge_bases` **仅存标识**（`user_id`、`scope`）；向量化状态、分块数等均在 **`knowledge_documents`**。
- **系统库占位**：`knowledge_bases` / `knowledge_documents` / `knowledge_vectors` 在系统场景下 `user_id` 约定为 `0`（与真实用户 ID 区分）。

## 1 用户模块

### 1.1 表 `users`

| 字段名称       | 字段类型         | 字段含义 | 约束                          | 枚举/JSON字段含义 |
| -------------- | ---------------- | -------- | ----------------------------- | ----------------- |
| `id`         | `BIGSERIAL`    | 用户ID   | `PK`                        | -                 |
| `username`   | `VARCHAR(50)`  | 用户名   | `UNIQUE, NOT NULL`          | -                 |
| `password`   | `VARCHAR(500)` | 加密密码 | `NOT NULL`                  | -                 |
| `email`      | `VARCHAR(100)` | 邮箱     | `UNIQUE, NOT NULL`          | -                 |
| `name`       | `VARCHAR(50)`  | 姓名     | 可空                          | -                 |
| `phone`      | `VARCHAR(20)`  | 电话     | 可空                          | -                 |
| `website`    | `VARCHAR(500)` | 个人主页 | 可空                          | -                 |
| `preferred_work_city` | `VARCHAR(255)` | 意向工作城市 | 可空                          | -                 |
| `created_at` | `TIMESTAMP`    | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | -                 |
| `updated_at` | `TIMESTAMP`    | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | -                 |

### 1.2 表 `user_llm_config`

| 字段名称               | 字段类型        | 字段含义                         | 约束                          | 枚举/JSON字段含义                                                    |
| ---------------------- | --------------- | -------------------------------- | ----------------------------- | -------------------------------------------------------------------- |
| `user_id`            | `BIGINT`      | 用户ID                           | `PK`，逻辑外键 `users.id` | -                                                                    |
| `model_code`         | `VARCHAR(32)` | 模型目录 code                    | `NOT NULL`                  | `qwen3.6-plus` / `qwen3.7-max` / `deepseek-v4-flash` / `deepseek-v4-pro` |
| `api_key_ciphertext` | `TEXT`        | Encryptors.text 加密后的 API Key | `NOT NULL`                  | -                                                                    |
| `created_at`         | `TIMESTAMP`   | 创建时间                         | `DEFAULT CURRENT_TIMESTAMP` | -                                                                    |
| `updated_at`         | `TIMESTAMP`   | 更新时间                         | `DEFAULT CURRENT_TIMESTAMP` | -                                                                    |

## 2 简历模块

### 2.1 表 `educations`

| 字段名称        | 字段类型         | 字段含义         | 约束                                | 枚举/JSON字段含义                                  |
| --------------- | ---------------- | ---------------- | ----------------------------------- | -------------------------------------------------- |
| `id`          | `BIGSERIAL`    | 教育经历ID       | `PK`                              | -                                                  |
| `user_id`     | `BIGINT`       | 用户ID           | `NOT NULL`，逻辑外键 `users.id` | -                                                  |
| `school`      | `VARCHAR(255)` | 学校名称         | `NOT NULL`                        | -                                                  |
| `major`       | `VARCHAR(255)` | 专业名称         | `NOT NULL`                        | -                                                  |
| `degree`      | `SMALLINT`     | 学历层次         | `NOT NULL`                        | `1=博士, 2=硕士, 3=本科, 4=大专, 5=高中, 6=其他` |
| `start_date`  | `DATE`         | 入学时间         | `NOT NULL`                        | -                                                  |
| `end_date`    | `DATE`         | 毕业时间         | 可空                                | -                                                  |
| `gpa`         | `VARCHAR(50)`  | 绩点             | 可空                                | -                                                  |
| `description` | `TEXT`         | 课程/奖项等描述  | 可空                                | -                                                  |
| `created_at`  | `TIMESTAMP`    | 创建时间         | `DEFAULT CURRENT_TIMESTAMP`       | -                                                  |
| `updated_at`  | `TIMESTAMP`    | 更新时间         | `DEFAULT CURRENT_TIMESTAMP`       | -                                                  |

### 2.2 表 `awards`

| 字段名称       | 字段类型         | 字段含义   | 约束                                | 枚举/JSON字段含义                          |
| -------------- | ---------------- | ---------- | ----------------------------------- | ------------------------------------------ |
| `id`         | `BIGSERIAL`    | 获奖ID     | `PK`                              | -                                          |
| `user_id`    | `BIGINT`       | 用户ID     | `NOT NULL`，逻辑外键 `users.id` | -                                          |
| `award_type` | `SMALLINT`     | 奖项类型   | `NOT NULL, CHECK 1-3`             | `1=奖学金, 2=竞赛获奖, 3=其他类型`         |
| `name`       | `VARCHAR(255)` | 奖项名称   | `NOT NULL`                        | -                                          |
| `award_date` | `DATE`         | 获奖时间   | `NOT NULL`                        | -                                          |
| `description`| `TEXT`         | 奖项说明   | 可空                                | -                                          |
| `created_at` | `TIMESTAMP`    | 创建时间   | `DEFAULT CURRENT_TIMESTAMP`       | -                                          |
| `updated_at` | `TIMESTAMP`    | 更新时间   | `DEFAULT CURRENT_TIMESTAMP`       | -                                          |

### 2.3 表 `skills`

| 字段名称       | 字段类型         | 字段含义     | 约束                                                                   | 枚举/JSON字段含义                                                |
| -------------- | ---------------- | ------------ | ---------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `id`         | `BIGSERIAL`    | 技能清单ID   | `PK`                                                                 | -                                                                |
| `user_id`    | `BIGINT`       | 用户ID       | `NOT NULL`，逻辑外键 `users.id`                                    | -                                                                |
| `content`    | `JSONB`        | 技能内容数组 | `NOT NULL, DEFAULT '[]'::jsonb, CHECK jsonb_typeof(content)='array'` | 数组项结构：`{"type":"前端框架","content":["React","Vue.js"]}` |
| `created_at` | `TIMESTAMP`    | 创建时间     | `DEFAULT CURRENT_TIMESTAMP`                                          | -                                                                |
| `updated_at` | `TIMESTAMP`    | 更新时间     | `DEFAULT CURRENT_TIMESTAMP`                                          | -                                                                |

### 2.4 表 `careers`

| 字段名称       | 字段类型         | 字段含义         | 约束                                | 枚举/JSON字段含义 |
| -------------- | ---------------- | ---------------- | ----------------------------------- | ----------------- |
| `id`         | `BIGSERIAL`    | 工作/实习经历ID  | `PK`                              | -                 |
| `user_id`    | `BIGINT`       | 用户ID           | `NOT NULL`，逻辑外键 `users.id` | -                 |
| `company`    | `VARCHAR(255)` | 公司名称         | `NOT NULL`                        | -                 |
| `position`   | `VARCHAR(255)` | 职位名称         | `NOT NULL`                        | -                 |
| `start_date` | `DATE`         | 入职时间         | `NOT NULL`                        | -                 |
| `end_date`   | `DATE`         | 离职时间         | 可空                                | -                 |
| `details`    | `TEXT`         | 工作职责/业绩    | 可空                                | -                 |
| `created_at` | `TIMESTAMP`    | 创建时间         | `DEFAULT CURRENT_TIMESTAMP`       | -                 |
| `updated_at` | `TIMESTAMP`    | 更新时间         | `DEFAULT CURRENT_TIMESTAMP`       | -                 |

### 2.5 表 `project_experiences`

| 字段名称          | 字段类型         | 字段含义         | 约束                                | 枚举/JSON字段含义                                                           |
| ----------------- | ---------------- | ---------------- | ----------------------------------- | --------------------------------------------------------------------------- |
| `id`            | `BIGSERIAL`    | 项目经历ID       | `PK`                              | -                                                                           |
| `user_id`       | `BIGINT`       | 用户ID           | `NOT NULL`，逻辑外键 `users.id` | -                                                                           |
| `name`          | `VARCHAR(255)` | 项目名称         | `NOT NULL`                        | -                                                                           |
| `start_date`    | `DATE`         | 开始时间         | `NOT NULL`                        | -                                                                           |
| `end_date`      | `DATE`         | 结束时间         | 可空                                | -                                                                           |
| `role`          | `VARCHAR(255)` | 项目角色         | 可空                                | -                                                                           |
| `description`   | `TEXT`         | 项目描述         | 可空                                | -                                                                           |
| `contribution`  | `VARCHAR(500)` | 核心贡献         | 可空                                | -                                                                           |
| `tech_stack`    | `JSONB`        | 技术栈           | `DEFAULT '[]'::jsonb`             | 示例：`["React","TypeScript","Node.js"]`                                  |
| `highlights`    | `JSONB`        | 项目亮点         | `DEFAULT '[]'::jsonb`             | 数组项结构：`{"type":"技术难点","content":"实现了分布式锁"}`              |
| `url`           | `VARCHAR(500)` | 项目链接         | 可空                                | -                                                                           |
| `status`        | `SMALLINT`     | AI分析状态       | `NOT NULL, DEFAULT 1`             | `1=committed已提交, 2=mining挖掘中, 3=polishing打磨中, 4=completed已完成` |
| `lookup_result` | `JSONB`        | AI分析结果       | 可空                                | 示例：`{"problem":[...],"solution":[...],"score":85}`                     |
| `created_at`    | `TIMESTAMP`    | 创建时间         | `DEFAULT CURRENT_TIMESTAMP`       | -                                                                           |
| `updated_at`    | `TIMESTAMP`    | 更新时间         | `DEFAULT CURRENT_TIMESTAMP`       | -                                                                           |

### 2.6 表 `resumes`

| 字段名称               | 字段类型         | 字段含义       | 约束                                | 枚举/JSON字段含义                                    |
| ---------------------- | ---------------- | -------------- | ----------------------------------- | ---------------------------------------------------- |
| `id`                 | `BIGSERIAL`    | 简历ID         | `PK`                              | -                                                    |
| `user_id`            | `BIGINT`       | 用户ID         | `UNIQUE, NOT NULL`，逻辑外键 `users.id` | -                                                    |
| `skill_id`           | `BIGINT`       | 技能清单ID     | 可空，逻辑外键 `skills.id`        | -                                                    |
| `projects`           | `JSONB`        | 项目经历ID数组 | `DEFAULT '[]'::jsonb`             | 示例：`[1,2,3]`（对应 `project_experiences.id`） |
| `careers`            | `JSONB`        | 工作经历ID数组 | `DEFAULT '[]'::jsonb`             | 示例：`[1,2]`（对应 `careers.id`）               |
| `educations`         | `JSONB`        | 教育经历ID数组 | `DEFAULT '[]'::jsonb`             | 示例：`[1]`（对应 `educations.id`）              |
| `awards`             | `JSONB`        | 获奖信息ID数组 | `DEFAULT '[]'::jsonb`             | 示例：`[1,2]`（对应 `awards.id`）                |
| `resume_matched_ids` | `JSONB`        | 专用简历ID数组 | `DEFAULT '[]'::jsonb`             | 示例：`[1,2]`（对应 `resume_matches.id`）        |
| `created_at`         | `TIMESTAMP`    | 创建时间       | `DEFAULT CURRENT_TIMESTAMP`       | -                                                    |
| `updated_at`         | `TIMESTAMP`    | 更新时间       | `DEFAULT CURRENT_TIMESTAMP`       | -                                                    |

### 2.7 表 `resume_matches`

| 字段名称       | 字段类型         | 字段含义             | 约束                                | 枚举/JSON字段含义                                                                |
| -------------- | ---------------- | -------------------- | ----------------------------------- | -------------------------------------------------------------------------------- |
| `id`         | `BIGSERIAL`    | 专用简历ID           | `PK`                              | -                                                                                |
| `user_id`    | `BIGINT`       | 用户ID               | `NOT NULL`，逻辑外键 `users.id` | -                                                                                |
| `resume_id`  | `BIGINT`       | 原始简历ID           | 可空，逻辑外键 `resumes.id`       | -                                                                                |
| `name`       | `VARCHAR(255)` | 简历名称             | `NOT NULL`                        | -                                                                                |
| `skill`      | `JSONB`        | 优化后的技能对象     | `NOT NULL`                        | 示例：`{"name":"技能清单","content":[{"type":"开发语言","content":["Java"]}]}` |
| `projects`   | `JSONB`        | 优化后的项目对象数组 | `DEFAULT '[]'::jsonb`             | 数组项含 `id/name/description/tech_stack/highlights` 等                        |
| `job_id`     | `BIGINT`       | 岗位ID               | 可空，逻辑外键 `jobs.id`          | -                                                                                |
| `status`     | `SMALLINT`     | 专用简历状态         | `DEFAULT 1`                       | `1=committed已提交, 2=generated已生成, 3=optimized已优化`                      |
| `created_at` | `TIMESTAMP`    | 创建时间             | `DEFAULT CURRENT_TIMESTAMP`       | -                                                                                |
| `updated_at` | `TIMESTAMP`    | 更新时间             | `DEFAULT CURRENT_TIMESTAMP`       | -                                                                                |

### 2.8 表 `student_capability_profiles`

| 字段名称                  | 字段类型         | 字段含义       | 约束                                                | 枚举/JSON字段含义                                                                                                                                |
| ------------------------- | ---------------- | -------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `id`                    | `BIGSERIAL`    | 画像ID         | `PK`                                              | -                                                                                                                                                |
| `user_id`               | `BIGINT`       | 关联用户ID     | `UNIQUE, NOT NULL`，逻辑外键 `users.id`         | -                                                                                                                                                |
| `professional_skills`   | `TEXT`         | 专业技能       | 可空                                                | -                                                                                                                                                |
| `honors_and_awards`     | `TEXT`         | 获奖经历       | 可空                                                | -                                                                                                                                                |
| `innovation_ability`    | `TEXT`         | 创新能力       | 可空                                                | -                                                                                                                                                |
| `learning_ability`      | `TEXT`         | 学习能力       | 可空                                                | -                                                                                                                                                |
| `pressure_resistance`   | `TEXT`         | 抗压能力       | 可空                                                | -                                                                                                                                                |
| `communication_ability` | `TEXT`         | 沟通能力       | 可空                                                | -                                                                                                                                                |
| `practical_ability`     | `TEXT`         | 实习能力       | 可空                                                | -                                                                                                                                                |
| `competitiveness_score` | `INTEGER`      | 竞争力评分     | `NOT NULL, DEFAULT 0`                             | `0-100`，由 `score_detail` 五项之和计算                                                                                                      |
| `role_confidence`       | `NUMERIC(5,4)` | 岗位识别置信度 | `NOT NULL, DEFAULT 0.0`                           | `0~1`                                                                                                                                          |
| `prompt_name`           | `VARCHAR(128)` | 提示词名称     | `NOT NULL, DEFAULT 'generate-capability-profile'` | -                                                                                                                                                |
| `target_role_type`      | `VARCHAR(64)`  | 岗位类型展示值 | `NOT NULL, DEFAULT 'default'`                     | 如 `software-test`                                                                                                                             |
| `score_detail`          | `JSONB`        | 分项评分明细   | `DEFAULT '{}'::jsonb`                             | 键：`projectExperienceScore`、`skillMatchScore`、`contentCompletenessScore`、`structureClarityScore`、`expressionProfessionalismScore` |
| `suggestions`           | `JSONB`        | 改进建议       | `DEFAULT '[]'::jsonb`                             | 结构化数组                                                                                                                                       |
| `created_at`            | `TIMESTAMP`    | 创建时间       | `DEFAULT CURRENT_TIMESTAMP`                       | -                                                                                                                                                |
| `updated_at`            | `TIMESTAMP`    | 更新时间       | `DEFAULT CURRENT_TIMESTAMP`                       | -                                                                                                                                                |

**索引**：`idx_student_capability_profiles_user_id`、`idx_student_capability_profiles_target_role`。

## 3 岗位模块

### 3.1 表 `jobs`

| 字段名称                      | 字段类型         | 字段含义         | 约束                                    | 枚举/JSON字段含义          |
| ----------------------------- | ---------------- | ---------------- | --------------------------------------- | -------------------------- |
| `id`                        | `BIGSERIAL`    | 岗位ID           | `PK`                                  | -                          |
| `job_name`                  | `VARCHAR(255)` | 岗位名称         | `NOT NULL`                            | -                          |
| `company_id`                | `BIGINT`       | 公司ID           | `NOT NULL`，逻辑外键 `companies.id` | -                          |
| `company_name`              | `VARCHAR(255)` | 公司名称（冗余） | `NOT NULL`                            | 用于列表筛选与展示         |
| `description`               | `TEXT`         | 岗位描述         | `NOT NULL`                            | -                          |
| `location`                  | `VARCHAR(255)` | 工作地点         | `NOT NULL`                            | -                          |
| `salary_min`                | `INTEGER`      | 薪资下限         | `NOT NULL`                            | 单位见 `salary_type`     |
| `salary_max`                | `INTEGER`      | 薪资上限         | `NOT NULL`                            | 单位见 `salary_type`     |
| `salary_type`               | `SMALLINT`     | 薪资类型         | `NOT NULL`                            | `1=日薪, 2=月薪, 3=年薪` |
| `keywords`                  | `JSONB`        | 岗位关键词       | `DEFAULT '[]'::jsonb`                 | 示例：`["Java","MySQL"]` |
| `content`                   | `JSONB`        | 工作内容         | `DEFAULT '[]'::jsonb`                 | 字符串数组                 |
| `requirements`              | `JSONB`        | 岗位要求         | `DEFAULT '[]'::jsonb`                 | 字符串数组                 |
| `content_embedding`         | `VECTOR(1024)` | 工作内容向量     | 可空                                    | HNSW 语义检索              |
| `critical_skills_embedding` | `VECTOR(1024)` | 关键技能向量     | 可空                                    | HNSW 语义检索              |
| `requirements_embedding`    | `VECTOR(1024)` | 岗位要求向量     | 可空                                    | HNSW 语义检索              |
| `link`                      | `VARCHAR(500)` | 岗位链接         | `NOT NULL`                            | -                          |
| `created_at`                | `TIMESTAMP`    | 创建时间         | `DEFAULT CURRENT_TIMESTAMP`           | -                          |
| `updated_at`                | `TIMESTAMP`    | 更新时间         | `DEFAULT CURRENT_TIMESTAMP`           | -                          |

**API 用工类型筛选**（`GET /jobs?employment=`，须严格传枚举名）：

| 参数值        | 数据库条件                | 含义              |
| ------------- | ------------------------- | ----------------- |
| `INTERN`    | `salary_type = 1`       | 实习（日薪）      |
| `FULL_TIME` | `salary_type IN (2, 3)` | 全职（月薪+年薪） |

**索引**：`idx_jobs_company_id`；`idx_jobs_updated_at`（`updated_at DESC`，列表排序）；`idx_jobs_job_name_trgm`、`idx_jobs_company_name_trgm` — `GIN (… gin_trgm_ops)`（岗位名/公司名模糊搜索）。向量字段暂不设 HNSW 索引，待语义检索上线后再加。

### 3.2 表 `companies`

| 字段名称         | 字段类型         | 字段含义     | 约束                          | 枚举/JSON字段含义                                                                              |
| ---------------- | ---------------- | ------------ | ----------------------------- | ---------------------------------------------------------------------------------------------- |
| `id`           | `BIGSERIAL`    | 公司ID       | `PK`                        | -                                                                                              |
| `name`         | `VARCHAR(255)` | 公司名称     | `NOT NULL`                  | -                                                                                              |
| `industries`   | `JSONB`        | 公司行业列表 | `DEFAULT '[]'::jsonb`       | 示例：`["计算机软件","IT服务"]`                                                              |
| `size`         | `SMALLINT`     | 公司规模     | 可空                          | `1=20人以下, 2=20-99人, 3=100-299人, 4=300-499人, 5=500-999人, 6=1000-9999人, 7=10000人以上` |
| `type`         | `SMALLINT`     | 融资阶段     | 可空                          | `1=A轮, 2=B轮, 3=C轮, 4=D轮及以上, 5=不需要融资, 6=天使轮, 7=已上市, 8=未融资`               |
| `introduction` | `TEXT`         | 公司详情     | 可空                          | -                                                                                              |
| `created_at`   | `TIMESTAMP`    | 创建时间     | `DEFAULT CURRENT_TIMESTAMP` | -                                                                                              |
| `updated_at`   | `TIMESTAMP`    | 更新时间     | `DEFAULT CURRENT_TIMESTAMP` | -                                                                                              |

**索引**：`idx_companies_name`、`idx_companies_name_trgm` — `GIN (name gin_trgm_ops)`；`idx_jobs_company_name_trgm` — `GIN (company_name gin_trgm_ops)`（jobs 冗余字段）；`idx_companies_size`、`idx_companies_type`、`idx_companies_industries`。

### 3.3 表 `job_student_matches`

人岗匹配分析表：每一行表示「某用户 × 某岗位」的**最新一次**匹配结果；`(user_id, job_id)` 唯一，重新分析覆盖写。

| 字段名称 | 字段类型 | 字段含义 | 约束 | 枚举/JSON字段含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGSERIAL` | 匹配ID | `PK` | - |
| `user_id` | `BIGINT` | 学生用户ID | `NOT NULL`，逻辑外键 `users.id` | - |
| `job_id` | `BIGINT` | 岗位ID | `NOT NULL`，逻辑外键 `jobs.id` | - |
| `overall_score` | `INTEGER` | 综合匹配度 | `NOT NULL DEFAULT 0` | 0~100 |
| `basic_score` / `professional_skill_score` / `professional_quality_score` / `development_potential_score` | `INTEGER` | 四维评分 | `NOT NULL DEFAULT 0` | 0~100 |
| `weights` | `JSONB` | 权重快照 | `NOT NULL DEFAULT '{}'` | `basic` / `professionalSkill` / … |
| `dimension_detail` | `JSONB` | 四维对比明细 | `NOT NULL DEFAULT '{}'` | 每维含 score/gap/evidence 等 |
| `matched_highlights` / `critical_gaps` / `matched_keywords` / `missing_keywords` | `JSONB` | 亮点/差距/关键词 | 默认 `[]` | 字符串数组 |
| `key_skill_match_rate` | `NUMERIC(5,4)` | 关键技能匹配率 | `NOT NULL DEFAULT 0` | 0~1 |
| `summary` | `TEXT` | 一句话总结 | 可空 | - |
| `target_role_type` | `VARCHAR(64)` | 岗位类型 | `NOT NULL DEFAULT 'default'` | 如 `java-backend` |
| `prompt_name` | `VARCHAR(128)` | 使用的提示词名 | `NOT NULL` | 如 `job-student-match/default` |
| `created_at` / `updated_at` | `TIMESTAMP` | 创建/更新时间 | `DEFAULT CURRENT_TIMESTAMP` | 列表按 `updated_at DESC` |

**索引**：`uk_job_student_matches_user_job` UNIQUE `(user_id, job_id)`；`idx_job_student_matches_user_id`；`idx_job_student_matches_job_id`；`idx_job_student_matches_role_type`。

**列表 API**：`GET /matches?page=&limit=`（当前用户，INNER JOIN `jobs`，岗位已删不返回）。

## 4 知识库模块

### 4.1 表 `knowledge_bases`

> 仅存知识库**标识**：用户与范围。不向量化汇总在此表维护（见 `knowledge_documents`）。

| 字段名称       | 字段类型      | 字段含义 | 约束                          | 枚举/JSON字段含义                                               |
| -------------- | ------------- | -------- | ----------------------------- | --------------------------------------------------------------- |
| `id`         | `BIGSERIAL` | 知识库ID | `PK`                        | -                                                               |
| `user_id`    | `BIGINT`    | 归属用户 | `NOT NULL, DEFAULT 0`       | `scope=1` 时为真实 `users.id`；`scope=2` 系统库约定 `0` |
| `scope`      | `SMALLINT`  | 范围     | `NOT NULL, DEFAULT 1`       | `1=USER 用户私有, 2=SYSTEM 系统通用`                          |
| `created_at` | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` | -                                                               |
| `updated_at` | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP` | -                                                               |

**唯一索引**

- `uk_knowledge_bases_user_single`：`UNIQUE (user_id) WHERE scope = 1` — 每用户最多一条用户知识库。
- `uk_knowledge_bases_system_default_single`：`UNIQUE (scope) WHERE scope = 2` — 系统默认知识库最多一条。

**其他索引**：`idx_knowledge_bases_user_id`。

> 从旧版（含 `vector_task_id`、`embedding_status` 等列）升级时，可参考 `pgsql.sql` 中注释的 `DROP INDEX` / `ALTER TABLE ... DROP COLUMN` 语句。

### 4.2 表 `knowledge_documents`

> 一个 `knowledge_bases` 下挂多条文档；**来源类型**为 `type`，**来源地址**为 `content`（与 Java 实体字段名一致）。

| 字段名称             | 字段类型         | 字段含义                           | 约束                                          | 枚举/JSON字段含义                               |
| -------------------- | ---------------- | ---------------------------------- | --------------------------------------------- | ----------------------------------------------- |
| `id`               | `BIGSERIAL`    | 文档ID                             | `PK`                                        | -                                               |
| `knowledge_id`     | `BIGINT`       | 知识库ID                           | `NOT NULL`，逻辑外键 `knowledge_bases.id` | -                                               |
| `user_id`          | `BIGINT`       | 归属用户                           | `NOT NULL, DEFAULT 0`                       | 用户文档为真实 `users.id`；系统库下约定 `0` |
| `type`             | `SMALLINT`     | 来源类型                           | `NOT NULL`                                  | `1=上传文件, 2=GitHub仓库, 3=DeepWiki`        |
| `content`          | `TEXT`         | 来源地址（如 COS URL、仓库地址等） | `NOT NULL`                                  | -                                               |
| `title`            | `VARCHAR(500)` | 文档标题                           | 可空                                          | -                                               |
| `content_hash`     | `VARCHAR(64)`  | 文档内容哈希                       | 可空                                          | -                                               |
| `embedding_status` | `SMALLINT`     | 文档向量化状态                     | `NOT NULL, DEFAULT 1`                       | `1=pending, 2=running, 3=success, 4=failed`   |
| `chunk_count`      | `INTEGER`      | 该文档分块数                       | `NOT NULL, DEFAULT 0`                       | -                                               |
| `last_embedded_at` | `TIMESTAMP`    | 最近向量化完成时间                 | 可空                                          | -                                               |
| `last_error`       | `TEXT`         | 最近错误信息                       | 可空                                          | -                                               |
| `metadata`         | `JSONB`        | 扩展元数据                         | `DEFAULT '{}'::jsonb`                       | -                                               |
| `created_at`       | `TIMESTAMP`    | 创建时间                           | `DEFAULT CURRENT_TIMESTAMP`                 | -                                               |
| `updated_at`       | `TIMESTAMP`    | 更新时间                           | `DEFAULT CURRENT_TIMESTAMP`                 | -                                               |

**唯一索引**：`uk_knowledge_documents_kid_content` — `UNIQUE (knowledge_id, content)`，同一库内相同来源地址只保留一条。

**其他索引**：`idx_knowledge_documents_knowledge_id`、`idx_knowledge_documents_user_id`、`idx_knowledge_documents_type`、`idx_knowledge_documents_embedding_status`（`(knowledge_id, embedding_status)`）、`idx_knowledge_documents_content_hash`。

## 5 向量检索模块

### 5.1 表 `knowledge_vectors`

| 字段名称         | 字段类型         | 字段含义     | 约束                                          | 枚举/JSON字段含义                                                 |
| ---------------- | ---------------- | ------------ | --------------------------------------------- | ----------------------------------------------------------------- |
| `id`           | `BIGSERIAL`    | 向量ID       | `PK`                                        | -                                                                 |
| `knowledge_id` | `BIGINT`       | 知识库ID     | `NOT NULL`，逻辑外键 `knowledge_bases.id` | -                                                                 |
| `document_id`  | `BIGINT`       | 文档ID       | 可空，逻辑外键 `knowledge_documents.id`     | 新数据建议始终写入，便于按文档删改向量                            |
| `user_id`      | `BIGINT`       | 用户ID       | `NOT NULL, DEFAULT 0`                       | 与知识库归属一致；系统场景为 `0`                                |
| `embedding`    | `VECTOR(1024)` | 文档块向量   | `NOT NULL`                                  | 维度 `1024`（与 `text-embedding-v4` 配置一致）                |
| `content`      | `TEXT`         | 文档块内容   | 可空                                          | -                                                                 |
| `metadata`     | `JSONB`        | 文档块元数据 | 可空                                          | 示例：`{"knowledgeDocumentId":"文档ID","source":"文件名或URL"}` |
| `chunk_index`  | `INTEGER`      | 分块序号     | 可空                                          | -                                                                 |
| `chunk_hash`   | `VARCHAR(64)`  | 分块哈希     | 可空                                          | -                                                                 |
| `token_count`  | `INTEGER`      | Token 数量   | 可空                                          | -                                                                 |
| `created_at`   | `TIMESTAMP`    | 创建时间     | `DEFAULT CURRENT_TIMESTAMP`                 | -                                                                 |

**唯一索引**：`uk_knowledge_vectors_kid_did_chunk` — `UNIQUE (knowledge_id, COALESCE(document_id, 0), chunk_index)`（兼容历史 `document_id` 为空行）。

**向量索引**：`idx_knowledge_vectors_embedding` — `HNSW (embedding vector_cosine_ops) WITH (M = 16, ef_construction = 100)`。

**其他索引**（与 `pgsql.sql` 一致）：`idx_knowledge_vectors_user_id`、`idx_knowledge_vectors_knowledge_id`、`idx_knowledge_vectors_user_id_knowledge_id`、`idx_knowledge_vectors_document_id`。

### 5.2 表 `knowledge_vector_tasks`

| 字段名称          | 字段类型      | 字段含义 | 约束                                      | 枚举/JSON字段含义                             |
| ----------------- | ------------- | -------- | ----------------------------------------- | --------------------------------------------- |
| `id`            | `BIGSERIAL` | 任务ID   | `PK`                                    | -                                             |
| `user_id`       | `BIGINT`    | 用户ID   | `NOT NULL`，逻辑外键 `users.id`       | -                                             |
| `knowledge_id`  | `BIGINT`    | 知识库ID | 可空，逻辑外键 `knowledge_bases.id`     | -                                             |
| `document_id`   | `BIGINT`    | 文档ID   | 可空，逻辑外键 `knowledge_documents.id` | 空表示整库任务                                |
| `task_type`     | `SMALLINT`  | 任务类型 | `NOT NULL`                              | `1=创建向量, 2=更新向量, 3=删除向量`        |
| `status`        | `SMALLINT`  | 任务状态 | `NOT NULL`                              | `1=pending, 2=running, 3=success, 4=failed` |
| `error_message` | `TEXT`      | 错误信息 | 可空                                      | -                                             |
| `started_at`    | `TIMESTAMP` | 开始时间 | `DEFAULT CURRENT_TIMESTAMP`             | -                                             |
| `completed_at`  | `TIMESTAMP` | 完成时间 | 可空                                      | -                                             |
| `created_at`    | `TIMESTAMP` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP`             | -                                             |
| `updated_at`    | `TIMESTAMP` | 更新时间 | `DEFAULT CURRENT_TIMESTAMP`             | -                                             |

**索引**（与 `pgsql.sql` 一致）：`idx_knowledge_vector_tasks_user_id`、`idx_knowledge_vector_tasks_knowledge_id`、`idx_knowledge_vector_tasks_document_id`、`idx_knowledge_vector_tasks_document_id_user_id`、`idx_knowledge_vector_tasks_knowledge_id_created_at`、`idx_knowledge_vector_tasks_status`、`idx_knowledge_vector_tasks_task_type`。

## 6 AI对话模块

### 6.1 表 `conversations`

| 字段名称            | 字段类型         | 字段含义         | 约束                                      | 枚举/JSON字段含义                                                                                      |
| ------------------- | ---------------- | ---------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `id`              | `BIGSERIAL`    | 会话ID           | `PK`                                    | -                                                                                                      |
| `user_id`         | `BIGINT`       | 用户ID           | `NOT NULL`，逻辑外键 `users.id`       | -                                                                                                      |
| `project_id`      | `BIGINT`       | 关联项目ID       | 可空，逻辑外键 `project_experiences.id` | -                                                                                                      |
| `title`           | `VARCHAR(255)` | 会话标题         | 可空                                      | -                                                                                                      |
| `config`          | `JSONB`        | 对话配置         | `DEFAULT '{}'::jsonb`                   | 示例：`{"ragEnabled":true,"useSystemKnowledge":true,"topK":10,"minScore":0.5}`（字段以应用约定为准） |
| `context`         | `JSONB`        | RAG上下文列表    | `DEFAULT '[]'::jsonb`                   | 示例：`[{"type":"knowledge_base","id":456,"name":"知识库名称"}]`                                     |
| `message_count`   | `INTEGER`      | 消息总数         | `DEFAULT 0`                             | -                                                                                                      |
| `last_message_at` | `TIMESTAMP`    | 最后一条消息时间 | 可空                                      | -                                                                                                      |
| `created_at`      | `TIMESTAMP`    | 创建时间         | `DEFAULT CURRENT_TIMESTAMP`             | -                                                                                                      |
| `updated_at`      | `TIMESTAMP`    | 更新时间         | `DEFAULT CURRENT_TIMESTAMP`             | -                                                                                                      |

**索引**：`idx_conversations_user_id`、`idx_conversations_user_id_created_at`、`idx_conversations_project_id`、`idx_conversations_user_id_project_id`、`idx_conversations_last_message_at`。

### 6.2 表 `messages`

| 字段名称            | 字段类型      | 字段含义     | 约束                                        | 枚举/JSON字段含义                             |
| ------------------- | ------------- | ------------ | ------------------------------------------- | --------------------------------------------- |
| `id`              | `BIGSERIAL` | 消息ID       | `PK`                                      | -                                             |
| `conversation_id` | `BIGINT`    | 会话ID       | `NOT NULL`，逻辑外键 `conversations.id` | -                                             |
| `user_id`         | `BIGINT`    | 用户ID       | `NOT NULL`，逻辑外键 `users.id`         | -                                             |
| `role`            | `SMALLINT`  | 消息角色     | `NOT NULL`                                | `1=user用户, 2=assistant助手, 3=system系统` |
| `content`         | `TEXT`      | 消息内容     | `NOT NULL`                                | -                                             |
| `sequence`        | `INTEGER`   | 会话内顺序号 | `NOT NULL`                                | 从 1 递增                                     |
| `created_at`      | `TIMESTAMP` | 创建时间     | `DEFAULT CURRENT_TIMESTAMP`               | -                                             |

**索引**：`idx_messages_conversation_id`、`idx_messages_conversation_id_sequence`、`idx_messages_user_id`、`idx_messages_user_id_conversation_id`。

### 6.3 表 `SPRING_AI_CHAT_MEMORY`

| 字段名称            | 字段类型        | 字段含义       | 约束                                    | 枚举/JSON字段含义                                       |
| ------------------- | --------------- | -------------- | --------------------------------------- | ------------------------------------------------------- |
| `conversation_id` | `VARCHAR(36)` | 会话ID（UUID） | `NOT NULL`                            | -                                                       |
| `content`         | `TEXT`        | 消息内容       | `NOT NULL`                            | -                                                       |
| `type`            | `VARCHAR(10)` | 消息类型       | `NOT NULL`                            | 常见值为 `USER/ASSISTANT/SYSTEM`（由 Spring AI 控制） |
| `"timestamp"`     | `TIMESTAMP`   | 时间戳         | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 列名为保留字，脚本中使用双引号                          |

**索引**：`idx_spring_ai_chat_memory_conv_ts` — `(conversation_id, "timestamp")`。

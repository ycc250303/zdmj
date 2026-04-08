-- ==========================数据库初始化脚本==========================
-- 
-- 说明：
-- 1. 本项目使用PostgreSQL + pgvector扩展，所有数据存储在PostgreSQL中
-- 2. 使用JSONB字段替代MongoDB存储非结构化数据
-- 3. 所有关联关系使用逻辑外键（在注释中说明），不设置数据库外键约束
-- 4. 时间字段使用TIMESTAMP（不带时区）
-- 5. 枚举字段使用SMALLINT，索引从1开始
--
-- ==========================扩展安装==========================
--
-- 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
-- 安装 hnsw 扩展
CREATE EXTENSION IF NOT EXISTS hnsw;
-- 删除表
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS user_behavior_logs;
DROP TABLE IF EXISTS educations;
DROP TABLE IF EXISTS skills;
DROP TABLE IF EXISTS careers;
DROP TABLE IF EXISTS project_experiences;
DROP TABLE IF EXISTS resumes;
DROP TABLE IF EXISTS resume_matches;
DROP TABLE IF EXISTS jobs;
DROP TABLE IF EXISTS companies;
DROP TABLE IF EXISTS knowledge_documents;
DROP TABLE IF EXISTS knowledge_bases;
DROP TABLE IF EXISTS knowledge_vectors;
DROP TABLE IF EXISTS knowledge_vector_tasks;
DROP TABLE IF EXISTS conversations;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS SPRING_AI_CHAT_MEMORY;
--
-- ==========================1 用户模块==========================
--
-- 1.1 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    -- 用户ID
    username VARCHAR(50) UNIQUE NOT NULL,
    -- 用户名
    password VARCHAR(500) NOT NULL,
    -- 密码（加密）
    email VARCHAR(100) UNIQUE NOT NULL,
    -- 邮箱
    name VARCHAR(50),
    -- 姓名
    phone VARCHAR(20),
    -- 电话
    website VARCHAR(500),
    -- 主页链接
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
-- 1.2 用户画像表
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    -- 画像ID
    user_id BIGINT UNIQUE NOT NULL,
    -- 关联用户ID（逻辑外键：users.id）
    basic_info JSONB NOT NULL,
    -- 基础信息（专业、年级、学校等）
    -- basic_info 示例
    -- {
    --   "major": "软件工程",
    --   "grade": "大三",
    --   "school": "XX大学"
    -- }
    skills JSONB NOT NULL,
    -- 技能画像（语言、框架、水平等）
    -- skills 示例
    -- {
    --   "languages": ["Java", "Python"],
    --   "frameworks": ["Spring Boot", "FastAPI"],
    --   "level": "中级"
    -- }
    job_intention JSONB NOT NULL,
    -- 求职意向（目标岗位、城市、薪资等）
    -- job_intention 示例
    -- {
    --   "position": "后端开发",
    --   "city": "北京",
    --   "salary_min": 15,
    --   "salary_max": 25
    -- }
    stage SMALLINT NOT NULL,
    -- 求职阶段（枚举：1=基础积累/2=项目强化/3=投递准备/4=面试冲刺）
    constraints JSONB,
    -- 约束条件（类型：日常实习/暑期实习/校招、准备时间等）
    -- constraints 示例
    -- {
    --   "type": "日常实习/暑期实习/校招",
    --   "prepare_time": "3"
    -- }
    preferences JSONB,
    -- 偏好（公司类型、行业、学习方式等）
    -- preferences 示例
    -- {
    --   "company_type": ["互联网", "金融"],
    --   "industry": ["科技", "教育"],
    --   "learning_style": "在线学习"
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_stage ON user_profiles(stage);
-- 1.3 用户行为日志表
CREATE TABLE IF NOT EXISTS user_behavior_logs (
    id BIGSERIAL PRIMARY KEY,
    -- 行为日志ID
    user_id BIGINT NOT NULL,
    -- 关联用户ID（逻辑外键：users.id）
    type SMALLINT NOT NULL,
    -- 行为类型（枚举：1=learn学习/2=project项目/3=resume简历/4=job岗位等）
    detail JSONB NOT NULL,
    -- 行为详情（操作对象、前后数据快照等）
    -- detail 示例
    -- {
    --   "action": "创建项目",
    --   "object_id": 123,
    --   "object_type": "project",
    --   "before": {},
    --   "after": {
    --     "name": "项目名称"
    --   }
    -- }
    result JSONB,
    -- 行为结果（通过/未通过/评分/反馈等）
    -- result 示例
    -- {
    --   "status": "success",
    --   "score": 85,
    --   "feedback": "项目分析完成",
    --   "passed": true
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 行为发生时间
);
CREATE INDEX IF NOT EXISTS idx_user_behavior_logs_user_id_type ON user_behavior_logs(user_id, type);
CREATE INDEX IF NOT EXISTS idx_user_behavior_logs_created_at ON user_behavior_logs(created_at);
--
-- ==========================2 简历模块==========================
--
-- 2.1 教育经历表
CREATE TABLE IF NOT EXISTS educations (
    id BIGSERIAL PRIMARY KEY,
    -- 教育经历ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    school VARCHAR(255) NOT NULL,
    -- 学校名称
    major VARCHAR(255) NOT NULL,
    -- 专业名称
    degree SMALLINT NOT NULL,
    -- 学历层次（枚举：1=博士/2=硕士/3=本科/4=大专/5=高中/6=其他）
    start_date DATE NOT NULL,
    -- 入学时间
    end_date DATE,
    -- 毕业时间（在读可为空）
    visible BOOLEAN DEFAULT true,
    -- 在简历中是否展示
    gpa VARCHAR(50),
    -- 绩点
    description TEXT,
    -- 描述（课程、奖项、社团、项目等）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_educations_user_id ON educations(user_id);
CREATE INDEX IF NOT EXISTS idx_educations_user_id_degree ON educations(user_id, degree);
CREATE INDEX IF NOT EXISTS idx_educations_user_id_school ON educations(user_id, school);
-- 2.2 技能表
CREATE TABLE IF NOT EXISTS skills (
    id BIGSERIAL PRIMARY KEY,
    -- 技能ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    name VARCHAR(255) NOT NULL,
    -- 技能清单名称
    content JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- 职业技能描述（数组对象，包含type和content字段）
    -- content 示例
    -- [
    --   {
    --     "type": "前端框架",
    --     "content": ["React", "Vue.js"]
    --   },
    --   {
    --     "type": "开发语言",
    --     "content": ["TypeScript", "JavaScript"]
    --   }
    -- ],
    CONSTRAINT chk_skills_content_is_array CHECK (jsonb_typeof(content) = 'array'),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_skills_user_id ON skills(user_id);
-- 2.3 工作/实习经历表
CREATE TABLE IF NOT EXISTS careers (
    id BIGSERIAL PRIMARY KEY,
    -- 工作/实习经历ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    company VARCHAR(255) NOT NULL,
    -- 公司名称
    position VARCHAR(255) NOT NULL,
    -- 职位名称
    start_date DATE NOT NULL,
    -- 入职时间
    end_date DATE,
    -- 离职时间（在职可为空）
    visible BOOLEAN DEFAULT true,
    -- 是否在简历中展示
    details TEXT,
    -- 工作职责/业绩（可富文本）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_careers_user_id ON careers(user_id);
CREATE INDEX IF NOT EXISTS idx_careers_user_id_company ON careers(user_id, company);
-- 2.4 项目经历表（合并了原projects表的功能，支持简历展示和AI分析）
CREATE TABLE IF NOT EXISTS project_experiences (
    id BIGSERIAL PRIMARY KEY,
    -- 项目经历ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    name VARCHAR(255) NOT NULL,
    -- 项目名称
    start_date DATE NOT NULL,
    -- 项目开始时间
    end_date DATE,
    -- 项目结束时间（进行中可为空）
    role VARCHAR(255),
    -- 在项目中的角色和职责
    description TEXT,
    -- 项目描述
    contribution VARCHAR(500),
    -- 核心贡献
    tech_stack JSONB DEFAULT '[]'::jsonb,
    -- 技术栈（JSONB数组）
    -- tech_stack 示例
    -- ["React", "TypeScript", "Node.js", "PostgreSQL"]
    highlights JSONB DEFAULT '[]'::jsonb,
    -- 项目亮点（JSONB数组，包含技术难点、成果等）
    -- highlights 示例
    -- [
    --   {
    --     "type": "技术难点",
    --     "content": "实现了分布式锁"
    --   },
    --   {
    --     "type": "成果",
    --     "content": "提升了50%的性能"
    --   }
    -- ]
    url VARCHAR(500),
    -- 项目链接
    visible BOOLEAN DEFAULT true,
    -- 是否在简历中展示
    status SMALLINT NOT NULL DEFAULT 1,
    -- 项目分析状态（枚举：1=committed已提交/2=mining挖掘中/3=polishing打磨中/4=completed已完成）
    -- 说明：用于跟踪AI分析流程，不影响简历展示
    lookup_result JSONB,
    -- AI分析结果（问题、解决方案、评分）
    -- lookup_result 示例
    -- {
    --   "problem": [
    --     {
    --       "name": "问题名称",
    --       "desc": "问题描述"
    --     }
    --   ],
    --   "solution": [
    --     {
    --       "name": "解决方案名称",
    --       "desc": "解决方案描述"
    --     }
    --   ],
    --   "score": 85
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id ON project_experiences(user_id);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id_name ON project_experiences(user_id, name);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id_visible ON project_experiences(user_id, visible);
CREATE INDEX IF NOT EXISTS idx_project_experiences_status ON project_experiences(status);
-- 2.5 简历表
CREATE TABLE IF NOT EXISTS resumes (
    id BIGSERIAL PRIMARY KEY,
    -- 简历ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    name VARCHAR(255) NOT NULL,
    -- 简历名称
    skill_id BIGINT,
    -- 技能清单ID（逻辑外键：skills.id）
    projects JSONB DEFAULT '[]'::jsonb,
    -- 项目经历ID数组（JSONB数组，存储project_experiences ID）
    -- projects 示例
    -- [1, 2, 3]
    careers JSONB DEFAULT '[]'::jsonb,
    -- 工作经历ID数组（JSONB数组，存储career ID）
    -- careers 示例
    -- [1, 2]
    educations JSONB DEFAULT '[]'::jsonb,
    -- 教育经历ID数组（JSONB数组，存储education ID）
    -- educations 示例
    -- [1]
    resume_matched_ids JSONB DEFAULT '[]'::jsonb,
    -- 专用简历ID数组（JSONB数组，存储resume_matches ID）
    -- resume_matched_ids 示例
    -- [1, 2]
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_resumes_user_id_name ON resumes(user_id, name);
CREATE INDEX IF NOT EXISTS idx_resumes_user_id ON resumes(user_id);
CREATE INDEX IF NOT EXISTS idx_resumes_skill_id ON resumes(skill_id);
-- 2.6 专用简历表
CREATE TABLE IF NOT EXISTS resume_matches (
    id BIGSERIAL PRIMARY KEY,
    -- 专用简历ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    resume_id BIGINT,
    -- 关联的原始简历ID（逻辑外键：resumes.id，可选）
    name VARCHAR(255) NOT NULL,
    -- 简历名称
    skill JSONB NOT NULL,
    -- 技能清单对象（JSONB，嵌入存储优化后的技能）
    -- skill 示例
    -- {
    --   "name": "技能清单",
    --   "content": [
    --     {
    --       "type": "前端框架",
    --       "content": ["React", "Vue.js"]
    --     }
    --   ]
    -- }
    projects JSONB DEFAULT '[]'::jsonb,
    -- 项目经历对象数组（JSONB数组，嵌入存储优化后的项目经历数据）
    -- projects 示例
    -- [
    --   {
    --     "id": 1,
    --     "name": "项目名称",
    --     "description": "项目描述（已优化）",
    --     "tech_stack": ["React", "TypeScript"],
    --     "highlights": [
    --       {
    --         "type": "技术难点",
    --         "content": "实现了分布式锁"
    --       }
    --     ]
    --   }
    -- ]
    job_id BIGINT,
    -- 岗位ID（逻辑外键：jobs.id）
    status SMALLINT DEFAULT 1,
    -- 简历状态（枚举：1=committed已提交/2=generated已生成/3=optimized已优化）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_resume_matches_user_id ON resume_matches(user_id);
CREATE INDEX IF NOT EXISTS idx_resume_matches_user_id_name ON resume_matches(user_id, name);
CREATE INDEX IF NOT EXISTS idx_resume_matches_job_id ON resume_matches(job_id);
CREATE INDEX IF NOT EXISTS idx_resume_matches_resume_id ON resume_matches(resume_id);
CREATE INDEX IF NOT EXISTS idx_resume_matches_status ON resume_matches(status);
-- 2.7 学生就业能力画像表
CREATE TABLE IF NOT EXISTS student_capability_profiles (
    id BIGSERIAL PRIMARY KEY,
    -- 画像ID
    user_id BIGINT UNIQUE NOT NULL,
    -- 关联用户ID（逻辑外键：users.id）
    professional_skills TEXT,
    -- 专业技能
    certificates TEXT,
    -- 证书
    innovation_ability TEXT,
    -- 创新能力
    learning_ability TEXT,
    -- 学习能力
    pressure_resistance TEXT,
    -- 抗压能力
    communication_ability TEXT,
    -- 沟通能力
    practical_ability TEXT,
    -- 实习能力
    completeness_score INTEGER NOT NULL DEFAULT 0,
    -- 完整度评分 (0-100)
    competitiveness_score INTEGER NOT NULL DEFAULT 0,
    -- 竞争力评分 (0-100)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_student_capability_profiles_user_id ON student_capability_profiles(user_id);
--
-- ==========================3 岗位模块==========================
--
-- 3.1 岗位表（使用JSONB替代MongoDB）
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    -- 岗位ID
    job_name VARCHAR(255) NOT NULL,
    -- 岗位名称
    company_id BIGINT NOT NULL,
    -- 公司ID（逻辑外键：companies.id）
    company_name VARCHAR(255) NOT NULL,
    -- 公司名称（冗余字段，用于查询）
    description TEXT NOT NULL,
    -- 岗位描述
    embedding VECTOR(1024),
    -- 岗位描述向量（1024维，使用text-embedding-v4模型）
    location VARCHAR(255) NOT NULL,
    -- 工作地点
    salary VARCHAR(100) NOT NULL,
    -- 薪资范围
    link VARCHAR(500) NOT NULL,
    -- 岗位链接
    content TEXT,
    -- 工作内容
    requirements TEXT,
    -- 岗位要求
    recall JSONB,
    -- 简历匹配记录数组
    -- recall 示例
    -- [
    --   {
    --     "resumeId": 1,
    --     "reason": "匹配原因"
    --   }
    -- ]
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_jobs_location ON jobs(location);
CREATE INDEX IF NOT EXISTS idx_jobs_company_id ON jobs(company_id);
CREATE INDEX IF NOT EXISTS idx_jobs_company_name ON jobs(company_name);
CREATE INDEX IF NOT EXISTS idx_jobs_embedding ON jobs USING HNSW (embedding vector_cosine_ops) WITH (M = 16, ef_construction = 100);
-- 3.2 公司表
CREATE TABLE IF NOT EXISTS companies (
    id BIGSERIAL PRIMARY KEY,
    -- 公司ID
    name VARCHAR(255) NOT NULL,
    -- 公司名称
    industries JSONB DEFAULT '[]'::jsonb,
    -- 公司行业
    -- industries 示例
    -- ["计算机软件", "IT服务", "专业技术服务"]
    size SMALLINT,
    -- 公司人员规模
    -- 1=20人以下/2=20-99人/3=100-299人/4=300-499人/5=500-999人/6=1000-9999人/7=10000人以上
    type SMALLINT,
    -- 公司类型（融资阶段）
    -- 1=A轮/2=B轮/3=C轮/4=D轮及以上/5=不需要融资/6=天使轮/7=已上市/8=未融资
    introduction TEXT,
    -- 公司详情
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_companies_name ON companies(name);
CREATE INDEX IF NOT EXISTS idx_companies_size ON companies(size);
CREATE INDEX IF NOT EXISTS idx_companies_type ON companies(type);
CREATE INDEX IF NOT EXISTS idx_companies_industries ON companies(industries);
--
-- ==========================4 知识库模块==========================
--
-- 4.1 知识库表（每个用户仅一个用户私有库 + 一个系统默认库；向量化汇总见 knowledge_documents）
CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    -- 知识库ID
    user_id BIGINT NOT NULL DEFAULT 0,
    -- 用户ID（逻辑外键：users.id）
    scope SMALLINT NOT NULL DEFAULT 1,
    -- 知识库范围（枚举：1=USER用户私有/2=SYSTEM系统通用）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_user_id ON knowledge_bases (user_id);
-- 约束：每个用户最多一个 USER 知识库
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_bases_user_single ON knowledge_bases (user_id)
WHERE scope = 1;
-- 约束：系统默认知识库最多一个
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_bases_system_default_single ON knowledge_bases (scope)
WHERE scope = 2;
-- 4.2 知识文档表（一个知识库可关联多个文件/链接）
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGSERIAL PRIMARY KEY,
    -- 文档ID
    knowledge_id BIGINT NOT NULL,
    -- 知识库ID（逻辑外键：knowledge_bases.id）
    user_id BIGINT NOT NULL DEFAULT 0,
    -- 用户ID（逻辑外键：users.id）
    type SMALLINT NOT NULL,
    -- 来源类型（枚举：1=上传文件/2=GitHub仓库/3=DeepWiki）
    content TEXT NOT NULL,
    -- 来源地址
    title VARCHAR(500),
    -- 文档标题
    content_hash VARCHAR(64),
    -- 文档内容哈希（去重/增量）
    embedding_status SMALLINT NOT NULL DEFAULT 1,
    -- 向量化状态（1=pending/2=running/3=success/4=failed）
    chunk_count INTEGER NOT NULL DEFAULT 0,
    -- 文档分块数量
    last_embedded_at TIMESTAMP,
    -- 最近一次向量化完成时间
    last_error TEXT,
    -- 最近错误信息
    metadata JSONB DEFAULT '{}'::jsonb,
    -- 文档元数据
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_knowledge_id ON knowledge_documents (knowledge_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_user_id ON knowledge_documents (user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_type ON knowledge_documents (type);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_embedding_status ON knowledge_documents (knowledge_id, embedding_status);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_content_hash ON knowledge_documents (content_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_documents_kid_content ON knowledge_documents (knowledge_id, content);
--
-- ==========================5 向量检索模块==========================
--
-- 5.1 知识库向量表
CREATE TABLE IF NOT EXISTS knowledge_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    knowledge_id BIGINT NOT NULL,
    -- 知识库ID（逻辑外键：knowledge_bases.id）
    document_id BIGINT,
    -- 文档ID（逻辑外键：knowledge_documents.id）
    user_id BIGINT NOT NULL DEFAULT 0,
    -- 用户ID（逻辑外键：users.id）
    embedding VECTOR(1024) NOT NULL,
    -- 文档块向量（1024维，使用text-embedding-v4模型）
    content TEXT,
    -- 文档块内容
    metadata JSONB,
    -- 元数据（文件名、标签、项目名等）
    -- metadata 示例
    -- {
    --   "knowledgeDocumentId": "知识文档ID",
    --   "source": "文档来源（文件名、URL等）"
    -- }
    chunk_index INTEGER,
    -- 文档块索引
    chunk_hash VARCHAR(64),
    -- 文档块哈希（用于去重）
    token_count INTEGER,
    -- 文档块Token数量（用于上下文预算控制）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_user_id ON knowledge_vectors (user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_knowledge_id ON knowledge_vectors (knowledge_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_user_id_knowledge_id ON knowledge_vectors (user_id, knowledge_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_document_id ON knowledge_vectors (document_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding ON knowledge_vectors USING HNSW (embedding vector_cosine_ops) WITH (M = 16, ef_construction = 100);
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_vectors_kid_did_chunk ON knowledge_vectors (
    knowledge_id,
    COALESCE(document_id, 0),
    chunk_index
);
-- 5.2 向量化任务表（异步任务）
CREATE TABLE IF NOT EXISTS knowledge_vector_tasks (
    id BIGSERIAL PRIMARY KEY,
    -- 任务自增ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    knowledge_id BIGINT,
    -- 知识库ID（逻辑外键：knowledge_bases.id）
    document_id BIGINT,
    -- 文档ID（逻辑外键：knowledge_documents.id，可空表示整库任务）
    task_type SMALLINT NOT NULL,
    -- 任务类型（枚举：1=创建向量/2=更新向量/3=删除向量）
    status SMALLINT NOT NULL,
    -- 任务状态（枚举：1=pending/2=running/3=success/4=failed）
    error_message TEXT,
    -- 错误信息（失败时记录）
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 开始时间
    completed_at TIMESTAMP,
    -- 完成时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_user_id ON knowledge_vector_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_knowledge_id ON knowledge_vector_tasks(knowledge_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_document_id ON knowledge_vector_tasks(document_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_document_id_user_id ON knowledge_vector_tasks(document_id, user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_knowledge_id_created_at ON knowledge_vector_tasks(knowledge_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_status ON knowledge_vector_tasks(status);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_task_type ON knowledge_vector_tasks(task_type);
--
-- ==========================6 AI对话模块==========================
--
-- 6.1 对话会话表
CREATE TABLE IF NOT EXISTS conversations (
    id BIGSERIAL PRIMARY KEY,
    -- 会话ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    title VARCHAR(255),
    -- 对话标题（可由AI生成或用户自定义，首次消息时可为空）
    config JSONB DEFAULT '{}'::jsonb,
    -- 对话配置（ragEnabled、max_tokens、top_p等参数）
    -- config 示例（JSON 仅为说明，实际由应用解析）
    -- {
    --   "ragEnabled": true,
    --   "useSystemKnowledge": true,
    --   "topK": 10,
    --   "minScore": 0.5
    -- }
    context JSONB DEFAULT '[]'::jsonb,
    -- 上下文信息（可关联知识库等，用于RAG检索）
    -- context 示例
    -- [
    --   {
    --     "type": "knowledge_base",
    --     "id": 456,
    --     "name": "知识库名称"
    --   }
    -- ]
    message_count INTEGER DEFAULT 0,
    -- 消息总数（用于快速统计）
    last_message_at TIMESTAMP,
    -- 最后一条消息时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_conversations_user_id ON conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user_id_created_at ON conversations(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at ON conversations(last_message_at DESC);
-- 6.2 消息表
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    -- 消息ID
    conversation_id BIGINT NOT NULL,
    -- 关联会话ID（逻辑外键：conversations.id）
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id，用于数据隔离和快速查询）
    role SMALLINT NOT NULL,
    -- 消息角色（枚举：1=user用户/2=assistant助手/3=system系统）
    content TEXT NOT NULL,
    -- 消息内容
    sequence INTEGER NOT NULL,
    -- 消息序号（在会话中的顺序，从1开始）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 消息创建时间
);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id_sequence ON messages(conversation_id, sequence);
CREATE INDEX IF NOT EXISTS idx_messages_user_id ON messages(user_id);
CREATE INDEX IF NOT EXISTS idx_messages_user_id_conversation_id ON messages(user_id, conversation_id);
--
-- 6.3 Spring AI Chat Memory（用于 /messages/chat 的对话上下文）
--
-- Spring AI JDBC ChatMemory 在 PostgreSQL 中使用表：SPRING_AI_CHAT_MEMORY
-- 其中 "timestamp" 需要使用双引号以匹配 Spring AI 生成的 SQL。
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL,
    "timestamp" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conv_ts ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");
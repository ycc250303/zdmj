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
    email VARCHAR(100) NOT NULL,
    -- 邮箱
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
    content JSONB NOT NULL,
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
    -- ]
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
--
-- ==========================3 项目模块==========================
--
-- 说明：原projects表已合并到project_experiences表中，以下表关联到project_experiences
-- 3.1 项目挖掘表
CREATE TABLE IF NOT EXISTS projects_mined (
    id BIGSERIAL PRIMARY KEY,
    -- 挖掘ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    project_id BIGINT,
    -- 关联项目ID（逻辑外键：project_experiences.id）
    -- 注意：项目名称可通过 project_id JOIN project_experiences.name 获取，或从 info JSONB 中解析
    info JSONB NOT NULL,
    -- 项目信息
    -- info 示例
    -- {
    --   "desc": {
    --     "role": "在项目中的角色和职责",
    --     "contribute": "核心贡献和参与程度",
    --     "bgAndTarget": "项目的背景和目的"
    --   },
    --   "techStack": ["Java", "Spring Boot"]
    -- }
    lightspot JSONB NOT NULL,
    -- 原始亮点
    -- lightspot 示例
    -- {
    --   "team": ["团队贡献1", "团队贡献2"],
    --   "skill": ["技术亮点/难点1", "技术亮点/难点2"],
    --   "user": ["用户体验/业务价值1", "用户体验/业务价值2"]
    -- }
    lightspot_added JSONB,
    -- 额外挖掘的亮点
    -- lightspot_added 示例
    -- {
    --   "team": [
    --     {
    --       "content": "团队贡献描述",
    --       "reason": "亮点添加原因",
    --       "tech": ["相关技术1", "相关技术2"]
    --     }
    --   ],
    --   "skill": [
    --     {
    --       "content": "技术亮点描述",
    --       "reason": "亮点添加原因",
    --       "tech": ["相关技术1", "相关技术2"]
    --     }
    --   ],
    --   "user": [
    --     {
    --       "content": "用户体验描述",
    --       "reason": "亮点添加原因",
    --       "tech": ["相关技术1", "相关技术2"]
    --     }
    --   ]
    -- }
    reason_content TEXT,
    -- 推理内容
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_projects_mined_user_id ON projects_mined(user_id);
CREATE INDEX IF NOT EXISTS idx_projects_mined_project_id ON projects_mined(project_id);
-- 3.2 项目打磨表（使用JSONB替代MongoDB）
CREATE TABLE IF NOT EXISTS projects_polished (
    id BIGSERIAL PRIMARY KEY,
    -- 打磨ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    project_id BIGINT,
    -- 关联项目ID（逻辑外键：project_experiences.id）
    -- 注意：项目名称可通过 project_id JOIN project_experiences.name 获取，或从 info JSONB 中解析
    info JSONB NOT NULL,
    -- 项目信息
    -- info 示例
    -- {
    --   "desc": {
    --     "role": "在项目中的角色和职责",
    --     "contribute": "核心贡献和参与程度",
    --     "bgAndTarget": "项目的背景和目的"
    --   },
    --   "techStack": ["Java", "Spring Boot"]
    -- }
    lightspot JSONB NOT NULL,
    -- 打磨后的亮点
    -- lightspot 示例
    -- {
    --   "team": [
    --     {
    --       "content": "团队贡献描述（已修正）",
    --       "advice": "亮点改进建议（可选）"
    --     }
    --   ],
    --   "skill": [
    --     {
    --       "content": "技术亮点描述（已修正）",
    --       "advice": "亮点改进建议（可选）"
    --     }
    --   ],
    --   "user": [
    --     {
    --       "content": "用户体验描述（已修正）",
    --       "advice": "亮点改进建议（可选）"
    --     }
    --   ],
    --   "deprecated": [
    --     {
    --       "content": "已废弃的亮点描述",
    --       "reason": "亮点删除原因"
    --     }
    --   ]
    -- }
    reason_content TEXT,
    -- 推理内容
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_projects_polished_user_id ON projects_polished(user_id);
CREATE INDEX IF NOT EXISTS idx_projects_polished_project_id ON projects_polished(project_id);
--
-- ==========================4 岗位模块==========================
--
-- 4.1 岗位表（使用JSONB替代MongoDB）
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    -- 岗位ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    job_name VARCHAR(255) NOT NULL,
    -- 岗位名称
    company_name VARCHAR(255) NOT NULL,
    -- 公司名称
    description TEXT NOT NULL,
    -- 岗位描述
    location VARCHAR(255),
    -- 工作地点
    salary VARCHAR(100),
    -- 薪资范围
    link VARCHAR(500),
    -- 岗位链接
    job_status SMALLINT DEFAULT 1,
    -- 外界状态（枚举：1=open开放/2=closed关闭）
    status SMALLINT DEFAULT 1,
    -- 内部状态（枚举：1=committed已提交/2=embedded已嵌入/3=matched已匹配）
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
CREATE INDEX IF NOT EXISTS idx_jobs_user_id ON jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_job_status ON jobs(job_status);
CREATE INDEX IF NOT EXISTS idx_jobs_company_name ON jobs(company_name);
--
-- ==========================5 知识库模块==========================
--
-- 5.1 知识库表（使用JSONB替代MongoDB）
CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    -- 知识库ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    name VARCHAR(255) NOT NULL,
    -- 知识库名称
    project_name VARCHAR(255) NOT NULL,
    -- 关联项目名称
    file_type SMALLINT NOT NULL,
    -- 文件类型（枚举：1=txt/2=URL/3=doc(pdf)/4=md）
    tag JSONB DEFAULT '[]'::jsonb,
    -- 知识标签数组
    -- tag 示例
    -- ["技术文档", "API文档", "架构设计"]
    type SMALLINT NOT NULL,
    -- 知识类型（枚举：1=项目文档/2=GitHub仓库代码/3=技术文档/4=其他/5=项目DeepWiki文档）
    content TEXT NOT NULL,
    -- 文档内容或URL
    vector_ids JSONB DEFAULT '[]'::jsonb,
    -- 关联的向量ID数组
    -- vector_ids 示例
    -- [1, 2, 3, 4, 5]
    vector_task_id VARCHAR(100),
    -- 最近一次向量化任务ID
    vector_task_status VARCHAR(20),
    -- 最近一次任务状态（PENDING/RUNNING/SUCCESS/FAILED/CANCELLED）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_user_id ON knowledge_bases(user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_user_id_project_name ON knowledge_bases(user_id, project_name);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_type ON knowledge_bases(type);
--
-- ==========================6 向量检索模块==========================
--
-- 6.1 知识库向量表
CREATE TABLE IF NOT EXISTS knowledge_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    knowledge_id BIGINT NOT NULL,
    -- 知识库文档ID（逻辑外键：knowledge_bases.id）
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    embedding VECTOR(1024) NOT NULL,
    -- 文档块向量（1024维，使用text-embedding-v4模型）
    content TEXT,
    -- 文档块内容
    metadata JSONB,
    -- 元数据（文件名、标签、项目名等）
    -- metadata 示例
    -- {
    --   "knowledgeId": "知识库ID",
    --   "source": "文档来源（文件名、URL等）"
    -- }
    chunk_index INTEGER,
    -- 文档块索引
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_user_id ON knowledge_vectors(user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_knowledge_id ON knowledge_vectors(knowledge_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding ON knowledge_vectors USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
-- 6.2 岗位向量表
CREATE TABLE IF NOT EXISTS job_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    job_id BIGINT NOT NULL,
    -- 岗位ID（逻辑外键：jobs.id）
    user_id BIGINT NOT NULL,
    -- 所属用户ID（逻辑外键：users.id，数据隔离）
    embedding VECTOR(1024) NOT NULL,
    -- 岗位描述向量（1024维，使用text-embedding-v4模型）
    metadata JSONB,
    -- 岗位元数据（职位名称、公司等）
    -- metadata 示例
    -- {
    --   "job_name": "后端开发工程师",
    --   "company_name": "XX公司",
    --   "location": "北京",
    --   "salary": "15-25k"
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_job_vectors_user_id ON job_vectors(user_id);
CREATE INDEX IF NOT EXISTS idx_job_vectors_job_id ON job_vectors(job_id);
CREATE INDEX IF NOT EXISTS idx_job_vectors_embedding ON job_vectors USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
-- 6.3 项目代码向量表
CREATE TABLE IF NOT EXISTS project_code_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    knowledge_id BIGINT NOT NULL,
    -- 知识库ID（逻辑外键：knowledge_bases.id）
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    file_path VARCHAR(500),
    -- 文件路径
    embedding VECTOR(1024) NOT NULL,
    -- 代码片段向量（1024维，使用text-embedding-v4模型）
    content TEXT,
    -- 代码片段内容
    metadata JSONB,
    -- 元数据（语言、函数名、起止行号等）
    -- metadata 示例
    -- {
    --   "source": "文件相对路径（如 src/utils/helper.ts）",
    --   "language": "编程语言（如 typescript, python）",
    --   "functionName": "函数名（如果是从函数中提取）",
    --   "startLine": 10,
    --   "endLine": 50
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
CREATE INDEX IF NOT EXISTS idx_project_code_vectors_user_id ON project_code_vectors(user_id);
CREATE INDEX IF NOT EXISTS idx_project_code_vectors_project_id ON project_code_vectors(project_id);
CREATE INDEX IF NOT EXISTS idx_project_code_vectors_embedding ON project_code_vectors USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
-- 6.4 向量化任务表（异步任务）
CREATE TABLE IF NOT EXISTS knowledge_vector_tasks (
    id BIGSERIAL PRIMARY KEY,
    -- 任务自增ID
    task_id VARCHAR(100) UNIQUE NOT NULL,
    -- 任务ID（供Java/Python交互使用）
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    knowledge_id BIGINT,
    -- 知识库ID（逻辑外键：knowledge_bases.id）
    task_type SMALLINT NOT NULL,
    -- 任务类型（枚举：1=创建向量/2=更新向量/3=删除向量）
    status SMALLINT NOT NULL,
    -- 任务状态（枚举：1=pending/2=running/3=success/4=failed/5=cancelled）
    vector_ids JSONB DEFAULT '[]'::jsonb,
    -- 任务完成后生成或保留的向量ID快照
    -- 示例: [1, 2, 3]
    error_message TEXT,
    -- 错误信息（失败时记录）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_user_id ON knowledge_vector_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_knowledge_id ON knowledge_vector_tasks(knowledge_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_status ON knowledge_vector_tasks(status);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_tasks_task_type ON knowledge_vector_tasks(task_type);
--
-- ==========================7 AI对话模块==========================
--
-- 7.1 对话会话表
CREATE TABLE IF NOT EXISTS conversations (
    id BIGSERIAL PRIMARY KEY,
    -- 会话ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    project_id BIGINT,
    -- 关联项目ID（逻辑外键：project_experiences.id，可选）
    -- 说明：NULL 表示通用对话（不关联项目），有值表示项目关联对话
    -- 项目关联对话会自动注入项目数据、文档、代码等上下文信息
    title VARCHAR(255),
    -- 对话标题（可由AI生成或用户自定义，首次消息时可为空）
    model VARCHAR(100) NOT NULL DEFAULT 'qwen',
    -- 使用的AI模型
    config JSONB DEFAULT '{}'::jsonb,
    -- 对话配置（temperature、max_tokens、top_p等参数）
    -- config 示例
    -- {
    --   "temperature": 0.7,
    --   "max_tokens": 2000,
    --   "top_p": 1.0,
    --   "frequency_penalty": 0,
    --   "presence_penalty": 0
    -- }
    context JSONB DEFAULT '[]'::jsonb,
    -- 上下文信息（可关联知识库等，用于RAG检索）
    -- 注意：如果 project_id 有值，项目信息会自动注入，无需在此重复
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
CREATE INDEX IF NOT EXISTS idx_conversations_user_id_status ON conversations(user_id, status);
CREATE INDEX IF NOT EXISTS idx_conversations_user_id_created_at ON conversations(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_project_id ON conversations(project_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user_id_project_id ON conversations(user_id, project_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at ON conversations(last_message_at DESC);
-- 7.2 消息表
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
    metadata JSONB DEFAULT '{}'::jsonb,
    -- 消息元数据（token数、模型版本、生成时间等）
    -- metadata 示例
    -- {
    --   "tokens": {
    --     "prompt": 150,
    --     "completion": 200,
    --     "total": 350
    --   },
    --   "model": "gpt-4",
    --   "finish_reason": "stop",
    --   "generation_time_ms": 1234
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 消息创建时间
);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id_sequence ON messages(conversation_id, sequence);
CREATE INDEX IF NOT EXISTS idx_messages_user_id ON messages(user_id);
CREATE INDEX IF NOT EXISTS idx_messages_user_id_conversation_id ON messages(user_id, conversation_id);
-- 7.3 消息向量表（用于检索历史对话，支持RAG功能）
CREATE TABLE IF NOT EXISTS message_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    message_id BIGINT NOT NULL,
    -- 消息ID（逻辑外键：messages.id）
    conversation_id BIGINT NOT NULL,
    -- 会话ID（逻辑外键：conversations.id，用于快速查询）
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id，数据隔离）
    embedding VECTOR(1024) NOT NULL,
    -- 消息向量（1024维，使用text-embedding-v4模型）
    content TEXT NOT NULL,
    -- 消息内容（存储用于检索的文本）
    metadata JSONB,
    -- 元数据（角色、时间等）
    -- metadata 示例
    -- {
    --   "role": "user",
    --   "conversation_id": 123,
    --   "sequence": 1,
    --   "created_at": "2024-01-01 12:00:00"
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
CREATE INDEX IF NOT EXISTS idx_message_vectors_user_id ON message_vectors(user_id);
CREATE INDEX IF NOT EXISTS idx_message_vectors_conversation_id ON message_vectors(conversation_id);
CREATE INDEX IF NOT EXISTS idx_message_vectors_message_id ON message_vectors(message_id);
CREATE INDEX IF NOT EXISTS idx_message_vectors_embedding ON message_vectors USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
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
-- 安装 pg_trgm 扩展
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- 删除表
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS user_behavior_logs;
DROP TABLE IF EXISTS educations;
DROP TABLE IF EXISTS awards;
DROP TABLE IF EXISTS skills;
DROP TABLE IF EXISTS careers;
DROP TABLE IF EXISTS project_experiences;
DROP TABLE IF EXISTS resumes;
DROP TABLE IF EXISTS resume_matches;
DROP TABLE IF EXISTS jobs;
DROP TABLE IF EXISTS companies;
DROP TABLE IF EXISTS job_career_graphs;
DROP TABLE IF EXISTS job_student_matches;
DROP TABLE IF EXISTS career_development_reports;
DROP TABLE IF EXISTS knowledge_documents;
DROP TABLE IF EXISTS knowledge_bases;
DROP TABLE IF EXISTS knowledge_vectors;
DROP TABLE IF EXISTS knowledge_vector_tasks;
DROP TABLE IF EXISTS async_llm_tasks;
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
    preferred_work_city VARCHAR(255),
    -- 意向工作城市
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
-- 1.2 用户大模型配置表
CREATE TABLE IF NOT EXISTS user_llm_config (
    user_id         BIGINT PRIMARY KEY,          -- users.id，每用户一行
    model_code      VARCHAR(32) NOT NULL,        -- qwen3.8-flash / qwen3.8-max 等
    api_key_ciphertext TEXT NOT NULL,            -- AES-GCM 密文（Base64）
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
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
-- 2.2 获奖信息表
CREATE TABLE IF NOT EXISTS awards (
    id BIGSERIAL PRIMARY KEY,
    -- 获奖ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    award_type SMALLINT NOT NULL,
    -- 奖项类型：1=奖学金, 2=竞赛获奖, 3=其他类型
    name VARCHAR(255) NOT NULL,
    -- 奖项名称
    award_date DATE NOT NULL,
    -- 获奖时间
    description TEXT,
    -- 奖项说明（可选）
    CONSTRAINT chk_awards_type CHECK (award_type IN (1, 2, 3)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_awards_user_id ON awards(user_id);
CREATE INDEX IF NOT EXISTS idx_awards_user_id_award_date ON awards(user_id, award_date);
-- 2.3 技能表
CREATE TABLE IF NOT EXISTS skills (
    id BIGSERIAL PRIMARY KEY,
    -- 技能ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
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
    contribution TEXT,
    -- 核心贡献
    tech_stack JSONB DEFAULT '[]'::jsonb,
    -- 技术栈（JSONB数组）
    -- tech_stack 示例
    -- ["React", "TypeScript", "Node.js", "PostgreSQL"]
    highlights JSONB DEFAULT '[]'::jsonb,
    -- 项目亮点（JSONB 字符串数组）
    -- highlights 示例
    -- ["实现了分布式锁", "提升了50%的性能"]
    url VARCHAR(500),
    -- 项目链接
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
CREATE INDEX IF NOT EXISTS idx_project_experiences_status ON project_experiences(status);
-- 2.5 简历表
CREATE TABLE IF NOT EXISTS resumes (
    id BIGSERIAL PRIMARY KEY,
    -- 简历ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
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
    awards JSONB DEFAULT '[]'::jsonb,
    -- 获奖信息ID数组（JSONB数组，存储 awards ID）
    -- awards 示例
    -- [1, 2]
    resume_matched_ids JSONB DEFAULT '[]'::jsonb,
    -- 专用简历ID数组（JSONB数组，存储resume_matches ID）
    -- resume_matched_ids 示例
    -- [1, 2]
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_resumes_user_id_unique ON resumes(user_id);
CREATE INDEX IF NOT EXISTS idx_resumes_skill_id ON resumes(skill_id);
-- 2.6 学生就业能力画像表（最小结构，支持岗位路由+结构化输出）
CREATE TABLE IF NOT EXISTS student_capability_profiles (
    id BIGSERIAL PRIMARY KEY,
    -- 画像ID
    user_id BIGINT UNIQUE NOT NULL,
    -- 关联用户ID（逻辑外键：users.id）
    professional_skills TEXT,
    -- 专业技能
    honors_and_awards TEXT,
    -- 获奖经历（在校荣誉、竞赛获奖等）
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
    competitiveness_score INTEGER NOT NULL DEFAULT 0,
    -- 竞争力评分 (0-100)，由 score_detail 五项之和计算
    role_confidence NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    -- 岗位识别置信度（0~1）
    prompt_name VARCHAR(128) NOT NULL DEFAULT 'generate-capability-profile',
    -- 实际使用的提示词名称
    target_role_type VARCHAR(64) NOT NULL DEFAULT 'default',
    -- 岗位类型展示值（如 software-test）
    score_detail JSONB DEFAULT '{}'::jsonb,
    -- scoreDetail JSON 键：projectExperienceScore, skillMatchScore, contentCompletenessScore, structureClarityScore, expressionProfessionalismScore
    -- 分项评分明细（结构化输出）
    suggestions JSONB DEFAULT '[]'::jsonb,
    -- 改进建议（结构化）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_student_capability_profiles_user_id
    ON student_capability_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_student_capability_profiles_target_role
    ON student_capability_profiles(target_role_type);
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
    location VARCHAR(255) NOT NULL,
    -- 工作地点
    salary_min INTEGER NOT NULL,
    -- 薪资范围
    salary_max INTEGER NOT NULL,
    -- 薪资范围
    salary_type SMALLINT NOT NULL,
    -- 薪资类型
    -- 1=日薪/2=月薪/3=年薪
    keywords JSONB DEFAULT '[]'::jsonb,
    -- 岗位关键词（字符串数组，用于检索/向量化等）
    -- ["Java","MySQL"]
    content  JSONB DEFAULT '[]'::jsonb,
    -- 工作内容
    requirements  JSONB DEFAULT '[]'::jsonb,
    -- 岗位要求
    content_embedding VECTOR(1024),
    -- 工作内容向量（1024维，使用text-embedding-v4模型）
    critical_skills_embedding VECTOR(1024),
    -- 关键技能向量（1024维，使用text-embedding-v4模型）
    requirements_embedding VECTOR(1024),
    -- 岗位要求向量（1024维，使用text-embedding-v4模型）
 
    link VARCHAR(500) NOT NULL,
    -- 岗位链接
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_jobs_company_id ON jobs(company_id);
CREATE INDEX IF NOT EXISTS idx_jobs_updated_at ON jobs(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_jobs_job_name_trgm ON jobs USING GIN (job_name gin_trgm_ops);
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
-- 公司名称模糊搜索（jobs 冗余字段 + companies 主表）
CREATE INDEX IF NOT EXISTS idx_jobs_company_name_trgm ON jobs USING GIN (company_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_companies_name_trgm ON companies USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_companies_size ON companies(size);
CREATE INDEX IF NOT EXISTS idx_companies_type ON companies(type);
CREATE INDEX IF NOT EXISTS idx_companies_industries ON companies(industries);
-- 3.3 岗位能力画像表（每用户 × 每岗位至多一条）
CREATE TABLE IF NOT EXISTS job_capability_profiles (
    id BIGSERIAL PRIMARY KEY,
    -- 岗位能力画像ID
    user_id BIGINT NOT NULL,
    -- 归属用户ID（逻辑外键：users.id）
    job_id BIGINT NOT NULL,
    -- 岗位ID（逻辑外键：jobs.id）
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
    role_confidence NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    -- 岗位分类置信度（0~1）
    prompt_name VARCHAR(128) NOT NULL DEFAULT 'job-requirement/default',
    -- 实际使用的岗位画像提示词名称
    target_role_type VARCHAR(64) NOT NULL DEFAULT 'default',
    -- 岗位类型展示值（如 software-test）
    strengths JSONB DEFAULT '[]'::jsonb,
    -- 岗位已写明的核心要求亮点
    missing_skills JSONB DEFAULT '[]'::jsonb,
    -- 补充要求：JD 未写明的该方向常见核心门槛
    summary TEXT,
    -- 一句话总结
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_job_capability_profiles_user_job ON job_capability_profiles(user_id, job_id);
CREATE INDEX IF NOT EXISTS idx_job_capability_profiles_role_type ON job_capability_profiles(target_role_type);
-- 3.4 岗位关联图谱表
CREATE TABLE IF NOT EXISTS job_career_graphs (
    id BIGSERIAL PRIMARY KEY,
    -- 图谱ID
    job_id BIGINT NOT NULL,
    -- 岗位ID（逻辑外键：jobs.id）
    role_confidence NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    -- 岗位分类置信度（0~1）
    prompt_name VARCHAR(128) NOT NULL DEFAULT 'job-career-graph/default',
    -- 实际使用的图谱提示词名称
    target_role_type VARCHAR(64) NOT NULL DEFAULT 'default',
    -- 岗位类型展示值（如 java-backend）
    current_node JSONB DEFAULT '{}'::jsonb,
    -- 当前岗位节点
    vertical_path JSONB DEFAULT '[]'::jsonb,
    -- 垂直岗位图谱（晋升路径）
    transition_paths JSONB DEFAULT '[]'::jsonb,
    -- 换岗路径图谱
    summary TEXT,
    -- 一句话总结
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_job_career_graphs_job_id ON job_career_graphs(job_id);
CREATE INDEX IF NOT EXISTS idx_job_career_graphs_role_type ON job_career_graphs(target_role_type);

-- 3.5 人岗匹配分析表（学生 × 岗位 多维匹配结果）
CREATE TABLE IF NOT EXISTS job_student_matches (
    id BIGSERIAL PRIMARY KEY,
    -- 匹配ID
    user_id BIGINT NOT NULL,
    -- 学生用户ID（逻辑外键：users.id）
    job_id BIGINT NOT NULL,
    -- 岗位ID（逻辑外键：jobs.id）
    overall_score INTEGER NOT NULL DEFAULT 0,
    -- 综合匹配度（0~100，按权重加权后）
    basic_score INTEGER NOT NULL DEFAULT 0,
    -- 基础要求维度评分（0~100）
    professional_skill_score INTEGER NOT NULL DEFAULT 0,
    -- 职业技能维度评分（0~100）
    professional_quality_score INTEGER NOT NULL DEFAULT 0,
    -- 职业素养维度评分（0~100）
    development_potential_score INTEGER NOT NULL DEFAULT 0,
    -- 发展潜力维度评分（0~100）
    weights JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- 维度权重快照
    -- weights 示例
    -- {
    --   "basic": 0.20,
    --   "professionalSkill": 0.40,
    --   "professionalQuality": 0.15,
    --   "developmentPotential": 0.25
    -- }
    dimension_detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- 各维度对比明细：jobSide / studentSide / score / gap / evidence
    matched_highlights JSONB DEFAULT '[]'::jsonb,
    -- 命中亮点（命中的关键能力点）
    critical_gaps JSONB DEFAULT '[]'::jsonb,
    -- 关键差距
    matched_keywords JSONB DEFAULT '[]'::jsonb,
    -- 命中的岗位关键词（用于关键技能匹配率）
    missing_keywords JSONB DEFAULT '[]'::jsonb,
    -- 缺失的岗位关键词
    key_skill_match_rate NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    -- 关键技能匹配率（命中关键词 / 岗位关键词总数）
    summary TEXT,
    -- 总结
    target_role_type VARCHAR(64) NOT NULL DEFAULT 'default',
    -- 岗位类型展示值（如 java-backend）
    prompt_name VARCHAR(128) NOT NULL DEFAULT 'job-student-match/default',
    -- 实际使用的提示词名称
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_job_student_matches_user_job
    ON job_student_matches(user_id, job_id);
CREATE INDEX IF NOT EXISTS idx_job_student_matches_user_id
    ON job_student_matches(user_id);
CREATE INDEX IF NOT EXISTS idx_job_student_matches_job_id
    ON job_student_matches(job_id);
CREATE INDEX IF NOT EXISTS idx_job_student_matches_role_type
    ON job_student_matches(target_role_type);

-- 3.6 职业发展报告表（单表 + JSONB 主存）
CREATE TABLE IF NOT EXISTS career_development_reports (
    id BIGSERIAL PRIMARY KEY,
    -- 报告ID
    user_id BIGINT NOT NULL,
    -- 用户ID（逻辑外键：users.id）
    job_id BIGINT NOT NULL,
    -- 岗位ID（逻辑外键：jobs.id）
    match_id BIGINT,
    -- 人岗匹配ID（逻辑外键：job_student_matches.id）
    career_graph_id BIGINT,
    -- 岗位图谱ID（逻辑外键：job_career_graphs.id）
    student_profile_snapshot JSONB DEFAULT '{}'::jsonb,
    -- 学生画像快照
    job_profile_snapshot JSONB DEFAULT '{}'::jsonb,
    -- 岗位画像快照
    match_snapshot JSONB DEFAULT '{}'::jsonb,
    -- 匹配结果快照
    knowledge_sources JSONB DEFAULT '[]'::jsonb,
    -- RAG命中的知识来源
    report_content JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- 报告正文结构
    quality_flags JSONB DEFAULT '{}'::jsonb,
    -- 完整性检查与质量标记
    status SMALLINT NOT NULL DEFAULT 1,
    -- 状态：1=draft/2=checked/3=published/4=check_failed
    completeness_score INTEGER NOT NULL DEFAULT 0,
    -- 完整度评分（0~100）
    version INTEGER NOT NULL DEFAULT 1,
    -- 同 user_id + job_id 下的版本号
    is_latest BOOLEAN NOT NULL DEFAULT true,
    -- 是否为该岗位的最新版本
    export_url VARCHAR(1000),
    -- 导出文件地址（可选）
    export_type VARCHAR(32),
    -- 导出类型（pdf/md）
    prompt_name VARCHAR(128) NOT NULL DEFAULT 'career-report/generate',
    -- 实际使用的提示词名称
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_career_reports_user_job_version
    ON career_development_reports(user_id, job_id, version);
CREATE INDEX IF NOT EXISTS idx_career_reports_user_job_latest
    ON career_development_reports(user_id, job_id, is_latest);
CREATE INDEX IF NOT EXISTS idx_career_reports_status_updated
    ON career_development_reports(status, updated_at DESC);
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
    -- 知识库范围（枚举：1=USER用户私有/2=SYSTEM系统通用/3=学习路线）
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
    -- 来源类型（枚举：1=上传pdf文件/2=GitHub仓库/3=DeepWiki/4=markdown）
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
    -- 对话配置（首条消息发出后不可改：useSystemKnowledge、ragDocumentIds）
    -- config 示例（JSON 仅为说明，实际由应用解析）
    -- {
    --   "useSystemKnowledge": true,
    --   "ragDocumentIds": [1, 2]
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_conversation_id_sequence ON messages(conversation_id, sequence);
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
--
-- ==========================7 异步 LLM 任务模块==========================
--
-- 7.1 Redis Stream 异步任务表（状态真相与抢占；队列本身在 Redis）
CREATE TABLE IF NOT EXISTS async_llm_tasks (
    id BIGSERIAL PRIMARY KEY,
    -- 任务 ID，即对外 taskId
    user_id BIGINT NOT NULL,
    -- 发起人（逻辑外键：users.id）；消费者无 UserHolder，权限与写库用此字段
    task_type SMALLINT NOT NULL,
    -- 任务类型（枚举：1=STUDENT_PROFILE 学生能力画像
    --           2=JOB_PROFILE 岗位能力画像
    --           3=JOB_GRAPH 岗位职业图谱
    --           4=JOB_MATCH 人岗匹配
    --           5=CAREER_REPORT 职业发展报告
    --           6=REPORT_POLISH 报告润色
    --           7=REPORT_CHECK 报告完整性检查
    --           8=RESUME_PARSE 简历识别
    --           9=KB_EMBED 知识库向量化
    --           10=KB_DELETE 知识库向量删除）
    biz_key VARCHAR(128) NOT NULL,
    -- 去重键（如 user:{userId}、user:{userId}:job:{jobId}）
    status SMALLINT NOT NULL DEFAULT 1,
    -- 任务状态（枚举：1=pending/2=running/3=success/4=failed，与向量任务一致）
    payload JSONB,
    -- 入队参数（如 match 的 weights、简历 pdfUrl/rawText）
    result JSONB,
    -- 无独立业务表时的成功结果（如 RESUME_PARSE）
    error_message TEXT,
    -- 失败摘要（不落堆栈）
    started_at TIMESTAMP,
    -- 开始执行时间（claim 时写入）
    completed_at TIMESTAMP,
    -- 完成时间（SUCCESS/FAILED）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_async_llm_tasks_type CHECK (task_type BETWEEN 1 AND 10),
    CONSTRAINT chk_async_llm_tasks_status CHECK (status IN (1, 2, 3, 4))
);
-- 进行中同一 (task_type, biz_key) 只允许一条；SUCCESS/FAILED 不占坑
CREATE UNIQUE INDEX IF NOT EXISTS uk_async_llm_tasks_inflight
    ON async_llm_tasks (task_type, biz_key)
    WHERE status IN (1, 2);
CREATE INDEX IF NOT EXISTS idx_async_llm_tasks_user_id
    ON async_llm_tasks (user_id);
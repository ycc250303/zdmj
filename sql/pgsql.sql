-- 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
--
-- ==========================用户模块==========================
--
-- 用户表
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
--
-- ==========================简历模块==========================
--
-- 教育经历表
CREATE TABLE IF NOT EXISTS educations (
    id BIGSERIAL PRIMARY KEY,
    -- 教育经历ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    school VARCHAR(255) NOT NULL,
    -- 学校名称
    major VARCHAR(255) NOT NULL,
    -- 专业名称
    degree SMALLINT NOT NULL,
    -- 学历层次（1: 博士, 2: 硕士, 3: 本科, 4: 大专, 5: 高中, 6: 其他）
    start_date DATE NOT NULL,
    -- 入学时间
    end_date DATE,
    -- 毕业时间（在读可为空）
    visible BOOLEAN DEFAULT true,
    -- 在简历中是否展示
    gpa VARCHAR(50),
    -- 绩点
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_educations_user_id ON educations(user_id);
CREATE INDEX IF NOT EXISTS idx_educations_user_id_degree ON educations(user_id, degree);
CREATE INDEX IF NOT EXISTS idx_educations_user_id_school ON educations(user_id, school);
-- 技能表
CREATE TABLE IF NOT EXISTS skills (
    id BIGSERIAL PRIMARY KEY,
    -- 技能ID
    user_id BIGINT NOT NULL,
    -- 用户ID
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
-- 工作/实习经历表
CREATE TABLE IF NOT EXISTS careers (
    id BIGSERIAL PRIMARY KEY,
    -- 工作/实习经历ID
    user_id BIGINT NOT NULL,
    -- 用户ID
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
CREATE TABLE IF NOT EXISTS project_experiences (
    id BIGSERIAL PRIMARY KEY,
    -- 项目经历ID
    user_id BIGINT NOT NULL,
    -- 用户ID
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
    tech_stack JSONB DEFAULT '[]'::jsonb,
    -- 技术栈（JSONB数组，如["React", "TypeScript", "Node.js"]）
    -- tech_stack 示例: ["React", "TypeScript", "Node.js", "PostgreSQL"]
    highlights JSONB DEFAULT '[]'::jsonb,
    -- 项目亮点（JSONB对象，包含team、skill、user字段）
    -- highlights 示例:
    -- {
    --   "team": ["负责核心模块开发", "参与技术方案设计"],
    --   "skill": ["使用React Hooks优化性能", "实现微前端架构"],
    --   "user": ["提升用户体验30%", "日活用户增长50%"]
    -- }
    url VARCHAR(500),
    -- 项目链接
    visible BOOLEAN DEFAULT true,
    -- 是否在简历中展示
    status SMALLINT DEFAULT 1 NOT NULL,
    -- 项目状态（1: 正常, 2: 挖掘中, 3: 优化中）
    lookup_result JSONB,
    -- 分析结果（JSONB对象，包含problem、solution、score字段）
    -- lookup_result 示例:
    -- {
    --   "problem": [
    --     {"type": "描述不够具体", "content": "缺少技术细节说明"},
    --     {"type": "亮点不突出", "content": "未体现个人贡献"}
    --   ],
    --   "solution": [
    --     {"type": "补充技术细节", "content": "详细说明使用的技术栈和实现方式"},
    --     {"type": "突出个人贡献", "content": "明确说明在项目中的具体职责和成果"}
    --   ],
    --   "score": 75
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id ON project_experiences(user_id);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id_name ON project_experiences(user_id, name);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id_visible ON project_experiences(user_id, visible);
CREATE INDEX IF NOT EXISTS idx_project_experiences_status ON project_experiences(status);
-- 简历表
CREATE TABLE IF NOT EXISTS resumes (
    id BIGSERIAL PRIMARY KEY,
    -- 简历ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    name VARCHAR(255) NOT NULL,
    -- 简历名称
    skill_id BIGINT,
    -- 技能清单ID（关联skills表）
    projects JSONB DEFAULT '[]'::jsonb,
    -- 项目经历ID数组（JSONB数组，存储project_experiences ID）
    -- projects 示例: [1, 2, 3]
    careers JSONB DEFAULT '[]'::jsonb,
    -- 工作经历ID数组（JSONB数组，存储career ID）
    -- careers 示例: [1, 2]
    educations JSONB DEFAULT '[]'::jsonb,
    -- 教育经历ID数组（JSONB数组，存储education ID）
    -- educations 示例: [1]
    resume_matched_ids JSONB DEFAULT '[]'::jsonb,
    -- 专用简历ID数组（JSONB数组，存储resume_matches ID）
    -- resume_matched_ids 示例: [1, 2, 3]
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_resumes_user_id_name ON resumes(user_id, name);
CREATE INDEX IF NOT EXISTS idx_resumes_user_id ON resumes(user_id);
CREATE INDEX IF NOT EXISTS idx_resumes_skill_id ON resumes(skill_id);
--
-- ==========================用户档案模块==========================
--
-- 用户档案表
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    -- 档案ID
    user_id BIGINT UNIQUE NOT NULL,
    -- 用户ID
    basic_info JSONB NOT NULL,
    -- 基本信息（JSONB对象）
    -- basic_info 示例:
    -- {
    --   "name": "张三",
    --   "phone": "13800138000",
    --   "email": "zhangsan@example.com",
    --   "age": 25,
    --   "location": "北京"
    -- }
    skills JSONB NOT NULL,
    -- 技能信息（JSONB对象）
    -- skills 示例:
    -- {
    --   "frontend": ["React", "Vue.js", "TypeScript"],
    --   "backend": ["Node.js", "Java", "Python"],
    --   "database": ["PostgreSQL", "MongoDB", "Redis"]
    -- }
    job_intention JSONB NOT NULL,
    -- 求职意向（JSONB对象）
    -- job_intention 示例:
    -- {
    --   "position": "前端开发工程师",
    --   "industry": "互联网",
    --   "location": ["北京", "上海"],
    --   "salary": "20k-30k"
    -- }
    stage SMALLINT NOT NULL,
    -- 阶段标识
    constraints JSONB,
    -- 约束条件（JSONB对象）
    -- constraints 示例:
    -- {
    --   "workLocation": ["北京", "上海"],
    --   "minSalary": 20000,
    --   "companySize": "大型"
    -- }
    preferences JSONB,
    -- 偏好设置（JSONB对象）
    -- preferences 示例:
    -- {
    --   "workMode": "远程办公",
    --   "companyType": "互联网公司",
    --   "benefits": ["五险一金", "带薪年假"]
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_stage ON user_profiles(stage);
--
-- ==========================简历匹配模块==========================
--
-- 简历匹配表
CREATE TABLE IF NOT EXISTS resume_matches (
    id BIGSERIAL PRIMARY KEY,
    -- 匹配简历ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    resume_id BIGINT,
    -- 关联的简历ID
    name VARCHAR(255) NOT NULL,
    -- 匹配简历名称
    skill JSONB NOT NULL,
    -- 技能信息（JSONB对象，嵌入的技能清单对象）
    -- skill 示例:
    -- {
    --   "name": "前端技能清单",
    --   "content": [
    --     {"type": "前端框架", "content": ["React", "Vue.js"]},
    --     {"type": "开发语言", "content": ["TypeScript", "JavaScript"]}
    --   ]
    -- }
    projects JSONB DEFAULT '[]'::jsonb,
    -- 项目经历（JSONB数组，嵌入的项目经验对象数组）
    -- projects 示例:
    -- [
    --   {
    --     "name": "电商平台",
    --     "info": {
    --       "name": "电商平台",
    --       "desc": {"role": "前端负责人", "contribute": "核心开发"},
    --       "techStack": ["React", "TypeScript"]
    --     },
    --     "lightspot": {
    --       "team": ["负责核心模块"],
    --       "skill": ["性能优化"],
    --       "user": ["提升用户体验"]
    --     }
    --   }
    -- ]
    job_id BIGINT,
    -- 关联的职位ID
    status SMALLINT DEFAULT 1,
    -- 状态（1: 待处理, 2: 已处理, 3: 已废弃）
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
-- ==========================项目挖掘模块==========================
--
-- 项目挖掘表
CREATE TABLE IF NOT EXISTS projects_mined (
    id BIGSERIAL PRIMARY KEY,
    -- 挖掘项目ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    project_id BIGINT,
    -- 关联的项目经历ID
    name VARCHAR(255) NOT NULL,
    -- 项目名称
    info JSONB NOT NULL,
    -- 项目信息（JSONB对象）
    -- info 示例:
    -- {
    --   "name": "电商平台",
    --   "desc": {
    --     "role": "前端负责人",
    --     "contribute": "负责核心模块开发，参与技术方案设计",
    --     "bgAndTarget": "为提升用户体验，开发新一代电商平台"
    --   },
    --   "techStack": ["React", "TypeScript", "Node.js"]
    -- }
    lightspot JSONB NOT NULL,
    -- 项目亮点（JSONB对象，原始亮点）
    -- lightspot 示例:
    -- {
    --   "team": ["负责核心模块开发"],
    --   "skill": ["使用React Hooks优化性能"],
    --   "user": ["提升用户体验30%"]
    -- }
    lightspot_added JSONB,
    -- 额外添加的亮点（JSONB对象，挖掘出的额外亮点）
    -- lightspot_added 示例:
    -- {
    --   "team": [
    --     {"content": "参与技术方案设计", "reason": "体现了团队协作能力"}
    --   ],
    --   "skill": [
    --     {"content": "实现微前端架构", "reason": "展示了架构设计能力"}
    --   ],
    --   "user": [
    --     {"content": "日活用户增长50%", "reason": "体现了业务价值"}
    --   ]
    -- }
    reason_content TEXT,
    -- 挖掘原因说明
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_projects_mined_user_id ON projects_mined(user_id);
CREATE INDEX IF NOT EXISTS idx_projects_mined_project_id ON projects_mined(project_id);
-- 项目优化表
CREATE TABLE IF NOT EXISTS projects_polished (
    id BIGSERIAL PRIMARY KEY,
    -- 优化项目ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    project_id BIGINT,
    -- 关联的项目经历ID
    name VARCHAR(255) NOT NULL,
    -- 项目名称
    info JSONB NOT NULL,
    -- 项目信息（JSONB对象）
    -- info 示例:
    -- {
    --   "name": "电商平台",
    --   "desc": {
    --     "role": "前端负责人",
    --     "contribute": "负责核心模块开发，参与技术方案设计",
    --     "bgAndTarget": "为提升用户体验，开发新一代电商平台"
    --   },
    --   "techStack": ["React", "TypeScript", "Node.js"]
    -- }
    lightspot JSONB NOT NULL,
    -- 项目亮点（JSONB对象，打磨后的亮点）
    -- lightspot 示例:
    -- {
    --   "team": [
    --     {"content": "负责核心模块开发", "status": "active"},
    --     {"content": "参与技术方案设计", "status": "active"}
    --   ],
    --   "skill": [
    --     {"content": "使用React Hooks优化性能", "status": "active"},
    --     {"content": "实现微前端架构", "status": "active"}
    --   ],
    --   "user": [
    --     {"content": "提升用户体验30%", "status": "active"}
    --   ],
    --   "deprecated": [
    --     {"content": "旧亮点描述", "reason": "不够具体"}
    --   ]
    -- }
    reason_content TEXT,
    -- 优化原因说明
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_projects_polished_user_id ON projects_polished(user_id);
CREATE INDEX IF NOT EXISTS idx_projects_polished_project_id ON projects_polished(project_id);
--
-- ==========================用户行为日志模块==========================
--
-- 用户行为日志表
CREATE TABLE IF NOT EXISTS user_behavior_logs (
    id BIGSERIAL PRIMARY KEY,
    -- 日志ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    type SMALLINT NOT NULL,
    -- 行为类型
    detail JSONB NOT NULL,
    -- 行为详情（JSONB对象）
    -- detail 示例:
    -- {
    --   "action": "create_resume",
    --   "target": "resume_id",
    --   "targetId": 123,
    --   "params": {"name": "前端工程师简历"}
    -- }
    result JSONB,
    -- 行为结果（JSONB对象）
    -- result 示例:
    -- {
    --   "success": true,
    --   "data": {"id": 123, "name": "前端工程师简历"},
    --   "message": "创建成功"
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
CREATE INDEX IF NOT EXISTS idx_user_behavior_logs_user_id_type ON user_behavior_logs(user_id, type);
CREATE INDEX IF NOT EXISTS idx_user_behavior_logs_created_at ON user_behavior_logs(created_at);
--
-- ==========================职位模块==========================
--
-- 职位表
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    -- 职位ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    job_name VARCHAR(255) NOT NULL,
    -- 职位名称
    company_name VARCHAR(255) NOT NULL,
    -- 公司名称
    description TEXT NOT NULL,
    -- 职位描述
    location VARCHAR(255),
    -- 工作地点
    salary VARCHAR(100),
    -- 薪资范围
    link VARCHAR(500),
    -- 职位链接
    job_status SMALLINT DEFAULT 1,
    -- 职位状态（1: 招聘中, 2: 已关闭, 3: 已暂停）
    status SMALLINT DEFAULT 1,
    -- 数据状态（1: 正常, 2: 已删除）
    recall JSONB,
    -- 召回信息（JSONB数组，简历匹配记录数组）
    -- recall 示例:
    -- [
    --   {
    --     "resumeId": 1,
    --     "reason": "技能匹配度高，项目经验相关"
    --   },
    --   {
    --     "resumeId": 2,
    --     "reason": "教育背景符合要求"
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
-- ==========================知识库模块==========================
--
-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    -- 知识库ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    name VARCHAR(255) NOT NULL,
    -- 知识库名称
    project_name VARCHAR(255) NOT NULL,
    -- 关联的项目名称
    file_type SMALLINT NOT NULL,
    -- 文件类型
    tag JSONB DEFAULT '[]'::jsonb,
    -- 标签（JSONB数组，用户自定义标签）
    -- tag 示例: ["前端", "React", "性能优化"]
    type SMALLINT NOT NULL,
    -- 知识库类型（1: 用户项目文档, 2: 用户项目代码, 3: 开源项目文档, 4: 开源项目代码, 5: 技术文档, 6: 面试题, 7: 其它）
    content TEXT NOT NULL,
    -- 内容（文本内容/文档URL/文档OSS URL）
    vector_ids JSONB DEFAULT '[]'::jsonb,
    -- 向量ID数组（JSONB数组，存储knowledge_vectors ID）
    -- vector_ids 示例: ["vec_001", "vec_002", "vec_003"]
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_user_id ON knowledge_bases(user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_user_id_project_name ON knowledge_bases(user_id, project_name);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_type ON knowledge_bases(type);
-- 知识向量表
CREATE TABLE IF NOT EXISTS knowledge_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    knowledge_id BIGINT NOT NULL,
    -- 关联的知识库ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    embedding VECTOR(768) NOT NULL,
    -- 向量嵌入（768维）
    content TEXT,
    -- 向量对应的内容
    metadata JSONB,
    -- 元数据（JSONB对象）
    -- metadata 示例:
    -- {
    --   "knowledgeId": "507f1f77bcf86cd799439011",
    --   "source": "项目文档.pdf",
    --   "namespace": "project-doc-user-123-my-project"
    -- }
    chunk_index INTEGER,
    -- 分块索引
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_user_id ON knowledge_vectors(user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_knowledge_id ON knowledge_vectors(knowledge_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding ON knowledge_vectors USING ivfflat (embedding vector_cosine_ops);
--
-- ==========================向量模块==========================
--
-- 职位向量表
CREATE TABLE IF NOT EXISTS job_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    job_id BIGINT NOT NULL,
    -- 关联的职位ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    embedding VECTOR(768) NOT NULL,
    -- 向量嵌入（768维）
    metadata JSONB,
    -- 元数据（JSONB对象）
    -- metadata 示例:
    -- {
    --   "jobName": "前端开发工程师",
    --   "companyName": "XX公司",
    --   "description": "职位描述摘要"
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_job_vectors_user_id ON job_vectors(user_id);
CREATE INDEX IF NOT EXISTS idx_job_vectors_job_id ON job_vectors(job_id);
CREATE INDEX IF NOT EXISTS idx_job_vectors_embedding ON job_vectors USING ivfflat (embedding vector_cosine_ops);
-- 项目代码向量表
CREATE TABLE IF NOT EXISTS project_code_vectors (
    id BIGSERIAL PRIMARY KEY,
    -- 向量ID
    project_id BIGINT NOT NULL,
    -- 关联的项目ID
    user_id BIGINT NOT NULL,
    -- 用户ID
    file_path VARCHAR(500),
    -- 文件路径
    embedding VECTOR(768) NOT NULL,
    -- 向量嵌入（768维）
    content TEXT,
    -- 代码内容
    metadata JSONB,
    -- 元数据（JSONB对象）
    -- metadata 示例:
    -- {
    --   "source": "src/utils/helper.ts",
    --   "language": "typescript",
    --   "functionName": "formatDate",
    --   "startLine": 10,
    --   "endLine": 25
    -- }
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
CREATE INDEX IF NOT EXISTS idx_project_code_vectors_user_id ON project_code_vectors(user_id);
CREATE INDEX IF NOT EXISTS idx_project_code_vectors_project_id ON project_code_vectors(project_id);
CREATE INDEX IF NOT EXISTS idx_project_code_vectors_embedding ON project_code_vectors USING ivfflat (embedding vector_cosine_ops);
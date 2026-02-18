-- 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
-- 用户模块
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
-- 简历模块
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_skills_user_id ON skills(user_id);
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
    highlights JSONB DEFAULT '[]'::jsonb,
    -- 项目亮点（JSONB数组，包含技术难点、成果等）
    url VARCHAR(500),
    -- 项目链接
    visible BOOLEAN DEFAULT true,
    -- 是否在简历中展示
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id ON project_experiences(user_id);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id_name ON project_experiences(user_id, name);
CREATE INDEX IF NOT EXISTS idx_project_experiences_user_id_visible ON project_experiences(user_id, visible);
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
    careers JSONB DEFAULT '[]'::jsonb,
    -- 工作经历ID数组（JSONB数组，存储career ID）
    educations JSONB DEFAULT '[]'::jsonb,
    -- 教育经历ID数组（JSONB数组，存储education ID）
    resume_matched_ids JSONB DEFAULT '[]'::jsonb,
    -- 专用简历ID数组（JSONB数组，存储resume_matches ID）
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_resumes_user_id_name ON resumes(user_id, name);
CREATE INDEX IF NOT EXISTS idx_resumes_user_id ON resumes(user_id);
CREATE INDEX IF NOT EXISTS idx_resumes_skill_id ON resumes(skill_id);

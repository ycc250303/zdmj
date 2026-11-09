-- 项目亮点为 JSONB 字符串数组，与技术栈同一类数据
ALTER TABLE project_experiences
    ADD COLUMN IF NOT EXISTS highlights JSONB DEFAULT '[]'::jsonb;

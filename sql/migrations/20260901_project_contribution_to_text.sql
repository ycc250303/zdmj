-- 简历导入 LLM 抽出的个人贡献常超过 500 字，与 description 对齐改为 TEXT
ALTER TABLE project_experiences
    ALTER COLUMN contribution TYPE TEXT;

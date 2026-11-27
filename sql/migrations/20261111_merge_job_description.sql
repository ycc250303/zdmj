-- 岗位描述使用统一文本和向量字段。
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS description_embedding VECTOR(1024);
ALTER TABLE jobs DROP COLUMN IF EXISTS content_embedding;
ALTER TABLE jobs DROP COLUMN IF EXISTS requirements_embedding;
ALTER TABLE jobs DROP COLUMN IF EXISTS content;
ALTER TABLE jobs DROP COLUMN IF EXISTS requirements;

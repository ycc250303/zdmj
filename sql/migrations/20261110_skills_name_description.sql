-- 技能字段：名称、描述及用户归属索引。
ALTER TABLE skills DROP COLUMN IF EXISTS content;
ALTER TABLE skills ADD COLUMN IF NOT EXISTS name VARCHAR(255) NOT NULL;
ALTER TABLE skills ADD COLUMN IF NOT EXISTS description VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_skills_user_id ON skills(user_id);

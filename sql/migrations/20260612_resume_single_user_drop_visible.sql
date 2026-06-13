-- 简历模型简化：一用户一份简历，废弃经历 visible 字段

-- 若存在多份简历，保留 id 最大的一条
DELETE FROM resumes r1
USING resumes r2
WHERE r1.user_id = r2.user_id
  AND r1.id < r2.id;

DROP INDEX IF EXISTS idx_resumes_user_id_name;
CREATE UNIQUE INDEX IF NOT EXISTS idx_resumes_user_id_unique ON resumes(user_id);

DROP INDEX IF EXISTS idx_project_experiences_user_id_visible;

ALTER TABLE educations DROP COLUMN IF EXISTS visible;
ALTER TABLE careers DROP COLUMN IF EXISTS visible;
ALTER TABLE project_experiences DROP COLUMN IF EXISTS visible;

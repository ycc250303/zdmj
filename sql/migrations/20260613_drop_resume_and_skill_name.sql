-- 取消简历名称与技能清单名称字段

ALTER TABLE resumes DROP COLUMN IF EXISTS name;
ALTER TABLE skills DROP COLUMN IF EXISTS name;

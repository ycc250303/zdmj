-- 经历归属以各经历表的 user_id 为准，简历行不再保存经历编号数组
ALTER TABLE resumes DROP COLUMN IF EXISTS projects;
ALTER TABLE resumes DROP COLUMN IF EXISTS careers;
ALTER TABLE resumes DROP COLUMN IF EXISTS educations;
ALTER TABLE resumes DROP COLUMN IF EXISTS awards;
ALTER TABLE resumes DROP COLUMN IF EXISTS resume_matched_ids;

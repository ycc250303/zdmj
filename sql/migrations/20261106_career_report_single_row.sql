-- 职业发展报告按用户、岗位唯一，移除版本字段。
DROP INDEX IF EXISTS uk_career_reports_user_job_version;
DROP INDEX IF EXISTS idx_career_reports_user_job_latest;

ALTER TABLE career_development_reports DROP COLUMN IF EXISTS version;
ALTER TABLE career_development_reports DROP COLUMN IF EXISTS is_latest;

CREATE UNIQUE INDEX IF NOT EXISTS uk_career_reports_user_job
    ON career_development_reports(user_id, job_id);

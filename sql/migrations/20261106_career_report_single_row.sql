-- 每人每岗只保留一份职业发展报告。
-- 同一用户和岗位有多行时，保留 is_latest 为真且 version 最大的一行。

DELETE FROM career_development_reports AS extra
WHERE extra.id NOT IN (
    SELECT kept.id
    FROM (
        SELECT DISTINCT ON (user_id, job_id) id
        FROM career_development_reports
        ORDER BY user_id, job_id, is_latest DESC, version DESC, id DESC
    ) AS kept
);

DROP INDEX IF EXISTS uk_career_reports_user_job_version;
DROP INDEX IF EXISTS idx_career_reports_user_job_latest;

ALTER TABLE career_development_reports DROP COLUMN IF EXISTS version;
ALTER TABLE career_development_reports DROP COLUMN IF EXISTS is_latest;

CREATE UNIQUE INDEX IF NOT EXISTS uk_career_reports_user_job
    ON career_development_reports(user_id, job_id);

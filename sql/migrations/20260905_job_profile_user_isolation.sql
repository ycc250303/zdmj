-- 岗位能力画像增加用户归属及用户、岗位唯一索引。
ALTER TABLE job_capability_profiles
    ADD COLUMN user_id BIGINT NOT NULL;

DROP INDEX IF EXISTS idx_job_capability_profiles_job_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_capability_profiles_user_job
    ON job_capability_profiles (user_id, job_id);

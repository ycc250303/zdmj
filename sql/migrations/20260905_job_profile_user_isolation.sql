-- 岗位能力画像按用户隔离：旧行仅有 job_id，无法还原归属，清空后由用户重新生成
DELETE FROM job_capability_profiles;

ALTER TABLE job_capability_profiles
    ADD COLUMN user_id BIGINT NOT NULL;

DROP INDEX IF EXISTS idx_job_capability_profiles_job_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_capability_profiles_user_job
    ON job_capability_profiles (user_id, job_id);

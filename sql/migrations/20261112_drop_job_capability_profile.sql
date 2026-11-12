DROP TABLE IF EXISTS job_capability_profiles;

ALTER TABLE career_development_reports
    DROP COLUMN IF EXISTS job_profile_snapshot;

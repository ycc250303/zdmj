-- 岗位能力画像：移除 weak_evidence_items（JD 含糊项不再单独落库）
ALTER TABLE job_capability_profiles DROP COLUMN IF EXISTS weak_evidence_items;

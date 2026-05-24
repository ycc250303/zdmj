-- 学生能力画像：移除 completeness_score、missing_skills、weak_evidence_items
ALTER TABLE student_capability_profiles DROP COLUMN IF EXISTS completeness_score;
ALTER TABLE student_capability_profiles DROP COLUMN IF EXISTS missing_skills;
ALTER TABLE student_capability_profiles DROP COLUMN IF EXISTS weak_evidence_items;

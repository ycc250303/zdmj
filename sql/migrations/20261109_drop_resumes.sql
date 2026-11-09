-- 一名用户只有一份简历，经历和技能按 user_id 归属。
-- 简历表仍在时，先保留它所指向的技能行，再删除简历壳和未被使用的专用简历表。
DO $$
BEGIN
    IF to_regclass('public.resumes') IS NOT NULL THEN
        DELETE FROM skills s
        USING resumes r
        WHERE s.user_id = r.user_id
          AND r.skill_id IS NOT NULL
          AND s.id <> r.skill_id;
    END IF;
END $$;

DROP TABLE IF EXISTS resume_matches;
DROP TABLE IF EXISTS resumes;

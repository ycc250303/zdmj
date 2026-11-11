-- 技能改为一行一项。只展开每个用户最新的一条旧清单，分类名不保留，「待补充」不迁入。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'skills'
          AND column_name = 'content'
    ) THEN
        CREATE TABLE skills_name_desc (
            id BIGSERIAL PRIMARY KEY,
            user_id BIGINT NOT NULL,
            name VARCHAR(255) NOT NULL,
            description VARCHAR(500),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        INSERT INTO skills_name_desc (user_id, name, description, created_at, updated_at)
        SELECT s.user_id,
               left(btrim(skill_name), 255),
               NULL,
               COALESCE(s.created_at, CURRENT_TIMESTAMP),
               COALESCE(s.updated_at, CURRENT_TIMESTAMP)
        FROM (
            SELECT DISTINCT ON (user_id) user_id, content, created_at, updated_at
            FROM skills
            ORDER BY user_id, updated_at DESC NULLS LAST, id DESC
        ) s
        CROSS JOIN LATERAL jsonb_array_elements(
            CASE WHEN jsonb_typeof(s.content) = 'array' THEN s.content ELSE '[]'::jsonb END
        ) AS grp
        CROSS JOIN LATERAL jsonb_array_elements_text(
            CASE
                WHEN jsonb_typeof(grp -> 'content') = 'array' THEN grp -> 'content'
                ELSE '[]'::jsonb
            END
        ) AS skill_name
        WHERE btrim(skill_name) <> ''
          AND btrim(skill_name) <> '待补充';

        DROP TABLE skills;
        ALTER TABLE skills_name_desc RENAME TO skills;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_skills_user_id ON skills(user_id);

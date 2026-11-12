-- 把岗位职责与岗位要求并入 description，删除拆分列和对应向量，新增岗位描述向量。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'jobs'
          AND column_name = 'content'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'jobs'
          AND column_name = 'requirements'
    ) THEN
        UPDATE jobs AS target
        SET description = CASE
            WHEN btrim(target.description) = '' THEN extra.lines
            ELSE btrim(target.description) || E'\n' || extra.lines
        END
        FROM (
            SELECT id, string_agg(text, E'\n' ORDER BY source_order, item_order) AS lines
            FROM (
                SELECT DISTINCT ON (id, text) id, text, source_order, item_order
                FROM (
                    SELECT j.id,
                           1 AS source_order,
                           t.ordinality AS item_order,
                           btrim(t.value) AS text
                    FROM jobs j
                    CROSS JOIN LATERAL jsonb_array_elements_text(
                        CASE
                            WHEN jsonb_typeof(j.content) = 'array' THEN j.content
                            ELSE '[]'::jsonb
                        END
                    ) WITH ORDINALITY AS t(value, ordinality)
                    WHERE btrim(t.value) <> ''
                      AND position(btrim(t.value) IN j.description) = 0
                    UNION ALL
                    SELECT j.id,
                           2 AS source_order,
                           t.ordinality AS item_order,
                           btrim(t.value) AS text
                    FROM jobs j
                    CROSS JOIN LATERAL jsonb_array_elements_text(
                        CASE
                            WHEN jsonb_typeof(j.requirements) = 'array' THEN j.requirements
                            ELSE '[]'::jsonb
                        END
                    ) WITH ORDINALITY AS t(value, ordinality)
                    WHERE btrim(t.value) <> ''
                      AND position(btrim(t.value) IN j.description) = 0
                ) raw
                ORDER BY id, text, source_order, item_order
            ) deduped
            GROUP BY id
        ) extra
        WHERE target.id = extra.id
          AND extra.lines IS NOT NULL
          AND extra.lines <> '';
    END IF;
END $$;

ALTER TABLE jobs ADD COLUMN IF NOT EXISTS description_embedding VECTOR(1024);
ALTER TABLE jobs DROP COLUMN IF EXISTS content_embedding;
ALTER TABLE jobs DROP COLUMN IF EXISTS requirements_embedding;
ALTER TABLE jobs DROP COLUMN IF EXISTS content;
ALTER TABLE jobs DROP COLUMN IF EXISTS requirements;

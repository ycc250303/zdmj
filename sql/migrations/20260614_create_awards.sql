-- 获奖信息表 + 简历 awards ID 列表

CREATE TABLE IF NOT EXISTS awards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    award_type SMALLINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    award_date DATE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_awards_type CHECK (award_type IN (1, 2, 3))
);

CREATE INDEX IF NOT EXISTS idx_awards_user_id ON awards(user_id);
CREATE INDEX IF NOT EXISTS idx_awards_user_id_award_date ON awards(user_id, award_date);

ALTER TABLE resumes ADD COLUMN IF NOT EXISTS awards JSONB DEFAULT '[]'::jsonb;

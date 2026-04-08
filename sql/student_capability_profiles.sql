-- ==========================学生就业能力画像表==========================
CREATE TABLE IF NOT EXISTS student_capability_profiles (
    id BIGSERIAL PRIMARY KEY,
    -- 画像ID
    user_id BIGINT UNIQUE NOT NULL,
    -- 关联用户ID（逻辑外键：users.id）
    professional_skills TEXT,
    -- 专业技能
    certificates TEXT,
    -- 证书
    innovation_ability TEXT,
    -- 创新能力
    learning_ability TEXT,
    -- 学习能力
    pressure_resistance TEXT,
    -- 抗压能力
    communication_ability TEXT,
    -- 沟通能力
    practical_ability TEXT,
    -- 实习能力
    completeness_score INTEGER NOT NULL DEFAULT 0,
    -- 完整度评分 (0-100)
    competitiveness_score INTEGER NOT NULL DEFAULT 0,
    -- 竞争力评分 (0-100)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_student_capability_profiles_user_id ON student_capability_profiles(user_id);

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    -- 用户ID
    username VARCHAR(50) UNIQUE NOT NULL,
    -- 用户名
    password VARCHAR(500) NOT NULL,
    -- 密码（加密）
    email VARCHAR(100) NOT NULL,
    -- 邮箱
    create_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- 创建时间
    update_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP -- 更新时间
);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
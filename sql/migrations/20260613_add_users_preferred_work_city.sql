-- 用户意向工作城市（我的简历-基本信息）
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_work_city VARCHAR(255);

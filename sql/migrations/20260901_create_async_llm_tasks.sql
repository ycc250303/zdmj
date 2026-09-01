-- LLM / Embedding 异步任务表（Redis Stream 入队的 DB 真相）
-- 设计见 docs/backend/llm-async-stream.md

CREATE TABLE IF NOT EXISTS async_llm_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    -- 发起人（逻辑外键：users.id）；消费者无 UserHolder，权限与写库用此字段
    task_type SMALLINT NOT NULL,
    -- 任务类型：1=STUDENT_PROFILE 学生能力画像
    --           2=JOB_PROFILE 岗位能力画像
    --           3=JOB_GRAPH 岗位职业图谱
    --           4=JOB_MATCH 人岗匹配
    --           5=CAREER_REPORT 职业发展报告
    --           6=REPORT_POLISH 报告润色
    --           7=REPORT_CHECK 报告完整性检查
    --           8=RESUME_PARSE 简历识别
    --           9=KB_EMBED 知识库向量化
    --           10=KB_DELETE 知识库向量删除
    biz_key VARCHAR(128) NOT NULL,
    -- 去重键，如 user:{userId}、job:{jobId}、user:{userId}:job:{jobId}
    status SMALLINT NOT NULL DEFAULT 1,
    -- 1=pending 2=running 3=success 4=failed（与 knowledge_vector_tasks 一致）
    payload JSONB,
    -- 入队参数，如 match 的 weights、简历 pdfUrl/rawText
    result JSONB,
    -- 无独立业务表时的成功结果（如 RESUME_PARSE）
    error_message TEXT,
    -- 失败摘要，不落堆栈
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_async_llm_tasks_type CHECK (task_type BETWEEN 1 AND 10),
    CONSTRAINT chk_async_llm_tasks_status CHECK (status IN (1, 2, 3, 4))
);

-- 进行中同一 (task_type, biz_key) 只允许一条；SUCCESS/FAILED 不占坑，可再点
CREATE UNIQUE INDEX IF NOT EXISTS uk_async_llm_tasks_inflight
    ON async_llm_tasks (task_type, biz_key)
    WHERE status IN (1, 2);

CREATE INDEX IF NOT EXISTS idx_async_llm_tasks_user_id
    ON async_llm_tasks (user_id);

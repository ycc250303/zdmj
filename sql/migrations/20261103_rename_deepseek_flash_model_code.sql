-- 用户 LLM 目录：deepseek-v4-flash 更名为 deepseek-flash（V4.1）
UPDATE user_llm_config
SET model_code = 'deepseek-flash', updated_at = CURRENT_TIMESTAMP
WHERE model_code = 'deepseek-v4-flash';

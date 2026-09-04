-- 用户 LLM 目录：qwen3.6-plus / qwen3.7-max 更名为 3.8 Flash / Max
UPDATE user_llm_config
SET model_code = 'qwen3.8-flash', updated_at = CURRENT_TIMESTAMP
WHERE model_code = 'qwen3.6-plus';

UPDATE user_llm_config
SET model_code = 'qwen3.8-max', updated_at = CURRENT_TIMESTAMP
WHERE model_code = 'qwen3.7-max';

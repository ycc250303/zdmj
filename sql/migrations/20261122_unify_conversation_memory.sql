-- 会话消息增加回复关联、生成状态及约束，移除独立记忆表。
BEGIN;
LOCK TABLE conversations, messages IN ACCESS EXCLUSIVE MODE;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_message_id BIGINT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS generation_status SMALLINT;

ALTER TABLE messages DROP CONSTRAINT IF EXISTS ck_messages_generation;
ALTER TABLE messages ADD CONSTRAINT ck_messages_generation CHECK (
        (role = 2 AND generation_status IS NOT NULL AND generation_status BETWEEN 1 AND 5
            AND reply_to_message_id IS NOT NULL AND reply_to_message_id > 0)
        OR (role IN (1, 3) AND generation_status IS NULL AND reply_to_message_id IS NULL)
    );
-- 用户范围查询使用已有 (user_id, conversation_id) 索引。
DROP INDEX IF EXISTS idx_messages_user_id;
CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_reply_to ON messages(reply_to_message_id);
DROP TABLE IF EXISTS spring_ai_chat_memory;
COMMIT;

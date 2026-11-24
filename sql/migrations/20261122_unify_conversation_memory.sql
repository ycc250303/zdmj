-- 停止应用写入并完成数据库备份后执行；单事务，可重复执行。
BEGIN;
LOCK TABLE conversations, messages IN ACCESS EXCLUSIVE MODE;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_message_id BIGINT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS generation_status SMALLINT;

-- 老回复仅按同用户、同会话、相邻序号的用户问题建立关联，不推断生成结果。
UPDATE messages a SET reply_to_message_id = q.id, generation_status = 5
FROM messages q
WHERE a.role = 2 AND a.generation_status IS NULL
    AND q.role = 1 AND q.user_id = a.user_id AND q.conversation_id = a.conversation_id
    AND q.sequence = a.sequence - 1;

DO $migration$
BEGIN
    IF EXISTS (SELECT 1 FROM messages a LEFT JOIN messages q ON q.id = a.reply_to_message_id
        WHERE a.role = 2 AND (a.generation_status IS NULL OR q.id IS NULL
            OR q.role <> 1 OR q.user_id <> a.user_id OR q.conversation_id <> a.conversation_id
            OR q.sequence >= a.sequence)) THEN
        RAISE EXCEPTION '存在无法关联用户问题的助手消息，迁移已终止';
    END IF;
    IF to_regclass('spring_ai_chat_memory') IS NOT NULL THEN
        -- 锁定并核查旧记忆，禁止丢失仍属于现存会话的用户问题或回复。
        LOCK TABLE spring_ai_chat_memory IN ACCESS EXCLUSIVE MODE;
        IF EXISTS (
            SELECT 1 FROM spring_ai_chat_memory memory
            JOIN conversations c ON c.id::text = memory.conversation_id
            WHERE memory.type IN ('USER', 'ASSISTANT') AND btrim(regexp_replace(memory.content, '\s+', ' ', 'g')) <> ''
            AND NOT EXISTS (
                SELECT 1 FROM messages m WHERE m.conversation_id = c.id AND m.user_id = c.user_id
                AND m.role = CASE memory.type WHEN 'USER' THEN 1 ELSE 2 END
                AND btrim(regexp_replace(m.content, '\s+', ' ', 'g'))
                    = btrim(regexp_replace(memory.content, '\s+', ' ', 'g'))
            )
        ) THEN
            RAISE EXCEPTION '独立记忆表存在消息表未保存的内容，迁移已终止，请核查数据';
        END IF;
    END IF;
END
$migration$;

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

-- 检索范围保存在 conversations.config，上下文列没有读取方
ALTER TABLE conversations DROP COLUMN IF EXISTS context;

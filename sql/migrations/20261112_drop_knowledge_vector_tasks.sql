DROP TABLE IF EXISTS knowledge_vector_tasks;

ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS file_status SMALLINT NOT NULL DEFAULT 1;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'knowledge_documents'::regclass
                   AND conname = 'ck_knowledge_documents_file_status') THEN
        ALTER TABLE knowledge_documents ADD CONSTRAINT ck_knowledge_documents_file_status
            CHECK (file_status BETWEEN 0 AND 3);
    END IF;
END $$;
COMMENT ON COLUMN knowledge_documents.file_status IS '原文件状态：0写入中、1可用、2写入失败、3删除中';
COMMENT ON COLUMN knowledge_documents.content IS '文件类型保存用户目录相对标识，链接类型保存来源地址';

UPDATE knowledge_documents SET metadata='{}'::jsonb WHERE metadata IS NULL;
ALTER TABLE knowledge_documents ALTER COLUMN metadata SET NOT NULL;

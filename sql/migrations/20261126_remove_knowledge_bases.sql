-- 文档直接保存范围与用户归属，向量只关联文档。
BEGIN;
ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS scope SMALLINT;
ALTER TABLE knowledge_documents ALTER COLUMN scope SET NOT NULL;
ALTER TABLE knowledge_documents ALTER COLUMN user_id DROP NOT NULL, ALTER COLUMN user_id DROP DEFAULT;
ALTER TABLE knowledge_documents ADD CONSTRAINT ck_knowledge_documents_scope_owner CHECK (
        (scope=1 AND user_id IS NOT NULL AND user_id>0)
        OR (scope IN (2,3) AND user_id IS NULL));
COMMENT ON COLUMN knowledge_documents.scope IS '文档使用范围：1用户私有、2系统通用、3学习路径';
COMMENT ON COLUMN knowledge_documents.user_id IS '私有文档的用户编号，系统资料为空';
DROP INDEX IF EXISTS idx_knowledge_documents_user_id;
DROP INDEX IF EXISTS idx_knowledge_documents_type;
DROP INDEX IF EXISTS idx_knowledge_documents_embedding_status;
DROP INDEX IF EXISTS idx_knowledge_documents_content_hash;
DROP INDEX IF EXISTS uk_knowledge_documents_kid_content;
DROP INDEX IF EXISTS idx_knowledge_documents_source_type;
DROP INDEX IF EXISTS idx_knowledge_documents_knowledge_id;
DROP INDEX IF EXISTS uk_knowledge_documents_kid_uri;
CREATE UNIQUE INDEX uk_knowledge_documents_user_content ON knowledge_documents(user_id,content) WHERE scope=1;
CREATE UNIQUE INDEX uk_knowledge_documents_system_content ON knowledge_documents(scope,content) WHERE scope IN (2,3);
CREATE INDEX idx_knowledge_documents_user_page ON knowledge_documents(user_id,created_at DESC,id DESC) WHERE scope=1;
CREATE INDEX idx_knowledge_documents_ready_scope ON knowledge_documents(scope,id) WHERE file_status=1 AND embedding_status=3;
DROP INDEX IF EXISTS idx_knowledge_vectors_user_id_knowledge_id;
DROP INDEX IF EXISTS idx_knowledge_vectors_document_id;
DROP INDEX IF EXISTS uk_knowledge_vectors_kid_did_chunk;
DROP INDEX IF EXISTS idx_knowledge_vectors_knowledge_id;
DROP INDEX IF EXISTS idx_knowledge_vectors_user_id;
ALTER TABLE knowledge_vectors ALTER COLUMN document_id SET NOT NULL,
    ALTER COLUMN chunk_index SET NOT NULL,
    ADD CONSTRAINT ck_knowledge_vectors_chunk_index CHECK (chunk_index>=0);
CREATE UNIQUE INDEX uk_knowledge_vectors_document_chunk ON knowledge_vectors(document_id,chunk_index);
ALTER TABLE knowledge_documents DROP COLUMN knowledge_id;
ALTER TABLE knowledge_vectors DROP COLUMN knowledge_id, DROP COLUMN user_id;
DROP TABLE knowledge_bases;
COMMIT;

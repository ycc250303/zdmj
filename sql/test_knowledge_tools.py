"""使用隔离 PostgreSQL 验证系统文档导入事务与评测样本格式。"""
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

import psycopg2

import import_system_knowledge as importer
import eval_rag_retrieval as evaluator


class KnowledgeToolsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.conn = psycopg2.connect(os.environ['KNOWLEDGE_TEST_DSN'])
        schema = Path(__file__).with_name('pgsql.sql').read_text()
        with cls.conn:
            with cls.conn.cursor() as cursor:
                cursor.execute(schema)

    @classmethod
    def tearDownClass(cls):
        cls.conn.close()

    def setUp(self):
        with self.conn:
            with self.conn.cursor() as cursor:
                cursor.execute('TRUNCATE knowledge_documents,knowledge_vectors RESTART IDENTITY')
        self.vectors = [[1.0] + [0.0] * 1023]

    def query(self, statement):
        with self.conn:
            with self.conn.cursor() as cursor:
                cursor.execute(statement)
                return cursor.fetchall()

    def save(self, scope=2, raw='正文', chunks=None, vectors=None):
        return importer.save_document(self.conn, scope, 'java/topic.md', '标题', raw,
                                      {'docCategory': 'general'},
                                      chunks or [importer.Chunk(0, raw)], vectors or self.vectors)

    def test_replace_preserves_document_and_atomically_replaces_chunks(self):
        identifier = self.save()
        created = self.query('SELECT created_at FROM knowledge_documents')[0][0]
        self.assertEqual(self.save(raw='更新正文'), identifier)
        self.assertEqual(self.query('SELECT scope,user_id,chunk_count,embedding_status,last_error FROM knowledge_documents'),
                         [(2, None, 1, 3, None)])
        self.assertEqual(self.query('SELECT created_at FROM knowledge_documents')[0][0], created)
        self.assertEqual(self.query('SELECT content FROM knowledge_vectors'), [('更新正文',)])
        self.assertEqual(self.query('SELECT content_hash FROM knowledge_documents'), [(importer.sha256_hex('更新正文'),)])
        self.assertTrue(self.query('SELECT last_embedded_at=updated_at FROM knowledge_documents')[0][0])
        self.assertNotEqual(self.save(scope=3), identifier)
        self.assertEqual(len(self.query('SELECT id FROM knowledge_documents')), 2)

    def test_failed_vector_replace_restores_previous_document_and_vectors(self):
        self.save()
        before = self.query('SELECT row_to_json(d)::text FROM knowledge_documents d')
        old_vectors = self.query('SELECT row_to_json(v)::text FROM knowledge_vectors v')
        with self.assertRaises(psycopg2.errors.UniqueViolation):
            self.save(raw='应回滚', chunks=[importer.Chunk(0, '甲'), importer.Chunk(0, '乙')],
                      vectors=self.vectors * 2)
        self.assertEqual(self.query('SELECT row_to_json(d)::text FROM knowledge_documents d'), before)
        self.assertEqual(self.query('SELECT row_to_json(v)::text FROM knowledge_vectors v'), old_vectors)

    def test_embedding_failure_does_not_modify_existing_document(self):
        self.save()
        before = self.query('SELECT row_to_json(d)::text FROM knowledge_documents d')
        client = Mock()
        client.embeddings.create.side_effect = RuntimeError('provider failed')
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'java/topic.md'
            path.parent.mkdir()
            path.write_text('新内容')
            with self.assertRaisesRegex(RuntimeError, 'provider failed'):
                importer.import_file(client, self.conn, 2, path, Path(directory), False)
        self.assertEqual(self.query('SELECT row_to_json(d)::text FROM knowledge_documents d'), before)

    def test_embedding_response_requires_exact_indices_dimensions_and_finite_values(self):
        client = Mock()
        for response in [[], [SimpleNamespace(index=1, embedding=self.vectors[0])],
                         [SimpleNamespace(index=0, embedding=[1.0])],
                         [SimpleNamespace(index=0, embedding=[float('nan')] * 1024)]]:
            with self.subTest(response_length=len(response)):
                client.embeddings.create.return_value = SimpleNamespace(data=response)
                with self.assertRaises(RuntimeError):
                    importer.embed_batch(client, ['正文'])
        client.embeddings.create.return_value = SimpleNamespace(data=[SimpleNamespace(index=0, embedding=self.vectors[0])])
        self.assertEqual(importer.embed_batch(client, ['正文']), self.vectors)

    def test_splitter_keeps_short_tail_and_discards_whitespace(self):
        splitter = importer.TokenTextSplitterCompat()
        self.assertEqual(splitter.split('   '), [])
        self.assertEqual([c.text for c in splitter.split('短尾')], ['短尾'])
        text = '完整内容。' * 1200 + '\n最后短尾'
        chunks = splitter.split(text)
        self.assertTrue(chunks[-1].text.endswith('最后短尾'))
        self.assertTrue(all(chunk.text.strip() for chunk in chunks))
        self.assertEqual([c.index for c in chunks], list(range(len(chunks))))

    def test_dataset_round_trip_preserves_gold_identifiers(self):
        case = evaluator.EvalCase(7, 3, 2, 0, 8, '标题', 'java/topic.md', '正文', '问题')
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'dataset.jsonl'
            evaluator.write_dataset(path, [case])
            self.assertEqual(evaluator.load_dataset(path), [case])
            record = json.loads(path.read_text())[0]
            self.assertEqual(record['scope'], 2)
            self.assertNotIn('knowledge_id', record)
            other = evaluator.EvalCase(8, 4, 3, 0, 8, '路径', 'roadmap.md', '正文', '问题')
            evaluator.write_dataset(path, [case, other])
            with self.assertRaisesRegex(ValueError, '相同资料范围'):
                evaluator.load_dataset(path)
        self.assertEqual(evaluator.run_self_test(), 0)


if __name__ == '__main__':
    unittest.main()

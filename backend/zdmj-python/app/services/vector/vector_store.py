"""
pgvector 向量存储服务（组合类）

功能：
- 统一接口，组合三种向量存储服务
- 保持向后兼容性
- 支持 knowledge_vectors、job_vectors、project_code_vectors 三种表

使用说明：
1. 知识库向量化：使用 save_knowledge_vectors() 保存文档块向量
2. 岗位向量化：使用 save_job_vectors() 保存岗位描述向量
3. 项目代码向量化：使用 save_project_code_vectors() 保存项目代码片段向量
4. 简历向量化：不需要存储，直接使用 embedding.py 的 QwenEmbedding.embed_query() 
   计算简历向量，然后使用 search_job_vectors() 进行岗位匹配
"""

from __future__ import annotations

import enum
from typing import Optional, Sequence

from langchain_core.documents import Document

from app.services.vector.job_vector_store import JobVectorStore
from app.services.vector.knowledge_vector_store import KnowledgeVectorStore
from app.services.vector.project_code_vector_store import ProjectCodeVectorStore


class VectorTableType(enum.Enum):
    """向量表类型枚举"""

    KNOWLEDGE = "knowledge_vectors"  # 知识库向量表
    JOB = "job_vectors"  # 岗位向量表
    PROJECT_CODE = "project_code_vectors"  # 项目代码向量表


class VectorStore:
    """
    pgvector 向量存储服务（组合类）

    封装多种向量表的存储、查询、删除操作。
    支持 knowledge_vectors、job_vectors、project_code_vectors 等表。
    使用组合模式，内部包含三个专门的向量存储服务实例。
    """

    def __init__(self) -> None:
        """初始化向量存储服务"""
        self._knowledge_store = KnowledgeVectorStore()
        self._job_store = JobVectorStore()
        self._project_code_store = ProjectCodeVectorStore()

    # ==================== 知识库向量方法 ====================

    async def save_knowledge_vectors(
        self,
        knowledge_id: int,
        user_id: int,
        documents: Sequence[Document],
        embeddings: Sequence[list[float]],
    ) -> list[int]:
        """批量保存向量到 knowledge_vectors 表"""
        return await self._knowledge_store.save_vectors(
            knowledge_id, user_id, documents, embeddings
        )

    async def search_knowledge_vectors(
        self,
        query_embedding: list[float],
        user_id: int,
        *,
        top_k: int = 10,
        knowledge_id: Optional[int] = None,
        min_score: Optional[float] = None,
    ) -> list[dict]:
        """基于余弦相似度搜索知识库向量"""
        return await self._knowledge_store.search(
            query_embedding, user_id, top_k=top_k, knowledge_id=knowledge_id, min_score=min_score
        )

    async def delete_knowledge_vectors(
        self,
        vector_ids: Optional[Sequence[int]] = None,
        knowledge_id: Optional[int] = None,
        user_id: Optional[int] = None,
    ) -> int:
        """删除知识库向量"""
        return await self._knowledge_store.delete(
            vector_ids=vector_ids, knowledge_id=knowledge_id, user_id=user_id
        )

    async def get_vectors_by_knowledge_id(
        self, knowledge_id: int, user_id: Optional[int] = None
    ) -> list[dict]:
        """根据 knowledge_id 获取所有向量（不进行相似度搜索）"""
        return await self._knowledge_store.get_by_knowledge_id(knowledge_id, user_id)

    # ==================== 岗位向量方法 ====================

    async def save_job_vectors(
        self,
        job_id: int,
        user_id: int,
        embedding: list[float],
        metadata: dict,
    ) -> int:
        """保存岗位向量到 job_vectors 表"""
        return await self._job_store.save(job_id, user_id, embedding, metadata)

    async def search_job_vectors(
        self,
        query_embedding: list[float],
        user_id: int,
        *,
        top_k: int = 10,
        min_score: Optional[float] = None,
    ) -> list[dict]:
        """基于余弦相似度搜索岗位向量"""
        return await self._job_store.search(
            query_embedding, user_id, top_k=top_k, min_score=min_score
        )

    async def delete_job_vector(
        self,
        job_id: int,
        user_id: Optional[int] = None,
    ) -> int:
        """删除岗位向量"""
        return await self._job_store.delete(job_id, user_id)

    # ==================== 项目代码向量方法 ====================

    async def save_project_code_vectors(
        self,
        knowledge_id: int,
        user_id: int,
        documents: Sequence[Document],
        embeddings: Sequence[list[float]],
    ) -> list[int]:
        """批量保存项目代码向量到 project_code_vectors 表"""
        return await self._project_code_store.save_vectors(
            knowledge_id, user_id, documents, embeddings
        )

    async def search_project_code_vectors(
        self,
        query_embedding: list[float],
        user_id: int,
        *,
        top_k: int = 10,
        knowledge_id: Optional[int] = None,
        min_score: Optional[float] = None,
    ) -> list[dict]:
        """基于余弦相似度搜索项目代码向量"""
        return await self._project_code_store.search(
            query_embedding,
            user_id,
            top_k=top_k,
            knowledge_id=knowledge_id,
            min_score=min_score,
        )

    async def delete_project_code_vectors(
        self,
        vector_ids: Optional[Sequence[int]] = None,
        knowledge_id: Optional[int] = None,
        user_id: Optional[int] = None,
    ) -> int:
        """删除项目代码向量"""
        return await self._project_code_store.delete(
            vector_ids=vector_ids, knowledge_id=knowledge_id, user_id=user_id
        )


async def main() -> None:
    """
    简单测试入口：

    运行方式（示例）：
        python -m app.services.vector.vector_store

    测试逻辑：
    - 连接数据库
    - 测试三种向量存储的保存、查询、删除操作
    """
    import asyncio
    import logging

    from app.database import db
    from app.services.vector.embedding import QwenEmbedding
    from langchain_core.documents import Document

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )

    # 连接数据库
    await db.connect_postgres()

    try:
        # 创建向量化服务和向量存储服务
        embedding_service = QwenEmbedding(batch_size=10)
        vector_store = VectorStore()
        user_id = 1

        # ==================== 测试知识库向量 ====================
        print("\n" + "=" * 60)
        print("==== 测试知识库向量存储 ====")
        print("=" * 60)

        knowledge_docs = [
            Document(
                page_content="LangChain 是一个强大的 LLM 应用开发框架，提供了丰富的工具和组件",
                metadata={"source": "langchain_doc.txt", "chunk_index": 0},
            ),
            Document(
                page_content="pgvector 是 PostgreSQL 的向量扩展，支持高效的向量检索和相似度搜索",
                metadata={"source": "pgvector_doc.txt", "chunk_index": 1},
            ),
        ]

        # 向量化
        knowledge_texts = [doc.page_content for doc in knowledge_docs]
        knowledge_embeddings = embedding_service.embed_documents(knowledge_texts)

        # 保存知识库向量
        knowledge_id = 1
        knowledge_vector_ids = await vector_store.save_knowledge_vectors(
            knowledge_id=knowledge_id,
            user_id=user_id,
            documents=knowledge_docs,
            embeddings=knowledge_embeddings,
        )
        print(f"✓ 知识库向量保存成功，vector_ids: {knowledge_vector_ids}")

        # 搜索知识库向量
        query_text = "什么是向量数据库？"
        query_vec = embedding_service.embed_query(query_text)
        knowledge_results = await vector_store.search_knowledge_vectors(
            query_embedding=query_vec,
            user_id=user_id,
            top_k=5,
        )
        print(f"✓ 知识库向量搜索成功，找到 {len(knowledge_results)} 个结果")
        for idx, result in enumerate(knowledge_results[:3], 1):
            print(f"  {idx}. score={result['score']:.4f}, content={result['content'][:50]}...")

        # ==================== 测试岗位向量 ====================
        print("\n" + "=" * 60)
        print("==== 测试岗位向量存储 ====")
        print("=" * 60)

        job_id = 1
        job_metadata = {
            "job_name": "后端开发工程师",
            "company_name": "测试公司",
            "location": "北京",
            "salary": "15-25k",
            "requirements": "熟悉Python、PostgreSQL、向量数据库",
        }
        job_text = "后端开发工程师，要求熟悉Python和PostgreSQL，有向量数据库使用经验"
        job_vector = embedding_service.embed_query(job_text)

        # 保存岗位向量
        job_vector_id = await vector_store.save_job_vectors(
            job_id=job_id,
            user_id=user_id,
            embedding=job_vector,
            metadata=job_metadata,
        )
        print(f"✓ 岗位向量保存成功，vector_id: {job_vector_id}")

        # 搜索岗位向量（使用简历向量作为查询）
        resume_text = "我有3年Python开发经验，熟悉PostgreSQL和向量数据库，有RAG项目经验"
        resume_vector = embedding_service.embed_query(resume_text)
        job_results = await vector_store.search_job_vectors(
            query_embedding=resume_vector,
            user_id=user_id,
            top_k=5,
        )
        print(f"✓ 岗位向量搜索成功，匹配到 {len(job_results)} 个岗位")
        for idx, result in enumerate(job_results, 1):
            print(f"  {idx}. job_id={result['job_id']}, score={result['score']:.4f}")
            print(f"     metadata={result['metadata']}")

        # ==================== 测试项目代码向量 ====================
        print("\n" + "=" * 60)
        print("==== 测试项目代码向量存储 ====")
        print("=" * 60)

        project_code_docs = [
            Document(
                page_content="""def calculate_sum(a: int, b: int) -> int:
    \"\"\"计算两个数的和\"\"\"
    return a + b""",
                metadata={
                    "path": "src/utils/math.py",
                    "language": "python",
                    "functionName": "calculate_sum",
                    "startLine": 10,
                    "endLine": 12,
                },
            ),
            Document(
                page_content="""async def fetch_user_data(user_id: int) -> dict:
    \"\"\"异步获取用户数据\"\"\"
    async with db.acquire() as conn:
        return await conn.fetchrow('SELECT * FROM users WHERE id = $1', user_id)""",
                metadata={
                    "path": "src/services/user_service.py",
                    "language": "python",
                    "functionName": "fetch_user_data",
                    "startLine": 25,
                    "endLine": 28,
                },
            ),
        ]

        # 向量化
        project_code_texts = [doc.page_content for doc in project_code_docs]
        project_code_embeddings = embedding_service.embed_documents(project_code_texts)

        # 保存项目代码向量
        knowledge_id = 1
        project_code_vector_ids = await vector_store.save_project_code_vectors(
            knowledge_id=knowledge_id,
            user_id=user_id,
            documents=project_code_docs,
            embeddings=project_code_embeddings,
        )
        print(f"✓ 项目代码向量保存成功，vector_ids: {project_code_vector_ids}")

        # 搜索项目代码向量
        code_query_text = "如何实现异步数据库查询？"
        code_query_vec = embedding_service.embed_query(code_query_text)
        project_code_results = await vector_store.search_project_code_vectors(
            query_embedding=code_query_vec,
            user_id=user_id,
            top_k=5,
            knowledge_id=knowledge_id,
        )
        print(f"✓ 项目代码向量搜索成功，找到 {len(project_code_results)} 个结果")
        for idx, result in enumerate(project_code_results, 1):
            print(f"  {idx}. score={result['score']:.4f}, file_path={result['file_path']}")
            print(f"     content={result['content'][:60]}...")

        # ==================== 测试删除操作 ====================
        print("\n" + "=" * 60)
        print("==== 测试删除操作 ====")
        print("=" * 60)

        # 删除知识库向量
        deleted_knowledge_count = await vector_store.delete_knowledge_vectors(
            vector_ids=knowledge_vector_ids,
            user_id=user_id,
        )
        print(f"✓ 删除知识库向量成功，删除数量: {deleted_knowledge_count}")

        # 删除岗位向量
        deleted_job_count = await vector_store.delete_job_vector(
            job_id=job_id,
            user_id=user_id,
        )
        print(f"✓ 删除岗位向量成功，删除数量: {deleted_job_count}")

        # 删除项目代码向量
        deleted_project_count = await vector_store.delete_project_code_vectors(
            vector_ids=project_code_vector_ids,
            user_id=user_id,
        )
        print(f"✓ 删除项目代码向量成功，删除数量: {deleted_project_count}")

        print("\n" + "=" * 60)
        print("==== 所有测试完成 ====")
        print("=" * 60)

    except Exception as exc:
        logger = logging.getLogger(__name__)
        logger.exception("测试失败: %s", exc)
        print(f"\n❌ 测试失败: {exc}")
    finally:
        await db.disconnect_postgres()


if __name__ == "__main__":
    import asyncio

    asyncio.run(main())

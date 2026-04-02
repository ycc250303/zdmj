"""
pgvector 向量存储服务（组合类）

功能：
- 统一接口，组合两种向量存储服务
- 保持向后兼容性
- 支持 knowledge_vectors、project_code_vectors 两种表

使用说明：
1. 知识库向量化：使用 save_knowledge_vectors() 保存文档块向量
2. 项目代码向量化：使用 save_project_code_vectors() 保存项目代码片段向量
"""

from __future__ import annotations

import enum
from typing import Optional, Sequence

from langchain_core.documents import Document

from app.services.vector.knowledge_vector_store import KnowledgeVectorStore
from app.services.vector.project_code_vector_store import ProjectCodeVectorStore


class VectorTableType(enum.Enum):
    """向量表类型枚举"""

    KNOWLEDGE = "knowledge_vectors"  # 知识库向量表
    PROJECT_CODE = "project_code_vectors"  # 项目代码向量表


class VectorStore:
    """
    pgvector 向量存储服务（组合类）

    封装多种向量表的存储、删除操作。
    支持 knowledge_vectors、project_code_vectors 两种表。
    使用组合模式，内部包含两个专门的向量存储服务实例。
    """

    def __init__(self) -> None:
        """初始化向量存储服务"""
        self._knowledge_store = KnowledgeVectorStore()
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
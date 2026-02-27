"""
知识库向量存储服务

功能：
- 保存向量到 knowledge_vectors 表
- 基于余弦相似度搜索知识库向量
- 删除知识库向量
- 支持批量操作和事务处理
"""

from __future__ import annotations

import json
import logging
from typing import Optional, Sequence

from langchain_core.documents import Document

from app.database import db
from app.services.vector.base_vector import BaseVectorStore

logger = logging.getLogger(__name__)


class KnowledgeVectorStore(BaseVectorStore):
    """知识库向量存储服务"""

    async def save_vectors(
        self,
        knowledge_id: int,
        user_id: int,
        documents: Sequence[Document],
        embeddings: Sequence[list[float]],
    ) -> list[int]:
        """
        批量保存向量到 knowledge_vectors 表

        :param knowledge_id: 知识库文档ID
        :param user_id: 用户ID（数据隔离）
        :param documents: Document 列表，包含 page_content 和 metadata
        :param embeddings: 向量列表，每个向量是 1024 维的浮点数列表
        :return: 插入的 vector_ids 列表
        """
        if len(documents) != len(embeddings):
            raise ValueError(
                f"文档数量 ({len(documents)}) 与向量数量 ({len(embeddings)}) 不匹配"
            )

        if not documents:
            logger.warning("输入的文档列表为空，返回空列表")
            return []

        logger.info(
            "开始批量保存向量: knowledge_id=%d, user_id=%d, 数量=%d",
            knowledge_id,
            user_id,
            len(documents),
        )

        vector_ids: list[int] = []

        # 准备批量插入数据
        insert_data: list[tuple] = []
        for doc, embedding in zip(documents, embeddings):
            # 验证向量维度
            if len(embedding) != 1024:
                logger.warning(
                    "向量维度不匹配: 期望 1024，实际 %d，已跳过", len(embedding)
                )
                continue

            # 准备 metadata（合并原始 metadata 和额外信息）
            metadata = doc.metadata.copy() if doc.metadata else {}
            metadata["knowledgeId"] = knowledge_id
            if "source" not in metadata:
                metadata["source"] = metadata.get("path", "unknown")

            # 获取 chunk_index（如果存在）
            chunk_index = metadata.get("chunk_index")

            # 将向量转换为字符串格式
            vector_str = self._vector_to_str(embedding)

            # 将 metadata 序列化为 JSON 字符串
            metadata_json = json.dumps(metadata, ensure_ascii=False)

            # PostgreSQL TEXT 类型不允许包含 \x00，需要在入库前清理
            safe_content = doc.page_content.replace("\x00", "") if doc.page_content else doc.page_content

            insert_data.append(
                (
                    knowledge_id,
                    user_id,
                    vector_str,
                    safe_content,
                    metadata_json,
                    chunk_index,
                )
            )

        if not insert_data:
            logger.warning("没有有效的向量数据需要插入")
            return []

        # 批量插入（分批处理，避免单次 SQL 过长）
        batch_size = 100
        async with db.postgres_pool.acquire() as conn:
            async with conn.transaction():
                for batch_start in range(0, len(insert_data), batch_size):
                    batch_data = insert_data[batch_start : batch_start + batch_size]

                    # 构建多值 INSERT 语句
                    values_parts: list[str] = []
                    params: list = []
                    param_idx = 1

                    for item in batch_data:
                        values_parts.append(
                            f"(${param_idx}, ${param_idx + 1}, ${param_idx + 2}::vector, "
                            f"${param_idx + 3}, ${param_idx + 4}::jsonb, ${param_idx + 5})"
                        )
                        params.extend(item)
                        param_idx += 6

                    values_clause = ", ".join(values_parts)

                    # 执行批量插入并返回 IDs
                    rows = await conn.fetch(
                        f"""
                        INSERT INTO knowledge_vectors 
                        (knowledge_id, user_id, embedding, content, metadata, chunk_index)
                        VALUES {values_clause}
                        RETURNING id
                        """,
                        *params,
                    )

                    batch_ids = [row["id"] for row in rows]
                    vector_ids.extend(batch_ids)

                    logger.debug(
                        "已保存 %d/%d 个向量",
                        min(batch_start + batch_size, len(insert_data)),
                        len(insert_data),
                    )

        logger.info(
            "批量保存向量完成: knowledge_id=%d, 成功保存 %d 个向量",
            knowledge_id,
            len(vector_ids),
        )
        return vector_ids

    async def search(
        self,
        query_embedding: list[float],
        user_id: int,
        *,
        top_k: int = 10,
        knowledge_id: Optional[int] = None,
        min_score: Optional[float] = None,
    ) -> list[dict]:
        """
        基于余弦相似度搜索知识库向量

        :param query_embedding: 查询向量（1024 维）
        :param user_id: 用户ID（数据隔离，只搜索该用户的向量）
        :param top_k: 返回最相似的 top_k 个结果，默认 10
        :param knowledge_id: 可选，限制搜索范围到指定知识库
        :param min_score: 可选，最小相似度阈值（0-1），低于此值的结果会被过滤
        :return: 搜索结果列表，每个结果包含 id, knowledge_id, content, metadata, score 等字段
        """
        if len(query_embedding) != 1024:
            raise ValueError(f"查询向量维度必须是 1024，实际为 {len(query_embedding)}")

        logger.info(
            "开始知识库向量搜索: user_id=%d, top_k=%d, knowledge_id=%s",
            user_id,
            top_k,
            knowledge_id,
        )

        query_vector_str = self._vector_to_str(query_embedding)

        where_clause = "WHERE user_id = $2"
        params: list = [query_vector_str, user_id]
        param_idx = 3

        if knowledge_id is not None:
            where_clause += f" AND knowledge_id = ${param_idx}"
            params.append(knowledge_id)
            param_idx += 1

        if min_score is not None:
            having_clause = f"HAVING (1 - (embedding <=> $1::vector)) >= ${param_idx}"
            params.append(min_score)
        else:
            having_clause = ""

        query = f"""
            SELECT 
                id,
                knowledge_id,
                content,
                metadata,
                chunk_index,
                1 - (embedding <=> $1::vector) AS score
            FROM knowledge_vectors
            {where_clause}
            {having_clause}
            ORDER BY embedding <=> $1::vector
            LIMIT ${param_idx}
        """
        params.append(top_k)

        async with db.postgres_pool.acquire() as conn:
            rows = await conn.fetch(query, *params)

        results = []
        for row in rows:
            results.append(
                {
                    "id": row["id"],
                    "knowledge_id": row["knowledge_id"],
                    "content": row["content"],
                    "metadata": row["metadata"] or {},
                    "chunk_index": row["chunk_index"],
                    "score": float(row["score"]),
                }
            )

        logger.info(
            "知识库向量搜索完成: user_id=%d, 返回结果数=%d",
            user_id,
            len(results),
        )
        return results

    async def delete(
        self,
        vector_ids: Optional[Sequence[int]] = None,
        knowledge_id: Optional[int] = None,
        user_id: Optional[int] = None,
    ) -> int:
        """
        删除知识库向量

        :param vector_ids: 可选，要删除的向量ID列表
        :param knowledge_id: 可选，删除指定知识库的所有向量
        :param user_id: 可选，数据隔离，只删除指定用户的向量
        :return: 删除的向量数量
        """
        if not vector_ids and not knowledge_id:
            raise ValueError("必须提供 vector_ids 或 knowledge_id 之一")

        conditions: list[str] = []
        params: list = []
        param_idx = 1

        if vector_ids:
            conditions.append(f"id = ANY(${param_idx}::bigint[])")
            params.append(list(vector_ids))
            param_idx += 1

        if knowledge_id is not None:
            conditions.append(f"knowledge_id = ${param_idx}")
            params.append(knowledge_id)
            param_idx += 1

        if user_id is not None:
            conditions.append(f"user_id = ${param_idx}")
            params.append(user_id)
            param_idx += 1

        where_clause = " AND ".join(conditions)

        query = f"""
            DELETE FROM knowledge_vectors
            WHERE {where_clause}
            RETURNING id
        """

        logger.info(
            "开始删除知识库向量: vector_ids=%s, knowledge_id=%s, user_id=%s",
            vector_ids,
            knowledge_id,
            user_id,
        )

        async with db.postgres_pool.acquire() as conn:
            deleted_rows = await conn.fetch(query, *params)

        deleted_count = len(deleted_rows)
        logger.info("删除知识库向量完成，删除数量：%d", deleted_count)
        return deleted_count

    async def get_by_knowledge_id(
        self, knowledge_id: int, user_id: Optional[int] = None
    ) -> list[dict]:
        """
        根据 knowledge_id 获取所有向量（不进行相似度搜索）

        :param knowledge_id: 知识库文档ID
        :param user_id: 可选，数据隔离，只获取指定用户的向量
        :return: 向量列表，包含 id, content, metadata 等
        """
        conditions: list[str] = [f"knowledge_id = $1"]
        params: list = [knowledge_id]

        if user_id is not None:
            conditions.append("user_id = $2")
            params.append(user_id)

        where_clause = " AND ".join(conditions)

        query = f"""
            SELECT 
                id,
                knowledge_id,
                content,
                metadata,
                chunk_index,
                created_at
            FROM knowledge_vectors
            WHERE {where_clause}
            ORDER BY chunk_index NULLS LAST, id
        """

        logger.info(
            "开始获取向量: knowledge_id=%d, user_id=%s",
            knowledge_id,
            user_id,
        )

        async with db.postgres_pool.acquire() as conn:
            rows = await conn.fetch(query, *params)

        results = []
        for row in rows:
            results.append(
                {
                    "id": row["id"],
                    "knowledge_id": row["knowledge_id"],
                    "content": row["content"],
                    "metadata": row["metadata"] or {},
                    "chunk_index": row["chunk_index"],
                    "created_at": row["created_at"],
                }
            )

        logger.info(
            "获取向量完成: knowledge_id=%d, 返回数量=%d",
            knowledge_id,
            len(results),
        )
        return results

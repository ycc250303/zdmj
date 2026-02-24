"""
项目代码向量存储服务

功能：
- 保存向量到 project_code_vectors 表
- 基于余弦相似度搜索项目代码向量
- 删除项目代码向量
- 支持批量操作和事务处理
"""

from __future__ import annotations

import json
import logging
from typing import Optional, Sequence

from langchain_core.documents import Document

from app.database import db
from app.services.vector.base import BaseVectorStore

logger = logging.getLogger(__name__)


class ProjectCodeVectorStore(BaseVectorStore):
    """项目代码向量存储服务"""

    async def save_vectors(
        self,
        project_id: int,
        user_id: int,
        documents: Sequence[Document],
        embeddings: Sequence[list[float]],
    ) -> list[int]:
        """
        批量保存项目代码向量到 project_code_vectors 表

        :param project_id: 项目ID
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
            "开始批量保存项目代码向量: project_id=%d, user_id=%d, 数量=%d",
            project_id,
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
            metadata["projectId"] = project_id
            if "source" not in metadata:
                metadata["source"] = metadata.get("path", "unknown")

            # 获取 file_path（从 metadata 或 path）
            file_path = metadata.get("path") or metadata.get("source") or None

            # 将向量转换为字符串格式
            vector_str = self._vector_to_str(embedding)

            # 将 metadata 序列化为 JSON 字符串
            metadata_json = json.dumps(metadata, ensure_ascii=False)

            insert_data.append(
                (
                    project_id,
                    user_id,
                    file_path,
                    vector_str,
                    doc.page_content,
                    metadata_json,
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
                            f"(${param_idx}, ${param_idx + 1}, ${param_idx + 2}, "
                            f"${param_idx + 3}::vector, ${param_idx + 4}, ${param_idx + 5}::jsonb)"
                        )
                        params.extend(item)
                        param_idx += 6

                    values_clause = ", ".join(values_parts)

                    # 执行批量插入并返回 IDs
                    rows = await conn.fetch(
                        f"""
                        INSERT INTO project_code_vectors 
                        (project_id, user_id, file_path, embedding, content, metadata)
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
            "批量保存项目代码向量完成: project_id=%d, 成功保存 %d 个向量",
            project_id,
            len(vector_ids),
        )
        return vector_ids

    async def search(
        self,
        query_embedding: list[float],
        user_id: int,
        *,
        top_k: int = 10,
        project_id: Optional[int] = None,
        min_score: Optional[float] = None,
    ) -> list[dict]:
        """
        基于余弦相似度搜索项目代码向量

        :param query_embedding: 查询向量（1024 维）
        :param user_id: 用户ID（数据隔离，只搜索该用户的向量）
        :param top_k: 返回最相似的 top_k 个结果，默认 10
        :param project_id: 可选，限制搜索范围到指定项目
        :param min_score: 可选，最小相似度阈值（0-1），低于此值的结果会被过滤
        :return: 搜索结果列表，每个结果包含 id, project_id, file_path, content, metadata, score 等字段
        """
        if len(query_embedding) != 1024:
            raise ValueError(f"查询向量维度必须是 1024，实际为 {len(query_embedding)}")

        logger.info(
            "开始项目代码向量搜索: user_id=%d, top_k=%d, project_id=%s",
            user_id,
            top_k,
            project_id,
        )

        query_vector_str = self._vector_to_str(query_embedding)

        where_clause = "WHERE user_id = $2"
        params: list = [query_vector_str, user_id]
        param_idx = 3

        if project_id is not None:
            where_clause += f" AND project_id = ${param_idx}"
            params.append(project_id)
            param_idx += 1

        if min_score is not None:
            having_clause = f"HAVING (1 - (embedding <=> $1::vector)) >= ${param_idx}"
            params.append(min_score)
        else:
            having_clause = ""

        query = f"""
            SELECT 
                id,
                project_id,
                file_path,
                content,
                metadata,
                1 - (embedding <=> $1::vector) AS score
            FROM project_code_vectors
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
                    "project_id": row["project_id"],
                    "file_path": row["file_path"],
                    "content": row["content"],
                    "metadata": row["metadata"] or {},
                    "score": float(row["score"]),
                }
            )

        logger.info(
            "项目代码向量搜索完成: user_id=%d, 返回结果数=%d",
            user_id,
            len(results),
        )
        return results

    async def delete(
        self,
        vector_ids: Optional[Sequence[int]] = None,
        project_id: Optional[int] = None,
        user_id: Optional[int] = None,
    ) -> int:
        """
        删除项目代码向量

        :param vector_ids: 可选，要删除的向量ID列表
        :param project_id: 可选，删除指定项目的所有向量
        :param user_id: 可选，数据隔离，只删除指定用户的向量
        :return: 删除的向量数量
        """
        if not vector_ids and not project_id:
            raise ValueError("必须提供 vector_ids 或 project_id 之一")

        conditions: list[str] = []
        params: list = []
        param_idx = 1

        if vector_ids:
            conditions.append(f"id = ANY(${param_idx}::bigint[])")
            params.append(list(vector_ids))
            param_idx += 1

        if project_id is not None:
            conditions.append(f"project_id = ${param_idx}")
            params.append(project_id)
            param_idx += 1

        if user_id is not None:
            conditions.append(f"user_id = ${param_idx}")
            params.append(user_id)
            param_idx += 1

        where_clause = " AND ".join(conditions)

        query = f"""
            DELETE FROM project_code_vectors
            WHERE {where_clause}
            RETURNING id
        """

        logger.info(
            "开始删除项目代码向量: vector_ids=%s, project_id=%s, user_id=%s",
            vector_ids,
            project_id,
            user_id,
        )

        async with db.postgres_pool.acquire() as conn:
            deleted_rows = await conn.fetch(query, *params)

        deleted_count = len(deleted_rows)
        logger.info("删除项目代码向量完成，删除数量：%d", deleted_count)
        return deleted_count

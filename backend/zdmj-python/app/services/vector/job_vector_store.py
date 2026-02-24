"""
岗位向量存储服务

功能：
- 保存向量到 job_vectors 表
- 基于余弦相似度搜索岗位向量
- 删除岗位向量
"""

from __future__ import annotations

import json
import logging
from typing import Optional

from app.database import db
from app.services.vector.base import BaseVectorStore

logger = logging.getLogger(__name__)


class JobVectorStore(BaseVectorStore):
    """岗位向量存储服务"""

    async def save(
        self,
        job_id: int,
        user_id: int,
        embedding: list[float],
        metadata: dict,
    ) -> int:
        """
        保存岗位向量到 job_vectors 表

        :param job_id: 岗位ID
        :param user_id: 用户ID（数据隔离）
        :param embedding: 向量（1024 维）
        :param metadata: 元数据（职位名称、公司等）
        :return: 插入的 vector_id
        """
        if len(embedding) != 1024:
            raise ValueError(f"向量维度必须是 1024，实际为 {len(embedding)}")

        logger.info(
            "开始保存岗位向量: job_id=%d, user_id=%d",
            job_id,
            user_id,
        )

        vector_str = self._vector_to_str(embedding)

        # 将 metadata 序列化为 JSON 字符串
        metadata_json = json.dumps(metadata, ensure_ascii=False)

        async with db.postgres_pool.acquire() as conn:
            # 先删除该岗位的旧向量（如果存在）
            await conn.execute(
                "DELETE FROM job_vectors WHERE job_id = $1 AND user_id = $2",
                job_id,
                user_id,
            )

            # 插入新向量
            vector_id = await conn.fetchval(
                """
                INSERT INTO job_vectors 
                (job_id, user_id, embedding, metadata)
                VALUES ($1, $2, $3::vector, $4::jsonb)
                RETURNING id
                """,
                job_id,
                user_id,
                vector_str,
                metadata_json,
            )

        logger.info("岗位向量保存完成: job_id=%d, vector_id=%d", job_id, vector_id)
        return vector_id

    async def search(
        self,
        query_embedding: list[float],
        user_id: int,
        *,
        top_k: int = 10,
        min_score: Optional[float] = None,
    ) -> list[dict]:
        """
        基于余弦相似度搜索岗位向量

        :param query_embedding: 查询向量（1024 维，通常是简历向量）
        :param user_id: 用户ID（数据隔离，只搜索该用户的岗位）
        :param top_k: 返回最相似的 top_k 个结果，默认 10
        :param min_score: 可选，最小相似度阈值（0-1），低于此值的结果会被过滤
        :return: 搜索结果列表，每个结果包含 id, job_id, metadata, score 等字段
        """
        if len(query_embedding) != 1024:
            raise ValueError(f"查询向量维度必须是 1024，实际为 {len(query_embedding)}")

        logger.info(
            "开始岗位向量搜索: user_id=%d, top_k=%d",
            user_id,
            top_k,
        )

        query_vector_str = self._vector_to_str(query_embedding)

        where_clause = "WHERE user_id = $2"
        params: list = [query_vector_str, user_id]
        param_idx = 3

        if min_score is not None:
            having_clause = f"HAVING (1 - (embedding <=> $1::vector)) >= ${param_idx}"
            params.append(min_score)
        else:
            having_clause = ""

        query = f"""
            SELECT 
                id,
                job_id,
                metadata,
                1 - (embedding <=> $1::vector) AS score
            FROM job_vectors
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
                    "job_id": row["job_id"],
                    "metadata": row["metadata"] or {},
                    "score": float(row["score"]),
                }
            )

        logger.info(
            "岗位向量搜索完成: user_id=%d, 返回结果数=%d",
            user_id,
            len(results),
        )
        return results

    async def delete(
        self,
        job_id: int,
        user_id: Optional[int] = None,
    ) -> int:
        """
        删除岗位向量

        :param job_id: 岗位ID
        :param user_id: 可选，数据隔离，只删除指定用户的向量
        :return: 删除的向量数量（通常为 0 或 1）
        """
        conditions: list[str] = [f"job_id = $1"]
        params: list = [job_id]

        if user_id is not None:
            conditions.append("user_id = $2")
            params.append(user_id)

        where_clause = " AND ".join(conditions)

        query = f"""
            DELETE FROM job_vectors
            WHERE {where_clause}
            RETURNING id
        """

        logger.info(
            "开始删除岗位向量: job_id=%d, user_id=%s",
            job_id,
            user_id,
        )

        async with db.postgres_pool.acquire() as conn:
            deleted_rows = await conn.fetch(query, *params)

        deleted_count = len(deleted_rows)
        logger.info("删除岗位向量完成，删除数量：%d", deleted_count)
        return deleted_count

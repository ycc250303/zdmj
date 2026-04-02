"""
项目代码向量存储服务

功能：
- 保存向量到 project_code_vectors 表
- 删除项目代码向量
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


class ProjectCodeVectorStore(BaseVectorStore):
    """项目代码向量存储服务"""

    async def save_vectors(
        self,
        knowledge_id: int,
        user_id: int,
        documents: Sequence[Document],
        embeddings: Sequence[list[float]],
    ) -> list[int]:
        """
        批量保存项目代码向量到 project_code_vectors 表

        :param knowledge_id: 知识库ID（逻辑外键：knowledge_bases.id）
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
            "开始批量保存项目代码向量: knowledge_id=%d, user_id=%d, 数量=%d",
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

            # 准备 metadata（只保留必要字段，删除冗余字段）
            # 删除冗余字段：path（表字段已有file_path）、source（与path相同）、
            # knowledgeId/knowledge_id（表字段已有knowledge_id）、repo_url（大量重复）
            metadata = {}
            
            # 定义需要保留的字段白名单
            ALLOWED_METADATA_FIELDS = {
                "chunk_index",      # 分块索引
                "total_chunks",     # 总块数
                "source_doc_index", # 源文档索引
                "language",         # 编程语言（用于未来语言特定检索）
                "type",             # 知识类型（用于过滤）
                "file_size",        # 文件大小（可选，用于过滤大文件）
            }
            
            # 只保留白名单中的字段，明确排除冗余字段
            if doc.metadata:
                for key in ALLOWED_METADATA_FIELDS:
                    if key in doc.metadata:
                        metadata[key] = doc.metadata[key]
                
            # 获取 file_path
            # 优先使用分块/增强阶段写入的 file_path，其次回退到原始的 path 字段
            file_path = None
            if doc.metadata:
                file_path = (
                    doc.metadata.get("file_path")
                    or doc.metadata.get("path")
                    or doc.metadata.get("source")
                )

            # 将向量转换为字符串格式
            vector_str = self._vector_to_str(embedding)

            # 将 metadata 序列化为 JSON 字符串
            metadata_json = json.dumps(metadata, ensure_ascii=False)

            insert_data.append(
                (
                    knowledge_id,
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
                        (knowledge_id, user_id, file_path, embedding, content, metadata)
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
            "批量保存项目代码向量完成: knowledge_id=%d, 成功保存 %d 个向量",
            knowledge_id,
            len(vector_ids),
        )
        return vector_ids

    async def delete(
        self,
        vector_ids: Optional[Sequence[int]] = None,
        knowledge_id: Optional[int] = None,
        user_id: Optional[int] = None,
    ) -> int:
        """
        删除项目代码向量

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
            DELETE FROM project_code_vectors
            WHERE {where_clause}
            RETURNING id
        """

        logger.info(
            "开始删除项目代码向量: vector_ids=%s, knowledge_id=%s, user_id=%s",
            vector_ids,
            knowledge_id,
            user_id,
        )

        async with db.postgres_pool.acquire() as conn:
            deleted_rows = await conn.fetch(query, *params)

        deleted_count = len(deleted_rows)
        logger.info("删除项目代码向量完成，删除数量：%d", deleted_count)
        return deleted_count

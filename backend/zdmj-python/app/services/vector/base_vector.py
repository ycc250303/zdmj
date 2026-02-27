"""
向量存储基类
包含公共方法和工具函数
"""

from __future__ import annotations

import logging

from app.database import db

logger = logging.getLogger(__name__)


class BaseVectorStore:
    """向量存储基类，包含公共方法"""

    def __init__(self) -> None:
        """初始化向量存储服务"""
        if not db.postgres_pool:
            raise RuntimeError(
                "PostgreSQL 连接池未初始化，请先调用 db.connect_postgres()"
            )

    @staticmethod
    def _vector_to_str(embedding: list[float]) -> str:
        """将向量转换为 pgvector 字符串格式"""
        return "[" + ",".join(str(x) for x in embedding) + "]"

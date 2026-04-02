"""知识库仓储。"""

from __future__ import annotations

from typing import Any, Optional

from app.database import db


async def load_knowledge_row(knowledge_id: int) -> Optional[dict[str, Any]]:
    """从 knowledge_bases 表加载单条记录。"""
    if not db.postgres_pool:
        raise RuntimeError("PostgreSQL 连接未初始化")

    async with db.postgres_pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, user_id, name, project_id, type, content, tag, vector_ids
            FROM knowledge_bases
            WHERE id = $1
            """,
            knowledge_id,
        )

    return dict(row) if row else None


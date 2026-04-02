"""知识向量任务仓储。"""

from __future__ import annotations

import json
import uuid
from dataclasses import dataclass
from typing import Optional

from app.database import db


@dataclass(frozen=True)
class TaskType:
    EMBEDDING: int = 1
    DELETE: int = 2


@dataclass(frozen=True)
class TaskStatus:
    PENDING: int = 1
    RUNNING: int = 2
    SUCCESS: int = 3
    FAILED: int = 4
    CANCELLED: int = 5


TASK_STATUS_TO_STR = {
    TaskStatus.PENDING: "PENDING",
    TaskStatus.RUNNING: "RUNNING",
    TaskStatus.SUCCESS: "SUCCESS",
    TaskStatus.FAILED: "FAILED",
    TaskStatus.CANCELLED: "CANCELLED",
}


async def create_task_record(
    *,
    task_type: int,
    knowledge_id: int,
    user_id: int,
    status: int = TaskStatus.PENDING,
    message: str = "",
) -> str:
    """在 knowledge_vector_tasks 表中创建任务记录，返回 task_id。"""
    task_id = uuid.uuid4().hex

    if not db.postgres_pool:
        raise RuntimeError("PostgreSQL 连接未初始化")

    async with db.postgres_pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO knowledge_vector_tasks
                (task_id, user_id, knowledge_id, task_type, status, vector_ids, error_message)
            VALUES
                ($1,      $2,      $3,          $4,        $5,     '[]'::jsonb,  $6)
            """,
            task_id,
            user_id,
            knowledge_id,
            task_type,
            status,
            json.dumps({"message": message}, ensure_ascii=False),
        )

    return task_id


async def update_task_status(
    task_id: str,
    *,
    status: int,
    vector_ids: Optional[list[int]] = None,
    error_message: Optional[str] = None,
) -> None:
    """更新任务状态及结果。"""
    if not db.postgres_pool:
        raise RuntimeError("PostgreSQL 连接未初始化")

    async with db.postgres_pool.acquire() as conn:
        await conn.execute(
            """
            UPDATE knowledge_vector_tasks
            SET status = $2,
                vector_ids = COALESCE($3::jsonb, vector_ids),
                error_message = COALESCE($4, error_message),
                updated_at = CURRENT_TIMESTAMP
            WHERE task_id = $1
            """,
            task_id,
            status,
            json.dumps(vector_ids) if vector_ids is not None else None,
            error_message,
        )


async def get_task_row(task_id: str):
    """按 task_id 查询任务记录。"""
    if not db.postgres_pool:
        raise RuntimeError("PostgreSQL 连接未初始化")

    async with db.postgres_pool.acquire() as conn:
        return await conn.fetchrow(
            """
            SELECT
                task_id,
                knowledge_id,
                status,
                vector_ids,
                error_message,
                created_at,
                updated_at
            FROM knowledge_vector_tasks
            WHERE task_id = $1
            """,
            task_id,
        )


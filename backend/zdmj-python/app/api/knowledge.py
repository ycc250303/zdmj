"""
知识库向量化 API 路由层。

仅负责：
- 请求/响应模型
- 任务创建与调度
- 任务状态查询
"""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.models.response import ApiResponse
from app.repositories.task_repository import (
    TASK_STATUS_TO_STR,
    TaskStatus,
    TaskType,
    create_task_record,
    get_task_row,
)
from app.services.knowledge_task_service import process_delete_task, process_embedding_task

logger = logging.getLogger(__name__)
router = APIRouter()


class KnowledgeEmbeddingRequest(BaseModel):
    """知识库向量化请求体（创建 / 重跑）。"""

    knowledgeId: int = Field(..., description="知识库ID（knowledge_bases.id）")
    userId: int = Field(..., description="用户ID（用于数据隔离）")


class KnowledgeEmbeddingTaskResult(BaseModel):
    taskId: str
    status: str
    message: str


class DeleteVectorsRequest(BaseModel):
    """整库向量删除请求体。"""

    knowledgeId: int = Field(..., description="知识库ID（knowledge_bases.id）")
    userId: int = Field(..., description="用户ID（用于数据隔离）")


class DeleteVectorsResult(BaseModel):
    taskId: str
    status: str
    message: str


class TaskStatusResponse(BaseModel):
    taskId: str
    knowledgeId: Optional[int] = None
    status: str
    vectorIds: Optional[list[int]] = None
    errorMessage: Optional[str] = None
    startTime: Optional[str] = None
    endTime: Optional[str] = None


@router.post(
    "/knowledge/embedding",
    response_model=ApiResponse[KnowledgeEmbeddingTaskResult],
    summary="知识库向量化（创建 / 重跑，异步）",
)
async def create_or_rerun_embedding(req: KnowledgeEmbeddingRequest):
    """按 knowledgeId 执行知识库向量化（异步）。"""
    logger.info("收到向量化请求: knowledgeId=%d, userId=%d", req.knowledgeId, req.userId)

    task_id = await create_task_record(
        task_type=TaskType.EMBEDDING,
        knowledge_id=req.knowledgeId,
        user_id=req.userId,
        status=TaskStatus.PENDING,
        message="知识库向量化任务已创建",
    )

    asyncio.create_task(process_embedding_task(task_id, req.knowledgeId, req.userId))

    result = KnowledgeEmbeddingTaskResult(
        taskId=task_id,
        status=TASK_STATUS_TO_STR[TaskStatus.PENDING],
        message="知识库向量化任务已创建",
    )
    return ApiResponse.success(data=result)


@router.post(
    "/knowledge/vectors/delete",
    response_model=ApiResponse[DeleteVectorsResult],
    summary="按知识库全量删除向量（异步）",
)
async def delete_vectors(req: DeleteVectorsRequest):
    """按 knowledgeId 删除该知识库下的全部向量（异步）。"""
    task_id = await create_task_record(
        task_type=TaskType.DELETE,
        knowledge_id=req.knowledgeId,
        user_id=req.userId,
        status=TaskStatus.PENDING,
        message="整库向量删除任务已创建",
    )

    asyncio.create_task(process_delete_task(task_id, req.knowledgeId, req.userId))

    result = DeleteVectorsResult(
        taskId=task_id,
        status=TASK_STATUS_TO_STR[TaskStatus.PENDING],
        message="整库向量删除任务已创建",
    )
    return ApiResponse.success(data=result)


@router.get(
    "/knowledge/embedding/tasks/{task_id}",
    response_model=ApiResponse[TaskStatusResponse],
    summary="查询向量化任务状态与结果",
)
async def get_task_status(task_id: str):
    """查询知识库向量化 / 删除任务状态与结果。"""
    row = await get_task_row(task_id)
    if row is None:
        return ApiResponse.error(code=404, msg="任务不存在")

    status_int = int(row["status"])
    status_str = TASK_STATUS_TO_STR.get(status_int, "UNKNOWN")

    vector_ids: Optional[list[int]] = None
    raw_value = row["vector_ids"]
    if raw_value is not None:
        if isinstance(raw_value, list):
            vector_ids = [int(x) for x in raw_value if x is not None]
        elif isinstance(raw_value, str):
            try:
                parsed = json.loads(raw_value)
                if isinstance(parsed, list):
                    vector_ids = [int(x) for x in parsed if x is not None]
            except (json.JSONDecodeError, ValueError, TypeError) as exc:
                logger.warning("解析 vector_ids 失败: task_id=%s, raw_value=%s, error=%s", task_id, raw_value, exc)
                vector_ids = None
        else:
            try:
                if hasattr(raw_value, "__iter__") and not isinstance(raw_value, str):
                    vector_ids = [int(x) for x in raw_value if x is not None]
            except (ValueError, TypeError) as exc:
                logger.warning(
                    "转换 vector_ids 失败: task_id=%s, raw_value=%s, type=%s, error=%s",
                    task_id,
                    raw_value,
                    type(raw_value),
                    exc,
                )
                vector_ids = None

    resp = TaskStatusResponse(
        taskId=row["task_id"],
        knowledgeId=row["knowledge_id"],
        status=status_str,
        vectorIds=vector_ids,
        errorMessage=row["error_message"],
        startTime=row["created_at"].isoformat() if row["created_at"] else None,
        endTime=row["updated_at"].isoformat() if row["updated_at"] else None,
    )
    return ApiResponse.success(data=resp)


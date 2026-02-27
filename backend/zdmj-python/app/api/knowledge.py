"""
知识库向量化 API

实现内容：
- POST /knowledge/embedding        按 knowledgeId 创建 / 重跑向量（异步）
- POST /knowledge/vectors/delete   按 knowledgeId 全量删除向量（异步）
- GET  /knowledge/embedding/tasks/{taskId}  查询任务状态和结果

说明：
- Python 服务只负责向量化与向量表/任务表的操作，不做权限和业务校验
- Java 负责决定何时调用（例如仅在 content/fileType/type 变化时重跑）
"""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any, Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field
from langchain_core.documents import Document

from app.database import db
from app.models.response import ApiResponse
from app.services.fetcher.cos_fetcher import COSFetcher
from app.services.fetcher.github_fetcher import GitHubFetcher
from app.services.processing.chunking import DocumentChunker
from app.services.vector.embedding import QwenEmbedding
from app.services.vector.knowledge_vector_store import KnowledgeVectorStore
from app.services.vector.project_code_vector_store import ProjectCodeVectorStore

logger = logging.getLogger(__name__)

router = APIRouter()


# 任务类型与状态常量（与 knowledge_vector_tasks 表保持一致）
class TaskType:
    EMBEDDING = 1 # 创建向量
    DELETE = 2 # 删除向量


class TaskStatus:
    PENDING = 1 # 待执行
    RUNNING = 2 # 执行中
    SUCCESS = 3 # 成功
    FAILED = 4 # 失败
    CANCELLED = 5 # 取消


TASK_STATUS_TO_STR = {
    TaskStatus.PENDING: "PENDING",
    TaskStatus.RUNNING: "RUNNING",
    TaskStatus.SUCCESS: "SUCCESS",
    TaskStatus.FAILED: "FAILED",
    TaskStatus.CANCELLED: "CANCELLED",
}


class KnowledgeEmbeddingRequest(BaseModel):
    """知识库向量化请求体（创建 / 重跑）"""

    knowledgeId: int = Field(..., description="知识库ID（knowledge_bases.id）")
    userId: int = Field(..., description="用户ID（用于数据隔离）")


class KnowledgeEmbeddingTaskResult(BaseModel):
    taskId: str # 任务ID
    status: str # 任务状态
    message: str # 任务消息


class DeleteVectorsRequest(BaseModel):
    """整库向量删除请求体"""

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


async def _create_task_record(
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

    logger.info(
        "创建知识库向量任务: task_id=%s, knowledge_id=%d, user_id=%d, type=%d",
        task_id,
        knowledge_id,
        user_id,
        task_type,
    )
    return task_id


async def _update_task_status(
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


async def _load_knowledge_row(
    knowledge_id: int,
) -> Optional[dict[str, Any]]:
    """从 knowledge_bases 表加载单条记录。"""
    if not db.postgres_pool:
        raise RuntimeError("PostgreSQL 连接未初始化")

    async with db.postgres_pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, user_id, name, project_name, file_type, type, content, tag, vector_ids
            FROM knowledge_bases
            WHERE id = $1
            """,
            knowledge_id,
        )

    if row is None:
        return None

    return dict(row)


async def _process_embedding_task(task_id: str, knowledge_id: int, user_id: int) -> None:
    """
    异步执行知识库向量化任务：
    - 读取 knowledge_bases
    - 拉取内容（COS/GitHub/直接文本）
    - 分块 + 向量化
    - 保存到对应向量表
    - 更新任务状态与 vector_ids
    """
    await _update_task_status(task_id, status=TaskStatus.RUNNING)

    try:
        knowledge = await _load_knowledge_row(knowledge_id)
        if knowledge is None:
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message=f"knowledge_id={knowledge_id} 不存在",
            )
            return

        if int(knowledge["user_id"]) != user_id:
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message="用户ID不匹配，禁止访问他人知识库",
            )
            return

        file_type: int = int(knowledge["file_type"])
        knowledge_type: int = int(knowledge["type"])
        content: str = knowledge["content"]

        # 1. 根据 file_type / type 获取 Document 列表
        documents: list[Document] = []

        # file_type: 1=txt, 2=url, 3=doc(pdf), 4=md （参考 Java 常量）
        # knowledge_type: 1=项目文档, 2=项目代码(GitHub), 3=技术文档, 4=其他, 5=项目DeepWiki文档

        # 文本 / Markdown 直接按内容处理
        if file_type in (1, 4):
            documents = [
                Document(
                    page_content=content,
                    metadata={
                        "knowledge_id": knowledge_id,
                        "file_type": file_type,
                        "type": knowledge_type,
                    },
                )
            ]

        # COS PDF (doc)
        elif file_type == 3:
            cos_fetcher = COSFetcher()
            text = cos_fetcher.fetch_pdf_text(content)
            documents = [
                Document(
                    page_content=text,
                    metadata={
                        "knowledge_id": knowledge_id,
                        "file_type": file_type,
                        "type": knowledge_type,
                        "source": content,
                    },
                )
            ]

        # URL 类型
        elif file_type == 2:
            # 如果是项目代码（GitHub），优先走 GitHubFetcher
            if knowledge_type == 2 and "github.com" in content:
                github_fetcher = GitHubFetcher()
                if "/blob/" in content:
                    text = github_fetcher.fetch_file_text(content)
                    documents = [
                        Document(
                            page_content=text,
                            metadata={
                                "knowledge_id": knowledge_id,
                                "file_type": file_type,
                                "type": knowledge_type,
                                "path": content,
                            },
                        )
                    ]
                else:
                    # 仓库模式：拉取一定数量的代码/文本文件
                    documents = github_fetcher.fetch_repository_documents(
                        content,
                        max_files=500,
                    )
                    # 补充知识库相关元数据
                    for doc in documents:
                        doc.metadata.setdefault("knowledge_id", knowledge_id)
                        doc.metadata.setdefault("type", knowledge_type)
            else:
                # 其他 URL 简单按文本拉取（可按需扩展更复杂逻辑）
                import requests

                resp = requests.get(content, timeout=10)
                resp.raise_for_status()
                text = resp.text
                documents = [
                    Document(
                        page_content=text,
                        metadata={
                            "knowledge_id": knowledge_id,
                            "file_type": file_type,
                            "type": knowledge_type,
                            "url": content,
                        },
                    )
                ]
        else:
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message=f"不支持的 file_type={file_type}",
            )
            return

        # 2. 分块
        chunker = DocumentChunker(chunk_size=1500, chunk_overlap=200)
        chunk_docs = chunker.chunk_documents(documents)

        if not chunk_docs:
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message="分块结果为空，无法向量化",
            )
            return

        # 3. 向量化
        embedding_service = QwenEmbedding()
        texts = [doc.page_content for doc in chunk_docs]
        embeddings = embedding_service.embed_documents(texts)

        if len(embeddings) != len(chunk_docs):
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message="向量数量与分块数量不匹配",
            )
            return

        # 4. 按 type 落到不同向量表
        vector_ids: list[int] = []

        # 先删除旧向量（幂等重跑）
        kv_store = KnowledgeVectorStore()
        pc_store = ProjectCodeVectorStore()

        # 删除知识库相关的所有旧向量（文本 + 代码）
        await kv_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)
        await pc_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)

        if knowledge_type == 2:
            # 项目代码 → project_code_vectors，knowledge_id 使用 knowledge_id 绑定
            vector_ids = await pc_store.save_vectors(
                knowledge_id=knowledge_id,
                user_id=user_id,
                documents=chunk_docs,
                embeddings=embeddings,
            )
        else:
            # 其他知识类型 → knowledge_vectors
            vector_ids = await kv_store.save_vectors(
                knowledge_id=knowledge_id,
                user_id=user_id,
                documents=chunk_docs,
                embeddings=embeddings,
            )

        # 5. 更新任务状态
        await _update_task_status(
            task_id,
            status=TaskStatus.SUCCESS,
            vector_ids=vector_ids,
        )

        logger.info(
            "知识库向量化任务完成: task_id=%s, knowledge_id=%d, 向量数量=%d",
            task_id,
            knowledge_id,
            len(vector_ids),
        )
    except Exception as exc:  # 捕获所有异常，写入任务失败状态
        logger.exception(
            "知识库向量化任务失败: task_id=%s, knowledge_id=%d, error=%s",
            task_id,
            knowledge_id,
            exc,
        )
        await _update_task_status(
            task_id,
            status=TaskStatus.FAILED,
            error_message=str(exc),
        )


async def _process_delete_task(task_id: str, knowledge_id: int, user_id: int) -> None:
    """异步执行整库向量删除任务。"""
    await _update_task_status(task_id, status=TaskStatus.RUNNING)

    try:
        kv_store = KnowledgeVectorStore()
        pc_store = ProjectCodeVectorStore()

        # 删除两个表中与该知识库相关的向量
        deleted_kv = await kv_store.delete(
            vector_ids=None,
            knowledge_id=knowledge_id,
            user_id=user_id,
        )
        deleted_pc = await pc_store.delete(
            vector_ids=None,
            knowledge_id=knowledge_id,
            user_id=user_id,
        )

        total_deleted = deleted_kv + deleted_pc

        await _update_task_status(
            task_id,
            status=TaskStatus.SUCCESS,
            vector_ids=[],  # 删除任务不再返回具体 vectorIds
        )

        logger.info(
            "整库向量删除任务完成: task_id=%s, knowledge_id=%d, 删除数量=%d",
            task_id,
            knowledge_id,
            total_deleted,
        )
    except Exception as exc:
        logger.exception(
            "整库向量删除任务失败: task_id=%s, knowledge_id=%d, error=%s",
            task_id,
            knowledge_id,
            exc,
        )
        await _update_task_status(
            task_id,
            status=TaskStatus.FAILED,
            error_message=str(exc),
        )


@router.post(
    "/knowledge/embedding",
    response_model=ApiResponse[KnowledgeEmbeddingTaskResult],
    summary="知识库向量化（创建 / 重跑，异步）",
)
async def create_or_rerun_embedding(
    req: KnowledgeEmbeddingRequest,
):
    """
    按 knowledgeId 执行知识库向量化：
    - 若无向量：等价于"创建向量"
    - 若已有向量：先删除旧向量，再按最新内容重跑

    注意：是否需要重跑，由 Java 根据 knowledge_bases 中字段变化自行判断。
    """
    logger.info(
        "收到向量化请求: knowledgeId=%d, userId=%d",
        req.knowledgeId,
        req.userId,
    )
    
    try:
        task_id = await _create_task_record(
            task_type=TaskType.EMBEDDING,
            knowledge_id=req.knowledgeId,
            user_id=req.userId,
            status=TaskStatus.PENDING,
            message="知识库向量化任务已创建",
        )

        # 使用 asyncio.create_task 在当前事件循环中异步执行向量化流程
        asyncio.create_task(_process_embedding_task(task_id, req.knowledgeId, req.userId))

        result = KnowledgeEmbeddingTaskResult(
            taskId=task_id,
            status=TASK_STATUS_TO_STR[TaskStatus.PENDING],
            message="知识库向量化任务已创建",
        )
        
        logger.info(
            "向量化任务创建成功: taskId=%s, knowledgeId=%d, userId=%d",
            task_id,
            req.knowledgeId,
            req.userId,
        )
        
        return ApiResponse.success(data=result)
    except Exception as e:
        logger.exception(
            "创建向量化任务失败: knowledgeId=%d, userId=%d, error=%s",
            req.knowledgeId,
            req.userId,
            str(e),
        )
        raise


@router.post(
    "/knowledge/vectors/delete",
    response_model=ApiResponse[DeleteVectorsResult],
    summary="按知识库全量删除向量（异步）",
)
async def delete_vectors(
    req: DeleteVectorsRequest,
):
    """
    按 knowledgeId 删除该知识库下的全部向量（knowledge_vectors + project_code_vectors）。

    适用于删除知识库时的“整库向量清理”场景。
    """
    task_id = await _create_task_record(
        task_type=TaskType.DELETE,
        knowledge_id=req.knowledgeId,
        user_id=req.userId,
        status=TaskStatus.PENDING,
        message="整库向量删除任务已创建",
    )

    # 使用 asyncio.create_task 在当前事件循环中异步执行删除流程
    asyncio.create_task(_process_delete_task(task_id, req.knowledgeId, req.userId))

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
    """
    查询知识库向量化 / 删除任务的当前状态及结果。
    """
    if not db.postgres_pool:
        raise RuntimeError("PostgreSQL 连接未初始化")

    async with db.postgres_pool.acquire() as conn:
        row = await conn.fetchrow(
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

    if row is None:
        return ApiResponse.error(code=404, msg="任务不存在")

    status_int: int = int(row["status"])
    status_str = TASK_STATUS_TO_STR.get(status_int, "UNKNOWN")

    vector_ids: Optional[list[int]] = None
    if row["vector_ids"] is not None:
        raw_value = row["vector_ids"]
        
        # 处理不同的数据格式
        if isinstance(raw_value, list):
            # 已经是列表，直接使用
            vector_ids = [int(x) for x in raw_value if x is not None]
        elif isinstance(raw_value, str):
            # 是字符串，需要解析 JSON
            try:
                parsed = json.loads(raw_value)
                if isinstance(parsed, list):
                    vector_ids = [int(x) for x in parsed if x is not None]
            except (json.JSONDecodeError, ValueError, TypeError) as e:
                logger.warning(
                    "解析 vector_ids 失败: task_id=%s, raw_value=%s, error=%s",
                    task_id, raw_value, e
                )
                vector_ids = None
        else:
            # 其他类型（如 dict），尝试转换
            try:
                if hasattr(raw_value, '__iter__') and not isinstance(raw_value, str):
                    vector_ids = [int(x) for x in raw_value if x is not None]
                else:
                    vector_ids = None
            except (ValueError, TypeError) as e:
                logger.warning(
                    "转换 vector_ids 失败: task_id=%s, raw_value=%s, type=%s, error=%s",
                    task_id, raw_value, type(raw_value), e
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


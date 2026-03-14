"""
知识库向量化 API

实现内容：
- POST /knowledge/embedding        按 knowledgeId 创建 / 重跑向量（异步）
- POST /knowledge/vectors/delete   按 knowledgeId 全量删除向量（异步）
- GET  /knowledge/embedding/tasks/{taskId}  查询任务状态和结果

说明：
- Python 服务只负责向量化与向量表/任务表的操作，不做权限和业务校验
- Java 负责决定何时调用（例如仅在 content/type 变化时重跑）
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

from app.config import settings
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
            SELECT id, user_id, name, project_id, type, content, tag, vector_ids
            FROM knowledge_bases
            WHERE id = $1
            """,
            knowledge_id,
        )

    if row is None:
        return None

    return dict(row)


def _infer_file_format(content: str, knowledge_type: int) -> str:
    """
    根据内容和知识类型推断文件格式（仅支持PDF、MD和GitHub）
    
    验证规则：
    - knowledge_type=1（项目文档）：必须是COS链接的PDF或MD文件
    - knowledge_type=2（GitHub链接）：必须是GitHub链接
    - knowledge_type=3（DeepWiki）：暂不支持
    
    Args:
        content: 文档内容或URL
        knowledge_type: 知识类型（1=项目文档，2=GitHub链接，3=项目DeepWiki文档）
    
    Returns:
        推断的文件格式：'doc'（PDF）, 'md'（Markdown）, 'github'（GitHub链接）
    
    Raises:
        ValueError: 当文件格式不支持或类型与内容不匹配时
    """
    if not content:
        raise ValueError("内容不能为空")
    
    # 验证URL格式
    if not content.startswith(("http://", "https://")):
        raise ValueError("内容必须是有效的URL链接")
    
    # 根据知识类型进行验证和推断
    if knowledge_type == 1:
        # type=1：项目文档，必须是COS链接的PDF或MD文件
        content_lower = content.lower()
        is_pdf = ".pdf" in content_lower or "/pdf/" in content_lower
        is_md = ".md" in content_lower or content.endswith(".md")
        
        if not is_pdf and not is_md:
            raise ValueError(
                f"项目文档类型（type=1）仅支持PDF和Markdown文件，当前URL: {content[:100]}"
            )
        
        # 返回文件格式
        return "doc" if is_pdf else "md"
        
    elif knowledge_type == 2:
        # type=2：GitHub链接，必须是GitHub链接
        if "github.com" not in content:
            raise ValueError(
                f"GitHub链接类型（type=2）必须是GitHub链接，当前URL: {content[:100]}"
            )
        return "github"
        
    elif knowledge_type == 3:
        # type=3：DeepWiki文档，暂不支持
        raise ValueError("项目DeepWiki文档类型（type=3）暂不支持")
        
    else:
        # 未知的知识类型
        raise ValueError(f"不支持的知识类型: {knowledge_type}")


async def _process_embedding_task(task_id: str, knowledge_id: int, user_id: int) -> None:
    """
    异步执行知识库向量化任务：
    - 读取 knowledge_bases
    - 拉取内容（COS/GitHub/直接文本）
    - 分块 + 向量化
    - 保存到对应向量表
    - 更新任务状态与 vector_ids
    """
    print(f"\n{'='*80}")
    print(f"🚀 [向量化任务开始] task_id={task_id}, knowledge_id={knowledge_id}, user_id={user_id}")
    print(f"{'='*80}\n")
    
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

        knowledge_type: int = int(knowledge["type"])
        content: str = knowledge["content"]

        # 1. 推断文件格式（仅支持PDF、MD和GitHub）
        try:
            inferred_format = _infer_file_format(content, knowledge_type)
        except ValueError as e:
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message=str(e),
            )
            return

        # 2. 根据推断的文件格式和知识类型获取 Document 列表
        documents: list[Document] = []

        # COS PDF (doc)
        if inferred_format == "doc":
            cos_fetcher = COSFetcher()
            text = cos_fetcher.fetch_pdf_text(content)
            documents = [
                Document(
                    page_content=text,
                    metadata={
                        "knowledge_id": knowledge_id,
                        "type": knowledge_type,
                        "source": content,
                    },
                )
            ]

        # Markdown格式：从COS下载
        elif inferred_format == "md":
            # 从COS下载Markdown文件
            cos_fetcher = COSFetcher()
            # 对于Markdown文件，下载字节后解码为文本
            md_bytes = cos_fetcher.fetch_pdf_bytes(content)
            text = md_bytes.decode("utf-8")
            documents = [
                Document(
                    page_content=text,
                    metadata={
                        "knowledge_id": knowledge_id,
                        "type": knowledge_type,
                        "source": content,
                    },
                )
            ]

        # GitHub链接
        elif inferred_format == "github":
            github_fetcher = GitHubFetcher()
            if "/blob/" in content:
                # 单个文件模式
                text = github_fetcher.fetch_file_text(content)
                documents = [
                    Document(
                        page_content=text,
                        metadata={
                            # 不添加 knowledge_id（表字段已有，避免冗余）
                            "type": knowledge_type,  # 保留知识类型
                            "path": content,  # 用于提取 file_path 表字段，之后会被删除
                        },
                    )
                ]
            else:
                # 仓库模式：拉取一定数量的代码/文本文件（支持大仓库稳定处理）
                print(f"\n📦 [GitHub仓库处理] 开始拉取仓库文档")
                print(f"   Repository: {content}")
                print(f"   Max Files: {settings.github_max_files}")
                print(f"   Knowledge ID: {knowledge_id}\n")
                
                logger.info(
                    "开始拉取GitHub仓库文档: knowledge_id=%d, repo_url=%s, max_files=%d",
                    knowledge_id,
                    content,
                    settings.github_max_files,
                )
                
                # 更新任务状态，告知用户开始处理GitHub仓库
                await _update_task_status(
                    task_id,
                    status=TaskStatus.RUNNING,
                    error_message=f"开始拉取GitHub仓库文档（最多{settings.github_max_files}个文件）",
                )
                
                # 使用线程池执行同步的GitHub文件拉取操作，避免阻塞事件循环
                loop = asyncio.get_event_loop()
                # 使用 lambda 包装，因为 run_in_executor 不支持关键字参数
                # 注意：fetch_repository_documents 的 max_files 是关键字参数（在 * 之后）
                def fetch_docs():
                    return github_fetcher.fetch_repository_documents(
                        repo_url=content,
                        max_files=settings.github_max_files
                    )
                documents = await loop.run_in_executor(None, fetch_docs)
                
                print(f"✅ [GitHub仓库处理] 文档拉取完成")
                print(f"   文档数量: {len(documents)} 个文件\n")
                
                logger.info(
                    "GitHub仓库文档拉取完成: knowledge_id=%d, 文档数量=%d",
                    knowledge_id,
                    len(documents),
                )
                
                # 更新任务状态
                await _update_task_status(
                    task_id,
                    status=TaskStatus.RUNNING,
                    error_message=f"GitHub仓库文档拉取完成，共{len(documents)}个文档，开始分块",
                )
                # 补充知识库相关元数据
                # 注意：不添加 knowledge_id 到 metadata（表字段已有，避免冗余）
                for doc in documents:
                    # 只添加 type（知识类型），用于后续过滤
                    doc.metadata.setdefault("type", knowledge_type)
        else:
            # 理论上不应该到达这里，因为 _infer_file_format 已经做了验证
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message=f"不支持的文件格式: {inferred_format}",
            )
            return

        # 2. 分块（使用异步执行，避免阻塞）
        print(f"📄 [文档分块] 开始处理")
        print(f"   输入文档数: {len(documents)}")
        print(f"   分块大小: 1500, 重叠: 200\n")
        
        logger.info(
            "开始文档分块: knowledge_id=%d, 文档数量=%d",
            knowledge_id,
            len(documents),
        )
        
        # 对于大量文档，使用异步执行分块操作
        loop = asyncio.get_event_loop()
        chunker = DocumentChunker(chunk_size=1500, chunk_overlap=200)
        
        # 在线程池中执行CPU密集型的分块操作，避免阻塞事件循环
        chunk_docs = await loop.run_in_executor(
            None,
            chunker.chunk_documents,
            documents
        )

        if not chunk_docs:
            print(f"❌ [文档分块] 失败: 分块结果为空\n")
            await _update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message="分块结果为空，无法向量化",
            )
            return

        print(f"✅ [文档分块] 完成")
        print(f"   生成分块数: {len(chunk_docs)} 个\n")
        
        logger.info(
            "文档分块完成: knowledge_id=%d, 分块数量=%d",
            knowledge_id,
            len(chunk_docs),
        )
        
        # 更新任务进度
        await _update_task_status(
            task_id,
            status=TaskStatus.RUNNING,
            error_message=f"文档分块完成，共{len(chunk_docs)}个分块，开始向量化",
        )

        # 3. 向量化（分批处理，避免一次性处理过多）
        print(f"🔢 [向量化] 开始处理")
        print(f"   分块总数: {len(chunk_docs)}")
        print(f"   批次大小: {settings.embedding_batch_size}")
        
        embedding_service = QwenEmbedding()
        texts = [doc.page_content for doc in chunk_docs]
        
        # 分批向量化，避免API限流和超时
        batch_size = settings.embedding_batch_size
        embeddings: list[list[float]] = []
        total_batches = (len(texts) + batch_size - 1) // batch_size
        
        print(f"   总批次数: {total_batches}")
        print(f"   预计处理时间: 约 {total_batches * 2} 秒\n")
        
        logger.info(
            "开始向量化: knowledge_id=%d, 分块数量=%d, 批次大小=%d",
            knowledge_id,
            len(chunk_docs),
            settings.embedding_batch_size,
        )
        
        # 进度更新间隔（每处理一定批次更新一次任务状态）
        progress_update_interval = max(5, total_batches // 20)  # 每5%更新一次
        
        for i in range(0, len(texts), batch_size):
            batch_texts = texts[i : i + batch_size]
            batch_num = i // batch_size + 1
            
            # 每批都打印进度（控制台输出）
            progress_pct = (batch_num / total_batches) * 100
            # 每批都显示进度，使用 \r 实现同一行更新
            print(f"   ⏳ 批次 {batch_num}/{total_batches} ({progress_pct:.1f}%) - 处理中...", end="\r", flush=True)
            
            logger.info(
                "向量化进度: knowledge_id=%d, 批次=%d/%d, 当前批次大小=%d",
                knowledge_id,
                batch_num,
                total_batches,
                len(batch_texts),
            )
            
            # 定期更新任务状态，让用户知道进度
            if batch_num % progress_update_interval == 0 or batch_num == total_batches:
                await _update_task_status(
                    task_id,
                    status=TaskStatus.RUNNING,
                    error_message=f"向量化中: {batch_num}/{total_batches} ({progress_pct:.1f}%)",
                )
                # 确保进度更新时换行显示（清除之前的 \r 输出）
                print(f"\n   ✅ 批次 {batch_num}/{total_batches} ({progress_pct:.1f}%) - 完成")
                logger.info(
                    "向量化进度更新: task_id=%s, knowledge_id=%d, 进度=%d/%d (%.1f%%)",
                    task_id,
                    knowledge_id,
                    batch_num,
                    total_batches,
                    progress_pct,
                )
            
            # 向量化处理，添加重试机制提高稳定性
            max_retries = 3
            retry_count = 0
            batch_embeddings = None
            
            while retry_count < max_retries:
                try:
                    batch_embeddings = embedding_service.embed_documents(batch_texts)
                    break
                except Exception as e:
                    retry_count += 1
                    if retry_count >= max_retries:
                        logger.error(
                            "向量化失败（已重试%d次）: knowledge_id=%d, batch=%d/%d, error=%s",
                            max_retries,
                            knowledge_id,
                            batch_num,
                            total_batches,
                            e,
                        )
                        raise
                    else:
                        wait_time = retry_count * 2  # 递增等待时间：2s, 4s, 6s
                        logger.warning(
                            "向量化失败，%d秒后重试（第%d次）: knowledge_id=%d, batch=%d/%d, error=%s",
                            wait_time,
                            retry_count,
                            knowledge_id,
                            batch_num,
                            total_batches,
                            e,
                        )
                        await asyncio.sleep(wait_time)
            
            if batch_embeddings is not None:
                embeddings.extend(batch_embeddings)
        
        print(f"\n✅ [向量化] 完成")
        print(f"   生成向量数: {len(embeddings)} 个\n")
        
        logger.info(
            "向量化完成: knowledge_id=%d, 向量数量=%d",
            knowledge_id,
            len(embeddings),
        )

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
        logger.info(
            "开始删除旧向量: knowledge_id=%d, user_id=%d",
            knowledge_id,
            user_id,
        )
        kv_store = KnowledgeVectorStore()
        pc_store = ProjectCodeVectorStore()

        # 删除知识库相关的所有旧向量（文本 + 代码）
        try:
            await kv_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)
            await pc_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)
            logger.info(
                "旧向量删除完成: knowledge_id=%d",
                knowledge_id,
            )
        except Exception as e:
            logger.warning(
                "删除旧向量时出错（可能不存在旧向量）: knowledge_id=%d, error=%s",
                knowledge_id,
                e,
            )
            # 继续执行，不影响新向量保存

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
        print(f"💾 [保存向量] 开始保存到数据库...")
        
        await _update_task_status(
            task_id,
            status=TaskStatus.SUCCESS,
            vector_ids=vector_ids,
        )

        print(f"💾 [保存向量] 完成")
        print(f"   保存向量数: {len(vector_ids)} 个")
        print(f"\n{'='*80}")
        print(f"🎉 [向量化任务完成] task_id={task_id}, knowledge_id={knowledge_id}")
        print(f"   总向量数: {len(vector_ids)}")
        print(f"{'='*80}\n")

        logger.info(
            "知识库向量化任务完成: task_id=%s, knowledge_id=%d, 向量数量=%d",
            task_id,
            knowledge_id,
            len(vector_ids),
        )
    except Exception as exc:  # 捕获所有异常，写入任务失败状态
        print(f"\n❌ [向量化任务失败] task_id={task_id}, knowledge_id={knowledge_id}")
        print(f"   错误信息: {str(exc)}")
        print(f"{'='*80}\n")
        
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


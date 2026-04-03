"""
知识库向量任务服务。

承载任务编排逻辑：
- 向量化任务执行
- 向量删除任务执行
"""

from __future__ import annotations

import asyncio
import logging

from langchain_core.documents import Document

from app.config import settings
from app.repositories.knowledge_repository import load_knowledge_row
from app.repositories.task_repository import TaskStatus, update_task_status
from app.services.fetcher.cos_fetcher import COSFetcher
from app.services.fetcher.github_fetcher import GitHubFetcher
from app.services.processing.ast_code_chunker import ASTCodeChunker
from app.services.processing.chunking import DocumentChunker
from app.services.processing.code_enhancer import CodeEnhancer
from app.services.vector.embedding import QwenEmbedding
from app.services.vector.knowledge_vector_store import KnowledgeVectorStore
from app.services.vector.project_code_vector_store import ProjectCodeVectorStore

logger = logging.getLogger(__name__)


def _is_code_file(file_path: str) -> bool:
    """根据文件路径判断是否为代码文件。"""
    if not file_path:
        return False

    code_extensions = {
        ".py",
        ".java",
        ".js",
        ".jsx",
        ".ts",
        ".tsx",
        ".go",
        ".rs",
        ".cs",
        ".php",
        ".rb",
        ".kt",
        ".swift",
        ".c",
        ".cpp",
        ".sh",
        ".sql",
    }

    path_lower = file_path.lower()
    if "github.com" in path_lower and "/blob/" in path_lower:
        parts = path_lower.split("/blob/")
        if len(parts) > 1:
            file_part = parts[1].split("/")[-1]
            if "." in file_part:
                ext = "." + file_part.split(".")[-1]
                return ext in code_extensions
    else:
        if "." in path_lower:
            ext = "." + path_lower.split(".")[-1].split("?")[0].split("#")[0]
            return ext in code_extensions

    return False


def _infer_file_format(content: str, knowledge_type: int) -> str:
    """最小推断：默认信任 Java 侧参数校验结果。"""
    if knowledge_type == 2:
        return "github"

    content_lower = (content or "").lower()
    if ".md" in content_lower or content_lower.endswith(".md"):
        return "md"
    return "doc"


async def process_embedding_task(task_id: str, knowledge_id: int, user_id: int) -> None:
    """异步执行知识库向量化任务。"""
    # 任务刚进入执行阶段，先将状态置为 RUNNING，便于 Java 侧轮询感知。
    await update_task_status(task_id, status=TaskStatus.RUNNING)

    try:
        knowledge = await load_knowledge_row(knowledge_id)
        if knowledge is None:
            await update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message=f"knowledge_id={knowledge_id} 不存在",
            )
            return

        knowledge_type = int(knowledge["type"])
        content: str = knowledge["content"]

        # 文件格式最小推断：只用于决定走哪条抓取/解析分支。
        inferred_format = _infer_file_format(content, knowledge_type)

        # 1) 拉取原始文档：COS(PDF/MD) 或 GitHub(单文件/仓库)。
        documents: list[Document] = []
        if inferred_format == "doc":
            cos_fetcher = COSFetcher()
            text = cos_fetcher.fetch_pdf_text(content)
            documents = [
                Document(
                    page_content=text,
                    metadata={"knowledge_id": knowledge_id, "type": knowledge_type, "source": content},
                )
            ]
        elif inferred_format == "md":
            cos_fetcher = COSFetcher()
            md_bytes = cos_fetcher.fetch_pdf_bytes(content)
            text = md_bytes.decode("utf-8")
            documents = [
                Document(
                    page_content=text,
                    metadata={"knowledge_id": knowledge_id, "type": knowledge_type, "source": content},
                )
            ]
        elif inferred_format == "github":
            github_fetcher = GitHubFetcher()
            if "/blob/" in content:
                text = github_fetcher.fetch_file_text(content)
                documents = [
                    Document(
                        page_content=text,
                        metadata={"type": knowledge_type, "path": content},
                    )
                ]
            else:
                await update_task_status(
                    task_id,
                    status=TaskStatus.RUNNING,
                    error_message=f"开始拉取GitHub仓库文档（最多{settings.github_max_files}个文件）",
                )
                # GitHub 仓库抓取是同步+IO密集，放线程池避免阻塞事件循环。
                loop = asyncio.get_event_loop()

                def fetch_docs():
                    return github_fetcher.fetch_repository_documents(
                        repo_url=content,
                        max_files=settings.github_max_files,
                    )

                documents = await loop.run_in_executor(None, fetch_docs)
                await update_task_status(
                    task_id,
                    status=TaskStatus.RUNNING,
                    error_message=f"GitHub仓库文档拉取完成，共{len(documents)}个文档，开始分块",
                )
                for doc in documents:
                    doc.metadata.setdefault("type", knowledge_type)
        else:
            await update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message=f"不支持的文件格式: {inferred_format}",
            )
            return

        # 2) 分块前先按文件类型分流：代码走 AST 分块，普通文档走通用分块。
        logger.info("开始文档分块: knowledge_id=%d, 文档数量=%d", knowledge_id, len(documents))
        code_documents: list[Document] = []
        doc_documents: list[Document] = []
        for doc in documents:
            file_path = doc.metadata.get("path", doc.metadata.get("file_path", ""))
            if _is_code_file(file_path):
                code_documents.append(doc)
            else:
                doc_documents.append(doc)

        loop = asyncio.get_event_loop()
        code_chunk_docs: list[Document] = []
        doc_chunk_docs: list[Document] = []

        if code_documents:
            ast_chunker = ASTCodeChunker(chunk_size=1500, chunk_overlap=200)
            code_chunks = await loop.run_in_executor(None, ast_chunker.chunk_documents, code_documents)
            if code_chunks:
                enhancer = CodeEnhancer()

                def enhance_chunks():
                    return enhancer.enhance_code_chunks(code_chunks, skip_non_code=True)

                enhanced_code_chunks = await loop.run_in_executor(None, enhance_chunks)
                code_chunk_docs.extend(enhanced_code_chunks)

        if doc_documents:
            doc_chunker = DocumentChunker(chunk_size=1500, chunk_overlap=200)
            doc_chunks = await loop.run_in_executor(None, doc_chunker.chunk_documents, doc_documents)
            if doc_chunks:
                doc_chunk_docs.extend(doc_chunks)

        chunk_docs = code_chunk_docs + doc_chunk_docs
        if not chunk_docs:
            await update_task_status(
                task_id,
                status=TaskStatus.FAILED,
                error_message="分块结果为空，无法向量化",
            )
            return

        # 3) 批量向量化并持续更新进度，供 Java 查询任务状态时展示。
        await update_task_status(
            task_id,
            status=TaskStatus.RUNNING,
            error_message=f"文档分块完成，共{len(chunk_docs)}个分块，开始向量化",
        )

        embedding_service = QwenEmbedding()
        batch_size = settings.embedding_batch_size

        async def embed_chunks(
            docs: list[Document],
            phase_name: str,
        ) -> list[list[float]]:
            if not docs:
                return []

            texts = [doc.page_content for doc in docs]
            embeddings: list[list[float]] = []
            total_batches = (len(texts) + batch_size - 1) // batch_size
            progress_update_interval = max(5, total_batches // 20)

            for i in range(0, len(texts), batch_size):
                batch_texts = texts[i : i + batch_size]
                batch_num = i // batch_size + 1
                progress_pct = (batch_num / total_batches) * 100
                if batch_num % progress_update_interval == 0 or batch_num == total_batches:
                    await update_task_status(
                        task_id,
                        status=TaskStatus.RUNNING,
                        error_message=f"{phase_name}向量化中: {batch_num}/{total_batches} ({progress_pct:.1f}%)",
                    )

                max_retries = 3
                retry_count = 0
                batch_embeddings = None
                while retry_count < max_retries:
                    try:
                        batch_embeddings = embedding_service.embed_documents(batch_texts)
                        break
                    except Exception:
                        # 外部向量接口存在瞬时失败时做轻量重试，避免整任务直接失败。
                        retry_count += 1
                        if retry_count >= max_retries:
                            raise
                        await asyncio.sleep(retry_count * 2)

                if batch_embeddings is not None:
                    embeddings.extend(batch_embeddings)

            if len(embeddings) != len(docs):
                raise RuntimeError(f"{phase_name}向量数量与分块数量不匹配")
            return embeddings

        doc_embeddings = await embed_chunks(doc_chunk_docs, "文档")
        code_embeddings = await embed_chunks(code_chunk_docs, "代码")

        # 4) 幂等重跑策略：先删旧向量，再写入新结果，避免历史脏数据残留。
        kv_store = KnowledgeVectorStore()
        pc_store = ProjectCodeVectorStore()
        try:
            await kv_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)
            await pc_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)
        except Exception as exc:
            logger.warning("删除旧向量时出错（忽略继续）: knowledge_id=%d, error=%s", knowledge_id, exc)

        vector_ids: list[int] = []
        if doc_chunk_docs:
            doc_vector_ids = await kv_store.save_vectors(
                knowledge_id=knowledge_id,
                user_id=user_id,
                documents=doc_chunk_docs,
                embeddings=doc_embeddings,
            )
            vector_ids.extend(doc_vector_ids)

        if code_chunk_docs:
            code_vector_ids = await pc_store.save_vectors(
                knowledge_id=knowledge_id,
                user_id=user_id,
                documents=code_chunk_docs,
                embeddings=code_embeddings,
            )
            vector_ids.extend(code_vector_ids)

        await update_task_status(
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
    except Exception as exc:
        logger.exception(
            "知识库向量化任务失败: task_id=%s, knowledge_id=%d, error=%s",
            task_id,
            knowledge_id,
            exc,
        )
        await update_task_status(task_id, status=TaskStatus.FAILED, error_message=str(exc))


async def process_delete_task(task_id: str, knowledge_id: int, user_id: int) -> None:
    """异步执行整库向量删除任务。"""
    # 删除任务同样遵循统一状态机：RUNNING -> SUCCESS/FAILED。
    await update_task_status(task_id, status=TaskStatus.RUNNING)
    try:
        kv_store = KnowledgeVectorStore()
        pc_store = ProjectCodeVectorStore()
        deleted_kv = await kv_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)
        deleted_pc = await pc_store.delete(vector_ids=None, knowledge_id=knowledge_id, user_id=user_id)
        total_deleted = deleted_kv + deleted_pc
        await update_task_status(task_id, status=TaskStatus.SUCCESS, vector_ids=[])
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
        await update_task_status(task_id, status=TaskStatus.FAILED, error_message=str(exc))


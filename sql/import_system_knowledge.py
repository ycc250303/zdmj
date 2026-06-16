#!/usr/bin/env python3
"""
离线导入系统知识库（scope=2 系统通用 / scope=3 学习路线）并向量化写入 PostgreSQL。

与 Java 后端对齐的参数（见 application.yml、EmbeddingConfig、KnowledgeEmbeddingServiceImpl）：
  - Embedding 模型：text-embedding-v4，dimensions=1024
  - API：DashScope OpenAI 兼容模式 /v1/embeddings
  - 分块：TokenTextSplitter 默认（chunk_size=800 token，CL100K_BASE）
  - 批量 embed：每批最多 10 条（MAX_BATCH_SIZE）
  - 向量格式：pgvector 字符串 "[0.1,0.2,...]"
  - chunk_hash：SHA-256(hex)；token_count ≈ len(text)//4

用法示例：
  export DASHSCOPE_API_KEY=sk-xxx
  python sql/import_system_knowledge.py \\
    --scope 3 \\
    --source-dir backend/zdmj/src/main/resources/prompts/study \\
    --pg-host 111.229.81.45

  # 仅预览分块，不写库
  python sql/import_system_knowledge.py --scope 3 --source-dir ./study --dry-run

依赖：pip install psycopg2-binary openai tiktoken
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Optional, Sequence

import psycopg2
import psycopg2.extras
import tiktoken
from openai import OpenAI

# ---- 与 Java TokenTextSplitter 默认构造器一致 ----
CHUNK_SIZE_TOKENS = 800
MIN_CHUNK_SIZE_CHARS = 350
MIN_CHUNK_LENGTH_TO_EMBED = 5
MAX_NUM_CHUNKS = 10_000
KEEP_SEPARATOR = True
EMBED_BATCH_SIZE = 10
EMBED_MODEL = "text-embedding-v4"
EMBED_DIMENSIONS = 1024
EMBED_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

SYSTEM_USER_ID = 0
EMBEDDING_SUCCESS = 3  # KnowledgeVectorTaskStatusEnum.SUCCESS
# 文档 type：当前 Java 枚举无 MARKDOWN，预导入用 1 占位；content 存稳定路径标识
DOC_TYPE_PLACEHOLDER = 1

PUNCTUATION = (".", "?", "!", "\n", "。", "？", "！")


def pg_config(args: argparse.Namespace) -> dict:
    return {
        "host": args.pg_host,
        "port": args.pg_port,
        "dbname": args.pg_db,
        "user": args.pg_user,
        "password": args.pg_password,
        "options": "-c client_encoding=UTF8",
    }


def connect_pg(args: argparse.Namespace):
    conn = psycopg2.connect(**pg_config(args))
    conn.set_client_encoding("UTF8")
    return conn


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def estimate_token(text: str) -> int:
    if not text or not text.strip():
        return 0
    return max(1, len(text) // 4)


def to_pg_vector(vec: Sequence[float]) -> str:
    return "[" + ",".join(str(v) for v in vec) + "]"


@dataclass
class Chunk:
    index: int
    text: str


class TokenTextSplitterCompat:
    """近似 Spring AI TokenTextSplitter（CL100K_BASE，默认参数）。"""

    def __init__(self) -> None:
        self.enc = tiktoken.get_encoding("cl100k_base")

    def token_len(self, text: str) -> int:
        return len(self.enc.encode(text))

    def split(self, text: str) -> List[Chunk]:
        text = text.strip()
        if not text:
            return []
        if self.token_len(text) <= CHUNK_SIZE_TOKENS and len(text) >= MIN_CHUNK_LENGTH_TO_EMBED:
            return [Chunk(0, text)]

        chunks: List[str] = []
        start = 0
        while start < len(text) and len(chunks) < MAX_NUM_CHUNKS:
            # 先按字符窗口粗切，再按 token 收缩到 CHUNK_SIZE_TOKENS
            end = min(len(text), start + CHUNK_SIZE_TOKENS * 4)
            piece = text[start:end]
            while self.token_len(piece) > CHUNK_SIZE_TOKENS and len(piece) > MIN_CHUNK_SIZE_CHARS:
                piece = piece[: int(len(piece) * 0.9)]

            if len(piece) < MIN_CHUNK_LENGTH_TO_EMBED:
                break

            # 在 minChunkSizeChars 之后找标点断点
            cut = len(piece)
            if len(piece) > MIN_CHUNK_SIZE_CHARS:
                search_from = MIN_CHUNK_SIZE_CHARS
                best = -1
                for mark in PUNCTUATION:
                    pos = piece.rfind(mark, search_from)
                    if pos > best:
                        best = pos
                if best > 0:
                    cut = best + (1 if KEEP_SEPARATOR else 0)

            chunk = piece[:cut].strip()
            if len(chunk) >= MIN_CHUNK_LENGTH_TO_EMBED:
                chunks.append(chunk)
            start += cut if cut > 0 else len(piece)

        return [Chunk(i, c) for i, c in enumerate(chunks)]


def infer_doc_category(title: str, content: str, scope: int) -> str:
    merged = (title + " " + content).lower()
    if scope == 3 or "学习路线" in title or "学习路径" in merged or "roadmap" in merged:
        return "learning_path"
    if "职业规划" in merged or "求职建议" in merged or "career" in merged:
        return "career_planning"
    if "行业趋势" in merged or "就业趋势" in merged or "trend" in merged:
        return "industry_trend"
    return "general"


def ensure_knowledge_base(conn, scope: int) -> int:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM knowledge_bases WHERE scope = %s LIMIT 1",
            (scope,),
        )
        row = cur.fetchone()
        if row:
            return row[0]
        cur.execute(
            """
            INSERT INTO knowledge_bases (user_id, scope, created_at, updated_at)
            VALUES (%s, %s, NOW(), NOW())
            RETURNING id
            """,
            (SYSTEM_USER_ID, scope),
        )
        kb_id = cur.fetchone()[0]
        conn.commit()
        print(f"已创建 knowledge_bases: id={kb_id}, scope={scope}")
        return kb_id


def embed_batch(client: OpenAI, texts: List[str]) -> List[List[float]]:
    resp = client.embeddings.create(
        model=EMBED_MODEL,
        input=texts,
        dimensions=EMBED_DIMENSIONS,
    )
    data = sorted(resp.data, key=lambda x: x.index)
    vectors = [list(d.embedding) for d in data]
    if any(len(v) != EMBED_DIMENSIONS for v in vectors):
        raise RuntimeError("embedding 维度与配置不一致")
    return vectors


def upsert_document(
    conn,
    knowledge_id: int,
    content_key: str,
    title: str,
    metadata: dict,
) -> int:
    content_hash = sha256_hex(content_key)
    meta_json = json.dumps(metadata, ensure_ascii=False)
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id FROM knowledge_documents
            WHERE knowledge_id = %s AND content = %s
            """,
            (knowledge_id, content_key),
        )
        row = cur.fetchone()
        if row:
            doc_id = row[0]
            cur.execute(
                """
                UPDATE knowledge_documents
                SET title = %s, metadata = %s::jsonb, content_hash = %s,
                    embedding_status = %s, updated_at = NOW()
                WHERE id = %s
                """,
                (title, meta_json, content_hash, EMBEDDING_SUCCESS, doc_id),
            )
        else:
            cur.execute(
                """
                INSERT INTO knowledge_documents (
                    knowledge_id, user_id, type, content, title, content_hash,
                    embedding_status, chunk_count, metadata, created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, 0, %s::jsonb, NOW(), NOW())
                RETURNING id
                """,
                (
                    knowledge_id,
                    SYSTEM_USER_ID,
                    DOC_TYPE_PLACEHOLDER,
                    content_key,
                    title,
                    content_hash,
                    EMBEDDING_SUCCESS,
                    meta_json,
                ),
            )
            doc_id = cur.fetchone()[0]
    conn.commit()
    return doc_id


def replace_vectors(
    conn,
    doc_id: int,
    knowledge_id: int,
    source_content_key: str,
    chunks: List[Chunk],
    vectors: List[List[float]],
) -> None:
    with conn.cursor() as cur:
        cur.execute(
            "DELETE FROM knowledge_vectors WHERE document_id = %s AND user_id = %s",
            (doc_id, SYSTEM_USER_ID),
        )
        rows = []
        for chunk, vec in zip(chunks, vectors):
            meta = {
                "DocumentId": str(doc_id),
                "KnowledgeId": str(knowledge_id),
                "content": source_content_key,
            }
            rows.append(
                (
                    knowledge_id,
                    doc_id,
                    SYSTEM_USER_ID,
                    to_pg_vector(vec),
                    chunk.text,
                    json.dumps(meta, ensure_ascii=False),
                    chunk.index,
                    sha256_hex(chunk.text),
                    estimate_token(chunk.text),
                )
            )
        psycopg2.extras.execute_batch(
            cur,
            """
            INSERT INTO knowledge_vectors (
                knowledge_id, document_id, user_id, embedding, content, metadata,
                chunk_index, chunk_hash, token_count, created_at
            ) VALUES (%s, %s, %s, CAST(%s AS vector), %s, %s::jsonb, %s, %s, %s, NOW())
            """,
            rows,
            page_size=50,
        )
        cur.execute(
            """
            UPDATE knowledge_documents
            SET chunk_count = %s, last_embedded_at = NOW(), last_error = NULL,
                embedding_status = %s, updated_at = NOW()
            WHERE id = %s
            """,
            (len(chunks), EMBEDDING_SUCCESS, doc_id),
        )
    conn.commit()


def iter_markdown_files(source_dir: Path) -> Iterable[Path]:
    for path in sorted(source_dir.rglob("*.md")):
        if path.name.upper() == "README.MD":
            continue
        yield path


def import_file(
    client: OpenAI,
    conn,
    knowledge_id: int,
    scope: int,
    md_path: Path,
    source_root: Path,
    dry_run: bool,
) -> None:
    raw = md_path.read_text(encoding="utf-8")
    title = md_path.stem
    m = re.search(r"^#\s+(.+)$", raw, re.MULTILINE)
    if m:
        title = m.group(1).strip()

    content_key = str(md_path.relative_to(source_root)).replace("\\", "/")
    splitter = TokenTextSplitterCompat()
    chunks = splitter.split(raw)
    if not chunks:
        print(f"  跳过（无有效分块）: {content_key}")
        return

    print(f"  {content_key}: {len(chunks)} chunks, ~{sum(len(c.text) for c in chunks)} chars")
    if dry_run:
        return

    metadata = {
        "docCategory": infer_doc_category(title, raw, scope),
        "roleType": "default",
        "difficulty": "basic",
        "sourcePriority": 1,
        "sourceFile": content_key,
    }
    doc_id = upsert_document(conn, knowledge_id, content_key, title, metadata)

    all_vectors: List[List[float]] = []
    texts = [c.text for c in chunks]
    for i in range(0, len(texts), EMBED_BATCH_SIZE):
        batch = texts[i : i + EMBED_BATCH_SIZE]
        all_vectors.extend(embed_batch(client, batch))
        time.sleep(0.2)  # 避免触发限流

    replace_vectors(conn, doc_id, knowledge_id, content_key, chunks, all_vectors)


def verify_retrieval(conn, knowledge_id: int, query_vec: List[float], top_k: int = 3) -> None:
    """用 pgvector 余弦距离验证检索（与 Java searchBySimilarity 一致）。"""
    vec = to_pg_vector(query_vec)
    with conn.cursor() as cur:
        # 向量参数只绑定一次，避免重复 CAST 在部分 PG/psycopg2 组合下触发编码异常
        cur.execute(
            """
            WITH q AS (SELECT %s::vector AS v)
            SELECT kv.document_id, kv.chunk_index,
                   (1 - (kv.embedding <=> q.v)) AS score,
                   kd.title
            FROM knowledge_vectors kv
            CROSS JOIN q
            LEFT JOIN knowledge_documents kd ON kd.id = kv.document_id
            WHERE kv.user_id = %s AND kv.knowledge_id = %s
            ORDER BY kv.embedding <=> q.v
            LIMIT %s
            """,
            (vec, SYSTEM_USER_ID, knowledge_id, top_k),
        )
        rows = cur.fetchall()
    print("\n--- 检索抽检 ---")
    for row in rows:
        title = row[3] or ""
        print(f"  doc={row[0]} chunk={row[1]} score={row[2]:.4f} title={title}")


def main() -> int:
    parser = argparse.ArgumentParser(description="离线导入并向量化系统知识库文档")
    parser.add_argument("--scope", type=int, required=True, choices=[2, 3], help="2=系统通用, 3=学习路线")
    parser.add_argument("--source-dir", type=Path, required=True, help="Markdown 根目录")
    parser.add_argument("--dry-run", action="store_true", help="只分块预览，不写库不调 API")
    parser.add_argument("--verify-query", type=str, default="", help="导入后用该问句做一次向量检索抽检")
    parser.add_argument("--pg-host", default=os.getenv("PG_HOST", os.getenv("APP_REMOTE_HOST", "111.229.81.45")))
    parser.add_argument("--pg-port", type=int, default=int(os.getenv("PG_PORT", "5432")))
    parser.add_argument("--pg-db", default=os.getenv("PG_DB", "zdmj"))
    parser.add_argument("--pg-user", default=os.getenv("PG_USER", "zdmj"))
    parser.add_argument("--pg-password", default=os.getenv("PG_PASSWORD", "zdmj"))
    args = parser.parse_args()

    source_dir = args.source_dir.resolve()
    if not source_dir.is_dir():
        print(f"目录不存在: {source_dir}", file=sys.stderr)
        return 1

    files = list(iter_markdown_files(source_dir))
    if not files:
        print(f"未找到 md 文件: {source_dir}", file=sys.stderr)
        return 1
    print(f"待导入 {len(files)} 个文件, scope={args.scope}, dry_run={args.dry_run}")

    api_key = os.getenv("DASHSCOPE_API_KEY") or os.getenv("SPRING_AI_OPENAI_API_KEY")
    if not args.dry_run and not api_key:
        print("请设置 DASHSCOPE_API_KEY", file=sys.stderr)
        return 1

    client = OpenAI(api_key=api_key, base_url=EMBED_BASE_URL) if not args.dry_run else None
    conn = None if args.dry_run else connect_pg(args)

    try:
        knowledge_id = None if args.dry_run else ensure_knowledge_base(conn, args.scope)
        if knowledge_id is not None:
            print(f"使用 knowledge_id={knowledge_id} (scope={args.scope})")

        for md in files:
            import_file(client, conn, knowledge_id, args.scope, md, source_dir, args.dry_run)

        if not args.dry_run and args.verify_query.strip():
            try:
                vecs = embed_batch(client, [args.verify_query.strip()])
                verify_retrieval(conn, knowledge_id, vecs[0])
            except Exception as exc:
                print(f"警告：检索抽检失败（数据已导入）: {exc}", file=sys.stderr)

        print("完成。")
        return 0
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    raise SystemExit(main())

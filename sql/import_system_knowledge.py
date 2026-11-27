#!/usr/bin/env python3
"""
离线导入系统知识库（scope=2 系统通用 / scope=3 学习路线）并向量化写入 PostgreSQL。

与 Java 后端一致的参数（见 application.yml、EmbeddingConfig、KnowledgeEmbeddingServiceImpl）：
  - Embedding 模型：text-embedding-v4，dimensions=1024
  - API：DashScope OpenAI 兼容模式 /v1/embeddings
  - 分块：与后端知识文档配置一致（800 token，CL100K_BASE，保留非空短尾）
  - 批量 embed：每批最多 10 条（MAX_BATCH_SIZE）
  - 向量格式：pgvector 字符串 "[0.1,0.2,...]"
  - chunk_hash：SHA-256(hex)；token_count ≈ len(text)//4

用法示例：
  export DASHSCOPE_API_KEY=sk-xxx
  export PG_HOST=127.0.0.1 PG_USER=zdmj PG_PASSWORD=your-password
  python sql/import_system_knowledge.py \\
    --scope 3 \\
    --source-dir backend/zdmj/src/main/resources/prompts/study

  # 仅预览分块，不写库
  python sql/import_system_knowledge.py --scope 3 --source-dir ./study --dry-run

依赖：pip install psycopg2-binary openai tiktoken
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
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

# ---- 与 Java 知识文档分块配置一致 ----
CHUNK_SIZE_TOKENS = 800
MIN_CHUNK_SIZE_CHARS = 350
MIN_CHUNK_LENGTH_TO_EMBED = 0
MAX_NUM_CHUNKS = sys.maxsize
KEEP_SEPARATOR = True
EMBED_BATCH_SIZE = 10
EMBED_MODEL = "text-embedding-v4"
EMBED_DIMENSIONS = 1024
EMBED_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

EMBEDDING_SUCCESS = 3  # KnowledgeEmbeddingStatusEnum.SUCCESS
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
    """近似 Spring AI TokenTextSplitter（CL100K_BASE，保留全部非空分块）。"""

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
            if chunk:
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


def embed_batch(client: OpenAI, texts: List[str]) -> List[List[float]]:
    resp = client.embeddings.create(
        model=EMBED_MODEL,
        input=texts,
        dimensions=EMBED_DIMENSIONS,
    )
    data = sorted(resp.data, key=lambda x: x.index)
    vectors = [list(d.embedding) for d in data]
    if len(vectors) != len(texts) or [d.index for d in data] != list(range(len(texts))):
        raise RuntimeError("embedding 数量或索引与分块不一致")
    if any(len(v) != EMBED_DIMENSIONS or not all(math.isfinite(x) for x in v) for v in vectors):
        raise RuntimeError("embedding 维度或数值与配置不一致")
    return vectors


def save_document(conn, scope: int, content_key: str, title: str, raw: str,
                  metadata: dict, chunks: List[Chunk], vectors: List[List[float]]) -> int:
    """全部嵌入成功后，单事务保存系统文档及完整分块，保持同路径文档编号。"""
    if scope not in (2, 3) or not chunks or len(chunks) != len(vectors):
        raise ValueError("系统文档范围或分块数量非法")
    with conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO knowledge_documents(scope,user_id,type,content,title,content_hash,
                    file_status,embedding_status,chunk_count,metadata,created_at,updated_at)
                VALUES (%s,NULL,%s,%s,%s,%s,1,3,%s,%s::jsonb,NOW(),NOW())
                ON CONFLICT (scope,content) WHERE scope IN (2,3)
                DO UPDATE SET title=EXCLUDED.title,content_hash=EXCLUDED.content_hash,
                    metadata=EXCLUDED.metadata,file_status=1,embedding_status=3,
                    chunk_count=EXCLUDED.chunk_count,updated_at=EXCLUDED.updated_at
                RETURNING id
                """,
                (scope, DOC_TYPE_PLACEHOLDER, content_key, title, sha256_hex(raw), len(chunks),
                 json.dumps(metadata, ensure_ascii=False)),
            )
            doc_id = cur.fetchone()[0]
            cur.execute("DELETE FROM knowledge_vectors WHERE document_id=%s", (doc_id,))
            rows = [(doc_id, to_pg_vector(vec), chunk.text,
                     json.dumps({"DocumentId": str(doc_id), "content": content_key}, ensure_ascii=False),
                     chunk.index, sha256_hex(chunk.text), estimate_token(chunk.text))
                    for chunk, vec in zip(chunks, vectors, strict=True)]
            inserted = psycopg2.extras.execute_values(cur,
                """INSERT INTO knowledge_vectors(document_id,embedding,content,metadata,
                       chunk_index,chunk_hash,token_count,created_at) VALUES %s RETURNING id""",
                rows, template="(%s,%s::vector,%s,%s::jsonb,%s,%s,%s,NOW())", fetch=True)
            if len(inserted) != len(chunks):
                raise RuntimeError("向量写入数量与分块数量不一致")
            cur.execute("""UPDATE knowledge_documents SET last_embedded_at=NOW(),last_error=NULL,
                           updated_at=NOW() WHERE id=%s AND scope=%s AND user_id IS NULL""", (doc_id, scope))
            if cur.rowcount != 1:
                raise RuntimeError("文档完成状态写入数量非法")
    return doc_id


def iter_markdown_files(source_dir: Path) -> Iterable[Path]:
    for path in sorted(source_dir.rglob("*.md")):
        if path.name.upper() == "README.MD":
            continue
        yield path


def import_file(
    client: OpenAI,
    conn,
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
    all_vectors: List[List[float]] = []
    texts = [c.text for c in chunks]
    for i in range(0, len(texts), EMBED_BATCH_SIZE):
        batch = texts[i : i + EMBED_BATCH_SIZE]
        all_vectors.extend(embed_batch(client, batch))
        time.sleep(0.2)  # 避免触发限流

    save_document(conn, scope, content_key, title, raw, metadata, chunks, all_vectors)


def verify_retrieval(conn, scope: int, query_vec: List[float], top_k: int = 3) -> None:
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
            JOIN knowledge_documents kd ON kd.id = kv.document_id
            WHERE kd.user_id IS NULL AND kd.scope = %s AND kd.file_status=1 AND kd.embedding_status=3
            ORDER BY kv.embedding <=> q.v
            LIMIT %s
            """,
            (vec, scope, top_k),
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
    parser.add_argument("--pg-host", default=os.getenv("PG_HOST", "127.0.0.1"))
    parser.add_argument("--pg-port", type=int, default=int(os.getenv("PG_PORT", "5432")))
    parser.add_argument("--pg-db", default=os.getenv("PG_DATABASE", os.getenv("PG_DB", "zdmj")))
    parser.add_argument("--pg-user", default=os.getenv("PG_USER", "zdmj"))
    parser.add_argument("--pg-password", default=os.getenv("PG_PASSWORD", ""))
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
    if not args.dry_run and not args.pg_password:
        print("请设置 PG_PASSWORD 或传入 --pg-password", file=sys.stderr)
        return 1

    client = OpenAI(api_key=api_key, base_url=EMBED_BASE_URL) if not args.dry_run else None
    conn = None if args.dry_run else connect_pg(args)

    try:
        for md in files:
            import_file(client, conn, args.scope, md, source_dir, args.dry_run)

        if not args.dry_run and args.verify_query.strip():
            vecs = embed_batch(client, [args.verify_query.strip()])
            verify_retrieval(conn, args.scope, vecs[0])

        print("完成。")
        return 0
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    raise SystemExit(main())

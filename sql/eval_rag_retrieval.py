#!/usr/bin/env python3
"""
系统知识库检索离线评测：Hit@5 + MRR。

不改 Java 业务检索策略。默认评测调用
`POST /api/zdmj/knowledge/retrievals`（`KnowledgeRagService.retrieveRanked`）。
`--ann-only` 才直连 `searchBySimilarity` 作对照。

用法（仓库根目录，会读取 .env）：
  python3 sql/eval_rag_retrieval.py --self-test
  python3 sql/eval_rag_retrieval.py --mode eval          # 调 Java retrieveRanked（需本机后端）
  python3 sql/eval_rag_retrieval.py --mode eval --ann-only  # 对照：直连 pgvector，无改写
  python3 sql/eval_rag_retrieval.py --mode eval --refresh-queries
  python3 sql/eval_rag_retrieval.py --mode build
"""

from __future__ import annotations

import argparse
import json
import os
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
import http.client as http_client
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional, Sequence

import psycopg2
import psycopg2.extras

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = REPO_ROOT / "sql" / "rag-eval" / "dataset.jsonl"
DEFAULT_DETAILS = REPO_ROOT / "sql" / "rag-eval" / "details.jsonl"
DEFAULT_REPORT_JSON = REPO_ROOT / "sql" / "rag-eval" / "report.json"
DEFAULT_REPORT_TXT = REPO_ROOT / "sql" / "rag-eval" / "report.txt"

SYSTEM_USER_ID = 0
EMBED_MODEL = "text-embedding-v4"
EMBED_DIMENSIONS = 1024
EMBED_BATCH_SIZE = 10
DASHSCOPE_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"
JSON_INDENT = 2
API_PREFIX = "/api/zdmj"

# 与 KnowledgeVectorMapper.xml#searchBySimilarity 一致（向量只绑定一次，避免重复 CAST）
SEARCH_SQL = """
WITH q AS (SELECT %s::vector AS v)
SELECT
    kv.id,
    kv.document_id AS document_id,
    kv.chunk_index AS chunk_index,
    (1 - (kv.embedding <=> q.v)) AS score
FROM knowledge_vectors kv
CROSS JOIN q
WHERE kv.user_id = %s
  AND kv.knowledge_id = %s
ORDER BY kv.embedding <=> q.v
LIMIT %s
"""

QUERY_GEN_SYSTEM = (
    "你是一名计算机相关专业、正在准备校招的学生，会向求职知识库助手提问。"
    "根据给定资料，写一个你可能会问出口的中文问题。"
    "要求："
    "1. 有一点基础，但口吻像学生请教，不要写成论文标题、面试官考题或文档目录。"
    "2. 描述不必精准：可以口语、含糊、举例，术语说得不完整也可以，但问题仍应能由这段资料回答。"
    "3. 不要堆砌全部考点，不要出现「根据原文/文档/这段」，不要复述整句。"
    "4. 只输出一个问题，不要解释。"
)


@dataclass
class EvalCase:
    chunk_id: int
    document_id: Optional[int]
    knowledge_id: int
    user_id: int
    chunk_index: Optional[int]
    token_count: Optional[int]
    title: str
    source: str
    content: str
    query: str


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[7:]
        key, _, val = line.partition("=")
        key = key.strip()
        val = val.strip().strip('"').strip("'")
        os.environ.setdefault(key, val)


def hit_at_k(ranks: Sequence[Optional[int]], k: int) -> float:
    if not ranks or k <= 0:
        return 0.0
    hits = sum(1 for r in ranks if r is not None and r > 0 and r <= k)
    return hits / len(ranks)


def mrr(ranks: Sequence[Optional[int]]) -> float:
    if not ranks:
        return 0.0
    total = 0.0
    for r in ranks:
        if r is not None and r > 0:
            total += 1.0 / r
    return total / len(ranks)


def run_self_test() -> int:
    assert abs(hit_at_k([1], 5) - 1.0) < 1e-9
    assert abs(mrr([1]) - 1.0) < 1e-9
    assert abs(hit_at_k([5], 5) - 1.0) < 1e-9
    assert abs(mrr([5]) - 0.2) < 1e-9
    assert abs(hit_at_k([6], 5) - 0.0) < 1e-9
    assert abs(mrr([6]) - 1.0 / 6.0) < 1e-9
    assert abs(hit_at_k([0, None], 5) - 0.0) < 1e-9
    assert abs(mrr([0, None]) - 0.0) < 1e-9
    mixed = [1, 5, 6, 0]
    assert abs(hit_at_k(mixed, 5) - 0.5) < 1e-9
    assert abs(mrr(mixed) - (1.0 + 0.2 + 1.0 / 6.0) / 4.0) < 1e-9
    assert hit_at_k([], 5) == 0.0
    print("self-test ok: Hit@K / MRR")
    return 0


def pg_connect(args: argparse.Namespace):
    conn = psycopg2.connect(
        host=args.pg_host,
        port=args.pg_port,
        dbname=args.pg_db,
        user=args.pg_user,
        password=args.pg_password,
        connect_timeout=10,
        keepalives=1,
        keepalives_idle=15,
        keepalives_interval=5,
        keepalives_count=3,
        options="-c client_encoding=UTF8",
    )
    conn.autocommit = True
    conn.set_client_encoding("UTF8")
    with conn.cursor() as cur:
        cur.execute("SET statement_timeout = '20s'")
        cur.execute("SET lock_timeout = '5s'")
    return conn


def resolve_knowledge_id(conn, scope: int) -> int:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM knowledge_bases WHERE scope = %s LIMIT 1",
            (scope,),
        )
        row = cur.fetchone()
    if not row:
        raise RuntimeError(f"未找到 scope={scope} 的 knowledge_bases")
    return int(row[0])


def sample_chunks(
    conn,
    knowledge_id: int,
    sample_size: int,
    seed: str,
    min_chars: int,
) -> list[dict[str, Any]]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id
            FROM knowledge_vectors
            WHERE knowledge_id = %s
              AND user_id = %s
            ORDER BY md5(id::text || %s)
            LIMIT %s
            """,
            (knowledge_id, SYSTEM_USER_ID, seed, sample_size * 2),
        )
        ids = [int(r[0]) for r in cur.fetchall()]
    if not ids:
        return []
    print(f"  抽到 {len(ids)} 个 chunk id，开始回表取文本", flush=True)
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            SELECT
                kv.id AS chunk_id,
                kv.document_id,
                kv.knowledge_id,
                kv.user_id,
                kv.chunk_index,
                kv.token_count,
                COALESCE(kd.title, '') AS title,
                COALESCE(kd.content, '') AS source,
                kv.content
            FROM knowledge_vectors kv
            LEFT JOIN knowledge_documents kd ON kd.id = kv.document_id
            WHERE kv.id = ANY(%s)
            """,
            (ids,),
        )
        by_id = {int(r["chunk_id"]): dict(r) for r in cur.fetchall()}
    ordered = [by_id[i] for i in ids if i in by_id]
    usable = [
        r
        for r in ordered
        if r.get("content") and len(str(r["content"]).strip()) >= min_chars
    ]
    return usable[:sample_size]


def to_pg_vector(vec: Sequence[float]) -> str:
    return "[" + ",".join(str(float(v)) for v in vec) + "]"


def post_json(
    url: str,
    payload: dict[str, Any],
    headers: dict[str, str],
    timeout: int = 90,
) -> tuple[int, Any]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    ctx = ssl.create_default_context()
    req = urllib.request.Request(url, data=data, method="POST")
    for key, value in headers.items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            raw = resp.read().decode("utf-8")
            body: Any = json.loads(raw) if raw else {}
            return resp.status, body
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            body = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            body = raw
        return exc.code, body
    except (TimeoutError, ConnectionError, http_client.IncompleteRead, OSError) as exc:
        raise urllib.error.URLError(exc) from exc


def http_json(url: str, payload: dict[str, Any], api_key: str, timeout: int = 90) -> dict[str, Any]:
    last_err: Optional[Exception] = None
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    for attempt in range(3):
        try:
            status, body = post_json(url, payload, headers, timeout=timeout)
            if status >= 400:
                raise RuntimeError(f"HTTP {status} {url}: {str(body)[:800]}")
            if not isinstance(body, dict):
                raise RuntimeError(f"非 JSON 响应: {url}")
            return body
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, http_client.IncompleteRead, RuntimeError) as exc:
            last_err = exc
            time.sleep(0.6 * (attempt + 1))
    raise RuntimeError(f"请求失败: {url}: {last_err}")


def embed_texts(api_key: str, texts: list[str]) -> list[list[float]]:
    all_vecs: list[list[float]] = []
    for i in range(0, len(texts), EMBED_BATCH_SIZE):
        batch = texts[i : i + EMBED_BATCH_SIZE]
        resp = http_json(
            f"{DASHSCOPE_BASE}/embeddings",
            {
                "model": EMBED_MODEL,
                "input": batch,
                "dimensions": EMBED_DIMENSIONS,
            },
            api_key,
        )
        data = sorted(resp.get("data") or [], key=lambda x: x.get("index", 0))
        if len(data) != len(batch):
            raise RuntimeError(f"embedding 条数异常: expect={len(batch)} got={len(data)}")
        for item in data:
            vec = list(item["embedding"])
            if len(vec) != EMBED_DIMENSIONS:
                raise RuntimeError(f"embedding 维数异常: {len(vec)}")
            all_vecs.append(vec)
        time.sleep(0.15)
    return all_vecs


_THINK_RE = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)


def _clean_query(text: str) -> str:
    text = _THINK_RE.sub("", text or "")
    text = text.replace("```", "").strip()
    text = re.sub(r"^[\s\d\.、\)]+", "", text).strip()
    text = " ".join(text.split())
    if len(text) >= 2 and text[0] in "\"“'" and text[-1] in "\"”'":
        text = text[1:-1].strip()
    return text[:280]


def generate_query(api_key: str, model: str, content: str) -> str:
    excerpt = (content or "").strip()
    payload = {
        "model": model,
        "temperature": 0.7,
        "enable_thinking": False,
        "messages": [
            {"role": "system", "content": QUERY_GEN_SYSTEM},
            {"role": "user", "content": "资料：\n" + excerpt},
        ],
    }
    last_err: Optional[Exception] = None
    for attempt in range(3):
        try:
            resp = http_json(f"{DASHSCOPE_BASE}/chat/completions", payload, api_key, timeout=60)
            msg = ((resp.get("choices") or [{}])[0].get("message") or {}).get("content") or ""
            query = _clean_query(msg)
            if len(query) >= 4:
                return query
            last_err = RuntimeError(f"模型返回过短: {msg!r}")
        except Exception as exc:  # noqa: BLE001 — 重试后上抛
            last_err = exc
            time.sleep(0.6 * (attempt + 1))
    raise RuntimeError(f"生成 query 失败: {last_err}")


def build_dataset(args: argparse.Namespace, conn) -> list[EvalCase]:
    knowledge_id = resolve_knowledge_id(conn, args.scope)
    print(
        f"抽样池: scope={args.scope} knowledge_id={knowledge_id} "
        f"sample={args.sample_size} seed={args.seed}",
        flush=True,
    )
    rows = sample_chunks(
        conn, knowledge_id, args.sample_size, args.seed, args.min_content_chars
    )
    if len(rows) < args.sample_size:
        print(f"警告：可用 chunk 仅 {len(rows)} 条，少于 {args.sample_size}", flush=True)
    api_key = args.api_key
    cases: list[EvalCase] = []
    for i, row in enumerate(rows, start=1):
        content = (row.get("content") or "").strip()
        query = generate_query(api_key, args.chat_model, content)
        case = EvalCase(
            chunk_id=int(row["chunk_id"]),
            document_id=int(row["document_id"]) if row.get("document_id") is not None else None,
            knowledge_id=int(row["knowledge_id"]),
            user_id=int(row["user_id"]),
            chunk_index=int(row["chunk_index"]) if row.get("chunk_index") is not None else None,
            token_count=int(row["token_count"]) if row.get("token_count") is not None else None,
            title=str(row.get("title") or ""),
            source=str(row.get("source") or ""),
            content=content,
            query=query,
        )
        cases.append(case)
        if i % 10 == 0 or i == len(rows):
            print(f"  出题 {i}/{len(rows)}  例: id={case.chunk_id} q={case.query}", flush=True)
        time.sleep(0.12)
    return cases


def refresh_queries(args: argparse.Namespace, cases: list[EvalCase]) -> list[EvalCase]:
    """保留已有 gold chunk，仅按当前出题口吻重写 query。"""
    print(f"按学生口吻重写出题: n={len(cases)} model={args.chat_model}", flush=True)
    refreshed: list[EvalCase] = []
    for i, case in enumerate(cases, start=1):
        query = generate_query(args.api_key, args.chat_model, case.content)
        refreshed.append(
            EvalCase(
                chunk_id=case.chunk_id,
                document_id=case.document_id,
                knowledge_id=case.knowledge_id,
                user_id=case.user_id,
                chunk_index=case.chunk_index,
                token_count=case.token_count,
                title=case.title,
                source=case.source,
                content=case.content,
                query=query,
            )
        )
        if i % 10 == 0 or i == len(cases):
            print(f"  出题 {i}/{len(cases)}  例: id={case.chunk_id} q={query}", flush=True)
        time.sleep(0.12)
    return refreshed


def dump_pretty(obj: Any) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=JSON_INDENT) + "\n"


def write_pretty_json(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(dump_pretty(obj), encoding="utf-8")


def write_dataset(path: Path, cases: list[EvalCase]) -> None:
    write_pretty_json(path, [asdict(c) for c in cases])
    if path.name != "dataset.jsonl":
        return
    preview = {
        "n": len(cases),
        "scope": 2,
        "knowledge_id": cases[0].knowledge_id if cases else None,
        "note": "完整 chunk 原文在 dataset.jsonl（gitignore）。本文件只含 query 与来源，便于查看。",
        "cases": [
            {
                "chunk_id": c.chunk_id,
                "document_id": c.document_id,
                "knowledge_id": c.knowledge_id,
                "chunk_index": c.chunk_index,
                "token_count": c.token_count,
                "title": c.title,
                "source": c.source,
                "query": c.query,
                "content_preview": " ".join((c.content or "").split())[:80],
            }
            for c in cases
        ],
    }
    write_pretty_json(path.with_name("dataset-preview.json"), preview)


def load_json_records(path: Path) -> list[dict[str, Any]]:
    """读 JSON 数组（现行格式）或旧版 JSONL（一行一个对象）。"""
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    if text[0] == "[":
        data = json.loads(text)
        if not isinstance(data, list):
            raise ValueError(f"{path} 根节点必须是 JSON 数组")
        return data
    rows: list[dict[str, Any]] = []
    for line in text.splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def load_dataset(path: Path) -> list[EvalCase]:
    cases: list[EvalCase] = []
    for raw in load_json_records(path):
        cases.append(EvalCase(**{k: raw[k] for k in EvalCase.__dataclass_fields__}))
    return cases


def search_by_similarity(
    conn, user_id: int, knowledge_id: int, query_vec: Sequence[float], limit: int
) -> list[dict[str, Any]]:
    vec = to_pg_vector(query_vec)
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(SEARCH_SQL, (vec, user_id, knowledge_id, limit))
        return [dict(r) for r in cur.fetchall()]


def rank_of(hits: list[dict[str, Any]], gold_id: int) -> Optional[int]:
    for i, hit in enumerate(hits, start=1):
        hit_id = hit.get("id")
        if hit_id is not None and int(hit_id) == gold_id:
            return i
    return None


def doc_rank_of(hits: list[dict[str, Any]], gold_doc_id: Optional[int]) -> Optional[int]:
    if gold_doc_id is None:
        return None
    for i, hit in enumerate(hits, start=1):
        did = hit.get("documentId", hit.get("document_id"))
        if did is not None and int(did) == int(gold_doc_id):
            return i
    return None


def app_url(args: argparse.Namespace, path: str) -> str:
    return args.api_base.rstrip("/") + API_PREFIX + path


def login_eval_user(args: argparse.Namespace) -> str:
    url = app_url(args, "/users/login")
    last_err: Optional[Exception] = None
    status, body = 0, None
    for attempt in range(6):
        try:
            status, body = post_json(
                url,
                {"usernameOrEmail": args.eval_user, "password": args.eval_password},
                {"Content-Type": "application/json"},
                timeout=30,
            )
            last_err = None
            break
        except urllib.error.URLError as exc:
            wait = 2 * (attempt + 1)
            print(f"  登录连接失败（{exc}），{wait}s 后重试", flush=True)
            time.sleep(wait)
            last_err = exc
    if last_err is not None:
        raise RuntimeError(
            f"无法连接后端 {args.api_base}（{last_err}）。请先启动: "
            "cd backend/zdmj && mvn -B -ntp spring-boot:run"
        ) from last_err
    if status >= 400 or not isinstance(body, dict):
        raise RuntimeError(f"登录失败 HTTP {status}: {str(body)[:400]}")
    if body.get("code") not in (0, None):
        raise RuntimeError(f"登录失败: {body.get('msg') or body}")
    data = body.get("data") if isinstance(body.get("data"), dict) else {}
    token = data.get("token") if isinstance(data, dict) else None
    if not token:
        raise RuntimeError("登录响应无 token")
    return str(token)


def post_retrieval(args: argparse.Namespace, token: str, query: str) -> dict[str, Any]:
    url = app_url(args, "/knowledge/retrievals")
    payload = {
        "query": query,
        "ragDocumentIds": [],
        "useSystemKnowledge": True,
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}",
    }
    last_err: Optional[Exception] = None
    for attempt in range(6):
        try:
            status, body = post_json(url, payload, headers, timeout=120)
        except urllib.error.URLError as exc:
            wait = 2 * (attempt + 1)
            print(f"  连接失败（{exc}），{wait}s 后重试", flush=True)
            time.sleep(wait)
            last_err = exc
            continue
        if status == 429:
            wait = 5 * (attempt + 1)
            print(f"  限流 429，{wait}s 后重试", flush=True)
            time.sleep(wait)
            last_err = RuntimeError("RATE_LIMIT_EXCEEDED")
            continue
        if status >= 400 or not isinstance(body, dict):
            raise RuntimeError(f"检索失败 HTTP {status}: {str(body)[:500]}")
        if body.get("code") not in (0, None):
            raise RuntimeError(f"检索失败: {body.get('msg') or body}")
        data = body.get("data")
        if not isinstance(data, dict):
            raise RuntimeError(f"检索响应无 data: {body}")
        return data
    raise RuntimeError(
        f"检索多次失败: {last_err}。请先启动: cd backend/zdmj && mvn -B -ntp spring-boot:run"
    )


def _hit_int(hit: dict[str, Any], *keys: str) -> Optional[int]:
    for key in keys:
        val = hit.get(key)
        if val is not None:
            return int(val)
    return None


def _top_hits(hits: list[dict[str, Any]], k: int) -> list[dict[str, Any]]:
    top: list[dict[str, Any]] = []
    for h in hits[:k]:
        top.append(
            {
                "id": _hit_int(h, "id"),
                "document_id": _hit_int(h, "documentId", "document_id"),
                "chunk_index": _hit_int(h, "chunkIndex", "chunk_index"),
                "score": float(h["score"]) if h.get("score") is not None else None,
            }
        )
    return top


def evaluate(args: argparse.Namespace, conn, cases: list[EvalCase]):
    if args.ann_only:
        return evaluate_ann_only(args, conn, cases)
    return evaluate_via_java(args, cases)


def evaluate_via_java(args: argparse.Namespace, cases: list[EvalCase]):
    if not cases:
        raise RuntimeError("dataset 为空")
    print(
        f"调用 Java retrieveRanked: {app_url(args, '/knowledge/retrievals')} n={len(cases)}",
        flush=True,
    )
    token = login_eval_user(args)
    details: list[dict[str, Any]] = []
    chunk_ranks: list[Optional[int]] = []
    document_ranks: list[Optional[int]] = []
    for i, case in enumerate(cases, start=1):
        data = post_retrieval(args, token, case.query)
        hits = data.get("hits") or []
        if not isinstance(hits, list):
            hits = []
        c_rank = rank_of(hits, case.chunk_id)
        d_rank = doc_rank_of(hits, case.document_id)
        chunk_ranks.append(c_rank)
        document_ranks.append(d_rank)
        details.append(
            {
                "chunk_id": case.chunk_id,
                "document_id": case.document_id,
                "query": case.query,
                "query_normalized": data.get("query"),
                "rewritten_query": data.get("rewrittenQuery"),
                "top_k": data.get("topK"),
                "min_score": data.get("minScore"),
                "rewrite_used": bool(data.get("rewriteUsed")),
                "chunk_rank": c_rank,
                "document_rank": d_rank,
                "hit_at_k": bool(c_rank is not None and c_rank <= args.k),
                "reciprocal_rank": (1.0 / c_rank) if c_rank else 0.0,
                "top_hits": _top_hits(hits, args.k),
            }
        )
        if i % 10 == 0 or i == len(cases):
            print(f"  检索 {i}/{len(cases)}  gold={case.chunk_id} rank={c_rank}", flush=True)
    return _build_report(
        args,
        cases,
        chunk_ranks,
        document_ranks,
        retriever="KnowledgeRagService.retrieveRanked (no expand)",
    ), details


def evaluate_ann_only(args: argparse.Namespace, conn, cases: list[EvalCase]):
    if not cases:
        raise RuntimeError("dataset 为空")
    knowledge_id = cases[0].knowledge_id
    queries = [c.query for c in cases]
    print(
        f"ANN-only Embedding {len(queries)} 条 query（{EMBED_MODEL} dim={EMBED_DIMENSIONS}）...",
        flush=True,
    )
    vectors = embed_texts(args.api_key, queries)

    details: list[dict[str, Any]] = []
    chunk_ranks: list[Optional[int]] = []
    document_ranks: list[Optional[int]] = []
    for case, vec in zip(cases, vectors):
        hits = search_by_similarity(
            conn, SYSTEM_USER_ID, knowledge_id, vec, args.candidate_limit
        )
        c_rank = rank_of(hits, case.chunk_id)
        d_rank = doc_rank_of(hits, case.document_id)
        chunk_ranks.append(c_rank)
        document_ranks.append(d_rank)
        details.append(
            {
                "chunk_id": case.chunk_id,
                "document_id": case.document_id,
                "query": case.query,
                "query_normalized": None,
                "rewritten_query": None,
                "top_k": args.candidate_limit,
                "min_score": None,
                "rewrite_used": False,
                "chunk_rank": c_rank,
                "document_rank": d_rank,
                "hit_at_k": bool(c_rank is not None and c_rank <= args.k),
                "reciprocal_rank": (1.0 / c_rank) if c_rank else 0.0,
                "top_hits": _top_hits(hits, args.k),
            }
        )
    return _build_report(
        args,
        cases,
        chunk_ranks,
        document_ranks,
        retriever="KnowledgeVectorMapper.searchBySimilarity (ann-only)",
    ), details


def _build_report(
    args: argparse.Namespace,
    cases: list[EvalCase],
    chunk_ranks: list[Optional[int]],
    document_ranks: list[Optional[int]],
    retriever: str,
) -> dict[str, Any]:
    k = args.k
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "scope": args.scope,
        "knowledge_id": cases[0].knowledge_id,
        "n": len(cases),
        "k": k,
        "candidate_limit": args.candidate_limit,
        "embed_model": EMBED_MODEL,
        "embed_dimensions": EMBED_DIMENSIONS,
        "chat_model": args.chat_model,
        "seed": args.seed,
        "retriever": retriever,
        "api_base": args.api_base,
        "hit_at_k": round(hit_at_k(chunk_ranks, k), 6),
        "mrr": round(mrr(chunk_ranks), 6),
        "hit_at_1": round(hit_at_k(chunk_ranks, 1), 6),
        "document_hit_at_k": round(hit_at_k(document_ranks, k), 6),
        "document_mrr": round(mrr(document_ranks), 6),
        "miss_chunk_ids": [c.chunk_id for c, r in zip(cases, chunk_ranks) if r is None or r > k],
    }


def format_report_txt(report: dict[str, Any]) -> str:
    lines = [
        "RAG 检索离线评测（系统知识库 / retrieveRanked）",
        f"时间: {report['generated_at']}",
        f"样本: n={report['n']}  scope={report['scope']}  knowledge_id={report['knowledge_id']}",
        f"契约: {report['retriever']}",
        f"api_base: {report.get('api_base', '')}  candidate_limit={report['candidate_limit']}",
        "",
        f"Hit@{report['k']} = {report['hit_at_k']:.4f}",
        f"MRR     = {report['mrr']:.4f}",
        f"Hit@1   = {report['hit_at_1']:.4f}  （诊断，非正式指标）",
        f"Doc Hit@{report['k']} = {report['document_hit_at_k']:.4f}  （同文档任一块，诊断）",
        f"Doc MRR = {report['document_mrr']:.4f}",
        "",
        f"未进 Top-{report['k']} 的 gold chunk 数: {len(report['miss_chunk_ids'])}",
    ]
    return "\n".join(lines) + "\n"


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    load_dotenv(REPO_ROOT / ".env")
    parser = argparse.ArgumentParser(description="系统知识库 RAG 检索 Hit@5 / MRR 评测")
    parser.add_argument("--self-test", action="store_true", help="只跑指标公式自测")
    parser.add_argument("--mode", choices=["all", "build", "eval"], default="all")
    parser.add_argument("--scope", type=int, default=2, choices=[2, 3], help="2=系统通用 3=学习路线")
    parser.add_argument("--sample-size", type=int, default=100)
    parser.add_argument("--seed", default="42")
    parser.add_argument("--k", type=int, default=5)
    parser.add_argument("--candidate-limit", type=int, default=20, help="检索条数，须 >= k，覆盖生产 topK 上限")
    parser.add_argument("--min-content-chars", type=int, default=80)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--details", type=Path, default=DEFAULT_DETAILS)
    parser.add_argument("--report-json", type=Path, default=DEFAULT_REPORT_JSON)
    parser.add_argument("--report-txt", type=Path, default=DEFAULT_REPORT_TXT)
    parser.add_argument("--rebuild-dataset", action="store_true")
    parser.add_argument(
        "--refresh-queries",
        action="store_true",
        help="保留 dataset 里的 gold chunk，按当前出题口吻重写 query",
    )
    parser.add_argument("--chat-model", default=os.getenv("RAG_EVAL_CHAT_MODEL", "qwen3.8-flash"))
    parser.add_argument(
        "--ann-only",
        action="store_true",
        help="对照：直连 pgvector ANN，不改写；主指标请走 Java retrieveRanked",
    )
    parser.add_argument(
        "--api-base",
        default=os.getenv("RAG_EVAL_API_BASE", "http://127.0.0.1:8080"),
        help="后端基址（默认 RAG_EVAL_API_BASE 或 127.0.0.1:8080）",
    )
    parser.add_argument("--eval-user", default=os.getenv("RAG_EVAL_USER", "testUser"))
    parser.add_argument("--eval-password", default=os.getenv("RAG_EVAL_PASSWORD", "123456"))
    parser.add_argument("--pg-host", default=os.getenv("PG_HOST") or os.getenv("APP_REMOTE_HOST", "127.0.0.1"))
    parser.add_argument("--pg-port", type=int, default=int(os.getenv("PG_PORT", "5432")))
    parser.add_argument("--pg-db", default=os.getenv("PG_DATABASE") or os.getenv("PG_DB", "zdmj"))
    parser.add_argument("--pg-user", default=os.getenv("PG_USER", "zdmj"))
    parser.add_argument("--pg-password", default=os.getenv("PG_PASSWORD", ""))
    args = parser.parse_args(argv)
    args.api_key = os.getenv("DASHSCOPE_API_KEY") or os.getenv("SPRING_AI_OPENAI_API_KEY") or ""
    if args.candidate_limit < args.k:
        parser.error("--candidate-limit 必须 >= --k")
    return args


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    if args.self_test:
        return run_self_test()

    need_build = args.mode in ("all", "build") and (args.rebuild_dataset or not args.dataset.is_file())
    need_eval = args.mode in ("all", "eval")
    need_pg = need_build or (need_eval and args.ann_only)
    need_llm = need_build or args.refresh_queries or (need_eval and args.ann_only)

    if need_eval and not need_build and not args.dataset.is_file():
        print(f"无 dataset: {args.dataset}，请先 --mode build", file=sys.stderr)
        return 1
    if need_pg and not args.pg_password:
        print("请设置 PG_PASSWORD 或传入 --pg-password", file=sys.stderr)
        return 1
    if need_llm and not args.api_key:
        print("请设置 DASHSCOPE_API_KEY", file=sys.stderr)
        return 1
    if args.refresh_queries and not args.dataset.is_file():
        print(f"无 dataset 可刷新: {args.dataset}", file=sys.stderr)
        return 1

    conn = None
    try:
        if need_pg:
            conn = pg_connect(args)
            print(f"已连接 {args.pg_host}:{args.pg_port}/{args.pg_db}", flush=True)

        if need_build:
            cases = build_dataset(args, conn)
            write_dataset(args.dataset, cases)
            print(f"已写 dataset: {args.dataset} ({len(cases)} 条)", flush=True)
        else:
            cases = load_dataset(args.dataset)
            print(f"复用 dataset: {args.dataset} ({len(cases)} 条)", flush=True)
            if args.refresh_queries:
                cases = refresh_queries(args, cases)
                write_dataset(args.dataset, cases)
                print(f"已更新 query: {args.dataset} ({len(cases)} 条)", flush=True)

        if not need_eval:
            return 0

        report, details = evaluate(args, conn, cases)
        write_pretty_json(args.report_json, report)
        write_pretty_json(args.details, details)
        txt = format_report_txt(report)
        args.report_txt.write_text(txt, encoding="utf-8")
        print()
        print(txt)
        print(f"报告: {args.report_json}")
        print(f"明细: {args.details}")
        return 0
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    raise SystemExit(main())

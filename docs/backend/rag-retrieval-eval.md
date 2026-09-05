# RAG 检索离线评测（Hit@5 / MRR）

检索层量化：只衡量「正确 chunk 有没有进 Top-K、排第几」，不跑生成层。**排序策略只维护在 Java**（改写、双路 ANN、动态 topK、minScore）；Python 只出题、调接口、算指标。切块策略不变。

**前端无需修改；数据库无需修改。**

## 指标

| 指标 | 含义 | 计算 |
|------|------|------|
| Hit@5 | 找没找到 | gold `chunk_id` 是否出现在排序后的前 5 条 |
| MRR | 排得有多靠前 | 对每个 query 取 `1/rank`（未召回记 0），再平均 |

rank 为 1-based。gold 是抽样到的 `knowledge_vectors.id`。

本评测对齐 `KnowledgeRagService.retrieveRanked`。对话 `streamAnswer` 直接调用同一方法，再用排序切块拼 prompt（不再整篇展开）。Hit@K/MRR 看的就是这层合并截断后的 `hits`。

## 契约

默认评测调用已登录接口（与对话 RAG 排序层同一实现）：

`POST /api/zdmj/knowledge/retrievals`

需 JWT。请求只搜系统库（空 `ragDocumentIds` 不搜用户库，与 `shouldSearchUserKnowledge` 一致）：

```json
{
  "query": "<学生问句>",
  "ragDocumentIds": [],
  "useSystemKnowledge": true
}
```

响应用 `data.hits[].id` 对 gold `chunk_id` 算 rank；`rewrittenQuery` / `topK` / `minScore` / `rewriteUsed` 写入 details。改写走 `ChatUtil.chatOnce(评测登录用户)`，与线上一致。

接口限流：USER **120 次/分钟**。脚本遇 HTTP 429 休眠重试。

`--ann-only` 才直连 `KnowledgeVectorMapper.searchBySimilarity`（无改写、`LIMIT=20`），仅作对照，**不是**主指标。

## 测试集

语料：系统知识库（源目录 `backend/zdmj/src/main/resources/system-knowledge`，gitignore；落库 `scope=2`）。

1. 读 `MAX(id)`，抽样 `id <= max_id` 的 100 条 chunk（`ORDER BY md5(id||seed)`，默认可复现）
2. 用 LLM 按 chunk 文本出一条**学生口吻**中文 query（有基础但不精准，口语化）
3. 每条至少含：`chunk_id`、`content`、`query`，以及 `document_id` / `chunk_index` / `title` / `source`

产物在 `sql/rag-eval/`（含原文，不入库）。复跑检索不要重出题：`--mode eval`。

合成 query 来自 gold chunk，分数是检索器上界参考，不能替代真实用户问句。

## 命令

本机需已启动后端（加载仓库根 `.env`）：

```bash
cd backend/zdmj && mvn -B -ntp spring-boot:run
```

```bash
python3 sql/eval_rag_retrieval.py --self-test
python3 sql/eval_rag_retrieval.py --mode eval              # 调 retrieveRanked（需登录）
python3 sql/eval_rag_retrieval.py --mode eval --ann-only   # 对照：直连 pgvector
python3 sql/eval_rag_retrieval.py --mode build             # 仅出题
```

默认基址 `http://127.0.0.1:8080`（`RAG_EVAL_API_BASE`，也可用 `localhost`）。登录 `testUser` / `123456`（`RAG_EVAL_USER` / `RAG_EVAL_PASSWORD` 可覆盖）。后端未启动则报错退出。

出题 / `--ann-only` 才需要 `.env` 的 `PG_*` 与 `DASHSCOPE_API_KEY`。指标公式用 `--self-test` 即可。

## 怎么读结果

- Hit@5 低：Embedding / 切块边界 / 改写跑偏 / 索引问题，优先查 Java 检索层
- Hit@5 高但 MRR 低：召回了但排不前，才考虑 Rerank
- 改策略只动 Java，脚本自动跟上
- 线上仍要看点踩、追问；本套是离线回归基线

本机 100 条（seed=42，`knowledge_id=11`）当前 `retrieveRanked`（改写双路、合并后截断 topK、无整篇展开）：**Hit@5 = 0.89，MRR = 0.80**。此前同数据集约 Hit@5=0.91 / MRR=0.79；`--ann-only` 对照约 Hit@5=0.90 / MRR=0.80。主指标以本次 Java 排序层为准。

package com.zdmj.knowledgeService.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.knowledgeService.config.RagConfig;
import com.zdmj.knowledgeService.config.RagConfig.Search;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalResponse;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.knowledgeService.service.KnowledgeRagService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRagServiceImpl implements KnowledgeRagService {

    private static final String SYSTEM_PROMPT = "system";
    private static final String RAG_PROMPT = "knowledgebase-rag-system";
    private static final String QUERY_REWRITE_PROMPT = "knowledgebase-query-rewrite";

    /** 单条命中写入日志时，文本预览最大字符数（避免超长 content 撑爆日志） */
    private static final int RAG_LOG_CONTENT_PREVIEW_MAX = 500;

    private final EmbeddingGateway embeddingGateway;
    private final RagConfig ragConfig;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final ModelGateway modelGateway;

    /** 
     * 检索知识并生成回复，三个检索分支使用同一份历史。
     *
     * @param actor 已认证操作者
     * @param conversationId 会话编号
     * @param request 模型请求
     * @param ragDocumentIds 私有文档范围，空列表关闭私有检索，null 表示全部
     * @param useSystemKnowledge 是否检索系统知识库
     * @return 模型增量文本，检索及生成错误立即传播
     */
    @Override
    public Flux<String> streamAnswer(CurrentActor actor, Long conversationId, ModelRequest request,
            List<Long> ragDocumentIds, boolean useSystemKnowledge) {
        // 1. 准备提示词
        String prompt = SYSTEM_PROMPT;
        Map<String, Object> variables = request.promptVars() == null
                ? null : new HashMap<>(request.promptVars());
        // 注意，此处 ragDocumentIds == null 表示检索用户知识库 + 系统知识库全部文档
        if (ragDocumentIds == null || !ragDocumentIds.isEmpty() || useSystemKnowledge) {
            // 2. 检索
            KnowledgeRetrievalResponse ranked = retrieveRanked(actor, request.message(), ragDocumentIds, useSystemKnowledge);
            // 3. 获取符合条件的文段
            List<KnowledgeRetrivalDTO> hits = ranked.hits() == null ? List.of() : ranked.hits();
            if (!hits.isEmpty()) {
                // 4. 记录检索明细
                logRagRetrievalHits(hits, actor.userId(), conversationId);
                // 5. 准备上下文
                variables = variables == null ? new HashMap<>() : variables;
                variables.put("context", buildContext(hits, ragConfig.getSearch().getContextBudget()));
                // 6. 准备提示词
                prompt = RAG_PROMPT;
            }
            // 7. 记录检索完成
            log.info("RAG 检索完成 conversationId={} hitCount={}", conversationId, hits.size());
        }
        // 8. 返回模型流
        return modelGateway.stream(actor, new ModelRequest(request.message(), prompt, variables,
                request.history(), request.historyNotice()));
    }

    /**
     * 排序层：改写 → 每句一次 embed → 双库 ANN → 去重留高分 → 截断 topK。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param userMessage 用户问题
     * @param ragDocumentIds 私有文档范围，空列表关闭私有检索，null 表示全部
     * @param useSystemKnowledge 是否检索系统知识库
     * @return 知识检索响应
     */
    @Override
    public KnowledgeRetrievalResponse retrieveRanked(CurrentActor actor, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge) {
        Long userId = actor.userId();

        // --- 1. 规范化问句，按句长选 topK / minScore ---
        String query = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");
        Search search = ragConfig.getSearch();
        int topK = resolveTopK(query.length(), search);
        double minScore = query.length() <= search.getShortQueryLength()
                ? search.getMinScoreShort()
                : search.getMinScoreDefault();

        // --- 2. 两库都关：返回元数据，不改写、不检索 ---
        if (ragDocumentIds != null && ragDocumentIds.isEmpty() && !useSystemKnowledge) {
            return new KnowledgeRetrievalResponse(query, query, topK, minScore, false, List.of());
        }

        // --- 3. 查询改写；与原文相同则只检索一路。改写失败时终止 ---
        String rewritten = query;
        if (ragConfig.getRewrite().isEnabled() && StringUtils.hasText(query)) {
            rewritten = rewriteQuery(actor, query);
        }
        boolean dualPath = !query.equals(rewritten);

        // --- 4. 原文 / 改写各 embed 一次（同句复用向量） ---
        String vecRaw = toPgVector(actor, query);
        String vecRewritten = dualPath ? toPgVector(actor, rewritten) : vecRaw;

        // --- 5. 个人库 + 系统库：每个向量各做一次 ANN ---
        List<KnowledgeRetrivalDTO> combined = new ArrayList<>();
        if (ragDocumentIds == null || !ragDocumentIds.isEmpty()) {
            Long userKbId = knowledgeBasesService.getOrCreateKnowledgeBaseId(actor);
            addAnnHits(combined, userId, userKbId, ragDocumentIds, vecRaw, topK, minScore);
            if (dualPath) {
                addAnnHits(combined, userId, userKbId, ragDocumentIds, vecRewritten, topK, minScore);
            }
        }
        if (useSystemKnowledge) {
            Long systemKbId = knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode());
            if (systemKbId == null) {
                log.warn("系统知识库未配置（scope=2），跳过系统库检索");
            } else {
                Long systemOwner = KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID;
                addAnnHits(combined, systemOwner, systemKbId, null, vecRaw, topK, minScore);
                if (dualPath) {
                    addAnnHits(combined, systemOwner, systemKbId, null, vecRewritten, topK, minScore);
                }
            }
        }

        // --- 6. 去重留高分，截断为 topK ---
        return new KnowledgeRetrievalResponse(query, rewritten, topK, minScore, dualPath,
                mergeAndSort(combined, topK));
    }

    /** 按句长选 topK / minScore */
    private int resolveTopK(int queryLen, Search search) {
        if (queryLen <= search.getShortQueryLength()) {
            return search.getTopkShort();
        }
        if (queryLen <= search.getMediumQueryLength()) {
            return search.getTopkMedium();
        }
        return search.getTopkLong();
    }

    /**
     * 把用户问题改写成更可检索的单句。改写失败或没有返回文本时终止本次检索。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param rawText 原始问题，调用前已确认为非空
     * @return 改写后的问题
     */
    private String rewriteQuery(CurrentActor actor, String rawText) {
        String queryText;
        try {
            queryText = modelGateway.generate(
                    actor,
                    new ModelRequest(rawText, QUERY_REWRITE_PROMPT, Map.of("question", rawText)));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("查询改写失败", exception);
            throw new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, exception);
        }
        if (!StringUtils.hasText(queryText)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, "查询改写没有返回可用文本");
        }
        log.info("RAG 查询改写: originalQuestion={} | rewrittenQuestion={}", rawText, queryText);
        return queryText.trim();
    }

    /**
     * 为检索文本生成向量字面量。文本为空时返回 null，表示这一路没有查询；嵌入失败时终止本次检索。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param queryText 待嵌入的问题
     * @return pgvector 字面量；文本为空时返回 null
     */
    private String toPgVector(CurrentActor actor, String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return null;
        }
        String literal = knowledgeEmbeddingService.toPgVector(embedQuery(actor, queryText));
        if (!StringUtils.hasText(literal)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, "查询向量生成失败");
        }
        return literal;
    }

    private float[] embedQuery(CurrentActor actor, String queryText) {
        List<float[]> vectors;
        try {
            vectors = embeddingGateway.embed(actor, List.of(queryText));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("查询向量生成失败", exception);
            throw new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, exception);
        }
        if (vectors == null || vectors.isEmpty() || vectors.get(0) == null || vectors.get(0).length == 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, "查询向量生成失败");
        }
        return vectors.get(0);
    }

    /**
     * 单次 ANN：已算好的向量 → pgvector 余弦检索 → Java 侧 minScore 过滤。
     * 阈值不写进 SQL，避免干扰索引形态。
     */
    private void addAnnHits(List<KnowledgeRetrivalDTO> out, Long ownerUserId, Long knowledgeId,
            List<Long> ragDocumentIds, String vecString, int topK, double minScore) {
        if (vecString == null || knowledgeId == null) {
            return;
        }
        // --- 1. ANN：未圈定文档搜整库，否则 document_id IN (...) ---
        List<KnowledgeRetrivalDTO> raw;
        if (ragDocumentIds == null || ragDocumentIds.isEmpty()) {
            raw = knowledgeVectorMapper.searchBySimilarity(ownerUserId, knowledgeId, vecString, topK);
        } else {
            raw = knowledgeVectorMapper.searchBySimilarityInDocuments(
                    ownerUserId, knowledgeId, ragDocumentIds, vecString, topK);
        }
        if (raw == null || raw.isEmpty()) {
            return;
        }
        // --- 2. SQL 的 LIMIT 只保证候选数，不够像的在这里丢掉 ---
        for (KnowledgeRetrivalDTO hit : raw) {
            if (hit.score() != null && hit.score() >= minScore) {
                out.add(hit);
            }
        }
    }

    /** 按 documentId-chunkIndex 去重，同块留更高分，按 score 降序后截断 topK。 */
    private List<KnowledgeRetrivalDTO> mergeAndSort(List<KnowledgeRetrivalDTO> list, int topK) {
        // --- 1. 同 documentId+chunkIndex 留更高分 ---
        Map<String, KnowledgeRetrivalDTO> map = new LinkedHashMap<>();
        for (KnowledgeRetrivalDTO item : list) {
            String key = (item.documentId() == null ? "" : item.documentId().toString()) + "-" +
                    (item.chunkIndex() == null ? "" : item.chunkIndex().toString());
            KnowledgeRetrivalDTO old = map.get(key);
            if (old == null
                    || (item.score() != null && old.score() != null && item.score() > old.score())) {
                map.put(key, item);
            }
        }
        // --- 2. score 降序，截断为 topK ---
        List<KnowledgeRetrivalDTO> sorted = map.values().stream()
                .sorted(Comparator.comparing(
                        KnowledgeRetrivalDTO::score,
                        Comparator.nullsLast(Double::compare))
                        .reversed())
                .collect(Collectors.toList());
        if (sorted.size() <= topK) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(0, topK));
    }

    /**
     * 记录检索明细
     * @param hits 检索命中结果
     * @param userId 用户编号
     * @param conversationId 会话编号
     */
    private void logRagRetrievalHits(List<KnowledgeRetrivalDTO> hits, Long userId, Long conversationId) {
        // 1. 记录检索明细
        int n = hits.size();
        log.info("RAG 检索明细: conversationId={} userId={} hitCount={}", conversationId, userId, n);
        for (int i = 0; i < n; i++) {
            KnowledgeRetrivalDTO r = hits.get(i);
            String full = r.content() == null ? "" : r.content();
            String preview = full.trim();
            int fullLen = preview.length();
            if (preview.length() > RAG_LOG_CONTENT_PREVIEW_MAX) {
                preview = preview.substring(0, RAG_LOG_CONTENT_PREVIEW_MAX) + "...(truncated, totalChars=" + fullLen
                        + ")";
            }
            log.info(
                    "RAG 命中 [{}/{}] vectorId={} documentId={} chunkIndex={} score={} metadata={} contentPreview={}",
                    i + 1, n,
                    r.id(),
                    r.documentId(),
                    r.chunkIndex(),
                    r.score(),
                    r.metadata(),
                    preview);
        }
    }

    /**
     * 丢掉空块，按文档原文顺序重排，再按字符预算截断后拼进 {context}。
     */
    private String buildContext(List<KnowledgeRetrivalDTO> list, int budget) {
        int safeBudget = Math.max(256, budget);
        // --- 1. 丢掉空 content，避免分隔符堆出空白上下文 ---
        List<KnowledgeRetrivalDTO> nonEmpty = new ArrayList<>();
        for (KnowledgeRetrivalDTO item : list) {
            String body = item.content() == null ? "" : item.content().trim();
            if (!body.isEmpty()) {
                nonEmpty.add(item);
            }
        }
        // --- 2. 改回 documentId + chunkIndex，与切块入库顺序一致 ---
        nonEmpty.sort(Comparator
                .comparing(KnowledgeRetrivalDTO::documentId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(KnowledgeRetrivalDTO::chunkIndex, Comparator.nullsLast(Integer::compareTo)));
        List<String> context = new ArrayList<>();
        int remaining = safeBudget;
        if (!nonEmpty.isEmpty()) {
            int perChunkCeiling = Math.max(512, safeBudget / nonEmpty.size());
            // --- 3. 总预算封顶；每块再封顶，避免第一块占满导致后续进不去 ---
            for (KnowledgeRetrivalDTO item : nonEmpty) {
                if (remaining <= 0) {
                    break;
                }
                String body = item.content().trim();
                int maxPiece = Math.min(perChunkCeiling, remaining);
                String piece = body.length() > maxPiece
                        ? body.substring(0, Math.min(maxPiece, body.length())) + "...(truncated)"
                        : body;
                context.add(piece);
                remaining -= piece.length();
            }
        }
        return String.join("\n\n---\n\n", context);
    }
}

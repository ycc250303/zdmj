package com.zdmj.knowledgeService.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zdmj.common.ai.config.RagConfig;
import com.zdmj.common.ai.config.RagConfig.Search;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.prompt.PromptNames;
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

    /** 单条命中写入日志时，文本预览最大字符数（避免超长 content 撑爆日志） */
    private static final int RAG_LOG_CONTENT_PREVIEW_MAX = 500;

    private final EmbeddingModel embeddingModel;
    private final RagConfig ragConfig;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final ChatUtil chatUtil;

    /**
     * 对话 RAG：retrieveRanked 出排序切块 → 无命中退回求职助手 → 有命中注入 {context} 后流式生成。
     * 不写 messages 表。
     */
    @Override
    public Flux<String> streamAnswer(Long userId, Long conversationId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge, Map<String, Object> promptVars) {
        Map<String, Object> chatPromptVars =
                (promptVars == null || promptVars.isEmpty()) ? null : new HashMap<>(promptVars);

        // --- 1. 两库都关：不检索、不改写，直接走求职助手 ---
        if (ragDocumentIds != null && ragDocumentIds.isEmpty() && !useSystemKnowledge) {
            return chatUtil.chatStreamInConversation(userId, conversationId, userMessage, PromptNames.SYSTEM, chatPromptVars);
        }

        // --- 2. 排序检索（改写 / embed / ANN / 合并截断） ---
        KnowledgeRetrievalResponse ranked = retrieveRanked(userId, userMessage, ragDocumentIds, useSystemKnowledge);
        List<KnowledgeRetrivalDTO> hits = ranked.getHits() == null ? List.of() : ranked.getHits();

        // --- 3. 无命中不硬答：空 {context} 会诱导编造 ---
        if (hits.isEmpty()) {
            log.info("RAG 无有效命中，退回求职导师对话: userId={}, useSystemKnowledge={}, queryLen={}",
                    userId, useSystemKnowledge, ranked.getQuery() == null ? 0 : ranked.getQuery().length());
            return chatUtil.chatStreamInConversation(
                    userId, conversationId, ranked.getQuery(), PromptNames.SYSTEM, chatPromptVars);
        }

        // --- 4. 用排序切块拼 {context}，user 消息仍用原文 ---
        logRagRetrievalHits(hits, userId, conversationId);
        Map<String, Object> ragPromptVars = chatPromptVars == null ? new HashMap<>() : new HashMap<>(chatPromptVars);
        ragPromptVars.put("context", buildContext(hits, ragConfig.getSearch().getContextBudget()));
        log.info("RAG 检索命中 {} 条片段，进入生成阶段 conversationId={}", hits.size(), conversationId);
        return chatUtil.chatStreamInConversation(
                userId, conversationId, ranked.getQuery(), PromptNames.KNOWLEDGEBASE_RAG_SYSTEM, ragPromptVars);
    }

    /**
     * 排序层：改写 → 每句一次 embed → 双库 ANN → 去重留高分 → 截断 topK。
     */
    @Override
    public KnowledgeRetrievalResponse retrieveRanked(Long userId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }

        // --- 1. 规范化问句，按句长选 topK / minScore ---
        String query = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");
        Search search = ragConfig.getSearch();
        int topK = resolveTopK(query.length(), search);
        double minScore = query.length() <= search.getShortQueryLength()
                ? search.getMinScoreShort()
                : search.getMinScoreDefault();

        KnowledgeRetrievalResponse response = new KnowledgeRetrievalResponse();
        response.setQuery(query);
        response.setRewrittenQuery(query);
        response.setTopK(topK);
        response.setMinScore(minScore);
        response.setRewriteUsed(false);
        response.setHits(List.of());

        // --- 2. 两库都关：返回元数据，不改写、不检索 ---
        if (ragDocumentIds != null && ragDocumentIds.isEmpty() && !useSystemKnowledge) {
            return response;
        }

        // --- 3. 查询改写；失败或与原文相同则只走一路 ---
        String rewritten = query;
        if (ragConfig.getRewrite().isEnabled() && StringUtils.hasText(query)) {
            rewritten = rewriteQuery(userId, query);
        }
        boolean dualPath = !query.equals(rewritten);
        response.setRewrittenQuery(rewritten);
        response.setRewriteUsed(dualPath);

        // --- 4. 原文 / 改写各 embed 一次（同句复用向量） ---
        String vecRaw = toPgVector(query);
        String vecRewritten = dualPath ? toPgVector(rewritten) : vecRaw;

        // --- 5. 个人库 + 系统库：每个向量各做一次 ANN ---
        List<KnowledgeRetrivalDTO> combined = new ArrayList<>();
        if (ragDocumentIds == null || !ragDocumentIds.isEmpty()) {
            Long userKbId = knowledgeBasesService.getOrCreateKnowledgeBaseId();
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
        response.setHits(mergeAndSort(combined, topK));
        return response;
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

    /** 把用户问题改写成更可检索的单句；空结果或异常一律回退原文。 */
    private String rewriteQuery(Long userId, String rawText) {
        try {
            String queryText = chatUtil.chatOnce(
                    userId,
                    rawText,
                    PromptNames.KNOWLEDGEBASE_RAG_QUERY_REWRITE,
                    Map.of("question", rawText));
            if (!StringUtils.hasText(queryText)) {
                log.info("RAG 查询改写: 模型返回空，沿用原文 | originalQuestion={}", rawText);
                return rawText;
            }
            log.info("RAG 查询改写: originalQuestion={} | rewrittenQuestion={}", rawText, queryText);
            return queryText;
        } catch (Exception e) {
            log.warn("RAG 查询改写失败，使用原文: originalQuestion={}, error={}", rawText, e.getMessage());
            return rawText;
        }
    }

    /** 查询文本 embed 一次并转成 pgvector 字面量；失败返回 null，该路当空命中。 */
    private String toPgVector(String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return null;
        }
        try {
            float[] vector = embeddingModel.embed(queryText);
            if (vector == null) {
                return null;
            }
            return knowledgeEmbeddingService.toPgVector(vector);
        } catch (Exception e) {
            log.warn("查询向量生成失败: {}", e.getMessage());
            return null;
        }
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
            if (hit.getScore() != null && hit.getScore() >= minScore) {
                out.add(hit);
            }
        }
    }

    /** 按 documentId-chunkIndex 去重，同块留更高分，按 score 降序后截断 topK。 */
    private List<KnowledgeRetrivalDTO> mergeAndSort(List<KnowledgeRetrivalDTO> list, int topK) {
        // --- 1. 同 documentId+chunkIndex 留更高分 ---
        Map<String, KnowledgeRetrivalDTO> map = new LinkedHashMap<>();
        for (KnowledgeRetrivalDTO item : list) {
            String key = (item.getDocumentId() == null ? "" : item.getDocumentId().toString()) + "-" +
                    (item.getChunkIndex() == null ? "" : item.getChunkIndex().toString());
            KnowledgeRetrivalDTO old = map.get(key);
            if (old == null
                    || (item.getScore() != null && old.getScore() != null && item.getScore() > old.getScore())) {
                map.put(key, item);
            }
        }
        // --- 2. score 降序，截断为 topK ---
        List<KnowledgeRetrivalDTO> sorted = map.values().stream()
                .sorted(Comparator.comparing(
                        KnowledgeRetrivalDTO::getScore,
                        Comparator.nullsLast(Double::compare))
                        .reversed())
                .collect(Collectors.toList());
        if (sorted.size() <= topK) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(0, topK));
    }

    private void logRagRetrievalHits(List<KnowledgeRetrivalDTO> hits, Long userId, Long conversationId) {
        int n = hits.size();
        log.info("RAG 检索明细: conversationId={} userId={} hitCount={}", conversationId, userId, n);
        for (int i = 0; i < n; i++) {
            KnowledgeRetrivalDTO r = hits.get(i);
            String full = r.getContent() == null ? "" : r.getContent();
            String preview = full.trim();
            int fullLen = preview.length();
            if (preview.length() > RAG_LOG_CONTENT_PREVIEW_MAX) {
                preview = preview.substring(0, RAG_LOG_CONTENT_PREVIEW_MAX) + "...(truncated, totalChars=" + fullLen
                        + ")";
            }
            log.info(
                    "RAG 命中 [{}/{}] vectorId={} documentId={} chunkIndex={} score={} metadata={} contentPreview={}",
                    i + 1, n,
                    r.getId(),
                    r.getDocumentId(),
                    r.getChunkIndex(),
                    r.getScore(),
                    r.getMetadata(),
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
            String body = item.getContent() == null ? "" : item.getContent().trim();
            if (!body.isEmpty()) {
                nonEmpty.add(item);
            }
        }
        // --- 2. 改回 documentId + chunkIndex，与切块入库顺序一致 ---
        nonEmpty.sort(Comparator
                .comparing(KnowledgeRetrivalDTO::getDocumentId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(KnowledgeRetrivalDTO::getChunkIndex, Comparator.nullsLast(Integer::compareTo)));
        List<String> context = new ArrayList<>();
        int remaining = safeBudget;
        if (!nonEmpty.isEmpty()) {
            int perChunkCeiling = Math.max(512, safeBudget / nonEmpty.size());
            // --- 3. 总预算封顶；每块再封顶，避免第一块占满导致后续进不去 ---
            for (KnowledgeRetrivalDTO item : nonEmpty) {
                if (remaining <= 0) {
                    break;
                }
                String body = item.getContent().trim();
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

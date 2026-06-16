package com.zdmj.knowledgeService.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zdmj.common.ai.EmbeddingQuerySupport;
import com.zdmj.common.ai.config.RagConfig;
import com.zdmj.common.ai.config.RagConfig.Search;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
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

    /** 展开「命中文档的全部分块」时的上限，防止单库超大文档拖垮上下文 */
    private static final int RAG_FULL_DOC_CHUNK_CAP = 48;

    private final EmbeddingModel embeddingModel;

    private final RagConfig ragConfig;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final ChatUtil chatUtil;

    public Flux<String> streamAnswer(Long conversationId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge, Map<String, Object> promptVars) {
        Map<String, Object> chatPromptVars = copyPromptVars(promptVars);

        if (!ragConfig.isEnabled()) {
            return chatUtil.chatStreamInConversation(conversationId, userMessage, PromptNames.SYSTEM, chatPromptVars);
        }

        // 显式关闭全部用户文档且未启用系统库：退回普通对话
        if (ragDocumentIds != null && ragDocumentIds.isEmpty() && !useSystemKnowledge) {
            return chatUtil.chatStreamInConversation(conversationId, userMessage, PromptNames.SYSTEM, chatPromptVars);
        }

        Long userId = UserHolder.requireUserId();
        Long userKnowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();

        String rawString = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");

        Search s = ragConfig.getSearch();
        String rewrittenText = rawString;
        if (ragConfig.getRewrite().isEnabled() && rawString.length() > s.getShortQueryLength()) {
            rewrittenText = rewriteQuery(rawString);
        }

        int topK = resolveTopK(rawString.length(), s);
        double minScore = rawString.length() <= s.getShortQueryLength() ? s.getMinScoreShort() : s.getMinScoreDefault();

        List<KnowledgeRetrivalDTO> retrivals = retrieveAndExpand(
                rawString, rewrittenText, userId, userKnowledgeId, topK, minScore, ragDocumentIds, useSystemKnowledge);

        if (retrivals.isEmpty()) {
            log.info("RAG 无有效命中，退回求职导师对话: userId={}, userKnowledgeId={}, useSystemKnowledge={}, rawStringLen={}",
                    userId, userKnowledgeId, useSystemKnowledge, rawString.length());
            return chatUtil.chatStreamInConversation(conversationId, rawString, PromptNames.SYSTEM, chatPromptVars);
        }

        logRagRetrievalHits(retrivals, userId, userKnowledgeId, conversationId);

        String context = buildContext(retrivals, ragConfig.getSearch().getContextBudget());
        Map<String, Object> ragPromptVars = mergeRagPromptVars(context, chatPromptVars);

        log.info("RAG 检索命中 {} 条片段，进入生成阶段 conversationId={}", retrivals.size(), conversationId);
        return chatUtil.chatStreamInConversation(
                conversationId,
                rawString,
                PromptNames.KNOWLEDGEBASE_RAG_SYSTEM,
                ragPromptVars);
    }

    private Map<String, Object> copyPromptVars(Map<String, Object> promptVars) {
        if (promptVars == null || promptVars.isEmpty()) {
            return null;
        }
        return new HashMap<>(promptVars);
    }

    private Map<String, Object> mergeRagPromptVars(String context, Map<String, Object> promptVars) {
        Map<String, Object> merged = promptVars == null ? new HashMap<>() : new HashMap<>(promptVars);
        merged.put("context", context);
        return merged;
    }

    private String rewriteQuery(String rawText) {
        try {
            String queryText = chatUtil.chatOnce(
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

    private float[] embedQueryText(String queryText) {
        return EmbeddingQuerySupport.embedQuery(embeddingModel, queryText);
    }

    private List<KnowledgeRetrivalDTO> retrieveAndExpand(String rawText, String rewrittenText, Long userId,
            Long userKnowledgeId, int topK, double minScore, List<Long> ragDocumentIds, boolean useSystemKnowledge) {
        List<KnowledgeRetrivalDTO> combined = new ArrayList<>();

        if (shouldSearchUserKnowledge(ragDocumentIds)) {
            List<KnowledgeRetrivalDTO> userHits = retrieveForKnowledgeBase(
                    rawText, rewrittenText, userId, userKnowledgeId, topK, minScore, ragDocumentIds);
            combined.addAll(expandHitsToFullDocuments(userId, userKnowledgeId, userHits));
        }

        if (useSystemKnowledge) {
            Long systemKbId = knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode());
            if (systemKbId != null) {
                List<KnowledgeRetrivalDTO> systemHits = retrieveForKnowledgeBase(
                        rawText, rewrittenText, KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, systemKbId,
                        topK, minScore, null);
                combined.addAll(expandHitsToFullDocuments(
                        KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, systemKbId, systemHits));
            } else {
                log.warn("系统知识库未配置（scope=2），跳过系统库检索");
            }
        }

        return mergeAndSort(combined);
    }

    private boolean shouldSearchUserKnowledge(List<Long> ragDocumentIds) {
        return ragDocumentIds == null || !ragDocumentIds.isEmpty();
    }

    private List<KnowledgeRetrivalDTO> retrieveForKnowledgeBase(String rawText, String rewrittenText, Long userId,
            Long knowledgeId, int topK, double minScore, List<Long> ragDocumentIds) {
        List<KnowledgeRetrivalDTO> primary = searchAndFilter(rawText, userId, knowledgeId, topK, minScore,
                ragDocumentIds);
        if (rawText.equals(rewrittenText)) {
            return mergeAndSort(primary);
        }
        List<KnowledgeRetrivalDTO> secondary = searchAndFilter(rewrittenText, userId, knowledgeId, topK, minScore,
                ragDocumentIds);
        List<KnowledgeRetrivalDTO> combined = new ArrayList<>(primary.size() + secondary.size());
        combined.addAll(primary);
        combined.addAll(secondary);
        return mergeAndSort(combined);
    }

    private List<KnowledgeRetrivalDTO> expandHitsToFullDocuments(Long userId, Long knowledgeId,
            List<KnowledgeRetrivalDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Set<Long> docIds = hits.stream()
                .map(KnowledgeRetrivalDTO::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) {
            return hits;
        }
        List<Long> idList = new ArrayList<>(docIds);
        List<KnowledgeRetrivalDTO> expanded = knowledgeVectorMapper.selectChunksByDocuments(userId, knowledgeId,
                idList);
        if (expanded == null || expanded.isEmpty()) {
            return hits;
        }
        if (expanded.size() > RAG_FULL_DOC_CHUNK_CAP) {
            return new ArrayList<>(expanded.subList(0, RAG_FULL_DOC_CHUNK_CAP));
        }
        return expanded;
    }

    private List<KnowledgeRetrivalDTO> searchAndFilter(String queryText, Long userId, Long knowledgeId,
            int topK, double minScore, List<Long> ragDocumentIds) {
        float[] vector = embedQueryText(queryText);
        if (vector == null) {
            return List.of();
        }
        String vecString = knowledgeEmbeddingService.toPgVector(vector);
        List<KnowledgeRetrivalDTO> raw;
        if (ragDocumentIds == null || ragDocumentIds.isEmpty()) {
            raw = knowledgeVectorMapper.searchBySimilarity(userId, knowledgeId, vecString, topK);
        } else {
            raw = knowledgeVectorMapper.searchBySimilarityInDocuments(userId, knowledgeId, ragDocumentIds, vecString,
                    topK);
        }
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(h -> h.getScore() != null && h.getScore() >= minScore)
                .collect(Collectors.toList());
    }

    private int resolveTopK(int queryLen, Search s) {
        if (queryLen <= s.getShortQueryLength()) {
            return s.getTopkShort();
        }
        if (queryLen <= s.getMediumQueryLength()) {
            return s.getTopkMedium();
        }
        return s.getTopkLong();
    }

    private List<KnowledgeRetrivalDTO> mergeAndSort(List<KnowledgeRetrivalDTO> list) {
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
        return map.values().stream()
                .sorted(Comparator.comparing(
                        KnowledgeRetrivalDTO::getScore,
                        Comparator.nullsLast(Double::compare))
                        .reversed())
                .collect(Collectors.toList());
    }

    private void logRagRetrievalHits(List<KnowledgeRetrivalDTO> retrivals, Long userId, Long knowledgeId,
            Long conversationId) {
        int n = retrivals.size();
        log.info("RAG 检索明细: conversationId={} userId={} knowledgeId={} hitCount={}",
                conversationId, userId, knowledgeId, n);
        for (int i = 0; i < n; i++) {
            KnowledgeRetrivalDTO r = retrivals.get(i);
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

    private String buildContext(List<KnowledgeRetrivalDTO> list, int budget) {
        int safeBudget = Math.max(256, budget);
        List<KnowledgeRetrivalDTO> nonEmpty = new ArrayList<>();
        for (KnowledgeRetrivalDTO item : list) {
            String b = item.getContent() == null ? "" : item.getContent().trim();
            if (!b.isEmpty()) {
                nonEmpty.add(item);
            }
        }
        nonEmpty.sort(Comparator
                .comparing(KnowledgeRetrivalDTO::getDocumentId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(KnowledgeRetrivalDTO::getChunkIndex, Comparator.nullsLast(Integer::compareTo)));
        List<String> context = new ArrayList<>();
        int remaining = safeBudget;

        if (!nonEmpty.isEmpty()) {
            int n = nonEmpty.size();
            int perChunkCeiling = Math.max(512, safeBudget / n);
            for (KnowledgeRetrivalDTO item : nonEmpty) {
                if (remaining <= 0) {
                    break;
                }
                String body = item.getContent().trim();
                String header = String.format("[docId=%s chunk=%s score=%.3f]%n",
                        item.getDocumentId(), item.getChunkIndex(),
                        item.getScore() == null ? 0.0 : item.getScore());
                int maxPiece = Math.min(perChunkCeiling, remaining);
                String piece = header + body;
                if (piece.length() > maxPiece) {
                    int maxBody = maxPiece - header.length();
                    if (maxBody < 1) {
                        maxBody = Math.min(body.length(), Math.max(1, remaining - header.length()));
                    }
                    piece = header + body.substring(0, Math.min(maxBody, body.length())) + "...(truncated)";
                }
                context.add(piece);
                remaining -= piece.length();
            }
        }
        return String.join("\n\n---\n\n", context);
    }
}

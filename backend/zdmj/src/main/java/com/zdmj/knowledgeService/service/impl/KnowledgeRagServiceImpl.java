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

    /** 展开「命中文档的全部分块」时的上限，防止单库超大文档拖垮上下文 */
    private static final int RAG_FULL_DOC_CHUNK_CAP = 48;

    private final EmbeddingModel embeddingModel;

    private final RagConfig ragConfig;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final ChatUtil chatUtil;

    private record RankedSearchPlan(String rawText, String rewrittenText, int topK, double minScore, boolean rewriteUsed) {
    }

    /**
     * 对话 RAG：准备查询 → 双库检索并整篇展开 → 拼上下文注入 system → SSE 流式生成。
     * 不写 messages 表；无命中或显式关闭检索时退回普通求职助手 prompt。
     */
    public Flux<String> streamAnswer(Long userId, Long conversationId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge, Map<String, Object> promptVars) {
        Map<String, Object> chatPromptVars = copyPromptVars(promptVars);

        // --- 1. 短路：用户关掉全部个人文档且未开系统库 ---
        if (ragDocumentIds != null && ragDocumentIds.isEmpty() && !useSystemKnowledge) {
            return chatUtil.chatStreamInConversation(userId, conversationId, userMessage, PromptNames.SYSTEM, chatPromptVars);
        }

        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        Long userKnowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();

        // --- 2. 查询侧：空白归一、可选改写、按句长选 topK / minScore ---
        RankedSearchPlan plan = prepareSearch(userId, userMessage);

        // --- 3. 召回：个人库 + 系统库；分数只决定「带哪几篇」，再展开全部分块 ---
        List<KnowledgeRetrivalDTO> retrivals = retrieveAndExpand(
                plan.rawText(), plan.rewrittenText(), userId, userKnowledgeId, plan.topK(), plan.minScore(),
                ragDocumentIds, useSystemKnowledge);

        // --- 4. 无命中不硬答：退回求职导师，避免空 {context} 诱导编造 ---
        if (retrivals.isEmpty()) {
            log.info("RAG 无有效命中，退回求职导师对话: userId={}, userKnowledgeId={}, useSystemKnowledge={}, rawStringLen={}",
                    userId, userKnowledgeId, useSystemKnowledge, plan.rawText().length());
            return chatUtil.chatStreamInConversation(userId, conversationId, plan.rawText(), PromptNames.SYSTEM, chatPromptVars);
        }

        // --- 5. 日志：检索命中明细 ---
        logRagRetrievalHits(retrivals, userId, userKnowledgeId, conversationId);

        // --- 6. 注入：按原文块序 + 字符预算拼 {context}，user 消息仍用原文（改写只服务检索） ---
        String context = buildContext(retrivals, ragConfig.getSearch().getContextBudget());
        Map<String, Object> ragPromptVars = mergeRagPromptVars(context, chatPromptVars);

        log.info("RAG 检索命中 {} 条片段，进入生成阶段 conversationId={}", retrivals.size(), conversationId);
        return chatUtil.chatStreamInConversation(
                userId,
                conversationId,
                plan.rawText(),
                PromptNames.KNOWLEDGEBASE_RAG_SYSTEM,
                ragPromptVars);
    }

    /**
     * 排序层检索（评测 / 调试）：与对话 RAG 同一套改写 + 双路 ANN + 阈值，但不做整篇展开。
     * 展开会把 score 置空，Hit@K / MRR 应对齐本方法返回的命中列表。
     */
    @Override
    public KnowledgeRetrievalResponse retrieveRanked(Long userId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        RankedSearchPlan plan = prepareSearch(userId, userMessage);
        KnowledgeRetrievalResponse response = new KnowledgeRetrievalResponse();
        response.setQuery(plan.rawText());
        response.setRewrittenQuery(plan.rewrittenText());
        response.setTopK(plan.topK());
        response.setMinScore(plan.minScore());
        response.setRewriteUsed(plan.rewriteUsed());

        if (ragDocumentIds != null && ragDocumentIds.isEmpty() && !useSystemKnowledge) {
            response.setHits(List.of());
            return response;
        }

        List<KnowledgeRetrivalDTO> combined = new ArrayList<>();
        if (shouldSearchUserKnowledge(ragDocumentIds)) {
            Long userKnowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();
            combined.addAll(retrieveForKnowledgeBase(
                    plan.rawText(), plan.rewrittenText(), userId, userKnowledgeId, plan.topK(), plan.minScore(),
                    ragDocumentIds));
        }
        if (useSystemKnowledge) {
            Long systemKbId = knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode());
            if (systemKbId != null) {
                combined.addAll(retrieveForKnowledgeBase(
                        plan.rawText(), plan.rewrittenText(), KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, systemKbId,
                        plan.topK(), plan.minScore(), null));
            } else {
                log.warn("系统知识库未配置（scope=2），跳过系统库检索");
            }
        }
        response.setHits(mergeAndSort(combined));
        return response;
    }

    /**
     * 检索入参：规范化原文、条件改写、按句长选择 topK / minScore。
     * 短句不改写（避免语义被扩飞）；改写失败则 rewrittenText 回退原文。
     */
    private RankedSearchPlan prepareSearch(Long userId, String userMessage) {
        String rawString = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");
        Search s = ragConfig.getSearch();
        boolean shouldRewrite = ragConfig.getRewrite().isEnabled() && rawString.length() > s.getShortQueryLength();
        String rewrittenText = shouldRewrite ? rewriteQuery(userId, rawString) : rawString;
        int topK = resolveTopK(rawString.length(), s);
        double minScore = rawString.length() <= s.getShortQueryLength() ? s.getMinScoreShort() : s.getMinScoreDefault();
        boolean rewriteUsed = shouldRewrite && !rawString.equals(rewrittenText);
        return new RankedSearchPlan(rawString, rewrittenText, topK, minScore, rewriteUsed);
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

    /** 把用户问题改写成更可检索的单句；空结果或异常一律回退原文，避免改写拖垮主路径。 */
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

    /** 查询向量与入库同一套 Embedding 模型；失败返回 null，由检索侧当空命中处理。 */
    private float[] embedQueryText(String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return null;
        }
        try {
            return embeddingModel.embed(queryText);
        } catch (Exception e) {
            log.warn("查询向量生成失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 对话侧召回：个人库 / 系统库各自「双查询 ANN + 阈值」后，按命中文档展开全部分块再合并。
     * 系统库不按 ragDocumentIds 过滤（用户只能圈定自己的文档）。
     */
    private List<KnowledgeRetrivalDTO> retrieveAndExpand(String rawText, String rewrittenText, Long userId,
            Long userKnowledgeId, int topK, double minScore, List<Long> ragDocumentIds, boolean useSystemKnowledge) {
        List<KnowledgeRetrivalDTO> combined = new ArrayList<>();

        // --- 1. 个人库：null=全部文档；非空列表=只搜这些 id；空列表由调用方短路，此处不进 ---
        if (shouldSearchUserKnowledge(ragDocumentIds)) {
            List<KnowledgeRetrivalDTO> userHits = retrieveForKnowledgeBase(
                    rawText, rewrittenText, userId, userKnowledgeId, topK, minScore, ragDocumentIds);
            combined.addAll(expandHitsToFullDocuments(userId, userKnowledgeId, userHits));
        }

        // --- 2. 系统库：owner 约定 user_id=0；与个人库简单并集，不按来源加权 ---
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

        // --- 3. 两库块去重（同 documentId+chunkIndex 留高分）；展开后 score 多为 null ---
        return mergeAndSort(combined);
    }

    private boolean shouldSearchUserKnowledge(List<Long> ragDocumentIds) {
        return ragDocumentIds == null || !ragDocumentIds.isEmpty();
    }

    /**
     * 单库双查询召回：原文、改写各搜一遍再按块去重留高分。
     * 改写可能漂移，原文兜底；两路相同则只搜一次。
     */
    private List<KnowledgeRetrivalDTO> retrieveForKnowledgeBase(String rawText, String rewrittenText, Long userId,
            Long knowledgeId, int topK, double minScore, List<Long> ragDocumentIds) {
        // --- 1. 主路：用户原话 embedding 后 ANN + 阈值 ---
        List<KnowledgeRetrivalDTO> primary = searchAndFilter(rawText, userId, knowledgeId, topK, minScore,
                ragDocumentIds);
        if (rawText.equals(rewrittenText)) {
            return mergeAndSort(primary);
        }
        // --- 2. 辅路：改写查询再搜一遍，合并后同块留更高分 ---
        List<KnowledgeRetrivalDTO> secondary = searchAndFilter(rewrittenText, userId, knowledgeId, topK, minScore,
                ragDocumentIds);
        List<KnowledgeRetrivalDTO> combined = new ArrayList<>(primary.size() + secondary.size());
        combined.addAll(primary);
        combined.addAll(secondary);
        return mergeAndSort(combined);
    }

    /**
     * 命中即带全篇：分数只决定「哪些文档够格」，再按 chunk_index 拉该文档全部切块。
     * 避免 Top-K 只拿到标题/目录、漏掉同文档里的职责与成果。展开块 score 为 null。
     */
    private List<KnowledgeRetrivalDTO> expandHitsToFullDocuments(Long userId, Long knowledgeId,
            List<KnowledgeRetrivalDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        // --- 1. 从过阈值的切块收集文档 id（去重） ---
        Set<Long> docIds = hits.stream()
                .map(KnowledgeRetrivalDTO::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) {
            return hits;
        }
        // --- 2. 按 document_id、chunk_index 拉全部分块；失败则退回原命中列表 ---
        List<Long> idList = new ArrayList<>(docIds);
        List<KnowledgeRetrivalDTO> expanded = knowledgeVectorMapper.selectChunksByDocuments(userId, knowledgeId,
                idList);
        if (expanded == null || expanded.isEmpty()) {
            return hits;
        }
        // --- 3. 单库硬顶 48 块：按 SQL 返回顺序截断（文档 id 升序），不是按相关度 ---
        if (expanded.size() > RAG_FULL_DOC_CHUNK_CAP) {
            return new ArrayList<>(expanded.subList(0, RAG_FULL_DOC_CHUNK_CAP));
        }
        return expanded;
    }

    /**
     * 单次向量检索：embed → pgvector 余弦 ANN 取 topK → Java 侧按 minScore 过滤。
     * 阈值不写进 SQL，避免干扰索引形态，也方便按句长切换 0.18 / 0.28。
     */
    private List<KnowledgeRetrivalDTO> searchAndFilter(String queryText, Long userId, Long knowledgeId,
            int topK, double minScore, List<Long> ragDocumentIds) {
        // --- 1. 查询 embed；失败当空命中，不把向量错误抛给用户 ---
        float[] vector = embedQueryText(queryText);
        if (vector == null) {
            return List.of();
        }
        String vecString = knowledgeEmbeddingService.toPgVector(vector);
        // --- 2. ANN：未圈定文档搜整库；否则 document_id IN (...) ---
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
        // --- 3. 相似度阈值：SQL 的 LIMIT 只保证候选数，不够像的在这里丢掉 ---
        return raw.stream()
                .filter(h -> h.getScore() != null && h.getScore() >= minScore)
                .collect(Collectors.toList());
    }

    /** 短句语义弱：topK 更大；长句更具体：topK 收小。 */
    private int resolveTopK(int queryLen, Search s) {
        if (queryLen <= s.getShortQueryLength()) {
            return s.getTopkShort();
        }
        if (queryLen <= s.getMediumQueryLength()) {
            return s.getTopkMedium();
        }
        return s.getTopkLong();
    }

    /**
     * 按 documentId-chunkIndex 去重，同块留更高分，再按 score 降序。
     * 展开后的块 score 为 null，会排到有分块之后；真正进 Prompt 前 {@link #buildContext} 会改按原文顺序。
     */
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

    /**
     * 上下文选择：丢掉空块，按文档原文顺序重排，再按字符预算截断后拼进 {context}。
     * 不用分数排队，是为了让模型通读同一文档的相邻切块，而不是看到打乱的高分片段。
     */
    private String buildContext(List<KnowledgeRetrivalDTO> list, int budget) {
        int safeBudget = Math.max(256, budget);
        // --- 1. 丢掉空 content，避免分隔符堆出空白上下文 ---
        List<KnowledgeRetrivalDTO> nonEmpty = new ArrayList<>();
        for (KnowledgeRetrivalDTO item : list) {
            String b = item.getContent() == null ? "" : item.getContent().trim();
            if (!b.isEmpty()) {
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
            int n = nonEmpty.size();
            int perChunkCeiling = Math.max(512, safeBudget / n);
            // --- 3. 总预算约 12000 字；每块再封顶，避免第一块占满导致后续进不去 ---
            for (KnowledgeRetrivalDTO item : nonEmpty) {
                if (remaining <= 0) {
                    break;
                }
                String body = item.getContent().trim();
                int maxPiece = Math.min(perChunkCeiling, remaining);
                String piece = body;
                if (piece.length() > maxPiece) {
                    piece = body.substring(0, Math.min(maxPiece, body.length())) + "...(truncated)";
                }
                context.add(piece);
                remaining -= piece.length();
            }
        }
        return String.join("\n\n---\n\n", context);
    }
}

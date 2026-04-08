package com.zdmj.knowledgeService.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import com.zdmj.common.config.RagConfig;
import com.zdmj.common.config.RagConfig.Search;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
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

    public Flux<String> streamAnswer(Long conversationId, String userMessage) {
        if (!ragConfig.getRewrite().isEnabled()) {
            // 总开关关闭时退回普通对话
            return chatUtil.chatStream(conversationId, userMessage, PromptUtil.PromptNames.SYSTEM);
        }

        // 1.获取用户ID和知识库ID
        Long userId = UserHolder.requireUserId();
        Long knowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();

        // 2.获取原始文本
        String rawString = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");

        // 3.获取改写后的文本
        String rewrittenText = rawString;
        if (ragConfig.getRewrite().isEnabled()) {
            rewrittenText = rewriteQuery(rawString);
        }

        // 4.提取配置值
        Search s = ragConfig.getSearch();
        int topK = resolveTopK(rawString.length(), s);
        double minScore = rawString.length() <= s.getShortQueryLength() ? s.getMinScoreShort() : s.getMinScoreDefault();

        // 5.检索并过滤（合并原文/改写两路召回；再将涉及文档展开为全部分块，避免 Top-K 漏掉同文档关键段）
        List<KnowledgeRetrivalDTO> retrivals = retrieve(rawString, rewrittenText, userId, knowledgeId, topK, minScore);
        retrivals = expandHitsToFullDocuments(userId, knowledgeId, retrivals);

        if (retrivals.isEmpty()) {
            log.info("RAG 无有效命中: userId={}, knowledgeId={}, rawStringLen={}", userId, knowledgeId, rawString.length());
            return Flux.just("我在你的知识库中没有检索到与问题足够相关的片段，因此无法基于资料作答。你可以尝试换一种问法，或先上传/向量化相关文档。");
        }

        // 6.输出检索命中明细   
        logRagRetrievalHits(retrivals, userId, knowledgeId, conversationId);

        // 7.构建上下文
        String context = buildContext(retrivals, ragConfig.getSearch().getContextBudget());

        // 8.生成答案
        log.info("RAG 检索命中 {} 条片段，进入生成阶段 conversationId={}", retrivals.size(), conversationId);
        return chatUtil.chatStream(
                conversationId,
                rawString,
                PromptUtil.PromptNames.KNOWLEDGEBASE_RAG_SYSTEM,
                Map.of("context", context));
    }

    /**
     * 查询改写
     * 
     * @param rawText 原始文本
     * @return 改写后的文本
     */
    private String rewriteQuery(String rawText) {
        try {
            String queryText = chatUtil.chat(null,
                    rawText,
                    PromptUtil.PromptNames.KNOWLEDGEBASE_RAG_QUERY_REWRITE,
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

    /**
     * 查询向量生成
     * 
     * @param queryText 查询文本
     * @return 查询向量
     */
    private float[] embedQueryText(String queryText) {
        if (queryText == null || queryText.isEmpty()) {
            return null;
        }
        try {
            var options = DashScopeEmbeddingOptions.builder()
                    .textType(DashScopeModel.EmbeddingTextType.QUERY.getValue())
                    .build();

            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of(queryText), options));
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return null;
            }
            return response.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.warn("查询向量生成失败，将降级为无检索: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检索并过滤
     * 
     * @param rawText       原始文本
     * @param rewrittenText 改写后的文本
     * @param userId        用户ID
     * @param knowledgeId   知识库ID
     * @param topK          topK
     * @param minScore      最小分数
     * @return 检索结果
     */
    private List<KnowledgeRetrivalDTO> retrieve(String rawText, String rewrittenText, Long userId, Long knowledgeId,
            int topK, double minScore) {
        List<KnowledgeRetrivalDTO> primary = searchAndFilter(rawText, userId, knowledgeId, topK, minScore);
        if (rawText.equals(rewrittenText)) {
            return mergeAndSort(primary);
        }
        List<KnowledgeRetrivalDTO> secondary = searchAndFilter(rewrittenText, userId, knowledgeId, topK, minScore);
        List<KnowledgeRetrivalDTO> combined = new ArrayList<>(primary.size() + secondary.size());
        combined.addAll(primary);
        combined.addAll(secondary);
        return mergeAndSort(combined);
    }

    /**
     * 对召回结果中出现的每个 document_id，拉取该文档下全部分块（有序），避免仅 Top-K 时漏掉同文档其它段落。
     */
    private List<KnowledgeRetrivalDTO> expandHitsToFullDocuments(Long userId, Long knowledgeId,
            List<KnowledgeRetrivalDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return hits;
        }
        Set<Long> docIds = hits.stream()
                .map(KnowledgeRetrivalDTO::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) {
            return hits;
        }
        List<Long> idList = new ArrayList<>(docIds);
        List<KnowledgeRetrivalDTO> expanded = knowledgeVectorMapper.selectChunksByDocuments(userId, knowledgeId, idList);
        if (expanded == null || expanded.isEmpty()) {
            return hits;
        }
        if (expanded.size() > RAG_FULL_DOC_CHUNK_CAP) {
            return new ArrayList<>(expanded.subList(0, RAG_FULL_DOC_CHUNK_CAP));
        }
        return expanded;
    }

    /**
     * 查询并过滤
     * 
     * @param queryText   查询文本
     * @param userId      用户ID
     * @param knowledgeId 知识库ID
     * @param topK        topK
     * @param minScore    最小分数
     * @return 查询结果
     */
    private List<KnowledgeRetrivalDTO> searchAndFilter(String queryText, Long userId, Long knowledgeId,
            int topK, double minScore) {
        float[] vector = embedQueryText(queryText);
        if (vector == null) {
            return List.of();
        }
        String vecString = knowledgeEmbeddingService.toPgVector(vector);
        List<KnowledgeRetrivalDTO> raw = knowledgeVectorMapper.searchBySimilarity(userId, knowledgeId, vecString, topK);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(h -> h.getScore() != null && h.getScore() >= minScore)
                .collect(Collectors.toList());
    }

    /**
     * 解析 topK
     * 
     * @param queryLen 查询文本长度
     * @param s        配置
     * @return topK
     */
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
     * 合并并排序
     * 
     * @param list 列表
     * @return 合并后的列表
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

    /**
     * 输出 RAG 检索命中明细（向量 id、得分、文本预览、文档/分块与元数据），便于排查召回质量。
     */
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
                preview = preview.substring(0, RAG_LOG_CONTENT_PREVIEW_MAX) + "...(truncated, totalChars=" + fullLen + ")";
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
        // 按原文档顺序拼接：相似度排序会把「后半段/文末摘要」提前，易导致模型只读到项目名称而忽略前文详细块
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

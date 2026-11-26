package com.zdmj.knowledgeService.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
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
import com.zdmj.knowledgeService.api.KnowledgeRetrievalResponse;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.api.KnowledgeHit;
import com.zdmj.knowledgeService.api.KnowledgeReference;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.api.KnowledgeRagService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRagServiceImpl implements KnowledgeRagService {

    private static final String SYSTEM_PROMPT = "system";
    private static final String RAG_PROMPT = "knowledgebase-rag-system";
    private static final String QUERY_REWRITE_PROMPT = "knowledgebase-query-rewrite";

    private static final int REPORT_TOP_K = 8;
    private static final double REPORT_MIN_SCORE = 0.35;
    private static final Set<String> REPORT_CATEGORIES = Set.of("learning_path", "career_planning", "industry_trend");

    private static final String CONTEXT_SEPARATOR = "\n\n---\n\n";

    private final EmbeddingGateway embeddingGateway;
    private final RagConfig ragConfig;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
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
        return Mono.fromCallable(() -> prepareAnswerRequest(actor, conversationId, request,
                        ragDocumentIds, useSystemKnowledge))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(prepared -> modelGateway.stream(actor, prepared));
    }

    /** 在可取消的订阅内准备检索上下文，取消后不再启动回答模型。 */
    private ModelRequest prepareAnswerRequest(CurrentActor actor, Long conversationId, ModelRequest request,
            List<Long> ragDocumentIds, boolean useSystemKnowledge) {
        String prompt = SYSTEM_PROMPT;
        Map<String, Object> variables = request.promptVars() == null
                ? null : new HashMap<>(request.promptVars());
        // null 表示不限用户文档，系统知识库由独立开关控制
        if (ragDocumentIds == null || !ragDocumentIds.isEmpty() || useSystemKnowledge) {
            KnowledgeRetrievalResponse ranked = retrieveRanked(actor, request.message(), ragDocumentIds, useSystemKnowledge, conversationId);
            String context = buildContext(ranked.hits(), ragConfig.getSearch().getContextBudget());
            if (!context.isEmpty()) {
                variables = variables == null ? new HashMap<>() : variables;
                variables.put("context", context);
                prompt = RAG_PROMPT;
            }
            log.info("RAG 上下文完成 conversationId={} userId={} contextChars={} contextBudget={}",
                    conversationId, actor.userId(), context.length(), ragConfig.getSearch().getContextBudget());
        }
        return new ModelRequest(request.message(), prompt, variables,
                request.history(), request.historyNotice());
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
        return retrieveRanked(actor, userMessage, ragDocumentIds, useSystemKnowledge, null);
    }

    private KnowledgeRetrievalResponse retrieveRanked(CurrentActor actor, String userMessage,
            List<Long> ragDocumentIds, boolean useSystemKnowledge, Long conversationId) {
        String operationId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        try {
            KnowledgeRetrievalResponse result = search(actor, userMessage, ragDocumentIds, useSystemKnowledge, operationId);
            log.info("RAG 检索完成 operationId={} userId={} conversationId={} userKnowledge={} systemKnowledge={} rewriteUsed={} topK={} hitCount={} elapsedMs={}",
                    operationId, actor.userId(), conversationId, ragDocumentIds == null || !ragDocumentIds.isEmpty(),
                    useSystemKnowledge, result.rewriteUsed(), result.topK(), result.hits().size(),
                    (System.nanoTime() - started) / 1_000_000);
            for (KnowledgeHit hit : result.hits()) {
                log.debug("RAG 命中 operationId={} vectorId={} documentId={} chunkIndex={} score={}",
                        operationId, hit.id(), hit.documentId(), hit.chunkIndex(), hit.score());
            }
            return result;
        } catch (RuntimeException exception) {
            log.error("RAG 检索失败 operationId={} conversationId={} errorType={} elapsedMs={}",
                    operationId, conversationId, exception.getClass().getName(), (System.nanoTime() - started) / 1_000_000);
            throw exception;
        }
    }

    private KnowledgeRetrievalResponse search(CurrentActor actor, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge, String operationId) {
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
            rewritten = rewriteQuery(actor, query, operationId);
        }
        boolean dualPath = !query.equals(rewritten);

        // --- 4. 原文 / 改写各 embed 一次（同句复用向量） ---
        String vecRaw = toPgVector(actor, query, operationId);
        String vecRewritten = dualPath ? toPgVector(actor, rewritten, operationId) : vecRaw;

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
                mergeAndSort(combined, topK).stream().map(this::publicHit).toList());
    }

    /**
     * 检索本人私有资料及学习路线资料；合并截取后执行分类偏好，远程调用位于事务外。
     *
     * @param actor 已认证操作者
     * @param query 报告模块构造的检索文本
     * @return 最多八条已授权命中；检索故障立即传播
     */
    @Override
    public List<KnowledgeHit> retrieveLearningPaths(CurrentActor actor, String query) {
        java.util.Objects.requireNonNull(actor, "操作者不能为空");
        String operationId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        try {
            String vector = toPgVector(actor, query, operationId);
            List<KnowledgeRetrivalDTO> combined = new ArrayList<>();
            addAnnHits(combined, actor.userId(), knowledgeBasesService.getOrCreateKnowledgeBaseId(actor),
                    null, vector, REPORT_TOP_K, REPORT_MIN_SCORE);
            Long learningBase = knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.LEARNING_PATH.getCode());
            addAnnHits(combined, KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, learningBase, null, vector, REPORT_TOP_K, REPORT_MIN_SCORE);
            List<KnowledgeRetrivalDTO> qualified = mergeAndSort(combined, REPORT_TOP_K);
            List<KnowledgeRetrivalDTO> preferred = qualified.stream()
                    .filter(hit -> hit.docCategory() != null
                            && REPORT_CATEGORIES.contains(hit.docCategory().trim().toLowerCase(Locale.ROOT)))
                    .toList();
            List<KnowledgeHit> result = (preferred.isEmpty() ? qualified : preferred).stream()
                    .map(this::publicHit).toList();
            log.info("报告知识检索完成 operationId={} userId={} hitCount={} elapsedMs={}",
                    operationId, actor.userId(), result.size(), (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (RuntimeException exception) {
            log.error("报告知识检索失败 operationId={} errorType={} elapsedMs={}", operationId,
                    exception.getClass().getName(), (System.nanoTime() - started) / 1_000_000);
            throw exception;
        }
    }

    /**
     * 重新核对来源可见范围；片段和得分保留生成快照，标题及分类缺省时取授权文档。
     *
     * @param actor 已认证操作者
     * @param snapshots 已解析并校验的历史来源
     * @return 按原顺序返回可见引用，缺失或越权文档不参与展示；数据库异常直接传播
     */
    @Override
    public List<KnowledgeReference> resolveReportReferences(CurrentActor actor, List<KnowledgeReference> snapshots) {
        java.util.Objects.requireNonNull(actor, "操作者不能为空");
        if (snapshots.isEmpty()) { return List.of(); }
        Map<Long, KnowledgeReference> visible = knowledgeDocumentMapper.selectReportReferences(actor.userId(),
                        snapshots.stream().map(KnowledgeReference::documentId).distinct().toList())
                .stream().collect(Collectors.toMap(KnowledgeReference::documentId, reference -> reference));
        List<KnowledgeReference> result = new ArrayList<>();
        for (KnowledgeReference snapshot : snapshots) {
            KnowledgeReference current = visible.get(snapshot.documentId());
            if (current == null) { continue; }
            String title = snapshot.title();
            if (!StringUtils.hasText(title) || title.matches("文档\\s*#\\d+")
                    || title.matches("(?s).*(?:user-\\d+/|[/\\\\]|pendingKey|retiredFile).*")) {
                title = displayTitle(current.title(), current.documentId());
            }
            result.add(new KnowledgeReference(snapshot.documentId(), snapshot.chunkIndex(), snapshot.score(),
                    title, snapshot.snippet(), snapshot.docCategory() == null ? current.docCategory() : snapshot.docCategory(),
                    current.downloadPath()));
        }
        return List.copyOf(result);
    }

    private KnowledgeHit publicHit(KnowledgeRetrivalDTO hit) {
        Map<String, Object> metadata = hit.metadata() == null ? Map.of() : hit.metadata();
        String title = metadata.get("documentTitle") instanceof String text ? text : null;
        String source = metadata.get("source") instanceof String text ? text : null;
        String downloadPath = ("/knowledge-document/" + hit.documentId() + "/download").equals(source) ? source : null;
        return new KnowledgeHit(hit.id(), hit.documentId(), hit.chunkIndex(), hit.content(), hit.score(),
                displayTitle(title, hit.documentId()), hit.docCategory(), downloadPath);
    }

    private static String displayTitle(String title, Long documentId) {
        return StringUtils.hasText(title) ? title.trim() : "知识文档 #" + documentId;
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
     * @param operationId 本次检索的日志关联编号
     * @return 改写后的问题
     */
    private String rewriteQuery(CurrentActor actor, String rawText, String operationId) {
        String queryText;
        try {
            queryText = modelGateway.generate(
                    actor,
                    new ModelRequest(rawText, QUERY_REWRITE_PROMPT, Map.of("question", rawText)));
        } catch (BusinessException exception) {
            throw new BusinessException(exception.getErrorCode(), exception.getErrorCode().getMessage(), exception);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("RAG 外部调用失败 operationId={} stage=rewrite errorType={}", operationId, exception.getClass().getName());
            throw new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, exception);
        }
        if (!StringUtils.hasText(queryText)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, "查询改写没有返回可用文本");
        }
        return queryText.trim();
    }

    /**
     * 为检索文本生成向量字面量。文本为空时返回 null，表示这一路没有查询；嵌入失败时终止本次检索。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param queryText 待嵌入的问题
     * @param operationId 本次检索的日志关联编号
     * @return pgvector 字面量；文本为空时返回 null
     */
    private String toPgVector(CurrentActor actor, String queryText, String operationId) {
        if (!StringUtils.hasText(queryText)) {
            return null;
        }
        return KnowledgeEmbeddingServiceImpl.toPgVector(embedQuery(actor, queryText, operationId));
    }

    private float[] embedQuery(CurrentActor actor, String queryText, String operationId) {
        List<float[]> vectors;
        try {
            vectors = embeddingGateway.embed(actor, List.of(queryText));
        } catch (BusinessException exception) {
            throw new BusinessException(exception.getErrorCode(), exception.getErrorCode().getMessage(), exception);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("RAG 外部调用失败 operationId={} stage=embedding errorType={}", operationId, exception.getClass().getName());
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
     * 过滤空块并按原文顺序拼接资料，所有输出共同占用 UTF-16 预算，截断保留完整代理对。
     *
     * @param list 排序层选出的命中
     * @param budget 正数资料预算，包含正文、分隔符及省略号
     * @return 长度不超过预算的上下文，没有可容纳正文时为空
     */
    private String buildContext(List<KnowledgeHit> list, int budget) {
        if (budget <= 0) {
            throw new IllegalArgumentException("资料上下文预算必须为正数");
        }
        List<KnowledgeHit> nonEmpty = list.stream()
                .filter(item -> item.content() != null && !item.content().trim().isEmpty())
                .sorted(Comparator.comparing(KnowledgeHit::documentId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(KnowledgeHit::chunkIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        StringBuilder context = new StringBuilder();
        int perChunkCeiling = Math.max(512, budget / Math.max(1, nonEmpty.size()));
        for (KnowledgeHit item : nonEmpty) {
            String separator = context.isEmpty() ? "" : CONTEXT_SEPARATOR;
            int available = Math.min(perChunkCeiling, budget - context.length() - separator.length());
            if (available <= 0) {
                break;
            }
            String body = item.content().trim();
            boolean truncated = body.length() > available;
            int end = truncated ? available - 1 : body.length();
            if (end > 0 && end < body.length() && Character.isHighSurrogate(body.charAt(end - 1))
                    && Character.isLowSurrogate(body.charAt(end))) {
                end--;
            }
            if (end <= 0) {
                break;
            }
            context.append(separator).append(body, 0, end);
            if (truncated) {
                context.append('…');
            }
        }
        return context.toString();
    }
}

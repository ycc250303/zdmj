package com.zdmj.knowledgeService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.zdmj.aiService.api.EmbeddingGateway;

import com.zdmj.knowledgeService.config.RagConfig;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalResponse;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import reactor.core.publisher.Flux;

class KnowledgeRagServiceImplTest {

    private CurrentActor actor = new CurrentActor(1L);

    private final EmbeddingGateway embeddingGateway = Mockito.mock(EmbeddingGateway.class);
    private final RagConfig ragConfig = new RagConfig();
    private final KnowledgeBasesService knowledgeBasesService = Mockito.mock(KnowledgeBasesService.class);
    private final KnowledgeEmbeddingService knowledgeEmbeddingService = Mockito.mock(KnowledgeEmbeddingService.class);
    private final KnowledgeVectorMapper knowledgeVectorMapper = Mockito.mock(KnowledgeVectorMapper.class);
    private final ModelGateway modelGateway = Mockito.mock(ModelGateway.class);

    @Test
    void streamAnswer_withoutSubscription_shouldNotStartRetrievalOrModel() {
        var service = new KnowledgeRagServiceImpl(embeddingGateway, ragConfig, knowledgeBasesService,
                knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);
        service.streamAnswer(actor, 1L, answerRequest("问题", null), null, true);
        Mockito.verifyNoInteractions(embeddingGateway, knowledgeBasesService,
                knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);
    }

    @Test
    void streamAnswer_cancelDuringRetrieval_shouldNotStartAnswer() throws Exception {
        ragConfig.getRewrite().setEnabled(false);
        var started = new java.util.concurrent.CountDownLatch(1);
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        when(embeddingGateway.embed(any(), any())).thenAnswer(invocation -> {
            started.countDown();
            try {
                if (!new java.util.concurrent.CountDownLatch(1).await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("检索等待取消超时");
                }
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("检索已中断", expected);
            }
            throw new IllegalStateException("检索未中断");
        });
        var service = new KnowledgeRagServiceImpl(embeddingGateway, ragConfig, knowledgeBasesService,
                knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);
        var subscription = service.streamAnswer(actor, 1L, answerRequest("问题", null), null, false)
                .subscribe(ignored -> { }, ignored -> { });
        try {
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            subscription.dispose();
            assertTrue(interrupted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            verify(modelGateway, never()).stream(any(), any());
            Mockito.verifyNoInteractions(knowledgeBasesService, knowledgeVectorMapper);
        } finally {
            subscription.dispose();
        }
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswer_whenEmbeddingFails_shouldStopBeforeAnswer() {
        actor = new CurrentActor(401L);
        ragConfig.getRewrite().setEnabled(false);
        when(embeddingGateway.embed(any(), any())).thenThrow(new RuntimeException("embed fail"));
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.streamAnswer(actor, 13L, answerRequest("hello", null), null, false).collectList().block(java.time.Duration.ofSeconds(5)));

        assertEquals(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED.getCode(), ex.getCode());
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertEquals("embed fail", ex.getCause().getMessage());
        verify(modelGateway, never()).stream(any(), any());
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswer_whenRetrievalMisses_shouldUseSystemPrompt() {
        actor = new CurrentActor(401L);
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(501L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.1,0.2]");
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f, 0.2f}));
        when(knowledgeVectorMapper.searchBySimilarity(401L, 501L, "[0.1,0.2]", ragConfig.getSearch().getTopkMedium()))
                .thenReturn(List.of());
        when(modelGateway.stream(same(actor), eq(answerRequest("hello", "system"))))
                .thenReturn(Flux.just("普通问答回复"));
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        List<String> chunks = service.streamAnswer(actor, 13L, answerRequest("hello", null), null, false).collectList().block();

        assertEquals(List.of("普通问答回复"), chunks);
        verify(knowledgeVectorMapper).searchBySimilarity(401L, 501L, "[0.1,0.2]", ragConfig.getSearch().getTopkMedium());
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswerRagHit_shouldUseRagSystemPromptWithContext() {
        actor = new CurrentActor(402L);
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(502L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.1,0.2]");

        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f, 0.2f}));

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO(1L, 900L, 0, "关键知识片段", 0.9, Map.of("source", "test"), null);

        when(knowledgeVectorMapper.searchBySimilarity(402L, 502L, "[0.1,0.2]", ragConfig.getSearch().getTopkMedium()))
                .thenReturn(List.of(hit));
        when(modelGateway.stream(same(actor), argThat((ModelRequest req) -> "hello question".equals(req.message()) && "knowledgebase-rag-system".equals(req.promptName())
                && req.history().equals(answerRequest("unused", null).history()) && "上一轮生成失败".equals(req.historyNotice()))))
                .thenReturn(Flux.just("rag-answer"));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        List<String> chunks = service.streamAnswer(actor, 14L, answerRequest("hello question", null), null, false).collectList().block();

        assertEquals(List.of("rag-answer"), chunks);
        verify(knowledgeVectorMapper).searchBySimilarity(402L, 502L, "[0.1,0.2]", ragConfig.getSearch().getTopkMedium());
        verify(embeddingGateway).embed(same(actor), eq(List.of("hello question")));
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswerRewriteEnabled_shouldCallRewritePromptThenRag() {
        actor = new CurrentActor(403L);
        ragConfig.getRewrite().setEnabled(true);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(503L);
        when(modelGateway.generate(same(actor), argThat((ModelRequest req) -> "raw question".equals(req.message()) && "knowledgebase-query-rewrite".equals(req.promptName()))))
                .thenReturn("rewritten question");
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.3,0.4]");

        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.3f, 0.4f}));

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO(2L, 901L, 1, "命中内容", 0.91, null, null);

        when(knowledgeVectorMapper.searchBySimilarity(eq(403L), eq(503L), eq("[0.3,0.4]"), anyInt()))
                .thenReturn(List.of(hit));
        when(modelGateway.stream(same(actor), argThat((ModelRequest req) -> "raw question".equals(req.message()) && "knowledgebase-rag-system".equals(req.promptName())
                && req.history().equals(answerRequest("unused", null).history()) && "上一轮生成失败".equals(req.historyNotice()))))
                .thenReturn(Flux.just("rewritten-rag-answer"));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        List<String> chunks = service.streamAnswer(actor, 15L, answerRequest("raw question", null), null, false).collectList().block();

        assertEquals(List.of("rewritten-rag-answer"), chunks);
        verify(embeddingGateway).embed(same(actor), eq(List.of("raw question")));
        verify(embeddingGateway).embed(same(actor), eq(List.of("rewritten question")));
        verify(knowledgeVectorMapper, Mockito.times(2)).searchBySimilarity(eq(403L), eq(503L), eq("[0.3,0.4]"), anyInt());
    }

    @Test
    void streamAnswerEmptyDocumentSelection_shouldUseSystemPrompt() {
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);
        when(modelGateway.stream(same(actor), eq(answerRequest("hello", "system"))))
                .thenReturn(Flux.just("no-rag"));

        List<String> chunks = service.streamAnswer(actor, 16L, answerRequest("hello", null), List.of(), false).collectList().block();

        assertEquals(List.of("no-rag"), chunks);
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswerSystemKnowledgeOnly_shouldSearchSystemKbWhenUserDocsDisabled() {
        actor = new CurrentActor(404L);
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.5,0.6]");
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.5f, 0.6f}));

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO(3L, 902L, 0, "系统库片段", 0.88, null, null);

        when(knowledgeVectorMapper.searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium()))
                .thenReturn(List.of(hit));
        when(modelGateway.stream(same(actor), argThat((ModelRequest req) -> "system only".equals(req.message()) && "knowledgebase-rag-system".equals(req.promptName())
                && req.history().equals(answerRequest("unused", null).history()) && "上一轮生成失败".equals(req.historyNotice()))))
                .thenReturn(Flux.just("system-rag"));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        List<String> chunks = service.streamAnswer(actor, 17L, answerRequest("system only", null), List.of(), true).collectList().block();

        assertEquals(List.of("system-rag"), chunks);
        verify(knowledgeVectorMapper).searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium());
        verify(knowledgeBasesService, never()).getOrCreateKnowledgeBaseId(actor);
        verify(knowledgeVectorMapper, never()).searchBySimilarity(eq(404L), any(), any(), anyInt());
    }

    @Test
    void retrieveRankedActorMissing_shouldFailFast() {
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        assertThrows(NullPointerException.class,
                () -> service.retrieveRanked(null, "hello", List.of(), true));
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    void retrieveRanked_whenRewriteFails_shouldStopBeforeEmbedding() {
        ragConfig.getRewrite().setEnabled(true);
        when(modelGateway.generate(eq(new CurrentActor(405L)), any())).thenThrow(new RuntimeException("rewrite fail"));
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrieveRanked(new CurrentActor(405L), "raw question", null, true));

        assertEquals(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED.getCode(), ex.getCode());
        assertEquals("rewrite fail", ex.getCause().getMessage());
        verify(embeddingGateway, never()).embed(any(), any());
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
        verify(modelGateway, never()).stream(any(), any());
    }

    @Test
    void retrieveRanked_whenRewriteReturnsBlank_shouldStopBeforeEmbedding() {
        ragConfig.getRewrite().setEnabled(true);
        when(modelGateway.generate(eq(new CurrentActor(405L)), any())).thenReturn("  ");
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrieveRanked(new CurrentActor(405L), "raw question", null, true));

        assertEquals(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED.getCode(), ex.getCode());
        assertEquals("查询改写没有返回可用文本", ex.getMessage());
        verify(embeddingGateway, never()).embed(any(), any());
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    void retrieveRankedBothSourcesOff_shouldReturnEmptyHits() {
        ragConfig.getRewrite().setEnabled(true);
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);
        String longQuery = "this is a long enough query to trigger rewrite if search ran";

        KnowledgeRetrievalResponse out = service.retrieveRanked(new CurrentActor(405L), longQuery, List.of(), false);

        assertEquals(List.of(), out.hits());
        assertEquals(longQuery, out.query());
        assertEquals(longQuery, out.rewrittenQuery());
        assertFalse(out.rewriteUsed());
        verify(modelGateway, never()).generate(any(), any());
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("null")
    void retrieveRankedSystemKnowledgeOnly_shouldSearchSystemKb() {
        actor = new CurrentActor(406L);
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.5,0.6]");
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.5f, 0.6f}));

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO(3L, 902L, 0, "系统库片段", 0.88, null, null);

        when(knowledgeVectorMapper.searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium()))
                .thenReturn(List.of(hit));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        KnowledgeRetrievalResponse out = service.retrieveRanked(actor, "system only", List.of(), true);

        assertEquals(1, out.hits().size());
        assertEquals(3L, out.hits().get(0).id());
        assertEquals("system only", out.query());
        assertFalse(out.rewriteUsed());
        assertEquals(ragConfig.getSearch().getTopkMedium(), out.topK());
        verify(knowledgeVectorMapper).searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium());
        verify(knowledgeBasesService, never()).getOrCreateKnowledgeBaseId(actor);
    }

    @Test
    @SuppressWarnings("null")
    void retrieveRankedRewriteEnabled_shouldSearchRawAndRewrittenThenMerge() {
        actor = new CurrentActor(407L);
        ragConfig.getRewrite().setEnabled(true);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(modelGateway.generate(same(actor), argThat((ModelRequest req) -> "raw question".equals(req.message()) && "knowledgebase-query-rewrite".equals(req.promptName()))))
                .thenReturn("rewritten question");
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.3,0.4]");
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.3f, 0.4f}));

        KnowledgeRetrivalDTO rawHit = new KnowledgeRetrivalDTO(21L, 901L, 0, null, 0.80, null, null);

        KnowledgeRetrivalDTO rewrittenHit = new KnowledgeRetrivalDTO(22L, 901L, 1, null, 0.91, null, null);

        when(knowledgeVectorMapper.searchBySimilarity(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(11L), eq("[0.3,0.4]"), anyInt()))
                .thenReturn(List.of(rawHit))
                .thenReturn(List.of(rewrittenHit));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        KnowledgeRetrievalResponse out = service.retrieveRanked(actor, "raw question", List.of(), true);

        assertTrue(out.rewriteUsed());
        assertEquals("raw question", out.query());
        assertEquals("rewritten question", out.rewrittenQuery());
        assertEquals(2, out.hits().size());
        assertEquals(22L, out.hits().get(0).id());
        verify(knowledgeVectorMapper, Mockito.times(2)).searchBySimilarity(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(11L), eq("[0.3,0.4]"), anyInt());
    }

    @Test
    @SuppressWarnings("null")
    void retrieveRankedShortQuery_shouldRewriteBecauseDualPathKeepsOriginal() {
        actor = new CurrentActor(408L);
        ragConfig.getRewrite().setEnabled(true);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(modelGateway.generate(same(actor), argThat((ModelRequest req) -> "Java".equals(req.message()) && "knowledgebase-query-rewrite".equals(req.promptName()))))
                .thenReturn("Java 后端开发");
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.3,0.4]");
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.3f, 0.4f}));

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO(31L, 901L, 0, null, 0.85, null, null);

        when(knowledgeVectorMapper.searchBySimilarity(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(11L), eq("[0.3,0.4]"), anyInt()))
                .thenReturn(List.of(hit));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        KnowledgeRetrievalResponse out = service.retrieveRanked(actor, "Java", List.of(), true);

        assertTrue(out.rewriteUsed());
        assertEquals("Java", out.query());
        assertEquals("Java 后端开发", out.rewrittenQuery());
        verify(knowledgeVectorMapper, Mockito.times(2)).searchBySimilarity(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(11L), eq("[0.3,0.4]"), anyInt());
    }

    @Test
    @SuppressWarnings("null")
    void retrieveRankedMergeOverTopK_shouldTruncateToTopK() {
        actor = new CurrentActor(409L);
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.1,0.2]");
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f, 0.2f}));

        int topK = ragConfig.getSearch().getTopkMedium();
        List<KnowledgeRetrivalDTO> overflow = new ArrayList<>();
        for (int i = 0; i < topK + 3; i++) {
            KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO((long) i, 900L, i, null, 1.0 - i * 0.01, null, null);

            overflow.add(hit);
        }
        when(knowledgeVectorMapper.searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.1,0.2]", topK))
                .thenReturn(overflow);

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingGateway, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);

        KnowledgeRetrievalResponse out = service.retrieveRanked(actor, "hello question", List.of(), true);

        assertEquals(topK, out.hits().size());
        assertEquals(0L, out.hits().get(0).id());
        assertEquals((long) (topK - 1), out.hits().get(topK - 1).id());
        verify(embeddingGateway).embed(same(actor), eq(List.of("hello question")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 8, 256, 12000})
    void 上下文预算覆盖分隔符标记并保留完整字符(int budget) {
        String longText = "材料😀".repeat(4000);
        String context = captureContext(budget, List.of(
                hit(2L, 2, longText), hit(1L, 0, "第一段"), hit(2L, 1, longText), hit(3L, 0, "  ")));
        assertTrue(context.length() <= budget);
        assertFalse(context.endsWith("\n\n---\n\n"));
        assertFalse(context.equals("…"));
        for (int i = 0; i < context.length(); i++) {
            char c = context.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertTrue(i + 1 < context.length() && Character.isLowSurrogate(context.charAt(++i)));
            } else {
                assertFalse(Character.isLowSurrogate(c));
            }
        }
        if (budget >= 256) {
            assertTrue(context.startsWith("第一段\n\n---\n\n"));
            assertTrue(context.endsWith("…"));
        }
    }

    @Test
    void 上下文恰好容纳时保留全文且空内容不选用资料提示词() {
        assertEquals("甲", captureContext(1, List.of(hit(1L, 0, "甲"))));
        assertEquals("甲\n\n---\n\n乙", captureContext(9, List.of(hit(2L, 0, "乙"), hit(1L, 0, "甲"))));
        assertEquals("甲乙…", captureContext(3, List.of(hit(1L, 0, "甲乙丙丁"))));
        assertEquals("", captureContext(1, List.of(hit(1L, 0, "😀甲"))));
        assertEquals("", captureContext(256, List.of(hit(1L, 0, "  "), hit(2L, 0, null))));
    }

    @Test
    void 检索成功和外部失败日志仅包含安全字段并保留异常原因() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var ragLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(KnowledgeRagServiceImpl.class);
        var oldLevel = ragLogger.getLevel();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.list = new java.util.concurrent.CopyOnWriteArrayList<>();
        appender.start();
        logger.addAppender(appender);
        ragLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        String secret = "敏感问题与供应商响应标识";
        try {
            ragConfig.getRewrite().setEnabled(true);
            when(modelGateway.generate(any(), any())).thenReturn("敏感改写标识");
            when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f}));
            when(knowledgeEmbeddingService.toPgVector(any())).thenReturn("[0.1]");
            when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(1L);
            when(knowledgeVectorMapper.searchBySimilarity(any(), any(), any(), anyInt()))
                    .thenReturn(List.of(new KnowledgeRetrivalDTO(1L, 1L, 0, "敏感文档标识", 0.9,
                            Map.of("pendingKey", "敏感文件标识"), null)));
            when(modelGateway.stream(any(), any())).thenReturn(Flux.just("回复"));
            var service = new KnowledgeRagServiceImpl(embeddingGateway, ragConfig, knowledgeBasesService,
                    knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);
            service.streamAnswer(actor, 1L, answerRequest(secret, null), null, false).collectList().block();
            for (boolean rewriteFailure : List.of(true, false)) {
                ragConfig.getRewrite().setEnabled(rewriteFailure);
                RuntimeException cause = new RuntimeException(secret);
                if (rewriteFailure) {
                    when(modelGateway.generate(any(), any())).thenThrow(cause);
                } else {
                    when(embeddingGateway.embed(any(), any())).thenThrow(cause);
                }
                BusinessException error = assertThrows(BusinessException.class,
                        () -> service.retrieveRanked(actor, secret, null, false));
                org.junit.jupiter.api.Assertions.assertSame(cause, error.getCause());
            }
            var providerFailure = new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED, secret);
            Mockito.doThrow(providerFailure).when(embeddingGateway).embed(any(), any());
            BusinessException wrapped = assertThrows(BusinessException.class,
                    () -> service.retrieveRanked(actor, secret, null, false));
            assertEquals(providerFailure.getErrorCode(), wrapped.getErrorCode());
            org.junit.jupiter.api.Assertions.assertSame(providerFailure, wrapped.getCause());
            assertEquals(ErrorCode.USER_LLM_NOT_CONFIGURED.getMessage(), wrapped.getMessage());
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage()
                    .startsWith("RAG 检索完成 operationId=")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.DEBUG
                    && event.getFormattedMessage().startsWith("RAG 命中 operationId=")));
            for (var event : appender.list) {
                String rendered = event.getFormattedMessage();
                if (event.getThrowableProxy() != null) {
                    rendered += ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy());
                }
                org.assertj.core.api.Assertions.assertThat(rendered)
                        .doesNotContain(secret, "敏感改写标识", "敏感文档标识", "敏感文件标识", "pendingKey");
            }
        } finally {
            ragLogger.setLevel(oldLevel);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 检索内部不变量错误直接传播() {
        var service = new KnowledgeRagServiceImpl(embeddingGateway, ragConfig, knowledgeBasesService,
                knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway);
        var defect = new IllegalStateException("内部结果不满足约束");
        when(modelGateway.generate(any(), any())).thenThrow(defect);
        org.junit.jupiter.api.Assertions.assertSame(defect, assertThrows(IllegalStateException.class,
                () -> service.retrieveRanked(actor, "问题", null, false)));
        ragConfig.getRewrite().setEnabled(false);
        when(embeddingGateway.embed(any(), any())).thenThrow(defect);
        org.junit.jupiter.api.Assertions.assertSame(defect, assertThrows(IllegalStateException.class,
                () -> service.retrieveRanked(actor, "问题", null, false)));
    }

    private String captureContext(int budget, List<KnowledgeRetrivalDTO> hits) {
        ragConfig.getRewrite().setEnabled(false);
        ragConfig.getSearch().setContextBudget(budget);
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f}));
        when(knowledgeEmbeddingService.toPgVector(any())).thenReturn("[0.1]");
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(1L);
        when(knowledgeVectorMapper.searchBySimilarity(any(), any(), any(), anyInt())).thenReturn(hits);
        var captured = new java.util.concurrent.atomic.AtomicReference<ModelRequest>();
        when(modelGateway.stream(any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return Flux.just("回复");
        });
        new KnowledgeRagServiceImpl(embeddingGateway, ragConfig, knowledgeBasesService,
                knowledgeEmbeddingService, knowledgeVectorMapper, modelGateway)
                .streamAnswer(actor, 1L, answerRequest("问题", null), null, false).collectList().block();
        ModelRequest request = captured.get();
        String context = request.promptVars() == null ? "" : (String) request.promptVars().get("context");
        assertEquals(context.isEmpty() ? "system" : "knowledgebase-rag-system", request.promptName());
        return context;
    }

    private static KnowledgeRetrivalDTO hit(long documentId, int chunk, String text) {
        return new KnowledgeRetrivalDTO(documentId * 10 + chunk, documentId, chunk, text, 0.9, null, null);
    }

    private static ModelRequest answerRequest(String message, String prompt) {
        return new ModelRequest(message, prompt, null,
                List.of(new ModelRequest.HistoryMessage(ModelRequest.HistoryMessage.Role.USER, "之前的任务"),
                        new ModelRequest.HistoryMessage(ModelRequest.HistoryMessage.Role.ASSISTANT, "部分回复")),
                "上一轮生成失败");
    }
}

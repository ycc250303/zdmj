package com.zdmj.knowledgeService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;

import com.zdmj.common.ai.config.RagConfig;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalResponse;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import reactor.core.publisher.Flux;

class KnowledgeRagServiceImplTest {

    private final EmbeddingModel embeddingModel = Mockito.mock(EmbeddingModel.class);
    private final RagConfig ragConfig = new RagConfig();
    private final KnowledgeBasesService knowledgeBasesService = Mockito.mock(KnowledgeBasesService.class);
    private final KnowledgeEmbeddingService knowledgeEmbeddingService = Mockito.mock(KnowledgeEmbeddingService.class);
    private final KnowledgeVectorMapper knowledgeVectorMapper = Mockito.mock(KnowledgeVectorMapper.class);
    private final ChatUtil chatUtil = Mockito.mock(ChatUtil.class);

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void streamAnswerNotLogin_shouldThrowUserNotLogin() {
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.streamAnswer(null, 12L, "hello", null, false, null));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(chatUtil, never()).chatStreamInConversation(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswerEmptyHitsFallback_shouldUseSystemPrompt() {
        UserHolder.set(UserContext.of(401L, "u"));
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(501L);
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("embed fail"));
        when(chatUtil.chatStreamInConversation(401L, 13L, "hello", PromptNames.SYSTEM, null))
                .thenReturn(Flux.just("fallback-system"));
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        List<String> chunks = service.streamAnswer(401L, 13L, "hello", null, false, null).collectList().block();

        assertEquals(List.of("fallback-system"), chunks);
        verify(chatUtil).chatStreamInConversation(401L, 13L, "hello", PromptNames.SYSTEM, null);
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswerRagHit_shouldUseRagSystemPromptWithContext() {
        UserHolder.set(UserContext.of(402L, "u"));
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(502L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.1,0.2]");

        when(embeddingModel.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f});

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO();
        hit.setId(1L);
        hit.setDocumentId(900L);
        hit.setChunkIndex(0);
        hit.setScore(0.9);
        hit.setContent("关键知识片段");
        hit.setMetadata(Map.of("source", "test"));
        when(knowledgeVectorMapper.searchBySimilarity(402L, 502L, "[0.1,0.2]", ragConfig.getSearch().getTopkMedium()))
                .thenReturn(List.of(hit));
        when(knowledgeVectorMapper.selectChunksByDocuments(eq(402L), eq(502L), any()))
                .thenReturn(List.of(hit));
        when(chatUtil.chatStreamInConversation(eq(402L), eq(14L), eq("hello question"),
                eq(PromptNames.KNOWLEDGEBASE_RAG_SYSTEM), any()))
                .thenReturn(Flux.just("rag-answer"));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        List<String> chunks = service.streamAnswer(402L, 14L, "hello question", null, false, null).collectList().block();

        assertEquals(List.of("rag-answer"), chunks);
        verify(chatUtil).chatStreamInConversation(eq(402L), eq(14L), eq("hello question"),
                eq(PromptNames.KNOWLEDGEBASE_RAG_SYSTEM), any());
        verify(knowledgeVectorMapper).searchBySimilarity(402L, 502L, "[0.1,0.2]", ragConfig.getSearch().getTopkMedium());
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswerRewriteEnabled_shouldCallRewritePromptThenRag() {
        UserHolder.set(UserContext.of(403L, "u"));
        ragConfig.getRewrite().setEnabled(true);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(503L);
        when(chatUtil.chatOnce(eq(403L), eq("raw question"), eq(PromptNames.KNOWLEDGEBASE_RAG_QUERY_REWRITE), any()))
                .thenReturn("rewritten question");
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.3,0.4]");

        when(embeddingModel.embed(anyString())).thenReturn(new float[] {0.3f, 0.4f});

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO();
        hit.setId(2L);
        hit.setDocumentId(901L);
        hit.setChunkIndex(1);
        hit.setScore(0.91);
        hit.setContent("命中内容");
        when(knowledgeVectorMapper.searchBySimilarity(eq(403L), eq(503L), eq("[0.3,0.4]"), anyInt()))
                .thenReturn(List.of(hit));
        when(knowledgeVectorMapper.selectChunksByDocuments(eq(403L), eq(503L), any()))
                .thenReturn(List.of(hit));
        when(chatUtil.chatStreamInConversation(eq(403L), eq(15L), eq("raw question"),
                eq(PromptNames.KNOWLEDGEBASE_RAG_SYSTEM), any()))
                .thenReturn(Flux.just("rewritten-rag-answer"));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        List<String> chunks = service.streamAnswer(403L, 15L, "raw question", null, false, null).collectList().block();

        assertEquals(List.of("rewritten-rag-answer"), chunks);
        verify(chatUtil).chatOnce(eq(403L), eq("raw question"), eq(PromptNames.KNOWLEDGEBASE_RAG_QUERY_REWRITE), any());
        verify(chatUtil).chatStreamInConversation(eq(403L), eq(15L), eq("raw question"),
                eq(PromptNames.KNOWLEDGEBASE_RAG_SYSTEM), any());
    }

    @Test
    void streamAnswerEmptyDocumentSelection_shouldFallbackSystemPrompt() {
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);
        when(chatUtil.chatStreamInConversation(1L, 16L, "hello", PromptNames.SYSTEM, null))
                .thenReturn(Flux.just("no-rag"));

        List<String> chunks = service.streamAnswer(1L, 16L, "hello", List.of(), false, null).collectList().block();

        assertEquals(List.of("no-rag"), chunks);
        verify(chatUtil).chatStreamInConversation(1L, 16L, "hello", PromptNames.SYSTEM, null);
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("null")
    void streamAnswerSystemKnowledgeOnly_shouldSearchSystemKbWhenUserDocsDisabled() {
        UserHolder.set(UserContext.of(404L, "u"));
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(504L);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.5,0.6]");
        when(embeddingModel.embed(anyString())).thenReturn(new float[] {0.5f, 0.6f});

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO();
        hit.setId(3L);
        hit.setDocumentId(902L);
        hit.setChunkIndex(0);
        hit.setScore(0.88);
        hit.setContent("系统库片段");
        when(knowledgeVectorMapper.searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium()))
                .thenReturn(List.of(hit));
        when(knowledgeVectorMapper.selectChunksByDocuments(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(11L), any()))
                .thenReturn(List.of(hit));
        when(chatUtil.chatStreamInConversation(eq(404L), eq(17L), eq("system only"),
                eq(PromptNames.KNOWLEDGEBASE_RAG_SYSTEM), any()))
                .thenReturn(Flux.just("system-rag"));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        List<String> chunks = service.streamAnswer(404L, 17L, "system only", List.of(), true, null).collectList().block();

        assertEquals(List.of("system-rag"), chunks);
        verify(knowledgeVectorMapper).searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium());
        verify(knowledgeVectorMapper, never()).searchBySimilarity(eq(404L), eq(504L), any(), anyInt());
    }

    @Test
    void retrieveRankedNotLogin_shouldThrowUserNotLogin() {
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrieveRanked(null, "hello", List.of(), true));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
    }

    @Test
    void retrieveRankedBothSourcesOff_shouldReturnEmptyHits() {
        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        KnowledgeRetrievalResponse out = service.retrieveRanked(405L, "hello", List.of(), false);

        assertEquals(List.of(), out.getHits());
        verify(knowledgeVectorMapper, never()).searchBySimilarity(any(), any(), any(), anyInt());
        verify(knowledgeVectorMapper, never()).selectChunksByDocuments(any(), any(), any());
    }

    @Test
    @SuppressWarnings("null")
    void retrieveRankedSystemKnowledgeOnly_shouldSearchSystemKbWithoutExpand() {
        UserHolder.set(UserContext.of(406L, "u"));
        ragConfig.getRewrite().setEnabled(false);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.5,0.6]");
        when(embeddingModel.embed(anyString())).thenReturn(new float[] {0.5f, 0.6f});

        KnowledgeRetrivalDTO hit = new KnowledgeRetrivalDTO();
        hit.setId(3L);
        hit.setDocumentId(902L);
        hit.setChunkIndex(0);
        hit.setScore(0.88);
        hit.setContent("系统库片段");
        when(knowledgeVectorMapper.searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium()))
                .thenReturn(List.of(hit));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        KnowledgeRetrievalResponse out = service.retrieveRanked(406L, "system only", List.of(), true);

        assertEquals(1, out.getHits().size());
        assertEquals(3L, out.getHits().get(0).getId());
        assertEquals("system only", out.getQuery());
        assertFalse(out.isRewriteUsed());
        assertEquals(ragConfig.getSearch().getTopkMedium(), out.getTopK());
        verify(knowledgeVectorMapper).searchBySimilarity(
                KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, 11L, "[0.5,0.6]", ragConfig.getSearch().getTopkMedium());
        verify(knowledgeBasesService, never()).getOrCreateKnowledgeBaseId();
        verify(knowledgeVectorMapper, never()).selectChunksByDocuments(any(), any(), any());
    }

    @Test
    @SuppressWarnings("null")
    void retrieveRankedRewriteEnabled_shouldSearchRawAndRewrittenThenMerge() {
        UserHolder.set(UserContext.of(407L, "u"));
        ragConfig.getRewrite().setEnabled(true);
        when(knowledgeBasesService.findKnowledgeBaseIdByScope(KnowledgeScopeEnum.SYSTEM.getCode())).thenReturn(11L);
        when(chatUtil.chatOnce(eq(407L), eq("raw question"), eq(PromptNames.KNOWLEDGEBASE_RAG_QUERY_REWRITE), any()))
                .thenReturn("rewritten question");
        when(knowledgeEmbeddingService.toPgVector(any(float[].class))).thenReturn("[0.3,0.4]");
        when(embeddingModel.embed(anyString())).thenReturn(new float[] {0.3f, 0.4f});

        KnowledgeRetrivalDTO rawHit = new KnowledgeRetrivalDTO();
        rawHit.setId(21L);
        rawHit.setDocumentId(901L);
        rawHit.setChunkIndex(0);
        rawHit.setScore(0.80);
        KnowledgeRetrivalDTO rewrittenHit = new KnowledgeRetrivalDTO();
        rewrittenHit.setId(22L);
        rewrittenHit.setDocumentId(901L);
        rewrittenHit.setChunkIndex(1);
        rewrittenHit.setScore(0.91);
        when(knowledgeVectorMapper.searchBySimilarity(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(11L), eq("[0.3,0.4]"), anyInt()))
                .thenReturn(List.of(rawHit))
                .thenReturn(List.of(rewrittenHit));

        KnowledgeRagServiceImpl service = new KnowledgeRagServiceImpl(
                embeddingModel, ragConfig, knowledgeBasesService, knowledgeEmbeddingService, knowledgeVectorMapper, chatUtil);

        KnowledgeRetrievalResponse out = service.retrieveRanked(407L, "raw question", List.of(), true);

        assertTrue(out.isRewriteUsed());
        assertEquals("raw question", out.getQuery());
        assertEquals("rewritten question", out.getRewrittenQuery());
        assertEquals(2, out.getHits().size());
        assertEquals(22L, out.getHits().get(0).getId());
        verify(chatUtil).chatOnce(eq(407L), eq("raw question"), eq(PromptNames.KNOWLEDGEBASE_RAG_QUERY_REWRITE), any());
        verify(knowledgeVectorMapper, Mockito.times(2)).searchBySimilarity(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(11L), eq("[0.3,0.4]"), anyInt());
        verify(knowledgeVectorMapper, never()).selectChunksByDocuments(any(), any(), any());
    }
}

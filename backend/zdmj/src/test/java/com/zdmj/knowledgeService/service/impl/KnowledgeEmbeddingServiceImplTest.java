package com.zdmj.knowledgeService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;

class KnowledgeEmbeddingServiceImplTest {

    private final TextSplitter textSplitter = Mockito.mock(TextSplitter.class);
    private final EmbeddingModel embeddingModel = Mockito.mock(EmbeddingModel.class);
    private final KnowledgeBasesService knowledgeBasesService = Mockito.mock(KnowledgeBasesService.class);
    private final KnowledgeDocumentMapper knowledgeDocumentMapper = Mockito.mock(KnowledgeDocumentMapper.class);
    private final KnowledgeVectorMapper knowledgeVectorMapper = Mockito.mock(KnowledgeVectorMapper.class);
    private final PdfParserUtil pdfParserUtil = Mockito.mock(PdfParserUtil.class);

    @Test
    void vectorizeAndStoreNotFound_shouldThrowKnowledgeBaseNotFound() {
        when(knowledgeDocumentMapper.selectById(999L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(999L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper).selectById(999L);
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void vectorizeAndStoreUnsupportedType_shouldThrowEmbeddingFailed() {
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(100L);
        kd.setUserId(1L);
        kd.setKnowledgeId(500L);
        kd.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        kd.setContent("https://github.com/acme/repo");
        when(knowledgeDocumentMapper.selectById(100L)).thenReturn(kd);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(100L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper, Mockito.atLeastOnce()).updateById(kd);
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(100L, 1L);
    }

    @Test
    void deleteVectorsSuccess_shouldResetDocumentEmbeddingFields() {
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(8101L);
        kd.setUserId(1L);
        kd.setChunkCount(12);
        kd.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.SUCCESS.getCode());
        when(knowledgeDocumentMapper.selectById(8101L)).thenReturn(kd);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil);

        service.deleteVectors(8101L);

        assertEquals(0, kd.getChunkCount());
        assertEquals(KnowledgeVectorTaskStatusEnum.PENDING.getCode(), kd.getEmbeddingStatus());
        assertNull(kd.getLastEmbeddedAt());
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(8101L, 1L);
        verify(knowledgeDocumentMapper).updateById(kd);
    }

    @Test
    void deleteVectorsNotFound_shouldThrowKnowledgeBaseNotFound() {
        when(knowledgeDocumentMapper.selectById(8201L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteVectors(8201L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void toPgVectorNullOrEmpty_shouldReturnNull() {
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil);

        assertNull(service.toPgVector(null));
        assertNull(service.toPgVector(new float[0]));
    }

    @Test
    void toPgVectorNonEmpty_shouldReturnBracketFormat() {
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil);

        String vector = service.toPgVector(new float[] {1.0f, -2.5f, 3.25f});

        assertTrue(vector.startsWith("["));
        assertTrue(vector.endsWith("]"));
        assertTrue(vector.contains("1.0"));
        assertTrue(vector.contains("-2.5"));
        assertTrue(vector.contains("3.25"));
    }

    @Test
    void deleteVectorsWhenMapperThrows_shouldThrowKnowledgeBaseDeleteFailed() {
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(8301L);
        kd.setUserId(1L);
        when(knowledgeDocumentMapper.selectById(8301L)).thenReturn(kd);
        Mockito.doThrow(new RuntimeException("db down"))
                .when(knowledgeVectorMapper).deleteByDocumentIdAndUserId(8301L, 1L);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteVectors(8301L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED.getCode(), ex.getCode());
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(8301L, 1L);
    }

}

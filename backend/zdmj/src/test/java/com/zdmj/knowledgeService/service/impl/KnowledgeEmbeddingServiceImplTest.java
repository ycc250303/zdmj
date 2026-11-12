package com.zdmj.knowledgeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.springframework.ai.document.Document;
import com.zdmj.aiService.api.EmbeddingGateway;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeEmbeddingStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;

class KnowledgeEmbeddingServiceImplTest {

    private final CurrentActor actor = new CurrentActor(1L);

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
    }

    private final TextSplitter textSplitter = Mockito.mock(TextSplitter.class);
    private final EmbeddingGateway embeddingGateway = Mockito.mock(EmbeddingGateway.class);
    private final KnowledgeDocumentMapper knowledgeDocumentMapper = Mockito.mock(KnowledgeDocumentMapper.class);
    private final KnowledgeVectorMapper knowledgeVectorMapper = Mockito.mock(KnowledgeVectorMapper.class);
    private final PdfParserUtil pdfParserUtil = Mockito.mock(PdfParserUtil.class);

    @Test
    void vectorizeAndStoreNotFound_shouldThrowKnowledgeBaseNotFound() {
        when(knowledgeDocumentMapper.selectById(999L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(actor, 999L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper).selectById(999L);
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void 文档属于其他用户_拒绝向量化与删除且不读取文件或调用模型() {
        KnowledgeDocument document = projectDocument(115L, "https://cdn.example.com/private.pdf");
        document.setUserId(2L);
        when(knowledgeDocumentMapper.selectById(115L)).thenReturn(document);
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException vectorize = assertThrows(BusinessException.class,
                () -> service.vectorizeAndStore(actor, 115L));
        BusinessException delete = assertThrows(BusinessException.class,
                () -> service.deleteVectors(actor, 115L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), vectorize.getCode());
        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), delete.getCode());
        verify(knowledgeDocumentMapper, never()).update(isNull(), any());
        Mockito.verifyNoInteractions(pdfParserUtil, textSplitter, embeddingGateway, knowledgeVectorMapper);
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
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(actor, 100L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper, times(2)).update(isNull(), any());
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(100L, 1L);
        verify(embeddingGateway, never()).embed(any(), anyList());
    }

    @Test
    void vectorizeAndStore_shouldEmbedOutsideTransactionAndSaveInside() {
        KnowledgeDocument kd = projectDocument(110L, "https://cdn.example.com/a.pdf");
        when(knowledgeDocumentMapper.selectById(110L)).thenReturn(kd);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(knowledgeDocumentMapper.updateById(kd)).thenReturn(1);
        AtomicBoolean inWrite = new AtomicBoolean(false);
        when(pdfParserUtil.extractTextFromUrl(Mockito.same(actor), Mockito.eq(kd.getContent()))).thenAnswer(invocation -> {
            assertFalse(inWrite.get());
            return "项目说明";
        });
        when(textSplitter.apply(any())).thenReturn(List.of(new Document("项目说明")));
        when(embeddingGateway.embed(any(), anyList())).thenAnswer(invocation -> {
            assertFalse(inWrite.get());
            return List.of(new float[] {0.25f});
        });
        when(knowledgeVectorMapper.batchInsert(any())).thenReturn(1);
        KnowledgeEmbeddingServiceImpl service = newService(writingTemplate(inWrite));

        service.vectorizeAndStore(actor, 110L);

        assertEquals(KnowledgeEmbeddingStatusEnum.SUCCESS.getCode(), kd.getEmbeddingStatus());
        assertEquals(1, kd.getChunkCount());
        InOrder order = inOrder(pdfParserUtil, embeddingGateway, knowledgeVectorMapper);
        order.verify(pdfParserUtil).extractTextFromUrl(Mockito.same(actor), Mockito.eq(kd.getContent()));
        order.verify(embeddingGateway).embed(Mockito.same(actor), anyList());
        order.verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(110L, 1L);
        order.verify(knowledgeVectorMapper).batchInsert(any());
    }

    @Test
    void vectorizeAndStore_whenEmbedFails_shouldMarkFailedWithoutDeletingVectors() {
        KnowledgeDocument kd = projectDocument(120L, "https://cdn.example.com/b.pdf");
        when(knowledgeDocumentMapper.selectById(120L)).thenReturn(kd);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(pdfParserUtil.extractTextFromUrl(new CurrentActor(1L), kd.getContent())).thenReturn("正文");
        when(textSplitter.apply(any())).thenReturn(List.of(new Document("正文")));
        doThrow(new RuntimeException("embed down")).when(embeddingGateway).embed(any(), anyList());
        KnowledgeEmbeddingServiceImpl service = newService();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.vectorizeAndStore(actor, 120L));

        assertEquals("embed down", ex.getMessage());
        verify(knowledgeDocumentMapper, times(2)).update(isNull(), any());
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(120L, 1L);
        verify(knowledgeVectorMapper, never()).batchInsert(any());
    }

    @Test
    void vectorizeAndStore_whenContentChangesBeforeSave_shouldNotReplaceVectors() {
        KnowledgeDocument loaded = projectDocument(130L, "https://cdn.example.com/old.pdf");
        KnowledgeDocument changed = projectDocument(130L, "https://cdn.example.com/new.pdf");
        when(knowledgeDocumentMapper.selectById(130L)).thenReturn(loaded, changed);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(pdfParserUtil.extractTextFromUrl(new CurrentActor(1L), loaded.getContent())).thenReturn("旧正文");
        when(textSplitter.apply(any())).thenReturn(List.of(new Document("旧正文")));
        doReturn(List.of(new float[] {0.5f})).when(embeddingGateway).embed(any(), anyList());
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(actor, 130L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(), ex.getCode());
        assertEquals("文档内容已变化", ex.getMessage());
        verify(knowledgeVectorMapper, never()).batchInsert(any());
        verify(embeddingGateway).embed(any(), anyList());
    }

    @Test
    void deleteVectorsSuccess_shouldResetDocumentEmbeddingFields() {
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(8101L);
        kd.setUserId(1L);
        kd.setChunkCount(12);
        kd.setEmbeddingStatus(KnowledgeEmbeddingStatusEnum.SUCCESS.getCode());
        when(knowledgeDocumentMapper.selectById(8101L)).thenReturn(kd);
        KnowledgeEmbeddingServiceImpl service = newService();

        service.deleteVectors(actor, 8101L);

        assertEquals(0, kd.getChunkCount());
        assertEquals(KnowledgeEmbeddingStatusEnum.PENDING.getCode(), kd.getEmbeddingStatus());
        assertNull(kd.getLastEmbeddedAt());
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(8101L, 1L);
        verify(knowledgeDocumentMapper).updateById(kd);
    }

    @Test
    void deleteVectorsNotFound_shouldThrowKnowledgeBaseNotFound() {
        when(knowledgeDocumentMapper.selectById(8201L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteVectors(actor, 8201L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void toPgVectorNullOrEmpty_shouldReturnNull() {
        KnowledgeEmbeddingServiceImpl service = newService();

        assertNull(service.toPgVector(null));
        assertNull(service.toPgVector(new float[0]));
    }

    @Test
    void toPgVectorNonEmpty_shouldReturnBracketFormat() {
        KnowledgeEmbeddingServiceImpl service = newService();

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
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteVectors(actor, 8301L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED.getCode(), ex.getCode());
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(8301L, 1L);
    }

    private KnowledgeEmbeddingServiceImpl newService() {
        return newService(executingTemplate());
    }

    private KnowledgeEmbeddingServiceImpl newService(TransactionTemplate transactionTemplate) {
        return new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingGateway, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil, transactionTemplate);
    }

    private static TransactionTemplate executingTemplate() {
        PlatformTransactionManager manager = Mockito.mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(Mockito.mock(TransactionStatus.class));
        return new TransactionTemplate(manager);
    }

    private static TransactionTemplate writingTemplate(AtomicBoolean inWrite) {
        PlatformTransactionManager manager = Mockito.mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(Mockito.mock(TransactionStatus.class));
        return new TransactionTemplate(manager) {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                inWrite.set(true);
                try {
                    return super.execute(action);
                } finally {
                    inWrite.set(false);
                }
            }
        };
    }

    private static KnowledgeDocument projectDocument(Long id, String content) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setUserId(1L);
        document.setKnowledgeId(500L);
        document.setType(KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode());
        document.setContent(content);
        return document;
    }

}

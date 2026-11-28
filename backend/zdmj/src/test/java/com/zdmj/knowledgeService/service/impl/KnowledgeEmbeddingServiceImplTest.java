package com.zdmj.knowledgeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
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
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.knowledgeService.config.KnowledgeDocumentConfig;
import java.io.InputStream;
import java.io.IOException;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
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
    private final FileUploadService fileUploadService = Mockito.mock(FileUploadService.class);
    private final InputStream input = Mockito.mock(InputStream.class);
    private final PdfParserUtil pdfParserUtil = Mockito.mock(PdfParserUtil.class);
    private final KnowledgeDocumentConfig documentConfig = new KnowledgeDocumentConfig(200_000);

    @Test
    void vectorizeAndStoreNotFound_shouldThrowDocumentNotFound() {
        when(knowledgeDocumentMapper.selectById(999L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(actor, 999L));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.getCode(), ex.getCode());
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

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.getCode(), vectorize.getCode());
        verify(knowledgeDocumentMapper, never()).update(isNull(), any());
        Mockito.verifyNoInteractions(pdfParserUtil, textSplitter, embeddingGateway, knowledgeVectorMapper);
    }

    @Test
    void vectorizeAndStoreUnsupportedType_shouldThrowEmbeddingFailed() {
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(100L);
        kd.setUserId(1L);
        kd.setScope(KnowledgeScopeEnum.USER.getCode());
        kd.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        kd.setContent("https://github.com/acme/repo");
        when(knowledgeDocumentMapper.selectById(100L)).thenReturn(kd);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(actor, 100L));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_EMBEDDING_FAILED.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper, times(2)).update(isNull(), any());
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(100L, 1L);
        verify(embeddingGateway, never()).embed(any(), anyList());
    }

    @Test
    void vectorizeAndStore_shouldEmbedOutsideTransactionAndSaveInside() throws Exception {
        KnowledgeDocument kd = projectDocument(110L, "https://cdn.example.com/a.pdf");
        when(knowledgeDocumentMapper.selectById(110L)).thenReturn(kd);
        when(knowledgeDocumentMapper.selectOwnedForUpdate(110L, 1L)).thenReturn(kd);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        AtomicBoolean inWrite = new AtomicBoolean(false);
        when(fileUploadService.openInputStream(actor, kd.getContent())).thenAnswer(invocation -> {
            assertFalse(inWrite.get());
            return input;
        });
        when(pdfParserUtil.extractText(input, documentConfig.maxExtractedChars())).thenAnswer(invocation -> {
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
        InOrder order = inOrder(fileUploadService, pdfParserUtil, input, embeddingGateway, knowledgeVectorMapper);
        order.verify(fileUploadService).openInputStream(Mockito.same(actor), Mockito.eq(kd.getContent()));
        order.verify(pdfParserUtil).extractText(input, documentConfig.maxExtractedChars());
        order.verify(input).close();
        order.verify(embeddingGateway).embed(Mockito.same(actor), anyList());
        order.verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(110L, 1L);
        order.verify(knowledgeVectorMapper).batchInsert(any());
    }

    @Test
    void vectorizeAndStore_whenEmbedFails_shouldMarkFailedWithoutDeletingVectors() throws Exception {
        KnowledgeDocument kd = projectDocument(120L, "https://cdn.example.com/b.pdf");
        when(knowledgeDocumentMapper.selectById(120L)).thenReturn(kd);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(fileUploadService.openInputStream(actor, kd.getContent())).thenReturn(input);
        when(pdfParserUtil.extractText(input, documentConfig.maxExtractedChars())).thenReturn("正文");
        when(textSplitter.apply(any())).thenReturn(java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> new Document("正文" + index)).toList());
        when(embeddingGateway.embed(any(), anyList()))
                .thenReturn(java.util.stream.IntStream.range(0, 10).mapToObj(index -> new float[] {0.5f}).toList())
                .thenThrow(new RuntimeException("embed down"));
        KnowledgeEmbeddingServiceImpl service = newService();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.vectorizeAndStore(actor, 120L));

        assertEquals("embed down", ex.getMessage());
        verify(knowledgeDocumentMapper, times(2)).update(isNull(), any());
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(120L, 1L);
        verify(knowledgeVectorMapper, never()).batchInsert(any());
        verify(embeddingGateway, times(2)).embed(any(), anyList());
    }

    @Test
    void vectorizeAndStore_whenContentChangesBeforeSave_shouldNotReplaceVectors() throws Exception {
        KnowledgeDocument loaded = projectDocument(130L, "https://cdn.example.com/old.pdf");
        KnowledgeDocument changed = projectDocument(130L, "https://cdn.example.com/new.pdf");
        when(knowledgeDocumentMapper.selectById(130L)).thenReturn(loaded);
        when(knowledgeDocumentMapper.selectOwnedForUpdate(130L, 1L)).thenReturn(changed);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(fileUploadService.openInputStream(actor, loaded.getContent())).thenReturn(input);
        when(pdfParserUtil.extractText(input, documentConfig.maxExtractedChars())).thenReturn("旧正文");
        when(textSplitter.apply(any())).thenReturn(List.of(new Document("旧正文")));
        doReturn(List.of(new float[] {0.5f})).when(embeddingGateway).embed(any(), anyList());
        KnowledgeEmbeddingServiceImpl service = newService();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(actor, 130L));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_EMBEDDING_FAILED.getCode(), ex.getCode());
        assertEquals("文档内容已变化", ex.getMessage());
        verify(knowledgeVectorMapper, never()).batchInsert(any());
        verify(embeddingGateway).embed(any(), anyList());
    }

    @Test
    void 原文件读取失败返回存储故障且不调用嵌入模型() throws Exception {
        KnowledgeDocument document = projectDocument(140L, "user-1/original.md");
        when(knowledgeDocumentMapper.selectById(140L)).thenReturn(document);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(fileUploadService.openInputStream(actor, document.getContent())).thenReturn(input);
        IOException cause = new IOException("read failed");
        when(pdfParserUtil.extractText(input, documentConfig.maxExtractedChars())).thenThrow(cause);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> newService().vectorizeAndStore(actor, 140L));

        assertEquals(ErrorCode.FILE_STORAGE_UNAVAILABLE, failure.getErrorCode());
        assertEquals(cause, failure.getCause());
        verify(input).close();
        Mockito.verifyNoInteractions(textSplitter, embeddingGateway, knowledgeVectorMapper);
        verify(knowledgeDocumentMapper, times(2)).update(isNull(), any());
    }

    @Test
    void 文本超过容量立即失败且不调用嵌入模型() throws Exception {
        KnowledgeDocument document = projectDocument(150L, "user-1/over-limit.md");
        when(knowledgeDocumentMapper.selectById(150L)).thenReturn(document);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(fileUploadService.openInputStream(actor, document.getContent())).thenReturn(input);
        BusinessException cause = new BusinessException(ErrorCode.VALIDATION_ERROR, "文档提取文本超过允许长度：200000");
        when(pdfParserUtil.extractText(input, documentConfig.maxExtractedChars())).thenThrow(cause);

        assertEquals(cause, assertThrows(BusinessException.class,
                () -> newService().vectorizeAndStore(actor, 150L)));

        verify(input).close();
        Mockito.verifyNoInteractions(textSplitter, embeddingGateway, knowledgeVectorMapper);
        verify(knowledgeDocumentMapper, times(2)).update(isNull(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 2})
    void 向量写入数量异常_立即终止且不保存成功状态(int count) throws Exception {
        KnowledgeDocument document = projectDocument(110L, "user-1/test.md");
        when(knowledgeDocumentMapper.selectById(110L)).thenReturn(document);
        when(knowledgeDocumentMapper.selectOwnedForUpdate(110L, 1L)).thenReturn(document);
        when(knowledgeDocumentMapper.update(isNull(), any())).thenReturn(1);
        when(fileUploadService.openInputStream(actor, document.getContent())).thenReturn(input);
        when(pdfParserUtil.extractText(input, documentConfig.maxExtractedChars())).thenReturn("文档");
        when(textSplitter.apply(any())).thenReturn(List.of(new Document("文档")));
        when(embeddingGateway.embed(any(), anyList())).thenReturn(List.of(new float[]{0.25f}));
        when(knowledgeVectorMapper.batchInsert(any())).thenReturn(count);

        var failure = assertThrows(IllegalStateException.class,
                () -> newService().vectorizeAndStore(actor, 110L));

        assertTrue(failure.getMessage().contains("向量写入数量"));
        assertNull(document.getLastEmbeddedAt());
        verify(knowledgeDocumentMapper, times(2)).update(isNull(), any());
    }

    @Test
    void 向量转换验证空值有限数值及完整格式() {
        assertEquals("[1.0,-2.5,3.25]", KnowledgeEmbeddingServiceImpl.toPgVector(new float[] {1, -2.5f, 3.25f}));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeEmbeddingServiceImpl.toPgVector(null));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeEmbeddingServiceImpl.toPgVector(new float[0]));
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> KnowledgeEmbeddingServiceImpl.toPgVector(new float[] {invalid}));
        }
    }

    private KnowledgeEmbeddingServiceImpl newService() {
        return newService(executingTemplate());
    }

    private KnowledgeEmbeddingServiceImpl newService(TransactionTemplate transactionTemplate) {
        return new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingGateway, knowledgeDocumentMapper, knowledgeVectorMapper,
                pdfParserUtil, fileUploadService, transactionTemplate, documentConfig, java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
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
        document.setScope(KnowledgeScopeEnum.USER.getCode());
        document.setType(KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode());
        document.setContent(content);
        document.setFileStatus(1);
        document.setEmbeddingStatus(2);
        return document;
    }

    private static final String SOURCE = "https://github.com/Snailclimb/JavaGuide/blob/" + "a".repeat(40) + "/docs/java/test.md";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 两种来源按同一批量规则保存关联来源和片段顺序(boolean system) {
        var rows = rows(501);
        List<List<KnowledgeVector>> saved = new ArrayList<>();
        when(knowledgeVectorMapper.deleteSystemByDocumentId(42L)).thenReturn(1);
        when(knowledgeVectorMapper.batchInsert(anyList())).thenAnswer(call -> {
            List<KnowledgeVector> batch = call.getArgument(0);
            saved.add(List.copyOf(batch));
            return batch.size();
        });

        replace(system, rows);

        assertThat(saved).extracting(List::size).containsExactly(500, 1);
        assertThat(saved.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(rows);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getDocumentId()).isEqualTo(42L);
            assertThat(row.getMetadata()).isEqualTo(system ? Map.of("source", SOURCE)
                    : Map.of("DocumentId", "42", "source", "/knowledge-document/42/download"));
        });
        assertThat(rows).extracting(KnowledgeVector::getChunkIndex).containsExactlyElementsOf(IntStream.range(0, 501).boxed().toList());
        var order = inOrder(knowledgeVectorMapper);
        if (system) {
            order.verify(knowledgeVectorMapper).deleteSystemByDocumentId(42L);
            verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(any(), any());
        } else {
            order.verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(42L, 7L);
            verify(knowledgeVectorMapper, never()).deleteSystemByDocumentId(any());
        }
        order.verify(knowledgeVectorMapper, times(2)).batchInsert(anyList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 第二批写入数量异常立即终止后续批次(boolean system) {
        when(knowledgeVectorMapper.deleteSystemByDocumentId(42L)).thenReturn(1);
        when(knowledgeVectorMapper.batchInsert(anyList())).thenReturn(500, 0);

        assertThatThrownBy(() -> replace(system, rows(1001))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("向量写入数量");
        verify(knowledgeVectorMapper, times(2)).batchInsert(anyList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 数据库异常保留原始原因并立即终止(boolean system) {
        when(knowledgeVectorMapper.deleteSystemByDocumentId(42L)).thenReturn(1);
        var failure = new org.springframework.dao.DataAccessResourceFailureException("测试数据库故障");
        when(knowledgeVectorMapper.batchInsert(anyList())).thenThrow(failure);

        assertThatThrownBy(() -> replace(system, rows(501))).isSameAs(failure);
        verify(knowledgeVectorMapper).batchInsert(anyList());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2})
    void 系统旧片段删除数量异常时禁止插入(int deleted) {
        when(knowledgeVectorMapper.deleteSystemByDocumentId(42L)).thenReturn(deleted);
        assertThatThrownBy(() -> KnowledgeEmbeddingServiceImpl.replaceSystem(knowledgeVectorMapper, 42L, 1, SOURCE, rows(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("删除数量");
        verify(knowledgeVectorMapper, never()).batchInsert(anyList());
    }

    @Test
    void 新增系统文档没有旧片段时直接写入() {
        when(knowledgeVectorMapper.batchInsert(anyList())).thenReturn(1);
        KnowledgeEmbeddingServiceImpl.replaceSystem(knowledgeVectorMapper, 42L, 0, SOURCE, rows(1));
        verify(knowledgeVectorMapper, never()).deleteSystemByDocumentId(any());
        verify(knowledgeVectorMapper).batchInsert(anyList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 待写入片段为空时禁止删除或插入(boolean system) {
        assertThatThrownBy(() -> replace(system, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> replace(system, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(knowledgeVectorMapper);
    }

    private void replace(boolean system, List<KnowledgeVector> rows) {
        if (system) { KnowledgeEmbeddingServiceImpl.replaceSystem(knowledgeVectorMapper, 42L, 1, SOURCE, rows); }
        else { KnowledgeEmbeddingServiceImpl.replaceUser(knowledgeVectorMapper, 42L, 7L, rows); }
    }

    private static List<KnowledgeVector> rows(int count) {
        return IntStream.range(0, count).mapToObj(index -> {
            var row = new KnowledgeVector();
            row.setDocumentId(99L);
            row.setMetadata(Map.of("source", "待替换来源"));
            row.setChunkIndex(index);
            return row;
        }).toList();
    }
}

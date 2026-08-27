package com.zdmj.knowledgeService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskTypeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;

class KnowledgeEmbeddingServiceImplTest {

    private final TextSplitter textSplitter = Mockito.mock(TextSplitter.class);
    private final EmbeddingModel embeddingModel = Mockito.mock(EmbeddingModel.class);
    private final KnowledgeBasesService knowledgeBasesService = Mockito.mock(KnowledgeBasesService.class);
    private final KnowledgeDocumentMapper knowledgeDocumentMapper = Mockito.mock(KnowledgeDocumentMapper.class);
    private final KnowledgeVectorMapper knowledgeVectorMapper = Mockito.mock(KnowledgeVectorMapper.class);
    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper = Mockito.mock(KnowledgeVectorTaskMapper.class);
    private final PdfParserUtil pdfParserUtil = Mockito.mock(PdfParserUtil.class);

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void submitVectorizeTaskCreateSuccess_shouldInsertPendingTask() {
        UserHolder.set(UserContext.of(301L, "u"));
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(401L);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);
        when(knowledgeVectorTaskMapper.insert(Mockito.any(KnowledgeVectorTask.class))).thenAnswer(invocation -> {
            KnowledgeVectorTask task = invocation.getArgument(0);
            task.setId(901L);
            return 1;
        });

        Long taskId = service.submitVectorizeTask(555L);

        assertEquals(901L, taskId);
        verify(knowledgeVectorTaskMapper).insert(Mockito.argThat(task ->
                task.getStatus().equals(KnowledgeVectorTaskStatusEnum.PENDING.getCode())
                        && task.getDocumentId().equals(555L)
                        && task.getKnowledgeId().equals(401L)));
    }

    @Test
    void vectorizeAndStoreNotFound_shouldThrowKnowledgeBaseNotFound() {
        when(knowledgeDocumentMapper.selectById(999L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(999L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper).selectById(999L);
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void executeTaskAsyncUnknownType_shouldMarkTaskFailed() {
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);
        KnowledgeVectorTask task = new KnowledgeVectorTask();
        task.setId(88L);
        task.setDocumentId(123L);
        task.setUserId(77L);
        task.setTaskType(999);
        when(knowledgeVectorTaskMapper.claimPendingTask(88L)).thenReturn(1);
        when(knowledgeVectorTaskMapper.selectById(88L)).thenReturn(task);

        service.executeTaskAsync(88L);

        verify(knowledgeVectorTaskMapper).claimPendingTask(88L);
        verify(knowledgeVectorTaskMapper).markTaskFailed(Mockito.eq(88L), Mockito.anyString());
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
                knowledgeVectorTaskMapper, pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.vectorizeAndStore(100L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper, Mockito.atLeastOnce()).updateById(kd);
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(100L, 1L);
    }

    @Test
    void submitDeleteTaskCreateSuccess_shouldInsertPendingDeleteTask() {
        UserHolder.set(UserContext.of(302L, "u"));
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(402L);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);
        when(knowledgeVectorTaskMapper.insert(Mockito.any(KnowledgeVectorTask.class))).thenAnswer(invocation -> {
            KnowledgeVectorTask task = invocation.getArgument(0);
            task.setId(902L);
            return 1;
        });

        Long taskId = service.submitDeleteTask(556L);

        assertEquals(902L, taskId);
        verify(knowledgeVectorTaskMapper).insert(Mockito.argThat(task ->
                task.getTaskType().equals(KnowledgeVectorTaskTypeEnum.DELETE.getCode())
                        && task.getStatus().equals(KnowledgeVectorTaskStatusEnum.PENDING.getCode())));
    }

    @Test
    void executeTaskAsyncWhenClaimFailed_shouldReturnWithoutSelectOrMark() {
        when(knowledgeVectorTaskMapper.claimPendingTask(7001L)).thenReturn(0);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);

        service.executeTaskAsync(7001L);

        verify(knowledgeVectorTaskMapper, never()).selectById(Mockito.anyLong());
        verify(knowledgeVectorTaskMapper, never()).markTaskSuccess(Mockito.anyLong());
        verify(knowledgeVectorTaskMapper, never()).markTaskFailed(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void executeTaskAsyncTaskMissingAfterClaim_shouldReturnWithoutMark() {
        KnowledgeVectorTask task = new KnowledgeVectorTask();
        task.setId(7002L);
        when(knowledgeVectorTaskMapper.claimPendingTask(7002L)).thenReturn(1);
        when(knowledgeVectorTaskMapper.selectById(7002L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);

        service.executeTaskAsync(7002L);

        verify(knowledgeVectorTaskMapper).claimPendingTask(7002L);
        verify(knowledgeVectorTaskMapper).selectById(7002L);
        verify(knowledgeVectorTaskMapper, never()).markTaskSuccess(Mockito.anyLong());
        verify(knowledgeVectorTaskMapper, never()).markTaskFailed(Mockito.anyLong(), Mockito.anyString());
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
                knowledgeVectorTaskMapper, pdfParserUtil);

        service.deleteVectors(8101L);

        assertEquals(0, kd.getChunkCount());
        assertEquals(KnowledgeVectorTaskStatusEnum.PENDING.getCode(), kd.getEmbeddingStatus());
        assertNull(kd.getLastEmbeddedAt());
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(8101L, 1L);
        verify(knowledgeDocumentMapper).updateById(kd);
    }

    @Test
    void submitVectorizeTaskNotLogin_shouldThrowUserNotLogin() {
        UserHolder.clear();
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submitVectorizeTask(991L));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(knowledgeVectorTaskMapper, never()).insert(Mockito.any(KnowledgeVectorTask.class));
    }

    @Test
    void deleteVectorsNotFound_shouldThrowKnowledgeBaseNotFound() {
        when(knowledgeDocumentMapper.selectById(8201L)).thenReturn(null);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteVectors(8201L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeVectorMapper, never()).deleteByDocumentIdAndUserId(Mockito.anyLong(), Mockito.anyLong());
    }

    @Test
    void resumePendingTasks_shouldDispatchAllPendingTaskIds() {
        KnowledgeVectorTask t1 = new KnowledgeVectorTask();
        t1.setId(9001L);
        KnowledgeVectorTask t2 = new KnowledgeVectorTask();
        t2.setId(9002L);
        when(knowledgeVectorTaskMapper.selectList(Mockito.any())).thenReturn(List.of(t1, t2));
        KnowledgeEmbeddingServiceImpl service = Mockito.spy(new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil));
        Mockito.doNothing().when(service).executeTaskAsync(Mockito.anyLong());

        service.resumePendingTasks();

        verify(service).executeTaskAsync(9001L);
        verify(service).executeTaskAsync(9002L);
    }

    @Test
    void toPgVectorNullOrEmpty_shouldReturnNull() {
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);

        assertNull(service.toPgVector(null));
        assertNull(service.toPgVector(new float[0]));
    }

    @Test
    void toPgVectorNonEmpty_shouldReturnBracketFormat() {
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);

        String vector = service.toPgVector(new float[] {1.0f, -2.5f, 3.25f});

        assertTrue(vector.startsWith("["));
        assertTrue(vector.endsWith("]"));
        assertTrue(vector.contains("1.0"));
        assertTrue(vector.contains("-2.5"));
        assertTrue(vector.contains("3.25"));
    }

    @Test
    void executeTaskAsyncDeleteTask_shouldMarkSuccess() {
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                textSplitter, embeddingModel, knowledgeBasesService, knowledgeDocumentMapper, knowledgeVectorMapper,
                knowledgeVectorTaskMapper, pdfParserUtil);
        KnowledgeVectorTask task = new KnowledgeVectorTask();
        task.setId(8801L);
        task.setDocumentId(120L);
        task.setUserId(1L);
        task.setTaskType(KnowledgeVectorTaskTypeEnum.DELETE.getCode());
        when(knowledgeVectorTaskMapper.claimPendingTask(8801L)).thenReturn(1);
        when(knowledgeVectorTaskMapper.selectById(8801L)).thenReturn(task);
        when(knowledgeDocumentMapper.selectById(120L)).thenReturn(null);

        service.executeTaskAsync(8801L);

        verify(knowledgeVectorTaskMapper).claimPendingTask(8801L);
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(120L, 1L);
        verify(knowledgeVectorTaskMapper).markTaskSuccess(8801L);
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
                knowledgeVectorTaskMapper, pdfParserUtil);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteVectors(8301L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED.getCode(), ex.getCode());
        verify(knowledgeVectorMapper).deleteByDocumentIdAndUserId(8301L, 1L);
    }

}

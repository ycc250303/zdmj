package com.zdmj.knowledgeService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

class KnowledgeDocumentServiceImplTest {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper = Mockito.mock(KnowledgeDocumentMapper.class);
    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper = Mockito.mock(KnowledgeVectorTaskMapper.class);
    private final KnowledgeBasesService knowledgeBasesService = Mockito.mock(KnowledgeBasesService.class);
    private final KnowledgeEmbeddingService knowledgeEmbeddingService = Mockito.mock(KnowledgeEmbeddingService.class);
    private final FileUploadService fileUploadService = Mockito.mock(FileUploadService.class);

    @AfterEach
    void tearDown() {
        UserHolder.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createSuccess_shouldSetPendingAndSubmitTask() {
        UserHolder.set(UserContext.of(201L, "u"));
        TransactionSynchronizationManager.initSynchronization();
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(501L);
        when(knowledgeEmbeddingService.submitVectorizeTask(301L)).thenReturn(701L);
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(0L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doAnswer(invocation -> {
            KnowledgeDocument arg = invocation.getArgument(0);
            arg.setId(301L);
            return true;
        }).when(service).save(any(KnowledgeDocument.class));
        doReturn(true).when(service).updateById(any(KnowledgeDocument.class));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("repo");

        KnowledgeDocumentResponse result = service.create(dto);

        assertNotNull(result);
        assertEquals(KnowledgeVectorTaskStatusEnum.PENDING.getCode(), result.getEmbeddingStatus());
        verify(service).save(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService).submitVectorizeTask(result.getId());
        verify(service).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void createDuplicateContent_shouldThrowKnowledgeDocumentContentExists() {
        UserHolder.set(UserContext.of(217L, "u"));
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(530L);
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(1L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("repo");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_EXISTS.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    void createInvalidUrl_shouldThrowUrlFormatError() {
        UserHolder.set(UserContext.of(202L, "u"));
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("not-a-url");
        dto.setTitle("bad");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService, never()).submitVectorizeTask(any(Long.class));
    }

    @Test
    void createSaveFail_shouldThrowKnowledgeDocumentCreateFailed() {
        UserHolder.set(UserContext.of(210L, "u"));
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(520L);
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(0L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(false).when(service).save(any(KnowledgeDocument.class));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("repo");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_CREATE_FAILED.getCode(), ex.getCode());
        verify(service).save(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService, never()).submitVectorizeTask(any(Long.class));
    }

    @Test
    void createPersistPendingStatusFail_shouldThrowKnowledgeDocumentUpdateFailed() {
        UserHolder.set(UserContext.of(211L, "u"));
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId()).thenReturn(521L);
        when(knowledgeEmbeddingService.submitVectorizeTask(311L)).thenReturn(711L);
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(0L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doAnswer(invocation -> {
            KnowledgeDocument arg = invocation.getArgument(0);
            arg.setId(311L);
            return true;
        }).when(service).save(any(KnowledgeDocument.class));
        doReturn(false).when(service).updateById(any(KnowledgeDocument.class));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("repo");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_UPDATE_FAILED.getCode(), ex.getCode());
        verify(knowledgeEmbeddingService).submitVectorizeTask(311L);
        verify(service).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void createGithubTypeButNonGithubUrl_shouldThrowUrlFormatError() {
        UserHolder.set(UserContext.of(214L, "u"));
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://gitlab.com/acme/repo");
        dto.setTitle("repo");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    void createUnsupportedDeepWiki_shouldThrowFileTypeNotExists() {
        UserHolder.set(UserContext.of(215L, "u"));
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.PROJECT_DEEPWIKI.getCode());
        dto.setContent("https://deepwiki.com/acme/wiki");
        dto.setTitle("wiki");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.FILE_TYPE_NOT_EXISTS.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    void updateContentChanged_shouldSubmitTaskAndSetPending() {
        UserHolder.set(UserContext.of(203L, "u"));
        TransactionSynchronizationManager.initSynchronization();
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(901L);
        existing.setUserId(203L);
        existing.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        existing.setContent("https://github.com/acme/old");
        when(knowledgeDocumentMapper.selectById(901L)).thenReturn(existing);
        when(knowledgeEmbeddingService.submitVectorizeTask(901L)).thenReturn(902L);

        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(true).when(service).updateById(any(KnowledgeDocument.class));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(901L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/new");
        dto.setTitle("new title");

        KnowledgeDocumentResponse result = service.update(dto);

        assertEquals(KnowledgeVectorTaskStatusEnum.PENDING.getCode(), result.getEmbeddingStatus());
        verify(knowledgeEmbeddingService).submitVectorizeTask(901L);
        verify(service).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void updateNotFound_shouldThrowKnowledgeDocumentNotFound() {
        UserHolder.set(UserContext.of(204L, "u"));
        when(knowledgeDocumentMapper.selectById(1001L)).thenReturn(null);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(1001L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("x");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper).selectById(1001L);
        verify(service, never()).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void updatePersistFail_shouldThrowKnowledgeDocumentUpdateFailed() {
        UserHolder.set(UserContext.of(212L, "u"));
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(1301L);
        existing.setUserId(212L);
        existing.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        existing.setContent("https://github.com/acme/new");
        when(knowledgeDocumentMapper.selectById(1301L)).thenReturn(existing);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(false).when(service).updateById(any(KnowledgeDocument.class));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(1301L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/new");
        dto.setTitle("new");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_UPDATE_FAILED.getCode(), ex.getCode());
        verify(service).updateById(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService, never()).submitVectorizeTask(any(Long.class));
    }

    @Test
    void updateContentNoChange_shouldSkipEmbeddingTask() {
        UserHolder.set(UserContext.of(205L, "u"));
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(1201L);
        existing.setUserId(205L);
        existing.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        existing.setContent("https://github.com/acme/same");
        when(knowledgeDocumentMapper.selectById(1201L)).thenReturn(existing);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(true).when(service).updateById(any(KnowledgeDocument.class));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(1201L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/same");
        dto.setTitle("same");

        KnowledgeDocumentResponse result = service.update(dto);

        assertEquals("same", result.getTitle());
        verify(knowledgeEmbeddingService, never()).submitVectorizeTask(any(Long.class));
        verify(service).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void getByIdNotOwner_shouldThrowKnowledgeDocumentNotFound() {
        UserHolder.set(UserContext.of(206L, "u"));
        KnowledgeDocument other = new KnowledgeDocument();
        other.setId(2222L);
        other.setUserId(9999L);
        when(knowledgeDocumentMapper.selectById(2222L)).thenReturn(other);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(2222L));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper).selectById(2222L);
    }

    @Test
    void getPublicByIdUnknownEmbeddingStatus_shouldMapNullName() {
        UserHolder.set(UserContext.of(207L, "u"));
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(3301L);
        kd.setUserId(207L);
        kd.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        kd.setContent("https://github.com/acme/repo");
        kd.setTitle("repo");
        kd.setEmbeddingStatus(999);
        when(knowledgeDocumentMapper.selectById(3301L)).thenReturn(kd);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        KnowledgeDocumentPublicResponse dto = service.getPublicById(3301L);

        assertEquals(3301L, dto.getId());
        assertNull(dto.getEmbeddingStatus());
        verify(knowledgeDocumentMapper).selectById(3301L);
    }

    @Test
    void getByPageDefaultLimit_shouldNormalizeAndReturnPage() {
        UserHolder.set(UserContext.of(208L, "u"));
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(4101L);
        kd.setUserId(208L);
        kd.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        kd.setContent("https://github.com/acme/repo");
        kd.setTitle("repo");
        Page<KnowledgeDocument> mpPage = new Page<>(1, 100);
        mpPage.setRecords(java.util.List.of(kd));
        mpPage.setTotal(0);
        when(knowledgeDocumentMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        var page = service.getByPage(null, 200);

        assertEquals(1, page.getList().size());
        assertEquals(100, page.getLimit());
        assertEquals(0, page.getTotal());
        verify(knowledgeDocumentMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getByPageWithInvalidPageAndLimit_shouldApplyDefaults() {
        UserHolder.set(UserContext.of(216L, "u"));
        Page<KnowledgeDocument> mpPage = new Page<>(1, 20);
        mpPage.setRecords(java.util.List.of());
        mpPage.setTotal(3);
        when(knowledgeDocumentMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        var page = service.getByPage(0, 0);

        assertEquals(1, page.getPage());
        assertEquals(20, page.getLimit());
        assertEquals(3, page.getTotal());
        assertEquals(0, page.getList().size());
    }

    @Test
    void deleteSuccess_shouldDeleteVectorsTasksAndDoc() {
        UserHolder.set(UserContext.of(209L, "u"));
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(5101L);
        kd.setKnowledgeId(6101L);
        kd.setUserId(209L);
        when(knowledgeDocumentMapper.selectById(5101L)).thenReturn(kd);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        service.delete(5101L);

        verify(knowledgeEmbeddingService).deleteVectors(5101L);
        verify(knowledgeVectorTaskMapper).delete(
                Mockito.<com.baomidou.mybatisplus.core.conditions.Wrapper<KnowledgeVectorTask>>any());
        verify(knowledgeDocumentMapper).deleteById(5101L);
    }

    @Test
    void deleteWhenDeleteVectorsThrows_shouldPropagateBusinessException() {
        UserHolder.set(UserContext.of(213L, "u"));
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(5201L);
        kd.setKnowledgeId(6201L);
        kd.setUserId(213L);
        when(knowledgeDocumentMapper.selectById(5201L)).thenReturn(kd);
        doThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED))
                .when(knowledgeEmbeddingService).deleteVectors(5201L);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeVectorTaskMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(5201L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED.getCode(), ex.getCode());
        verify(knowledgeEmbeddingService).deleteVectors(5201L);
        verify(knowledgeDocumentMapper, never()).deleteById(5201L);
    }
}

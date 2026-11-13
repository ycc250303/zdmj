package com.zdmj.knowledgeService.service.impl;

import static org.mockito.ArgumentMatchers.eq;
import com.zdmj.common.context.CurrentActor;
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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeEmbeddingStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

class KnowledgeDocumentServiceImplTest {

    private CurrentActor actor = new CurrentActor(1L);

    private final KnowledgeDocumentMapper knowledgeDocumentMapper = Mockito.mock(KnowledgeDocumentMapper.class);
    private final KnowledgeBasesService knowledgeBasesService = Mockito.mock(KnowledgeBasesService.class);
    private final KnowledgeEmbeddingService knowledgeEmbeddingService = Mockito.mock(KnowledgeEmbeddingService.class);
    private final FileUploadService fileUploadService = Mockito.mock(FileUploadService.class);

    @Test
    void createSuccess_shouldVectorizeAndReturnStoredDocument() {
        actor = new CurrentActor(201L);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(501L);
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(0L);
        KnowledgeDocument stored = new KnowledgeDocument();
        stored.setId(301L);
        stored.setEmbeddingStatus(KnowledgeEmbeddingStatusEnum.SUCCESS.getCode());
        when(knowledgeDocumentMapper.selectById(301L)).thenReturn(stored);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doAnswer(invocation -> {
            KnowledgeDocument arg = invocation.getArgument(0);
            arg.setId(301L);
            return true;
        }).when(service).save(any(KnowledgeDocument.class));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("repo");

        KnowledgeDocumentResponse result = service.create(actor, dto);

        assertNotNull(result);
        assertEquals(301L, result.getId());
        assertEquals(KnowledgeEmbeddingStatusEnum.SUCCESS.getCode(), result.getEmbeddingStatus());
        verify(service).save(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService).vectorizeAndStore(actor, 301L);
    }

    @Test
    void createDuplicateContent_shouldThrowKnowledgeDocumentContentExists() {
        actor = new CurrentActor(217L);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(530L);
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(1L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("repo");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(actor, dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_EXISTS.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    void createInvalidUrl_shouldThrowUrlFormatError() {
        actor = new CurrentActor(202L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("not-a-url");
        dto.setTitle("bad");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(actor, dto));

        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService, never()).vectorizeAndStore(eq(actor), any(Long.class));
    }

    @Test
    void createSaveFail_shouldThrowKnowledgeDocumentCreateFailed() {
        actor = new CurrentActor(210L);
        when(knowledgeBasesService.getOrCreateKnowledgeBaseId(actor)).thenReturn(520L);
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(0L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(false).when(service).save(any(KnowledgeDocument.class));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("repo");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(actor, dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_CREATE_FAILED.getCode(), ex.getCode());
        verify(service).save(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService, never()).vectorizeAndStore(eq(actor), any(Long.class));
    }

    @Test
    void createGithubTypeButNonGithubUrl_shouldThrowUrlFormatError() {
        actor = new CurrentActor(214L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://gitlab.com/acme/repo");
        dto.setTitle("repo");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(actor, dto));

        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    void createUnsupportedDeepWiki_shouldThrowFileTypeNotExists() {
        actor = new CurrentActor(215L);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.PROJECT_DEEPWIKI.getCode());
        dto.setContent("https://deepwiki.com/acme/wiki");
        dto.setTitle("wiki");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(actor, dto));

        assertEquals(ErrorCode.FILE_TYPE_NOT_EXISTS.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    void updateContentChanged_shouldVectorize() {
        actor = new CurrentActor(203L);
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(901L);
        existing.setUserId(203L);
        existing.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        existing.setContent("https://github.com/acme/old");
        when(knowledgeDocumentMapper.selectById(901L)).thenReturn(existing);

        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(true).when(service).updateById(any(KnowledgeDocument.class));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(901L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/new");
        dto.setTitle("new title");

        KnowledgeDocumentResponse result = service.update(actor, dto);

        assertEquals("new title", result.getTitle());
        verify(knowledgeEmbeddingService).vectorizeAndStore(actor, 901L);
        verify(service).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void updateNotFound_shouldThrowKnowledgeDocumentNotFound() {
        actor = new CurrentActor(204L);
        when(knowledgeDocumentMapper.selectById(1001L)).thenReturn(null);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(1001L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/repo");
        dto.setTitle("x");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(actor, dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper).selectById(1001L);
        verify(service, never()).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void updatePersistFail_shouldThrowKnowledgeDocumentUpdateFailed() {
        actor = new CurrentActor(212L);
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(1301L);
        existing.setUserId(212L);
        existing.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        existing.setContent("https://github.com/acme/new");
        when(knowledgeDocumentMapper.selectById(1301L)).thenReturn(existing);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(false).when(service).updateById(any(KnowledgeDocument.class));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(1301L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/new");
        dto.setTitle("new");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(actor, dto));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_UPDATE_FAILED.getCode(), ex.getCode());
        verify(service).updateById(any(KnowledgeDocument.class));
        verify(knowledgeEmbeddingService, never()).vectorizeAndStore(eq(actor), any(Long.class));
    }

    @Test
    void updateContentNoChange_shouldSkipEmbeddingTask() {
        actor = new CurrentActor(205L);
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(1201L);
        existing.setUserId(205L);
        existing.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        existing.setContent("https://github.com/acme/same");
        when(knowledgeDocumentMapper.selectById(1201L)).thenReturn(existing);
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        doReturn(true).when(service).updateById(any(KnowledgeDocument.class));

        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setId(1201L);
        dto.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        dto.setContent("https://github.com/acme/same");
        dto.setTitle("same");

        KnowledgeDocumentResponse result = service.update(actor, dto);

        assertEquals("same", result.getTitle());
        verify(knowledgeEmbeddingService, never()).vectorizeAndStore(eq(actor), any(Long.class));
        verify(service).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void getByIdNotOwner_shouldThrowKnowledgeDocumentNotFound() {
        actor = new CurrentActor(206L);
        KnowledgeDocument other = new KnowledgeDocument();
        other.setId(2222L);
        other.setUserId(9999L);
        when(knowledgeDocumentMapper.selectById(2222L)).thenReturn(other);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(actor, 2222L));

        assertEquals(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.getCode(), ex.getCode());
        verify(knowledgeDocumentMapper).selectById(2222L);
    }

    @Test
    void getPublicByIdUnknownEmbeddingStatus_shouldMapNullName() {
        actor = new CurrentActor(207L);
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(3301L);
        kd.setUserId(207L);
        kd.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
        kd.setContent("https://github.com/acme/repo");
        kd.setTitle("repo");
        kd.setEmbeddingStatus(999);
        when(knowledgeDocumentMapper.selectById(3301L)).thenReturn(kd);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        KnowledgeDocumentPublicResponse dto = service.getPublicById(actor, 3301L);

        assertEquals(3301L, dto.getId());
        assertNull(dto.getEmbeddingStatus());
        verify(knowledgeDocumentMapper).selectById(3301L);
    }

    @Test
    void getByPageDefaultLimit_shouldNormalizeAndReturnPage() {
        actor = new CurrentActor(208L);
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
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        var page = service.getByPage(actor, null, 200);

        assertEquals(1, page.getList().size());
        assertEquals(100, page.getLimit());
        assertEquals(0, page.getTotal());
        verify(knowledgeDocumentMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getByPageWithInvalidPageAndLimit_shouldApplyDefaults() {
        actor = new CurrentActor(216L);
        Page<KnowledgeDocument> mpPage = new Page<>(1, 20);
        mpPage.setRecords(java.util.List.of());
        mpPage.setTotal(3);
        when(knowledgeDocumentMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        var page = service.getByPage(actor, 0, 0);

        assertEquals(1, page.getPage());
        assertEquals(20, page.getLimit());
        assertEquals(3, page.getTotal());
        assertEquals(0, page.getList().size());
    }

    @Test
    void deleteSuccess_shouldDeleteVectorsTasksAndDoc() {
        actor = new CurrentActor(209L);
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(5101L);
        kd.setKnowledgeId(6101L);
        kd.setUserId(209L);
        when(knowledgeDocumentMapper.selectById(5101L)).thenReturn(kd);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        service.delete(actor, 5101L);

        verify(knowledgeEmbeddingService).deleteVectors(actor, 5101L);
        verify(knowledgeDocumentMapper).deleteById(5101L);
    }

    @Test
    void deleteWhenDeleteVectorsThrows_shouldPropagateBusinessException() {
        actor = new CurrentActor(213L);
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setId(5201L);
        kd.setKnowledgeId(6201L);
        kd.setUserId(213L);
        when(knowledgeDocumentMapper.selectById(5201L)).thenReturn(kd);
        doThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED))
                .when(knowledgeEmbeddingService).deleteVectors(actor, 5201L);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(actor, 5201L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED.getCode(), ex.getCode());
        verify(knowledgeEmbeddingService).deleteVectors(actor, 5201L);
        verify(knowledgeDocumentMapper, never()).deleteById(5201L);
    }

    @Test
    void createProjectDocumentNonCosUrl_shouldThrowUrlFormatError() {
        actor = new CurrentActor(216L);
        when(fileUploadService.existsByUrl(actor, "https://evil.example/file.pdf"))
                .thenThrow(new BusinessException(ErrorCode.URL_FORMAT_ERROR));
        KnowledgeDocumentServiceImpl service = spy(new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper, knowledgeBasesService, knowledgeEmbeddingService, fileUploadService));
        KnowledgeDocumentRequest dto = new KnowledgeDocumentRequest();
        dto.setType(KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode());
        dto.setContent("https://evil.example/file.pdf");
        dto.setTitle("doc");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(actor, dto));

        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(service, never()).save(any(KnowledgeDocument.class));
        verify(fileUploadService).existsByUrl(actor, "https://evil.example/file.pdf");
    }
}

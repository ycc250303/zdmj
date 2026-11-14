package com.zdmj.knowledgeService.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.common.storage.FileUploadService.StoredFile;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import java.util.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class KnowledgeDocumentServiceImplTest {
    private final CurrentActor actor = new CurrentActor(1L);
    private final KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeBasesService bases = mock(KnowledgeBasesService.class);
    private final KnowledgeEmbeddingService embedding = mock(KnowledgeEmbeddingService.class);
    private final FileUploadService files = mock(FileUploadService.class);
    private final KnowledgeVectorMapper vectors = mock(KnowledgeVectorMapper.class);

    @BeforeAll static void init() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KnowledgeDocument.class);
    }

    private KnowledgeDocumentServiceImpl service() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new KnowledgeDocumentServiceImpl(mapper, bases, embedding, files, vectors, new TransactionTemplate(manager));
    }

    @Test void 无权查看文档_不读取原文件() {
        KnowledgeDocument document = document(1);
        document.setUserId(2L);
        when(mapper.selectById(9L)).thenReturn(document);
        assertThatThrownBy(() -> service().openDownload(actor, 9L)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.getCode());
        verifyNoInteractions(files);
    }

    @Test void 下载只允许原文件可用状态() {
        when(mapper.selectById(9L)).thenReturn(document(3));
        assertThatThrownBy(() -> service().openDownload(actor, 9L)).extracting("code")
                .isEqualTo(ErrorCode.KNOWLEDGE_DOCUMENT_STATE_CONFLICT.getCode());
        verifyNoInteractions(files);
    }

    @Test void 删除磁盘失败_不删除数据库记录_保留原始原因() {
        KnowledgeDocument document = document(1);
        when(mapper.selectOwnedForUpdate(9L, 1L)).thenReturn(document);
        when(mapper.update(isNull(), any())).thenReturn(1);
        RuntimeException failure = new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        doThrow(failure).when(files).delete(actor, document.getContent());
        assertThatThrownBy(() -> service().delete(actor, 9L)).isSameAs(failure);
        verify(vectors, never()).deleteByDocumentIdAndUserId(any(), any());
        verify(mapper, never()).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void 删除中记录可继续删除_同时清理待写入和旧原文件() {
        KnowledgeDocument document = document(3);
        document.getMetadata().put("pendingKey", "new");
        document.getMetadata().put("retiredFile", "old");
        when(mapper.selectOwnedForUpdate(9L, 1L)).thenReturn(document);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(mapper.delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        service().delete(actor, 9L);
        var order = inOrder(files, vectors, mapper);
        order.verify(files).delete(actor, document.getContent());
        order.verify(files).delete(actor, "new");
        order.verify(files).delete(actor, "old");
        order.verify(vectors).deleteByDocumentIdAndUserId(9L, 1L);
        order.verify(mapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void 正在写入的文档不能删除() {
        when(mapper.selectOwnedForUpdate(9L, 1L)).thenReturn(document(0));
        assertThatThrownBy(() -> service().delete(actor, 9L)).extracting("code")
                .isEqualTo(ErrorCode.KNOWLEDGE_DOCUMENT_STATE_CONFLICT.getCode());
        verifyNoInteractions(files, vectors);
    }

    @Test void 上传保存失败_不执行向量化_文档记录保留() {
        var file = new MockMultipartFile("file", "a.md", "text/markdown", "text".getBytes());
        var target = new StoredFile("user-1/" + "a".repeat(32) + ".md", "a.md", 4L, "text/markdown");
        when(files.prepare(actor, file)).thenReturn(target);
        when(bases.getOrCreateKnowledgeBaseId(actor)).thenReturn(7L);
        when(mapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            invocation.<KnowledgeDocument>getArgument(0).setId(9L); return 1;
        });
        RuntimeException failure = new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        doThrow(failure).when(files).save(actor, target, file);
        assertThatThrownBy(() -> service().upload(actor, request("学习路径"), file)).hasCause(failure);
        verifyNoInteractions(embedding);
        verify(mapper).update(isNull(), any());
    }

    @Test void 标题修改保留文件元数据_公开结果不暴露内部清理标识() {
        KnowledgeDocument document = document(1);
        document.getMetadata().put("retiredFile", "internal");
        when(mapper.selectOwnedForUpdate(9L, 1L)).thenReturn(document);
        when(mapper.selectById(9L)).thenReturn(document);
        when(mapper.update(isNull(), any())).thenReturn(1);
        var result = service().update(actor, request("职业规划"));
        assertThat(result.getContent()).isEqualTo("/knowledge-document/9/download");
        assertThat(result.getMetadata()).containsEntry("fileName", "a.md").doesNotContainKey("retiredFile");
        verifyNoInteractions(files, embedding, vectors);
    }

    @Test void 地址创建与非法标题拒绝() {
        assertThatThrownBy(() -> service().create(actor, request("title"))).isInstanceOf(BusinessException.class);
        var request = request(" ");
        assertThatThrownBy(() -> service().upload(actor, request, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    private KnowledgeDocument document(int state) {
        var d = new KnowledgeDocument();
        d.setId(9L); d.setUserId(1L); d.setKnowledgeId(7L); d.setType(1);
        d.setContent("user-1/" + "a".repeat(32) + ".md");
        d.setTitle("Java项目"); d.setFileStatus(state); d.setEmbeddingStatus(3);
        d.setMetadata(new LinkedHashMap<>(Map.of("fileName", "a.md", "fileSize", 4L, "contentType", "text/markdown")));
        return d;
    }

    private KnowledgeDocumentRequest request(String title) {
        var r = new KnowledgeDocumentRequest(); r.setId(9L); r.setTitle(title); r.setType(1); return r;
    }
}

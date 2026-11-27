package com.zdmj.knowledgeService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.common.storage.StoredFile;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentDownload;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeFileStatusEnum;
import com.zdmj.knowledgeService.enums.KnowledgeEmbeddingStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeDocumentService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 知识文档生命周期：短事务登记状态，事务外读写文件和调用模型。 */
@RequiredArgsConstructor
@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final FileUploadService fileUploadService;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public KnowledgeDocumentResponse create(CurrentActor actor, KnowledgeDocumentRequest request) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请通过文件表单上传知识文档");
    }

    /** 先登记文件标识，再同步保存和向量化；失败记录保留用于下载、重试或删除。 */
    @Override
    public KnowledgeDocumentResponse upload(CurrentActor actor, KnowledgeDocumentRequest request, MultipartFile file) {
        validateRequest(request);
        StoredFile target = fileUploadService.prepare(actor, file);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(actor.userId());
        document.setScope(KnowledgeScopeEnum.USER.getCode());
        document.setType(1);
        document.setContent(target.key());
        document.setTitle(request.title().trim());
        document.setFileStatus(KnowledgeFileStatusEnum.WRITING.code());
        document.setEmbeddingStatus(KnowledgeEmbeddingStatusEnum.PENDING.getCode());
        document.setMetadata(fileMetadata(request, target));
        transactionTemplate.executeWithoutResult(status -> {
            if (knowledgeDocumentMapper.insert(document) != 1 || document.getId() == null) {
                throw new IllegalStateException("知识文档插入结果无效");
            }
        });
        try {
            persist(actor, document.getId(), target, file, false);
            return retryEmbedding(actor, document.getId());
        } catch (RuntimeException failure) {
            throw new OperationFailure(document.getId(), failure);
        }
    }

    /** 替换文件先登记待写入标识，旧文件保留到新文件可用；每一步均可由记录定位。 */
    @Override
    public KnowledgeDocumentResponse replace(CurrentActor actor, KnowledgeDocumentRequest request, MultipartFile file) {
        validateRequest(request);
        StoredFile target = fileUploadService.prepare(actor, file);
        transactionTemplate.executeWithoutResult(status -> {
            KnowledgeDocument current = locked(actor, request.id());
            requireReady(current);
            if (current.getMetadata().containsKey("retiredFile")
                    || Objects.equals(current.getEmbeddingStatus(), KnowledgeEmbeddingStatusEnum.RUNNING.getCode())) {
                throw conflict();
            }
            Map<String, Object> metadata = new LinkedHashMap<>(current.getMetadata());
            metadata.put("pendingFile", fileMetadata(request, target));
            metadata.put("pendingKey", target.key());
            metadata.put("pendingTitle", request.title().trim());
            change(actor, current.getId(), KnowledgeFileStatusEnum.READY, KnowledgeFileStatusEnum.WRITING, metadata);
        });
        try {
            persist(actor, request.id(), target, file, true);
            return retryEmbedding(actor, request.id());
        } catch (RuntimeException failure) {
            throw new OperationFailure(request.id(), failure);
        }
    }

    private void persist(CurrentActor actor, Long id, StoredFile target, MultipartFile file, boolean replacement) {
        try {
            String hash = fileUploadService.save(actor, target, file);
            transactionTemplate.executeWithoutResult(status -> {
                KnowledgeDocument current = locked(actor, id);
                if (!Objects.equals(current.getFileStatus(), KnowledgeFileStatusEnum.WRITING.code())) {
                    throw conflict();
                }
                Map<String, Object> metadata;
                String title = current.getTitle();
                if (replacement) {
                    if (!target.key().equals(current.getMetadata().get("pendingKey"))) {
                        throw conflict();
                    }
                    metadata = new LinkedHashMap<>();
                    ((Map<?, ?>) current.getMetadata().get("pendingFile"))
                            .forEach((key, value) -> metadata.put(key.toString(), value));
                    metadata.put("retiredFile", current.getContent());
                    title = current.getMetadata().get("pendingTitle").toString();
                } else {
                    metadata = current.getMetadata();
                }
                var update = owned(actor, id).eq(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.WRITING.code())
                        .set(KnowledgeDocument::getContent, target.key()).set(KnowledgeDocument::getTitle, title)
                        .set(KnowledgeDocument::getContentHash, hash)
                        .set(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.READY.code())
                        .set(KnowledgeDocument::getEmbeddingStatus, KnowledgeEmbeddingStatusEnum.PENDING.getCode())
                        .set(KnowledgeDocument::getChunkCount, 0).set(KnowledgeDocument::getLastError, null)
                        .set(KnowledgeDocument::getLastEmbeddedAt, null)
                        .set(KnowledgeDocument::getMetadata, metadata,
                                "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler");
                if (knowledgeDocumentMapper.update(null, update) != 1) {
                    throw conflict();
                }
                knowledgeVectorMapper.deleteByDocumentIdAndUserId(id, actor.userId());
            });
        } catch (RuntimeException failure) {
            try {
                knowledgeDocumentMapper.update(null, owned(actor, id)
                        .eq(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.WRITING.code())
                        .set(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.FAILED.code()));
            } catch (RuntimeException markingFailure) {
                failure.addSuppressed(markingFailure);
            }
            throw failure;
        }
    }

    @Override
    public KnowledgeDocument getById(CurrentActor actor, Long id) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null || !Objects.equals(document.getScope(), KnowledgeScopeEnum.USER.getCode()) || !actor.userId().equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    @Override
    public KnowledgeDocumentPublicResponse getPublicById(CurrentActor actor, Long id) {
        return publicResponse(getById(actor, id));
    }

    @Override
    public PageDTO<KnowledgeDocumentPublicResponse> getByPage(CurrentActor actor, Integer page, Integer limit) {
        Page<KnowledgeDocument> result = knowledgeDocumentMapper.selectPage(PageRequests.toPage(PageRequests.normalize(page, limit)),
                new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.USER.getCode()).eq(KnowledgeDocument::getUserId, actor.userId())
                        .orderByDesc(KnowledgeDocument::getCreatedAt).orderByDesc(KnowledgeDocument::getId));
        List<KnowledgeDocumentPublicResponse> list = result.getRecords().stream().map(this::publicResponse).toList();
        return PageDTO.from(result, list);
    }

    /** 只更新允许编辑的标题及业务元数据，不写回向量状态与文件位置。 */
    @Override
    public KnowledgeDocumentResponse update(CurrentActor actor, KnowledgeDocumentRequest request) {
        return transactionTemplate.execute(status -> {
            KnowledgeDocument current = locked(actor, request.id());
            requireReady(current);
            if (request.content() != null && !request.content().equals(downloadPath(current))
                    && !request.content().equals(current.getContent())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "替换原文件请提交文件表单");
            }
            if (request.title() == null || request.title().isBlank() || request.title().length() > 500) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文档标题长度必须为 1 至 500 个字符");
            }
            Map<String, Object> metadata = new LinkedHashMap<>(current.getMetadata());
            metadata.putAll(buildMetadata(request));
            if (knowledgeDocumentMapper.update(null, owned(actor, current.getId())
                    .eq(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.READY.code())
                    .set(KnowledgeDocument::getTitle, request.title().trim())
                    .set(KnowledgeDocument::getMetadata, metadata,
                            "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler")) != 1) {
                throw conflict();
            }
            return response(getById(actor, current.getId()));
        });
    }

    /** 先阻止新的文件操作，再在事务外删除文件，最后原子删除向量和文档。 */
    @Override
    public void delete(CurrentActor actor, Long id) {
        KnowledgeDocument document = transactionTemplate.execute(status -> {
            KnowledgeDocument current = locked(actor, id);
            if (Objects.equals(current.getFileStatus(), KnowledgeFileStatusEnum.WRITING.code())) {
                throw conflict();
            }
            if (knowledgeDocumentMapper.update(null, owned(actor, id)
                    .set(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.DELETING.code())) != 1) {
                throw conflict();
            }
            return current;
        });
        fileUploadService.delete(actor, document.getContent());
        for (String field : List.of("pendingKey", "retiredFile")) {
            Object key = document.getMetadata().get(field);
            if (key != null) {
                fileUploadService.delete(actor, key.toString());
            }
        }
        transactionTemplate.executeWithoutResult(status -> {
            knowledgeVectorMapper.deleteByDocumentIdAndUserId(id, actor.userId());
            int count = knowledgeDocumentMapper.delete(ownedQuery(actor, id)
                    .eq(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.DELETING.code()));
            if (count != 1) {
                throw conflict();
            }
        });
    }

    /** 按文档归属打开本地原文件，文件名、长度和类型取自 metadata。 */
    @Override
    public KnowledgeDocumentDownload openDownload(CurrentActor actor, Long id) {
        KnowledgeDocument document = getById(actor, id);
        requireReady(document);
        Map<String, Object> meta = document.getMetadata();
        String name = Objects.requireNonNull(meta.get("fileName"), "缺少原始文件名").toString();
        String type = Objects.requireNonNull(meta.get("contentType"), "缺少文件类型").toString();
        long size = ((Number) Objects.requireNonNull(meta.get("fileSize"), "缺少文件大小")).longValue();
        return new KnowledgeDocumentDownload(fileUploadService.openInputStream(actor, document.getContent()), name, size, type);
    }

    @Override
    public KnowledgeDocumentResponse retryEmbedding(CurrentActor actor, Long id) {
        KnowledgeDocument document = getById(actor, id);
        if (Objects.equals(document.getFileStatus(), KnowledgeFileStatusEnum.FAILED.code())
                && document.getMetadata().containsKey("pendingKey")) {
            String pending = document.getMetadata().get("pendingKey").toString();
            fileUploadService.delete(actor, pending);
            transactionTemplate.executeWithoutResult(status -> {
                KnowledgeDocument current = locked(actor, id);
                if (!Objects.equals(current.getFileStatus(), KnowledgeFileStatusEnum.FAILED.code())
                        || !pending.equals(current.getMetadata().get("pendingKey"))) { throw conflict(); }
                Map<String, Object> metadata = new LinkedHashMap<>(current.getMetadata());
                List.of("pendingKey", "pendingFile", "pendingTitle").forEach(metadata::remove);
                change(actor, id, KnowledgeFileStatusEnum.FAILED, KnowledgeFileStatusEnum.READY, metadata);
            });
            document = getById(actor, id);
        }
        requireReady(document);
        KnowledgeDocument snapshot = document;
        Object retired = document.getMetadata().get("retiredFile");
        if (retired != null) {
            fileUploadService.delete(actor, retired.toString());
            transactionTemplate.executeWithoutResult(status -> {
                KnowledgeDocument current = locked(actor, id);
                requireReady(current);
                if (!snapshot.getContent().equals(current.getContent())) { throw conflict(); }
                Map<String, Object> metadata = new LinkedHashMap<>(current.getMetadata());
                metadata.remove("retiredFile");
                change(actor, id, KnowledgeFileStatusEnum.READY, KnowledgeFileStatusEnum.READY, metadata);
            });
        }
        knowledgeEmbeddingService.vectorizeAndStore(actor, id);
        return response(getById(actor, id));
    }

    /** 单实例启动时释放上次进程中断的状态，恢复由用户显式重试或删除触发。 */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationStartedEvent.class)
    public void recoverInterruptedOperations() {
        transactionTemplate.executeWithoutResult(status -> {
            knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.USER.getCode()).eq(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.WRITING.code())
                    .set(KnowledgeDocument::getFileStatus, KnowledgeFileStatusEnum.FAILED.code())
                    .set(KnowledgeDocument::getUpdatedAt, com.zdmj.common.util.DateTimeUtil.now()));
            knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.USER.getCode()).eq(KnowledgeDocument::getEmbeddingStatus, KnowledgeEmbeddingStatusEnum.RUNNING.getCode())
                    .set(KnowledgeDocument::getEmbeddingStatus, KnowledgeEmbeddingStatusEnum.FAILED.getCode())
                    .set(KnowledgeDocument::getLastError, "处理被中断，请重新处理文档")
                    .set(KnowledgeDocument::getUpdatedAt, com.zdmj.common.util.DateTimeUtil.now()));
        });
    }

    private KnowledgeDocument locked(CurrentActor actor, Long id) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectOwnedForUpdate(id, actor.userId());
        if (document == null) { throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND); }
        return document;
    }

    private void change(CurrentActor actor, Long id, KnowledgeFileStatusEnum expected, KnowledgeFileStatusEnum next, Map<String, Object> metadata) {
        if (knowledgeDocumentMapper.update(null, owned(actor, id)
                .eq(KnowledgeDocument::getFileStatus, expected.code())
                .set(KnowledgeDocument::getFileStatus, next.code())
                .set(KnowledgeDocument::getMetadata, metadata,
                        "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler")) != 1) {
            throw conflict();
        }
    }

    private static LambdaUpdateWrapper<KnowledgeDocument> owned(CurrentActor actor, Long id) {
        return new LambdaUpdateWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getId, id)
                .eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.USER.getCode()).eq(KnowledgeDocument::getUserId, actor.userId())
                .set(KnowledgeDocument::getUpdatedAt, com.zdmj.common.util.DateTimeUtil.now());
    }

    private static LambdaQueryWrapper<KnowledgeDocument> ownedQuery(CurrentActor actor, Long id) {
        return new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getId, id)
                .eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.USER.getCode()).eq(KnowledgeDocument::getUserId, actor.userId());
    }

    private static void requireReady(KnowledgeDocument document) {
        if (!Objects.equals(document.getFileStatus(), KnowledgeFileStatusEnum.READY.code())) { throw conflict(); }
    }

    private static BusinessException conflict() {
        return new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_STATE_CONFLICT);
    }

    private static void validateRequest(KnowledgeDocumentRequest request) {
        if (request.title() == null || request.title().isBlank() || request.title().length() > 500
                || request.type() != null && request.type() != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请提供标题和 PDF 或 Markdown 文件");
        }
    }

    private Map<String, Object> fileMetadata(KnowledgeDocumentRequest request, StoredFile file) {
        Map<String, Object> metadata = buildMetadata(request);
        metadata.put("fileName", file.fileName());
        metadata.put("fileSize", file.fileSize());
        metadata.put("contentType", file.contentType());
        return metadata;
    }

    private Map<String, Object> buildMetadata(KnowledgeDocumentRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String title = request == null ? "" : request.title();
        String content = request == null ? "" : request.content();
        String merged = (title == null ? "" : title) + " " + (content == null ? "" : content);
        String lower = merged.toLowerCase(Locale.ROOT);

        String category = "general";
        if (lower.contains("学习路径") || lower.contains("成长路径") || lower.contains("roadmap")) {
            category = "learning_path";
        } else if (lower.contains("职业规划") || lower.contains("求职建议") || lower.contains("career")) {
            category = "career_planning";
        } else if (lower.contains("行业趋势") || lower.contains("就业趋势") || lower.contains("trend")) {
            category = "industry_trend";
        }

        metadata.put("docCategory", category);
        metadata.put("roleType", "default");
        metadata.put("difficulty", "basic");
        metadata.put("sourcePriority", 1);
        return metadata;
    }

    private static String downloadPath(KnowledgeDocument document) {
        return "/knowledge-document/" + document.getId() + "/download";
    }

    private static Map<String, Object> publicMetadata(KnowledgeDocument document) {
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        boolean canRestore = metadata.containsKey("pendingKey") && Objects.equals(document.getFileStatus(), KnowledgeFileStatusEnum.FAILED.code());
        List.of("pendingFile", "pendingKey", "pendingTitle", "retiredFile").forEach(metadata::remove);
        metadata.put("canRestoreOriginal", canRestore);
        return metadata;
    }

    private KnowledgeDocumentResponse response(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(document.getId(), document.getUserId(),
                document.getType(), downloadPath(document), document.getFileStatus(), document.getTitle(),
                document.getContentHash(), document.getEmbeddingStatus(), document.getChunkCount(),
                document.getLastEmbeddedAt(), document.getLastError(), publicMetadata(document));
    }

    private KnowledgeDocumentPublicResponse publicResponse(KnowledgeDocument document) {
        return new KnowledgeDocumentPublicResponse(document.getId(), document.getType(), downloadPath(document),
                document.getFileStatus(), document.getTitle(),
                KnowledgeEmbeddingStatusEnum.fromCode(document.getEmbeddingStatus()).getName(),
                document.getLastEmbeddedAt(), document.getLastError(), publicMetadata(document));
    }
}

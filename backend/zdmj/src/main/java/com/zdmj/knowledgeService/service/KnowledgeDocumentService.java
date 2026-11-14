package com.zdmj.knowledgeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.model.PageDTO;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;

public interface KnowledgeDocumentService {

    /**
     * 创建知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 知识文档
     */
    KnowledgeDocumentResponse create(CurrentActor actor, KnowledgeDocumentRequest request);

    /**
     * 根据ID获取知识文档（内部完整实体，含权限校验）
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 知识文档ID
     * @return 知识文档实体
     */
    KnowledgeDocument getById(CurrentActor actor, Long id);

    /**
     * 对外查询单条文档（脱敏字段）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    KnowledgeDocumentPublicResponse getPublicById(CurrentActor actor, Long id);

    /**
     * 分页查询当前用户知识文档（列表项为 {@link KnowledgeDocumentPublicResponse}）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    PageDTO<KnowledgeDocumentPublicResponse> getByPage(CurrentActor actor, Integer page, Integer limit);

    /**
     * 更新知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 知识文档
     */
    KnowledgeDocumentResponse update(CurrentActor actor, KnowledgeDocumentRequest request);

    /**
     * 删除知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 知识文档ID
     */
    void delete(CurrentActor actor, Long id);
    /** 接收文件并同步保存和向量化。 */
    KnowledgeDocumentResponse upload(CurrentActor actor, KnowledgeDocumentRequest request,
            org.springframework.web.multipart.MultipartFile file);

    /** 替换当前文档原文件，保留文档编号并重新向量化。 */
    KnowledgeDocumentResponse replace(CurrentActor actor, KnowledgeDocumentRequest request,
            org.springframework.web.multipart.MultipartFile file);

    /** 验证归属后打开原文件，响应转换器负责关闭输入流。 */
    Download openDownload(CurrentActor actor, Long id);

    /** 显式重新执行当前文档向量化。 */
    KnowledgeDocumentResponse retryEmbedding(CurrentActor actor, Long id);

    /** 清空调用开始时已有的用户文档，任一失败立即终止。 */
    void clear(CurrentActor actor);

    /** 已登记文档的处理失败；保留原始异常类型供 HTTP 层选择错误码并返回文档编号。 */
    final class OperationFailure extends RuntimeException {
        private final Long documentId;
        public OperationFailure(Long documentId, RuntimeException cause) {
            super(cause);
            this.documentId = java.util.Objects.requireNonNull(documentId);
        }
        public Long documentId() { return documentId; }
    }

    /** 下载所需的可信输入流和附件元数据，长度单位为字节。 */
    record Download(java.io.InputStream input, String fileName, long fileSize, String contentType) {
        public Download {
            java.util.Objects.requireNonNull(input);
            java.util.Objects.requireNonNull(fileName);
            java.util.Objects.requireNonNull(contentType);
            if (fileSize <= 0) { throw new IllegalArgumentException("文件长度无效"); }
        }
    }
}

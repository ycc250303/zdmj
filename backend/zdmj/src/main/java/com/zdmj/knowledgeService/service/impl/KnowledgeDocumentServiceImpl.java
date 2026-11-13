package com.zdmj.knowledgeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeEmbeddingStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeDocumentService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final FileUploadService fileUploadService;

    /**
     * 创建知识文档。文档插入先提交，再在事务外向量化。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 向量化完成后的知识文档
     */
    @Override
    public KnowledgeDocumentResponse create(CurrentActor actor, KnowledgeDocumentRequest request) {

        // 1. 验证内容
        validateContent(actor, request);

        // 2. 创建知识文档
        Long userId = actor.userId();
        Long knowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId(actor);
        KnowledgeDocument knowledgeDocument = new KnowledgeDocument();
        knowledgeDocument.setUserId(userId);
        knowledgeDocument.setKnowledgeId(knowledgeId);
        knowledgeDocument.setType(request.getType());
        knowledgeDocument.setContent(request.getContent());
        knowledgeDocument.setTitle(request.getTitle());
        knowledgeDocument.setMetadata(buildMetadata(request));
        assertContentNotExists(knowledgeId, request.getContent());

        // 3. 保存知识文档到数据库
        boolean saved;
        try {
            saved = save(knowledgeDocument);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateContent(e)) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_EXISTS);
            }
            throw e;
        }
        if (!saved) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_CREATE_FAILED);
        }

        knowledgeEmbeddingService.vectorizeAndStore(actor, knowledgeDocument.getId());
        KnowledgeDocument stored = knowledgeDocumentMapper.selectById(knowledgeDocument.getId());
        log.info("创建知识文档并完成向量化，documentId={}, knowledgeId={}",
                knowledgeDocument.getId(), knowledgeId);
        return convertToResponse(stored);
    }

    /**
     * 根据ID获取知识文档（内部完整实体，含权限校验）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    @Override
    public KnowledgeDocument getById(CurrentActor actor, Long id) {
        KnowledgeDocument knowledgeDocument = knowledgeDocumentMapper.selectById(id);
        if (knowledgeDocument == null || !knowledgeDocument.getUserId().equals(actor.userId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        return knowledgeDocument;
    }

    @Override
    public KnowledgeDocumentPublicResponse getPublicById(CurrentActor actor, Long id) {
        return toPublicResponse(getById(actor, id));
    }

    private KnowledgeDocumentPublicResponse toPublicResponse(KnowledgeDocument kd) {
        KnowledgeDocumentPublicResponse response = new KnowledgeDocumentPublicResponse();
        response.setId(kd.getId());
        response.setType(kd.getType());
        response.setContent(kd.getContent());
        response.setTitle(kd.getTitle());
        KnowledgeEmbeddingStatusEnum statusEnum = KnowledgeEmbeddingStatusEnum.fromCode(kd.getEmbeddingStatus());
        response.setEmbeddingStatus(statusEnum != null ? statusEnum.getName() : null);
        response.setLastEmbeddedAt(kd.getLastEmbeddedAt());
        response.setLastError(kd.getLastError());
        response.setMetadata(kd.getMetadata());
        return response;
    }

    /**
     * 分页查询知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param page  页码
     * @param limit 每页数量
     * @return 知识文档列表
     */
    public PageDTO<KnowledgeDocumentPublicResponse> getByPage(CurrentActor actor, Integer page, Integer limit) {
        Long userId = actor.userId();
        PageRequests.Normalized paging = PageRequests.normalize(page, limit);
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getUserId, userId)
                .orderByDesc(KnowledgeDocument::getCreatedAt);
        Page<KnowledgeDocument> result = knowledgeDocumentMapper.selectPage(PageRequests.toPage(paging), queryWrapper);
        List<KnowledgeDocumentPublicResponse> list = new ArrayList<>(result.getRecords().size());
        for (KnowledgeDocument kd : result.getRecords()) {
            list.add(toPublicResponse(kd));
        }
        return PageDTO.from(result, list);
    }

    /**
     * 更新知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 知识文档
     */
    public KnowledgeDocumentResponse update(CurrentActor actor, KnowledgeDocumentRequest request) {
        // 1. 验证内容
        validateContent(actor, request);
        // 2. 获取知识文档
        KnowledgeDocument knowledgeDocument = knowledgeDocumentMapper.selectById(request.getId());
        if (knowledgeDocument == null || !knowledgeDocument.getUserId().equals(actor.userId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        // 3. 判断内容是否发生变化
        boolean contentChanged = !request.getType().equals(knowledgeDocument.getType())
                || !request.getContent().equals(knowledgeDocument.getContent());

        // 4.更新知识文档
        knowledgeDocument.setType(request.getType());
        knowledgeDocument.setContent(request.getContent());
        knowledgeDocument.setTitle(request.getTitle());
        knowledgeDocument.setMetadata(buildMetadata(request));

        boolean updated = updateById(knowledgeDocument);
        if (!updated) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_UPDATE_FAILED);
        }
        if (contentChanged) {
            knowledgeEmbeddingService.vectorizeAndStore(actor, knowledgeDocument.getId());
            knowledgeDocument = knowledgeDocumentMapper.selectById(knowledgeDocument.getId());
            log.info("更新知识文档并完成向量化，knowledgeId={}", knowledgeDocument.getId());
        } else {
            log.info("更新知识文档，knowledgeId={}", knowledgeDocument.getId());
        }

        return convertToResponse(knowledgeDocument);
    }

    /**
     * 删除知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 知识文档ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(CurrentActor actor, Long id) {
        KnowledgeDocument kd = getById(actor, id);
        knowledgeEmbeddingService.deleteVectors(actor, id);
        knowledgeDocumentMapper.deleteById(id);
        log.info("删除知识文档，documentId={}, knowledgeId={}", id, kd.getKnowledgeId());
    }

    private void assertContentNotExists(Long knowledgeId, String content) {
        Long count = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeId, knowledgeId)
                .eq(KnowledgeDocument::getContent, content));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_EXISTS);
        }
    }

    private static boolean isDuplicateContent(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String message = cause != null ? cause.getMessage() : e.getMessage();
        return message != null && message.contains("uk_knowledge_documents_kid");
    }

    /**
     * 验证内容
     *
     * 验证规则：
     * - type=1（项目文档）：必须是COS链接的PDF或MD文件
     * - type=2（GitHub链接）：必须是GitHub链接
     * - type=3（DeepWiki）：暂不支持
     *
     * @param actor 已认证操作者，包含用户编号
     */
    private void validateContent(CurrentActor actor, KnowledgeDocumentRequest request) {
        Integer type = request.getType();
        String content = request.getContent();

        // type 为空时，说明更新场景下该字段可能未传，直接跳过校验
        if (type == null || content == null) {
            return;
        }

        // 验证URL格式
        if (!content.startsWith("http://") && !content.startsWith("https://")) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR,
                    "内容必须是有效的URL链接");
        }

        // 根据知识类型进行验证
        if (type == KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode()) {
            String lowerContent = content.toLowerCase();
            boolean isPdf = lowerContent.contains(".pdf") || lowerContent.contains("/pdf/");
            boolean isMd = lowerContent.contains(".md") || content.endsWith(".md");

            if (!isPdf && !isMd) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS,
                        "项目文档类型（type=1）仅支持PDF和Markdown文件");
            }

            if (!fileUploadService.existsByUrl(actor, content)) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS);
            }

        } else if (type == KnowledgeTypeEnum.GITHUB_REPO.getCode()) {
            // type=2：GitHub链接，必须是GitHub链接
            if (!content.contains("github.com")) {
                throw new BusinessException(ErrorCode.URL_FORMAT_ERROR,
                        "GitHub链接类型（type=2）必须是GitHub链接，当前内容不是GitHub链接");
            }

        } else if (type == KnowledgeTypeEnum.PROJECT_DEEPWIKI.getCode()) {
            // type=3：DeepWiki文档，暂不支持
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS,
                    "项目DeepWiki文档类型（type=3）暂不支持");
        } else {
            // 未知的知识类型
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS,
                    "不支持的知识类型: " + type);
        }
    }

    /**
     * 构建知识文档元数据：
     * - docCategory: learning_path / career_planning / industry_trend / general
     * - roleType: default
     * - difficulty: basic
     * - sourcePriority: 1
     *
     * 这些字段用于职业发展报告场景下的检索过滤与重排。
     */
    private Map<String, Object> buildMetadata(KnowledgeDocumentRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String title = request == null ? "" : request.getTitle();
        String content = request == null ? "" : request.getContent();
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

    private KnowledgeDocumentResponse convertToResponse(KnowledgeDocument document) {
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse();
        BeanUtils.copyProperties(document, response);
        return response;
    }
}

package com.zdmj.knowledgeService.service.impl;

import java.net.URI;
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
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.CosUtil;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentDTO;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicDTO;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeDocumentService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    /**
     * 创建知识文档
     *
     * @param knowledgeDocumentDTO 知识文档DTO
     * @return 知识文档
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument create(KnowledgeDocumentDTO knowledgeDocumentDTO) {

        // 1. 验证内容
        validateContent(knowledgeDocumentDTO);

        // 2. 创建知识文档
        Long userId = UserHolder.requireUserId();
        Long knowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();
        KnowledgeDocument knowledgeDocument = new KnowledgeDocument();
        knowledgeDocument.setUserId(userId);
        knowledgeDocument.setKnowledgeId(knowledgeId);
        knowledgeDocument.setType(knowledgeDocumentDTO.getType());
        knowledgeDocument.setContent(knowledgeDocumentDTO.getContent());
        knowledgeDocument.setTitle(knowledgeDocumentDTO.getTitle());
        knowledgeDocument.setMetadata(buildMetadata(knowledgeDocumentDTO));
        assertContentNotExists(knowledgeId, knowledgeDocumentDTO.getContent());
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

        // 3. 提交异步向量化任务
        Long taskId = knowledgeEmbeddingService.submitVectorizeTask(knowledgeDocument.getId());

        knowledgeDocument.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        knowledgeDocument.setLastError(null);
        boolean persisted = updateById(knowledgeDocument);
        if (!persisted) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_UPDATE_FAILED);
        }
        dispatchTaskAfterCommit(taskId);
        log.info("创建知识文档并提交异步向量化任务，documentId={}, knowledgeId={}, taskId={}",
                knowledgeDocument.getId(), knowledgeId, taskId);
        return knowledgeDocument;
    }

    /**
     * 根据ID获取知识文档（内部完整实体，含权限校验）
     */
    @Override
    public KnowledgeDocument getById(Long id) {
        KnowledgeDocument knowledgeDocument = knowledgeDocumentMapper.selectById(id);
        if (knowledgeDocument == null || !knowledgeDocument.getUserId().equals(UserHolder.requireUserId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        return knowledgeDocument;
    }

    @Override
    public KnowledgeDocumentPublicDTO getPublicById(Long id) {
        return toPublicDto(getById(id));
    }

    private KnowledgeDocumentPublicDTO toPublicDto(KnowledgeDocument kd) {
        KnowledgeDocumentPublicDTO dto = new KnowledgeDocumentPublicDTO();
        dto.setId(kd.getId());
        dto.setType(kd.getType());
        dto.setContent(kd.getContent());
        dto.setTitle(kd.getTitle());
        KnowledgeVectorTaskStatusEnum statusEnum = KnowledgeVectorTaskStatusEnum.fromCode(kd.getEmbeddingStatus());
        dto.setEmbeddingStatus(statusEnum != null ? statusEnum.getName() : null);
        dto.setLastEmbeddedAt(kd.getLastEmbeddedAt());
        dto.setLastError(kd.getLastError());
        dto.setMetadata(kd.getMetadata());
        return dto;
    }

    /**
     * 分页查询知识文档
     *
     * @param page  页码
     * @param limit 每页数量
     * @return 知识文档列表
     */
    public PageDTO<KnowledgeDocumentPublicDTO> getByPage(Integer page, Integer limit) {
        Long userId = UserHolder.requireUserId();
        PageRequests.Normalized paging = PageRequests.normalize(page, limit);
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getUserId, userId)
                .orderByDesc(KnowledgeDocument::getCreatedAt);
        Page<KnowledgeDocument> result = knowledgeDocumentMapper.selectPage(PageRequests.toPage(paging), queryWrapper);
        List<KnowledgeDocumentPublicDTO> list = new ArrayList<>(result.getRecords().size());
        for (KnowledgeDocument kd : result.getRecords()) {
            list.add(toPublicDto(kd));
        }
        return PageDTO.from(result, list);
    }

    /**
     * 更新知识文档
     *
     * @param knowledgeDocumentDTO 知识文档DTO
     * @return 知识文档
     */
    public KnowledgeDocument update(KnowledgeDocumentDTO knowledgeDocumentDTO) {
        // 1. 验证内容
        validateContent(knowledgeDocumentDTO);
        // 2. 获取知识文档
        KnowledgeDocument knowledgeDocument = knowledgeDocumentMapper.selectById(knowledgeDocumentDTO.getId());
        if (knowledgeDocument == null || !knowledgeDocument.getUserId().equals(UserHolder.requireUserId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        // 3. 判断内容是否发生变化
        boolean contentChanged = !knowledgeDocumentDTO.getType().equals(knowledgeDocument.getType())
                || !knowledgeDocumentDTO.getContent().equals(knowledgeDocument.getContent());

        // 4.更新知识文档
        knowledgeDocument.setType(knowledgeDocumentDTO.getType());
        knowledgeDocument.setContent(knowledgeDocumentDTO.getContent());
        knowledgeDocument.setTitle(knowledgeDocumentDTO.getTitle());
        knowledgeDocument.setMetadata(buildMetadata(knowledgeDocumentDTO));

        // 5. 提交异步向量化任务
        Long taskId = null;
        if (contentChanged) {
            taskId = knowledgeEmbeddingService.submitVectorizeTask(knowledgeDocument.getId());
            knowledgeDocument.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        }

        // 6. 更新知识文档
        boolean updated = updateById(knowledgeDocument);
        if (!updated) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_UPDATE_FAILED);
        }
        if (contentChanged) {
            dispatchTaskAfterCommit(taskId);
            log.info("更新知识文档并提交异步向量化任务，knowledgeId={}, taskId={}", knowledgeDocument.getId(), taskId);
        } else {
            log.info("更新知识文档，knowledgeId={}", knowledgeDocument.getId());
        }

        return knowledgeDocument;
    }

    /**
     * 删除知识文档
     *
     * @param id 知识文档ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeDocument kd = getById(id);
        knowledgeEmbeddingService.deleteVectors(id);
        knowledgeVectorTaskMapper.delete(new LambdaQueryWrapper<KnowledgeVectorTask>()
                .eq(KnowledgeVectorTask::getDocumentId, id));
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
     */
    private void validateContent(KnowledgeDocumentDTO dto) {
        Integer type = dto.getType();
        String content = dto.getContent();

        // type 为空时，说明更新场景下该字段可能未传，直接跳过校验
        if (type == null || content == null) {
            return;
        }

        // 验证URL格式
        if (!content.startsWith("http://") && !content.startsWith("https://")) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR.getCode(),
                    "内容必须是有效的URL链接");
        }

        // 根据知识类型进行验证
        if (type == KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode()) {
            // type=1：项目文档，必须是COS链接的PDF或MD文件
            String lowerContent = content.toLowerCase();
            boolean isPdf = lowerContent.contains(".pdf") || lowerContent.contains("/pdf/");
            boolean isMd = lowerContent.contains(".md") || content.endsWith(".md");

            if (!isPdf && !isMd) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS.getCode(),
                        "项目文档类型（type=1）仅支持PDF和Markdown文件");
            }

            // 验证COS文件是否存在
            String cosKey = extractCosKeyFromUrl(content);
            if (cosKey != null && !CosUtil.fileExists(cosKey)) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS);
            }

        } else if (type == KnowledgeTypeEnum.GITHUB_REPO.getCode()) {
            // type=2：GitHub链接，必须是GitHub链接
            if (!content.contains("github.com")) {
                throw new BusinessException(ErrorCode.URL_FORMAT_ERROR.getCode(),
                        "GitHub链接类型（type=2）必须是GitHub链接，当前内容不是GitHub链接");
            }

        } else if (type == KnowledgeTypeEnum.PROJECT_DEEPWIKI.getCode()) {
            // type=3：DeepWiki文档，暂不支持
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS.getCode(),
                    "项目DeepWiki文档类型（type=3）暂不支持");
        } else {
            // 未知的知识类型
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS.getCode(),
                    "不支持的知识类型: " + type);
        }
    }

    /**
     * 从COS URL中提取key
     */
    private String extractCosKeyFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            // 移除开头的斜杠
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            // 移除查询参数（URI已经自动处理了）
            return path;
        } catch (Exception e) {
            log.error("解析COS URL失败: {}", url, e);
            return null;
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
    private Map<String, Object> buildMetadata(KnowledgeDocumentDTO dto) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String title = dto == null ? "" : dto.getTitle();
        String content = dto == null ? "" : dto.getContent();
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

    /**
     * 提交异步向量化任务
     * 
     * @param taskId 任务ID
     */
    private void dispatchTaskAfterCommit(Long taskId) {
        if (taskId == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                knowledgeEmbeddingService.executeTaskAsync(taskId);
            }
        });
    }
}

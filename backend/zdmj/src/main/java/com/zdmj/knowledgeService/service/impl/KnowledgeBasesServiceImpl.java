package com.zdmj.knowledgeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.PageResult;
import com.zdmj.common.util.CosUtil;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.knowledgeService.dto.KnowledgeBasesDTO;
import com.zdmj.knowledgeService.dto.KnowledgeEmbeddingProgressDTO;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesStructMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class KnowledgeBasesServiceImpl extends ServiceImpl<KnowledgeBasesMapper, KnowledgeBases>
        implements KnowledgeBasesService {

    private final ProjectExperienceMapper projectExperienceMapper;
    private final KnowledgeBasesStructMapper knowledgeBasesStructMapper;
    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBases create(KnowledgeBasesDTO knowledgeBasesDTO) {
        Long userId = UserHolder.requireUserId();
        log.info("用户 {} 创建知识库: {}", userId, knowledgeBasesDTO.getName());

        // 1. 项目ID为可选，仅在传入时校验归属
        if (knowledgeBasesDTO.getProjectId() != null) {
            ProjectExperience projectExperience = projectExperienceMapper.selectById(knowledgeBasesDTO.getProjectId());
            if (projectExperience == null || !projectExperience.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
            }
        }

        // 2. 验证文件类型和内容
        validateContent(knowledgeBasesDTO);

        // 3. 同名处理：同名则复用原知识库并重新向量化，不同名则新建
        KnowledgeBases knowledgeBases = lambdaQuery()
                .eq(KnowledgeBases::getUserId, userId)
                .eq(KnowledgeBases::getName, knowledgeBasesDTO.getName())
                .last("LIMIT 1")
                .one();
        if (knowledgeBases == null) {
            knowledgeBases = knowledgeBasesStructMapper.fromDto(knowledgeBasesDTO);
            knowledgeBases.setUserId(userId);
            boolean saved = save(knowledgeBases);
            if (!saved) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_SAVE_FAILED);
            }
        } else {
            knowledgeBases.setType(knowledgeBasesDTO.getType());
            knowledgeBases.setContent(knowledgeBasesDTO.getContent());
            knowledgeBases.setTag(knowledgeBasesDTO.getTag());
            knowledgeBases.setProjectId(knowledgeBasesDTO.getProjectId());
        }

        // 4. 提交异步向量化任务
        Long taskId = knowledgeEmbeddingService.submitVectorizeTask(knowledgeBases.getId(), userId);
        knowledgeBases.setVectorTaskId(taskId);
        knowledgeBases.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        knowledgeBases.setLastError(null);
        boolean persisted = updateById(knowledgeBases);
        if (!persisted) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_UPDATE_FAILED);
        }
        dispatchTaskAfterCommit(taskId);
        log.info("创建/复用知识库并提交异步向量化任务，knowledgeId={}, taskId={}", knowledgeBases.getId(), taskId);
        return knowledgeBases;
    }

    @Override
    public List<KnowledgeBases> getByUserId() {
        return baseMapper.selectByUserId(UserHolder.requireUserId());
    }

    @Override
    public PageResult<KnowledgeBases> getPage(Integer page, Integer limit, Long projectId, Integer type) {
        Long userId = UserHolder.requireUserId();
        // 参数校验和默认值设置
        if (page == null || page < 1) {
            page = 1;
        }
        if (limit == null || limit < 1) {
            limit = 10;
        }
        // 计算偏移量
        int offset = (page - 1) * limit;
        // 查询数据列表
        List<KnowledgeBases> data = baseMapper.selectPage(userId, offset, limit, projectId, type);
        // 查询总数
        Long total = baseMapper.countPage(userId, projectId, type);
        // 构建分页结果
        return PageResult.of(data, total, page, limit);
    }

    @Override
    public KnowledgeBases getById(Long id) {
        Long userId = UserHolder.requireUserId();
        KnowledgeBases knowledgeBases = requireKnowledgeBases(id);
        // 检查权限：只能查看自己的知识库
        if (!knowledgeBases.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + "查看他人知识库");
        }
        return knowledgeBases;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBases update(KnowledgeBasesDTO knowledgeBasesDTO) {
        Long userId = UserHolder.requireUserId();
        Long id = knowledgeBasesDTO.getId();
        // 1. 校验ID不能为空
        if (id == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_ID_EMPTY);
        }

        // 2. 查询原记录
        KnowledgeBases knowledgeBases = requireKnowledgeBasesAndCheckOwnership(id, userId, "修改");

        // 3. 检查名称是否重复（如果名称有变化）
        if (knowledgeBasesDTO.getName() != null && !knowledgeBases.getName().equals(knowledgeBasesDTO.getName())) {
            if (baseMapper.existsByName(userId, knowledgeBasesDTO.getName(), id)) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
            }
        }

        // 4. 组合更新后的文件类型与内容用于校验（DTO 优先，其次使用原值）
        KnowledgeBasesDTO validateDTO = new KnowledgeBasesDTO();
        validateDTO.setContent(
                knowledgeBasesDTO.getContent() != null ? knowledgeBasesDTO.getContent()
                        : knowledgeBases.getContent());
        validateContent(validateDTO);

        // 5. 项目ID可选：仅在前端传入时校验归属
        if (knowledgeBasesDTO.getProjectId() != null) {
            ProjectExperience projectExperience = projectExperienceMapper.selectById(knowledgeBasesDTO.getProjectId());
            if (projectExperience == null || !projectExperience.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
            }
        }

        // 6. 判断内容是否变化
        boolean contentChanged = knowledgeBasesDTO.getContent() != null
                && !knowledgeBasesDTO.getContent().equals(knowledgeBases.getContent());

        // 7. 更新知识库信息（只有非空字段才会覆盖原值）
        knowledgeBasesStructMapper.updateEntityFromDto(knowledgeBasesDTO, knowledgeBases);

        // 8. 判断内容变更时重置向量化任务字段
        Long taskId = null;
        if (contentChanged) {
            taskId = knowledgeEmbeddingService.submitVectorizeTask(knowledgeBases.getId(), userId);
            knowledgeBases.setVectorTaskId(taskId);
            knowledgeBases.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        }

        boolean updated = updateById(knowledgeBases);
        if (!updated) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_UPDATE_FAILED);
        }
        if (contentChanged) {
            dispatchTaskAfterCommit(taskId);
            log.info("更新知识库成功并提交异步向量化任务，knowledgeId={}, taskId={}", knowledgeBases.getId(), taskId);
        } else {
            log.info("更新知识库成功（内容未变化），ID: {}", knowledgeBases.getId());
        }

        return knowledgeBases;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 1. 检查知识库是否存在且属于当前用户
        Long userId = UserHolder.requireUserId();
        KnowledgeBases knowledgeBases = requireKnowledgeBasesAndCheckOwnership(id, userId, "删除");

        // 2. 删除知识库
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED);
        }
        Long taskId = knowledgeEmbeddingService.submitDeleteTask(knowledgeBases.getId(), userId);
        dispatchTaskAfterCommit(taskId);
        log.info("删除知识库成功并提交异步删除向量任务，knowledgeId={}, taskId={}", knowledgeBases.getId(), taskId);
    }

    @Override
    public KnowledgeEmbeddingProgressDTO getEmbeddingProgress(Long id) {
        Long userId = UserHolder.requireUserId();
        KnowledgeBases knowledgeBases = requireKnowledgeBasesAndCheckOwnership(id, userId, "查看");

        KnowledgeEmbeddingProgressDTO dto = new KnowledgeEmbeddingProgressDTO();
        dto.setKnowledgeId(knowledgeBases.getId());
        dto.setVectorTaskId(knowledgeBases.getVectorTaskId());
        dto.setChunkCount(knowledgeBases.getChunkCount());
        dto.setLastError(knowledgeBases.getLastError());

        Integer taskStatusCode = null;
        String taskStatusName = null;
        if (knowledgeBases.getVectorTaskId() != null) {
            KnowledgeVectorTask task = knowledgeVectorTaskMapper.selectById(knowledgeBases.getVectorTaskId());
            if (task != null && userId.equals(task.getUserId())) {
                taskStatusCode = task.getStatus();
                KnowledgeVectorTaskStatusEnum statusEnum = KnowledgeVectorTaskStatusEnum.fromCode(taskStatusCode);
                taskStatusName = statusEnum != null ? statusEnum.getName() : "UNKNOWN";
                dto.setStartedAt(task.getStartedAt());
                dto.setCompletedAt(task.getCompletedAt());
            }
        }

        dto.setTaskStatusName(taskStatusName);
        return dto;
    }

    private KnowledgeBases requireKnowledgeBases(Long id) {
        KnowledgeBases knowledgeBases = baseMapper.selectById(id);
        if (knowledgeBases == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return knowledgeBases;
    }

    private KnowledgeBases requireKnowledgeBasesAndCheckOwnership(Long id, Long userId, String action) {
        KnowledgeBases knowledgeBases = requireKnowledgeBases(id);
        if (!knowledgeBases.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人知识库");
        }
        return knowledgeBases;
    }

    /**
     * 验证内容
     * 
     * 验证规则：
     * - type=1（项目文档）：必须是COS链接的PDF或MD文件
     * - type=2（GitHub链接）：必须是GitHub链接
     * - type=3（DeepWiki）：暂不支持
     */
    private void validateContent(KnowledgeBasesDTO dto) {
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

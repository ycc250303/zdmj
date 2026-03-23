package com.zdmj.knowledgeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.PageResult;
import com.zdmj.common.util.CosUtil;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.knowledgeService.dto.KnowledgeBasesDTO;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KnowledgeBasesServiceImpl extends ServiceImpl<KnowledgeBasesMapper, KnowledgeBases>
        implements KnowledgeBasesService {

    private final ProjectExperienceMapper projectExperienceMapper;

    public KnowledgeBasesServiceImpl(ProjectExperienceMapper projectExperienceMapper) {
        this.projectExperienceMapper = projectExperienceMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBases create(KnowledgeBasesDTO knowledgeBasesDTO) {
        Long userId = UserHolder.requireUserId();
        log.info("用户 {} 创建知识库: {}", userId, knowledgeBasesDTO.getName());

        // 1. 检查是否存在同名知识库
        if (baseMapper.existsByName(userId, knowledgeBasesDTO.getName(), null)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
        }

        // 2. 检查项目ID是否存在且属于当前用户
        if (knowledgeBasesDTO.getProjectId() == null) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
        }
        ProjectExperience projectExperience = projectExperienceMapper.selectById(knowledgeBasesDTO.getProjectId());
        if (projectExperience == null || !projectExperience.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
        }

        // 3. 验证文件类型和内容
        validateContent(knowledgeBasesDTO);

        // 4. 创建知识库实体
        KnowledgeBases knowledgeBases = new KnowledgeBases();
        knowledgeBases.setUserId(userId);
        knowledgeBases.setName(knowledgeBasesDTO.getName());
        knowledgeBases.setProjectId(knowledgeBasesDTO.getProjectId());
        // 直接设置List，TypeHandler会自动处理JSONB转换
        knowledgeBases.setTag(knowledgeBasesDTO.getTag());
        knowledgeBases.setType(knowledgeBasesDTO.getType());
        knowledgeBases.setContent(knowledgeBasesDTO.getContent());
        // 初始化vectorIds为空数组
        knowledgeBases.setVectorIds(new ArrayList<>());

        // 5. 保存到数据库
        boolean saved = save(knowledgeBases);
        if (!saved) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_SAVE_FAILED);
        }
        log.info("创建知识库成功（已下线在线向量化流程），ID: {}", knowledgeBases.getId());
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

        // 5. 项目ID一旦确定，不能修改（如果前端传了 projectId 且与原值不同则报错）
        if (knowledgeBasesDTO.getProjectId() != null
                && !knowledgeBases.getProjectId().equals(knowledgeBasesDTO.getProjectId())) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NAME_NOT_ALLOW_CHANGE);
        }

        // 6. 判断内容是否变化
        boolean contentChanged = knowledgeBasesDTO.getContent() != null
                && !knowledgeBasesDTO.getContent().equals(knowledgeBases.getContent());

        // 7. 更新知识库信息（只有非空字段才会覆盖原值）
        if (knowledgeBasesDTO.getName() != null) {
            knowledgeBases.setName(knowledgeBasesDTO.getName());
        }
        if (knowledgeBasesDTO.getTag() != null) {
            // 直接设置List，TypeHandler会自动处理JSONB转换
            knowledgeBases.setTag(knowledgeBasesDTO.getTag());
        }
        if (knowledgeBasesDTO.getType() != null) {
            knowledgeBases.setType(knowledgeBasesDTO.getType());
        }
        if (knowledgeBasesDTO.getContent() != null) {
            knowledgeBases.setContent(knowledgeBasesDTO.getContent());
        }

        // 在线向量化已下线：内容变化时仅清空历史向量元数据
        if (contentChanged) {
            knowledgeBases.setVectorIds(new ArrayList<>());
            knowledgeBases.setVectorTaskId(null);
            knowledgeBases.setVectorTaskStatus(null);
        }
        boolean updated = updateById(knowledgeBases);
        if (!updated) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_UPDATE_FAILED);
        }

        log.info("更新知识库成功（在线向量化已下线），ID: {}", knowledgeBases.getId());
        return knowledgeBases;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        KnowledgeBases knowledgeBases = requireKnowledgeBasesAndCheckOwnership(id, userId, "删除");
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED);
        }
        log.info("删除知识库任务已创建: {}", knowledgeBases.getName());
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
     * 知识类型常量
     */
    private static class TypeConstant {
        /** 项目文档类型（包含txt、pdf、md、普通URL等） */
        public static final int PROJECT_DOCUMENT = 1;
        /** GitHub链接类型（GitHub仓库或文件） */
        public static final int GITHUB_REPO = 2;
        /** 项目DeepWiki文档类型（暂不实现，留作扩展） */
        public static final int PROJECT_DEEPWIKI = 3;
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
        if (type == TypeConstant.PROJECT_DOCUMENT) {
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

        } else if (type == TypeConstant.GITHUB_REPO) {
            // type=2：GitHub链接，必须是GitHub链接
            if (!content.contains("github.com")) {
                throw new BusinessException(ErrorCode.URL_FORMAT_ERROR.getCode(),
                        "GitHub链接类型（type=2）必须是GitHub链接，当前内容不是GitHub链接");
            }

        } else if (type == TypeConstant.PROJECT_DEEPWIKI) {
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

}

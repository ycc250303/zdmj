package com.zdmj.knowledgeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.PageResult;
import com.zdmj.common.util.CosUtil;
import com.zdmj.exception.ErrorCode;
import com.zdmj.exception.BusinessException;
import com.zdmj.knowledgeService.dto.KnowledgeBasesDTO;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBases create(KnowledgeBasesDTO knowledgeBasesDTO) {
        Long userId = UserHolder.requireUserId();
        log.info("用户 {} 创建知识库: {}", userId, knowledgeBasesDTO.getName());

        // 1. 检查是否存在同名知识库
        if (baseMapper.existsByName(userId, knowledgeBasesDTO.getName(), null)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
        }

        // 2. 验证文件类型和内容
        validateContent(knowledgeBasesDTO);

        // 3. 创建知识库实体
        KnowledgeBases knowledgeBases = new KnowledgeBases();
        knowledgeBases.setUserId(userId);
        knowledgeBases.setName(knowledgeBasesDTO.getName());
        knowledgeBases.setProjectName(knowledgeBasesDTO.getProjectName());
        knowledgeBases.setFileType(knowledgeBasesDTO.getFileType());
        // 直接设置List，TypeHandler会自动处理JSONB转换
        knowledgeBases.setTag(knowledgeBasesDTO.getTag());
        knowledgeBases.setType(knowledgeBasesDTO.getType());
        knowledgeBases.setContent(knowledgeBasesDTO.getContent());
        // 初始化vectorIds为空数组
        knowledgeBases.setVectorIds(new ArrayList<>());

        // 4. 保存到数据库
        boolean saved = save(knowledgeBases);
        if (!saved) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_SAVE_FAILED);
        }
        log.info("知识库保存成功，ID: {}", knowledgeBases.getId());

        // 5. TODO：调用Python服务向量化
        return knowledgeBases;
    }

    @Override
    public List<KnowledgeBases> getByUserId() {
        return baseMapper.selectByUserId(UserHolder.requireUserId());
    }

    @Override
    public PageResult<KnowledgeBases> getPage(Integer page, Integer limit, String projectName, Integer type) {
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
        List<KnowledgeBases> data = baseMapper.selectPage(userId, offset, limit, projectName, type);

        // 查询总数
        Long total = baseMapper.countPage(userId, projectName, type);

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
        if (!knowledgeBases.getName().equals(knowledgeBasesDTO.getName())) {
            if (baseMapper.existsByName(userId, knowledgeBasesDTO.getName(), id)) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
            }
        }

        // 4. 验证内容
        validateContent(knowledgeBasesDTO);

        // 5. TODO：删除旧的向量数据（如果存在）

        // 6. 更新知识库信息
        knowledgeBases.setName(knowledgeBasesDTO.getName());
        knowledgeBases.setProjectName(knowledgeBasesDTO.getProjectName());
        knowledgeBases.setFileType(knowledgeBasesDTO.getFileType());
        // 直接设置List，TypeHandler会自动处理JSONB转换
        knowledgeBases.setTag(knowledgeBasesDTO.getTag());
        knowledgeBases.setType(knowledgeBasesDTO.getType());
        knowledgeBases.setContent(knowledgeBasesDTO.getContent());
        // 重置向量ID为空数组，等待重新向量化
        knowledgeBases.setVectorIds(new ArrayList<>());

        boolean updated = updateById(knowledgeBases);
        if (!updated) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_UPDATE_FAILED);
        }

        // 7. TODO：调用Python服务向量化
        log.info("知识库更新成功，ID: {}", knowledgeBases.getId());
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
        // TODO：删除向量数据
        log.info("删除知识库成功: {}", knowledgeBases.getName());
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
     * 文件类型常量
     */
    private class FileTypeConstant {
        /** 文本类型 */
        public static final int TXT = 1;
        /** URL链接类型 */
        public static final int URL = 2;
        /** PDF文档类型 */
        public static final int DOC = 3;
        /** Markdown文档类型 */
        public static final int MD = 4;
    }

    /**
     * 知识类型常量
     */
    private class KnowledgeTypeConstant {
        /** 项目文档 */
        public static final int USER_PROJECT_DOC = 1;
        /** 项目代码（GitHub仓库） */
        public static final int USER_PROJECT_CODE = 2;
        /** 技术文档 */
        public static final int TECH_DOC = 3;
        /** 其他 */
        public static final int OTHER = 4;
        /** 项目DeepWiki文档 */
        public static final int USER_PROJECT_DEEP_WIKI = 5;
    }

    /**
     * 验证内容
     */
    private void validateContent(KnowledgeBasesDTO dto) {
        // 验证doc或md类型：检查COS文件是否存在
        if (dto.getFileType() == FileTypeConstant.DOC || dto.getFileType() == FileTypeConstant.MD) {
            if (!dto.getContent().startsWith("http")) {
                if (dto.getFileType() == FileTypeConstant.DOC) {
                    throw new BusinessException(ErrorCode.PDF_URL_REQUIRED);
                } else {
                    throw new BusinessException(ErrorCode.MARKDOWN_URL_REQUIRED);
                }
            }
            // 从URL中提取COS key并验证文件是否存在
            String cosKey = extractCosKeyFromUrl(dto.getContent());
            if (cosKey != null && !CosUtil.fileExists(cosKey)) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS);
            }
        }

        // 验证url类型：检查URL格式
        if (dto.getFileType() == FileTypeConstant.URL) {
            if (!dto.getContent().startsWith("http://") && !dto.getContent().startsWith("https://")) {
                throw new BusinessException(ErrorCode.URL_FORMAT_ERROR);
            }
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

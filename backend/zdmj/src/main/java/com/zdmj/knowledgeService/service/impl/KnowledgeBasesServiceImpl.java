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
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.python.dto.PythonApiResponse;
import com.zdmj.python.dto.knowledge.DeleteVectorsRequest;
import com.zdmj.python.dto.knowledge.DeleteVectorsResult;
import com.zdmj.python.dto.knowledge.KnowledgeEmbeddingRequest;
import com.zdmj.python.dto.knowledge.KnowledgeEmbeddingTaskResult;
import com.zdmj.python.dto.knowledge.TaskStatusResponse;
import com.zdmj.python.service.PythonServiceClient;
import com.zdmj.python.constant.PythonAPI;
import com.zdmj.common.exception.PythonServiceException;

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
    private final PythonServiceClient pythonServiceClient;

    public KnowledgeBasesServiceImpl(ProjectExperienceMapper projectExperienceMapper,
            PythonServiceClient pythonServiceClient) {
        this.projectExperienceMapper = projectExperienceMapper;
        this.pythonServiceClient = pythonServiceClient;
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

        // 2. 检查项目名称是否存在
        if (projectExperienceMapper.selectIdByUserIdAndName(userId, knowledgeBasesDTO.getProjectName()) == null) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
        }

        // 3. 验证文件类型和内容
        validateContent(knowledgeBasesDTO);

        // 4. 创建知识库实体
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

        // 5. 保存到数据库
        boolean saved = save(knowledgeBases);
        if (!saved) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_SAVE_FAILED);
        }

        // 6. 调用Python服务向量化（异步任务，仅负责触发，不影响事务）
        KnowledgeEmbeddingTaskResult task = triggerEmbeddingTask(knowledgeBases.getId(), userId, "创建知识库触发向量化任务");
        if (task != null) {
            // 将任务ID和状态保存到知识库记录
            knowledgeBases.setVectorTaskId(task.getTaskId());
            knowledgeBases.setVectorTaskStatus(task.getStatus());
            updateById(knowledgeBases);
            log.info("创建知识库触发向量化任务成功: knowledgeId={}, taskId={}, status={}",
                    knowledgeBases.getId(), task.getTaskId(), task.getStatus());
        } else {
            // 任务创建失败，仅记录日志，不回滚知识库创建事务
            log.warn("创建知识库触发向量化任务失败: knowledgeId={}，知识库已创建，但向量化任务未启动，可稍后手动重试",
                    knowledgeBases.getId());
        }
        log.info("创建知识库任务已创建，ID: {}，向量化耗时较长，请耐心等待...", knowledgeBases.getId());
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
        if (knowledgeBasesDTO.getName() != null && !knowledgeBases.getName().equals(knowledgeBasesDTO.getName())) {
            if (baseMapper.existsByName(userId, knowledgeBasesDTO.getName(), id)) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
            }
        }

        // 4. 组合更新后的文件类型与内容用于校验（DTO 优先，其次使用原值）
        KnowledgeBasesDTO validateDTO = new KnowledgeBasesDTO();
        validateDTO.setFileType(
                knowledgeBasesDTO.getFileType() != null ? knowledgeBasesDTO.getFileType()
                        : knowledgeBases.getFileType());
        validateDTO.setContent(
                knowledgeBasesDTO.getContent() != null ? knowledgeBasesDTO.getContent()
                        : knowledgeBases.getContent());
        validateContent(validateDTO);

        // 5. 项目名称一旦确定，不能修改（如果前端传了 projectName 且与原值不同则报错）
        if (knowledgeBasesDTO.getProjectName() != null
                && !knowledgeBases.getProjectName().equals(knowledgeBasesDTO.getProjectName())) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NAME_NOT_ALLOW_CHANGE);
        }

        // 6. 判断是否需要新向量化（基于原始值与 DTO 中的非空值比较）
        boolean contentChanged = knowledgeBasesDTO.getContent() != null
                && !knowledgeBasesDTO.getContent().equals(knowledgeBases.getContent());
        boolean fileTypeChanged = knowledgeBasesDTO.getFileType() != null
                && !knowledgeBasesDTO.getFileType().equals(knowledgeBases.getFileType());
        boolean typeChanged = knowledgeBasesDTO.getType() != null
                && !knowledgeBasesDTO.getType().equals(knowledgeBases.getType());

        // 7. 更新知识库信息（只有非空字段才会覆盖原值）
        if (knowledgeBasesDTO.getName() != null) {
            knowledgeBases.setName(knowledgeBasesDTO.getName());
        }
        if (knowledgeBasesDTO.getFileType() != null) {
            knowledgeBases.setFileType(knowledgeBasesDTO.getFileType());
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

        // 如果只是知识库名称和标签变化，不需要重新向量化；
        // 如果知识库内容（content）、文件类型（fileType）、知识类型（type）有任一变化，则需要重新向量化
        if (contentChanged || fileTypeChanged || typeChanged) {
            // 重置向量ID为空数组，等待重新向量化
            knowledgeBases.setVectorIds(new ArrayList<>());
            // 调用Python服务重新向量化（异步任务）
            KnowledgeEmbeddingTaskResult task = triggerEmbeddingTask(knowledgeBases.getId(), userId, "更新知识库触发重新向量化任务");
            if (task != null) {
                // 将任务ID和状态保存到知识库记录
                knowledgeBases.setVectorTaskId(task.getTaskId());
                knowledgeBases.setVectorTaskStatus(task.getStatus());
            } else {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED);
            }
        }
        boolean updated = updateById(knowledgeBases);
        if (!updated) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_UPDATE_FAILED);
        }

        log.info("更新知识库任务已创建，ID: {}，向量化耗时较长，请耐心等待...", knowledgeBases.getId());
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
        // 调用Python服务异步删除向量数据
        triggerDeleteVectorsTask(id, userId);
        log.info("删除知识库任务已创建: {}", knowledgeBases.getName());
    }

    /**
     * 触发知识库向量化/重新向量化任务（创建或更新场景共用）
     *
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     * @param actionDesc  日志中的动作描述（例如：创建/更新）
     * @return 任务结果，如果创建失败或返回为空则返回 null
     */
    private KnowledgeEmbeddingTaskResult triggerEmbeddingTask(Long knowledgeId, Long userId, String actionDesc) {
        try {
            KnowledgeEmbeddingRequest request = new KnowledgeEmbeddingRequest();
            request.setKnowledgeId(knowledgeId);
            request.setUserId(userId);

            log.debug("{}开始: knowledgeId={}, userId={}, 请求Python服务: POST /api/knowledge/embedding",
                    actionDesc, knowledgeId, userId);

            PythonApiResponse<KnowledgeEmbeddingTaskResult> response = pythonServiceClient
                    .post(PythonAPI.Knowledge.EMBEDDING, request, KnowledgeEmbeddingTaskResult.class)
                    .block();

            // 详细记录响应信息用于诊断
            if (response != null) {
                log.debug("{}响应接收: knowledgeId={}, code={}, msg={}, data={}",
                        actionDesc, knowledgeId, response.getCode(), response.getMsg(), response.getData());

                if (response.getData() != null) {
                    // PythonServiceClient 已经处理了类型转换，直接使用
                    KnowledgeEmbeddingTaskResult task = response.getData();
                    log.info("{}成功: knowledgeId={}, taskId={}, status={}, message={}",
                            actionDesc, knowledgeId, task.getTaskId(), task.getStatus(), task.getMessage());
                    return task;
                } else {
                    log.warn("{}返回为空: knowledgeId={}, response.code={}, response.msg={}, response.data=null",
                            actionDesc, knowledgeId, response.getCode(), response.getMsg());
                    return null;
                }
            } else {
                log.warn("{}响应为null: knowledgeId={}", actionDesc, knowledgeId);
                return null;
            }
        } catch (PythonServiceException e) {
            // 不回滚业务事务，只记录错误，后续可支持手动重试
            log.error("{}失败 (PythonServiceException): knowledgeId={}, errorCode={}, httpStatus={}, errorMsg={}",
                    actionDesc, knowledgeId, e.getCode(), e.getHttpStatus(), e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("{}发生未知异常: knowledgeId={}, exceptionType={}, errorMsg={}",
                    actionDesc, knowledgeId, e.getClass().getName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 触发整库向量删除任务（删除知识库时使用）
     *
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     */
    private void triggerDeleteVectorsTask(Long knowledgeId, Long userId) {
        try {
            DeleteVectorsRequest request = new DeleteVectorsRequest();
            request.setKnowledgeId(knowledgeId);
            request.setUserId(userId);

            PythonApiResponse<DeleteVectorsResult> response = pythonServiceClient
                    .post(PythonAPI.Knowledge.DELETE_VECTORS, request, DeleteVectorsResult.class)
                    .block();

            if (response != null && response.getData() != null) {
                DeleteVectorsResult task = response.getData();
                log.info("删除知识库触发整库向量删除任务成功: knowledgeId={}, taskId={}, status={}",
                        knowledgeId, task.getTaskId(), task.getStatus());
            } else {
                log.warn("删除知识库触发整库向量删除任务返回为空: knowledgeId={}", knowledgeId);
            }
        } catch (PythonServiceException e) {
            // 不回滚业务删除，只记录错误，后续可通过任务查询或管理工具补偿
            log.error("调用Python服务删除知识库向量失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("调用Python服务删除知识库向量发生未知异常: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
        }
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
     * 验证内容
     */
    private void validateContent(KnowledgeBasesDTO dto) {
        Integer fileType = dto.getFileType();
        String content = dto.getContent();

        // fileType 为空时，说明更新场景下该字段可能未传，直接跳过校验
        if (fileType == null) {
            return;
        }

        // 验证doc或md类型：检查COS文件是否存在
        if (fileType == FileTypeConstant.DOC || fileType == FileTypeConstant.MD) {
            if (content == null || !content.startsWith("http")) {
                if (fileType == FileTypeConstant.DOC) {
                    throw new BusinessException(ErrorCode.PDF_URL_REQUIRED);
                } else {
                    throw new BusinessException(ErrorCode.MARKDOWN_URL_REQUIRED);
                }
            }
            // 从URL中提取COS key并验证文件是否存在
            String cosKey = extractCosKeyFromUrl(content);
            if (cosKey != null && !CosUtil.fileExists(cosKey)) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS);
            }
        }

        // 验证url类型：检查URL格式
        if (fileType == FileTypeConstant.URL) {
            if (content == null || (!content.startsWith("http://") && !content.startsWith("https://"))) {
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

    @Override
    public TaskStatusResponse refreshVectorTaskStatus(Long knowledgeId) {
        Long userId = UserHolder.requireUserId();
        // 1. 查询知识库并校验所有权
        KnowledgeBases knowledgeBases = requireKnowledgeBasesAndCheckOwnership(knowledgeId, userId, "查询");

        // 2. 检查是否有任务ID
        if (knowledgeBases.getVectorTaskId() == null || knowledgeBases.getVectorTaskId().isEmpty()) {
            log.warn("知识库 {} 未触发向量化任务", knowledgeId);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), "未触发向量化任务");
        }

        String taskId = knowledgeBases.getVectorTaskId();
        log.info("刷新向量化任务状态: knowledgeId={}, taskId={}", knowledgeId, taskId);

        try {
            // 3. 调用 Python 服务查询任务状态
            PythonApiResponse<TaskStatusResponse> response = pythonServiceClient
                    .get(PythonAPI.Knowledge.EMBEDDING_TASK_STATUS + taskId, TaskStatusResponse.class)
                    .block();

            if (response == null || response.getData() == null) {
                log.warn("查询任务状态返回为空: knowledgeId={}, taskId={}", knowledgeId, taskId);
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), "任务状态查询失败");
            }

            // PythonServiceClient 已经处理了类型转换，直接使用
            TaskStatusResponse taskStatus = response.getData();

            String status = taskStatus.getStatus();

            // 4. 更新任务状态
            knowledgeBases.setVectorTaskStatus(status);

            // 5. 根据状态处理向量ID和错误信息
            if ("SUCCESS".equals(status)) {
                // 任务成功，更新向量ID
                if (taskStatus.getVectorIds() != null && !taskStatus.getVectorIds().isEmpty()) {
                    knowledgeBases.setVectorIds(taskStatus.getVectorIds());
                    log.info("任务成功，更新向量ID: knowledgeId={}, taskId={}, vectorIds={}",
                            knowledgeId, taskId, taskStatus.getVectorIds());
                } else {
                    log.warn("任务成功但未返回向量ID: knowledgeId={}, taskId={}", knowledgeId, taskId);
                }
            } else if ("FAILED".equals(status)) {
                // 任务失败，记录错误信息
                String errorMessage = taskStatus.getErrorMessage();
                log.error("向量化任务失败: knowledgeId={}, taskId={}, errorMessage={}",
                        knowledgeId, taskId, errorMessage);
                // 不修改 vectorIds，保持为空或旧值
            } else {
                // PENDING/RUNNING/CANCELLED 状态，仅更新状态，不修改 vectorIds
                log.debug("任务状态更新: knowledgeId={}, taskId={}, status={}", knowledgeId, taskId, status);
            }

            // 6. 写入数据库
            updateById(knowledgeBases);

            log.info("任务状态刷新完成: knowledgeId={}, taskId={}, status={}", knowledgeId, taskId, status);
            return taskStatus;

        } catch (PythonServiceException e) {
            log.error("查询任务状态失败: knowledgeId={}, taskId={}, error={}",
                    knowledgeId, taskId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(),
                    "查询任务状态失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("查询任务状态发生未知异常: knowledgeId={}, taskId={}, error={}",
                    knowledgeId, taskId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(),
                    "查询任务状态失败: " + e.getMessage());
        }
    }
}

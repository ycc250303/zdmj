package com.zdmj.knowledgeService.service;

import com.zdmj.common.model.PageResult;
import com.zdmj.knowledgeService.dto.KnowledgeBasesDTO;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.python.dto.knowledge.TaskStatusResponse;

import java.util.List;

public interface KnowledgeBasesService {

    /**
     * 创建知识库的方法
     *
     * @param knowledgeBasesDTO 包含知识库信息的数据传输对象
     * @return KnowledgeBases 返回创建后的知识库实体
     */
    KnowledgeBases create(KnowledgeBasesDTO knowledgeBasesDTO);

    /**
     * 根据用户ID获取知识库列表
     *
     * @return 返回用户关联的知识库列表，KnowledgeBases类型的集合
     */
    List<KnowledgeBases> getByUserId();

    /**
     * 分页查询知识库列表（支持项目名称和类型过滤）
     *
     * @param page        页码（从1开始），默认为1
     * @param limit       每页数量，默认为10
     * @param projectName 项目名称（可选）
     * @param type        知识类型（可选）
     * @return 分页结果
     */
    PageResult<KnowledgeBases> getPage(Integer page, Integer limit, String projectName, Integer type);

    /**
     * 根据ID获取知识库
     *
     * @param id 知识库ID
     * @return 返回知识库实体
     */
    KnowledgeBases getById(Long id);

    /**
     * 更新知识库
     *
     * @param knowledgeBasesDTO 包含知识库信息的数据传输对象
     * @return 返回更新后的知识库实体
     */
    KnowledgeBases update(KnowledgeBasesDTO knowledgeBasesDTO);

    /**
     * 删除知识库
     *
     * @param id 知识库ID
     */
    void delete(Long id);

    /**
     * 刷新向量化任务状态
     * 通过 Python 任务查询接口获取状态并回写 vector_ids 与任务状态
     *
     * @param knowledgeId 知识库ID
     * @return 任务状态响应，包含最新任务状态与向量信息
     */
    TaskStatusResponse refreshVectorTaskStatus(Long knowledgeId);
}

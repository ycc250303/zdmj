package com.zdmj.knowledgeService.service;

import com.zdmj.common.model.PageDTO;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentDTO;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicDTO;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;

public interface KnowledgeDocumentService {

    /**
     * 创建知识文档
     *
     * @param knowledgeDocumentDTO 知识文档DTO
     * @return 知识文档
     */
    KnowledgeDocument create(KnowledgeDocumentDTO knowledgeDocumentDTO);

    /**
     * 根据ID获取知识文档
     *
     * @param id 知识文档ID
     * @return 知识文档
     */
    KnowledgeDocument getById(Long id);

    /**
     * 对外查询单条文档（脱敏字段）
     */
    KnowledgeDocumentPublicDTO getPublicById(Long id);

    /**
     * 分页查询当前用户知识文档（列表项为 {@link KnowledgeDocumentPublicDTO}）
     */
    PageDTO<KnowledgeDocumentPublicDTO> getByPage(Integer page, Integer limit);

    /**
     * 更新知识文档
     *
     * @param knowledgeDocumentDTO 知识文档DTO
     * @return 知识文档
     */
    KnowledgeDocument update(KnowledgeDocumentDTO knowledgeDocumentDTO);

    /**
     * 删除知识文档
     *
     * @param id 知识文档ID
     */
    void delete(Long id);
}

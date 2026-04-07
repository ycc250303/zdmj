package com.zdmj.knowledgeService.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 根据文档ID查询知识库ID
     * @param documentId 文档ID
     * @return 知识库ID
     */
    Long selectKnowledgIdByDocumentId(Long documentId);

    /**
     * 根据知识库ID删除知识文档
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);
}

package com.zdmj.knowledgeService.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /** 短事务内按文档及用户编号锁定一条记录。 */
    KnowledgeDocument selectOwnedForUpdate(@org.apache.ibatis.annotations.Param("id") Long id,
            @org.apache.ibatis.annotations.Param("userId") Long userId);

    /**
     * 根据知识库ID删除知识文档
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);
}

package com.zdmj.knowledgeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库Mapper接口
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础的 CRUD 方法
 */
@Mapper
public interface KnowledgeBasesMapper extends BaseMapper<KnowledgeBases> {

        /**
         * 根据用户ID查询知识库
         *
         * @param userId 用户ID
         * @return 知识库列表
         */
        KnowledgeBases selectByUserId(@Param("userId") Long userId);

        /**
         * 根据用户ID查询知识库ID
         *
         * @param userId 用户ID
         * @return 知识库ID
         */
        Long selectKnowledgeIdByUserId(@Param("userId") Long userId);

        /**
         * 按 scope 查询知识库 ID（系统库 scope=2/3 全局唯一一条）。
         */
        Long selectKnowledgeIdByScope(@Param("scope") int scope);
}
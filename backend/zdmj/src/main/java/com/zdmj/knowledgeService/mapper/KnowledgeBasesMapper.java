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
         * 根据用户ID查询所有知识库
         *
         * @param userId 用户ID
         * @return 知识库列表
         */
        List<KnowledgeBases> selectByUserId(@Param("userId") Long userId);

        /**
         * 根据用户ID和项目名称查询知识库列表
         *
         * @param userId      用户ID
         * @param projectName 项目名称
         * @return 知识库列表
         */
        List<KnowledgeBases> selectByUserIdAndProjectName(
                        @Param("userId") Long userId,
                        @Param("projectName") String projectName);

        /**
         * 根据用户ID和知识类型查询知识库列表
         *
         * @param userId 用户ID
         * @param type   知识类型
         * @return 知识库列表
         */
        List<KnowledgeBases> selectByUserIdAndType(
                        @Param("userId") Long userId,
                        @Param("type") Integer type);

        /**
         * 检查用户是否存在同名知识库
         *
         * @param userId    用户ID
         * @param name      知识库名称
         * @param excludeId 排除的ID（用于更新时检查）
         * @return true表示存在，false表示不存在
         */
        boolean existsByName(
                        @Param("userId") Long userId,
                        @Param("name") String name,
                        @Param("excludeId") Long excludeId);

        /**
         * 根据用户ID统计知识库数量
         *
         * @param userId 用户ID
         * @return 知识库数量
         */
        Long countByUserId(@Param("userId") Long userId);

        /**
         * 根据项目名称查询知识库列表
         *
         * @param userId      用户ID
         * @param projectName 项目名称
         * @return 知识库列表
         */
        List<KnowledgeBases> selectByProjectName(
                        @Param("userId") Long userId,
                        @Param("projectName") String projectName);

        /**
         * 分页查询知识库列表（支持项目名称和类型过滤）
         *
         * @param userId      用户ID
         * @param offset      偏移量（(page - 1) * limit）
         * @param limit       每页数量
         * @param projectName 项目名称（可选）
         * @param type        知识类型（可选）
         * @return 知识库列表
         */
        List<KnowledgeBases> selectPage(
                        @Param("userId") Long userId,
                        @Param("offset") Integer offset,
                        @Param("limit") Integer limit,
                        @Param("projectName") String projectName,
                        @Param("type") Integer type);

        /**
         * 统计符合条件的知识库数量（用于分页）
         *
         * @param userId      用户ID
         * @param projectName 项目名称（可选）
         * @param type        知识类型（可选）
         * @return 知识库数量
         */
        Long countPage(
                        @Param("userId") Long userId,
                        @Param("projectName") String projectName,
                        @Param("type") Integer type);
}
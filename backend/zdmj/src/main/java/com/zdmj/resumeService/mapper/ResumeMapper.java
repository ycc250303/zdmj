package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.Resume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 简历Mapper接口
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础的 CRUD 方法
 */
@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {

    /**
     * 根据用户ID查询所有简历
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId 用户ID
     * @return 简历列表
     */
    List<Resume> selectByUserId(@Param("userId") Long userId);

    /**
     * 检查用户是否存在同名简历
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId    用户ID
     * @param name      简历名称
     * @param excludeId 排除的简历ID（用于更新时检查，如果为null则不排除）
     * @return 如果存在同名简历返回true，否则返回false
     */
    boolean existsByName(@Param("userId") Long userId, @Param("name") String name, @Param("excludeId") Long excludeId);
}

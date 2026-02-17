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
}

package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.Education;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教育经历 Mapper
 */
@Mapper
public interface EducationMapper extends BaseMapper<Education> {

    /**
     * 根据用户 ID 查询全部教育经历
     */
    List<Education> selectByUserId(@Param("userId") Long userId);
}

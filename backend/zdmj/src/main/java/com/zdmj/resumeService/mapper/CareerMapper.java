package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.Career;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作/实习经历 Mapper
 */
@Mapper
public interface CareerMapper extends BaseMapper<Career> {

    /**
     * 根据用户 ID 查询全部工作经历
     */
    List<Career> selectByUserId(@Param("userId") Long userId);
}

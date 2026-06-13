package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.Award;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AwardMapper extends BaseMapper<Award> {

    List<Award> selectByUserId(@Param("userId") Long userId);
}

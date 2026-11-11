package com.zdmj.resumeService.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 整份简历查询。一名用户的技能、经历和资料在一次查询中返回。
 */
@Mapper
public interface ResumeMapper {

    /**
     * 按用户编号读取整份简历。
     * 技能取该用户更新时间最新的一行。教育按入学时间升序，工作、项目按开始时间降序，获奖按获奖日期降序。
     *
     * @param userId 用户编号
     * @return 整份内容；用户不存在时返回 null
     */
    ResumeContentRow selectContentByUserId(@Param("userId") Long userId);
}

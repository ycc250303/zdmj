package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.Skill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 技能Mapper接口
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础的 CRUD 方法
 */
@Mapper
public interface SkillMapper extends BaseMapper<Skill> {

    /**
     * 根据用户ID查询所有技能
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId 用户ID
     * @return 技能列表
     */
    List<Skill> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询技能ID列表
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId 用户ID
     * @return 技能ID列表
     */
    List<Long> selectSkillIds(@Param("userId") Long userId);
}

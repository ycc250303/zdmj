package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.SkillDTO;
import com.zdmj.resumeService.entity.Skill;
import java.util.List;

/**
 * 技能服务接口
 */
public interface SkillService {

    /**
     * 创建技能
     *
     * @param skill 技能实体
     * @return 创建的技能实体
     */
    Skill create(SkillDTO skillDTO);

    /**
     * 根据ID查询技能
     *
     * @param id 技能ID
     * @return 技能实体
     */
    Skill getById(Long id);

    /**
     * 根据用户ID查询所有技能
     *
     * @return 技能列表
     */
    List<Skill> getByUserId();

    /**
     * 更新技能
     *
     * @param skill 技能实体
     * @return 更新后的技能实体
     */
    Skill update(SkillDTO skillDTO);

    /**
     * 删除技能
     *
     * @param id 技能ID
     */
    void delete(Long id);
}

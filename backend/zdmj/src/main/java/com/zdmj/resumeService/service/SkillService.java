package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.SkillRequest;
import com.zdmj.resumeService.dto.SkillResponse;
import com.zdmj.resumeService.entity.Skill;

/**
 * 技能服务。一项技能对应一行。
 */
public interface SkillService {

    /**
     * 为当前用户新增一项技能。
     *
     * @param skillRequest 技能名称和说明
     * @return 保存后的技能
     */
    SkillResponse create(SkillRequest skillRequest);

    /**
     * 更新当前用户的一项技能。说明为空白时清空原说明。
     *
     * @param skillRequest 含技能编号、名称和说明
     * @return 更新后的技能
     */
    SkillResponse update(SkillRequest skillRequest);

    /**
     * 删除当前用户的一项技能。
     *
     * @param id 技能编号
     */
    void delete(Long id);

    /**
     * 把已确认归属的技能写成本次请求。调用方负责创建组校验。
     *
     * @param existing 当前用户已加载的技能
     * @param skillRequest 本次技能内容
     */
    void updateOwned(Skill existing, SkillRequest skillRequest);

    /**
     * 删除已确认属于当前用户的技能。
     *
     * @param id 技能编号
     */
    void deleteOwned(Long id);

    /**
     * 把已加载的技能转成响应。
     *
     * @param skill 技能实体，为空时返回 null
     * @return 技能响应
     */
    SkillResponse toResponse(Skill skill);
}

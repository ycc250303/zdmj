package com.zdmj.resumeService.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.util.SkillContentValidator;
import com.zdmj.exception.BusinessException;
import com.zdmj.resumeService.dto.SkillDTO;
import com.zdmj.resumeService.dto.SkillItemDTO;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.SkillMapper;
import com.zdmj.resumeService.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能服务实现类
 */
@Slf4j
@Service
public class SkillServiceImpl implements SkillService {

    private final SkillMapper skillMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillServiceImpl(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    @Override
    public Skill create(SkillDTO skillDTO) {
        Long userId = requireUserId();

        Skill skill = new Skill();
        skill.setUserId(userId);
        skill.setName(skillDTO.getName());

        // 如果提供了 content，进行转换和验证
        if (skillDTO.getContent() != null) {
            String contentJson = convertContentToJson(skillDTO.getContent());
            skill.setContent(contentJson);
        }

        LocalDateTime now = DateTimeUtil.now();
        skill.setCreatedAt(now);
        skill.setUpdatedAt(now);

        int result = skillMapper.insert(skill);
        if (result <= 0) {
            throw new BusinessException(500, "添加技能失败");
        }
        log.info("添加技能成功: {}", skill.getName());
        return skill;
    }

    @Override
    public Skill getById(Long id) {
        return requireSkill(id);
    }

    @Override
    public List<Skill> getByUserId() {
        Long userId = requireUserId();
        return skillMapper.selectByUserId(userId);
    }

    @Override
    public Skill update(SkillDTO skillDTO) {
        Long userId = requireUserId();

        Long id = skillDTO.getId();
        if (id == null) {
            throw new BusinessException(400, "技能ID不能为空");
        }

        Skill skill = requireSkillAndCheckOwnership(id, userId, "修改");

        // 更新字段
        if (skillDTO.getName() != null) {
            skill.setName(skillDTO.getName());
        }

        // 如果提供了 content，进行转换和验证
        if (skillDTO.getContent() != null) {
            String contentJson = convertContentToJson(skillDTO.getContent());
            skill.setContent(contentJson);
        }

        LocalDateTime now = DateTimeUtil.now();
        skill.setUpdatedAt(now);

        int result = skillMapper.updateById(skill);
        if (result <= 0) {
            throw new BusinessException(500, "更新技能失败");
        }
        log.info("更新技能成功: {}", skill.getName());
        return skill;
    }

    @Override
    public void delete(Long id) {
        Long userId = requireUserId();
        Skill skill = requireSkillAndCheckOwnership(id, userId, "删除");

        int result = skillMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(500, "删除技能失败");
        }
        log.info("删除技能成功: {}", skill.getName());
    }

    /**
     * 校验用户是否已登录，返回用户ID
     *
     * @return 用户ID
     * @throws BusinessException 如果用户未登录
     */
    private Long requireUserId() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "用户未登录");
        }
        return userId;
    }

    /**
     * 校验技能是否存在，返回技能实体
     *
     * @param id 技能ID
     * @return 技能实体
     * @throws BusinessException 如果技能不存在
     */
    private Skill requireSkill(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new BusinessException(404, "技能不存在");
        }
        return skill;
    }

    /**
     * 校验技能是否存在且用户有权限操作，返回技能实体
     *
     * @param id     技能ID
     * @param userId 用户ID
     * @param action 操作类型（用于错误提示）
     * @return 技能实体
     * @throws BusinessException 如果技能不存在或用户无权限
     */
    private Skill requireSkillAndCheckOwnership(Long id, Long userId, String action) {
        Skill skill = requireSkill(id);
        if (!skill.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权" + action + "他人技能");
        }
        return skill;
    }

    /**
     * 将 List<SkillItemDTO> 转换为 JSON 字符串，并验证结构
     */
    private String convertContentToJson(List<SkillItemDTO> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            throw new BusinessException(400, "技能内容不能为空");
        }

        try {
            // 转换为 JSON 字符串
            String json = objectMapper.writeValueAsString(contentList);

            // 验证并清理（确保只有 type 和 content 字段）
            return SkillContentValidator.validate(json);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "技能内容格式错误: " + e.getMessage());
        }
    }

    /**
     * 将 JSON 字符串转换为 List<SkillItemDTO>
     */
    public List<SkillItemDTO> convertJsonToContent(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<SkillItemDTO>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(400, "技能内容解析失败: " + e.getMessage());
        }
    }
}

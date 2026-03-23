package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.util.json.SkillContentValidator;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
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
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements SkillService {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        boolean saved = save(skill);
        if (!saved) {
            throw new BusinessException(ErrorCode.SKILL_ADD_FAILED);
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
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public Skill update(SkillDTO skillDTO) {
        Long userId = requireUserId();

        Long id = skillDTO.getId();
        if (id == null) {
            throw new BusinessException(ErrorCode.SKILL_ID_EMPTY);
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

        boolean updated = updateById(skill);
        if (!updated) {
            throw new BusinessException(ErrorCode.SKILL_UPDATE_FAILED);
        }
        log.info("更新技能成功: {}", skill.getName());
        return skill;
    }

    @Override
    public void delete(Long id) {
        Long userId = requireUserId();
        Skill skill = requireSkillAndCheckOwnership(id, userId, "删除");

        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.SKILL_DELETE_FAILED);
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
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
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
        Skill skill = baseMapper.selectById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND);
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
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人技能");
        }
        return skill;
    }

    /**
     * 将 List<SkillItemDTO> 转换为 JSON 字符串，并验证结构
     */
    private String convertContentToJson(List<SkillItemDTO> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            throw new BusinessException(ErrorCode.SKILL_CONTENT_EMPTY);
        }

        try {
            // 转换为 JSON 字符串
            String json = objectMapper.writeValueAsString(contentList);

            // 验证并清理（确保只有 type 和 content 字段）
            return SkillContentValidator.validate(json);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SKILL_CONTENT_FORMAT_ERROR.getCode(),
                    ErrorCode.SKILL_CONTENT_FORMAT_ERROR.getMessage() + ": " + e.getMessage());
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
            throw new BusinessException(ErrorCode.SKILL_CONTENT_PARSE_FAILED.getCode(),
                    ErrorCode.SKILL_CONTENT_PARSE_FAILED.getMessage() + ": " + e.getMessage());
        }
    }
}

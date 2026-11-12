package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.SkillRequest;
import com.zdmj.resumeService.dto.SkillResponse;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.SkillMapper;
import com.zdmj.resumeService.service.SkillService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements SkillService {

    private final Validator validator;

    @Override
    public SkillResponse create(CurrentActor actor, SkillRequest skillRequest) {
        Long userId = actor.userId();
        validate(skillRequest, CreateGroup.class);

        Skill skill = new Skill();
        skill.setUserId(userId);
        applyFields(skill, skillRequest);

        boolean saved = save(skill);
        if (!saved) {
            throw new BusinessException(ErrorCode.SKILL_ADD_FAILED);
        }
        log.info("用户 {} 添加技能成功: id={}", userId, skill.getId());
        return toResponse(skill);
    }

    @Override
    public SkillResponse update(CurrentActor actor, SkillRequest skillRequest) {
        Long userId = actor.userId();
        Skill skill = requireSkillAndCheckOwnership(skillRequest.getId(), userId, "修改");
        validate(skillRequest, UpdateGroup.class);
        updateOwned(skill, skillRequest);
        return toResponse(skill);
    }

    @Override
    public void updateOwned(Skill existing, SkillRequest skillRequest) {
        applyFields(existing, skillRequest);
        if (!updateById(existing)) {
            throw new BusinessException(ErrorCode.SKILL_UPDATE_FAILED);
        }
        log.info("用户 {} 更新技能成功: id={}", existing.getUserId(), existing.getId());
    }

    @Override
    public void delete(CurrentActor actor, Long id) {
        Long userId = actor.userId();
        requireSkillAndCheckOwnership(id, userId, "删除");
        deleteOwned(id);
    }

    @Override
    public void deleteOwned(Long id) {
        if (!removeById(id)) {
            throw new BusinessException(ErrorCode.SKILL_DELETE_FAILED);
        }
        log.info("删除技能成功: id={}", id);
    }

    @Override
    public SkillResponse toResponse(Skill skill) {
        if (skill == null) {
            return null;
        }
        SkillResponse response = new SkillResponse();
        response.setId(skill.getId());
        response.setName(skill.getName());
        response.setDescription(skill.getDescription());
        return response;
    }

    /**
     * 写入名称和说明。名称去掉首尾空白；说明为空白时保存 null。
     *
     * @param skill 待写入的技能
     * @param skillRequest 已通过校验的请求
     */
    private void applyFields(Skill skill, SkillRequest skillRequest) {
        skill.setName(skillRequest.getName().trim());
        String description = skillRequest.getDescription();
        skill.setDescription(StringUtils.hasText(description) ? description.trim() : null);
    }

    private void validate(SkillRequest skillRequest, Class<?> group) {
        Set<ConstraintViolation<SkillRequest>> violations = validator.validate(skillRequest, group);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, detail);
        }
    }

    private Skill requireSkill(Long id) {
        Skill skill = baseMapper.selectById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND);
        }
        return skill;
    }

    private Skill requireSkillAndCheckOwnership(Long id, Long userId, String action) {
        Skill skill = requireSkill(id);
        if (!skill.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人技能");
        }
        return skill;
    }
}

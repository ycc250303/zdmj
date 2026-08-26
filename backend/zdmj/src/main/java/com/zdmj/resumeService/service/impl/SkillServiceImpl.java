package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements SkillService {

    private final Validator validator;

    @Override
    public SkillResponse create(SkillRequest skillRequest) {
        Long userId = requireUserId();
        validateContent(skillRequest, CreateGroup.class);

        Skill skill = new Skill();
        skill.setUserId(userId);
        skill.setContent(skillRequest.getContent());

        boolean saved = save(skill);
        if (!saved) {
            throw new BusinessException(ErrorCode.SKILL_ADD_FAILED);
        }
        log.info("添加技能成功: userId={}", userId);
        return convertToResponse(skill);
    }

    @Override
    public SkillResponse getById(Long id) {
        return convertToResponse(requireSkill(id));
    }

    @Override
    public List<SkillResponse> getByUserId() {
        Long userId = requireUserId();
        return baseMapper.selectByUserId(userId).stream().map(this::convertToResponse).toList();
    }

    @Override
    public SkillResponse update(SkillRequest skillRequest) {
        Long userId = requireUserId();

        Long id = skillRequest.getId();
        Skill skill = requireSkillAndCheckOwnership(id, userId, "修改");
        validateContent(skillRequest, UpdateGroup.class);
        skill.setContent(skillRequest.getContent());

        boolean updated = updateById(skill);
        if (!updated) {
            throw new BusinessException(ErrorCode.SKILL_UPDATE_FAILED);
        }
        log.info("更新技能成功: skillId={}", skill.getId());
        return convertToResponse(skill);
    }

    @Override
    public void delete(Long id) {
        Long userId = requireUserId();
        Skill skill = requireSkillAndCheckOwnership(id, userId, "删除");

        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.SKILL_DELETE_FAILED);
        }
        log.info("删除技能成功: skillId={}", skill.getId());
    }

    private SkillResponse convertToResponse(Skill skill) {
        SkillResponse response = new SkillResponse();
        response.setId(skill.getId());
        response.setContent(skill.getContent() != null ? skill.getContent() : Collections.emptyList());
        return response;
    }

    private void validateContent(SkillRequest skillRequest, Class<?> group) {
        Set<ConstraintViolation<SkillRequest>> violations = validator.validate(skillRequest, group);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), detail);
        }
    }

    private Long requireUserId() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return userId;
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
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人技能");
        }
        return skill;
    }
}

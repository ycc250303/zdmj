package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.SkillDTO;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.SkillMapper;
import com.zdmj.resumeService.service.SkillService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 技能服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements SkillService {

    private final Validator validator;

    @Override
    public Skill create(SkillDTO skillDTO) {
        Long userId = requireUserId();
        validateContent(skillDTO, CreateGroup.class);

        Skill skill = new Skill();
        skill.setUserId(userId);
        skill.setContent(skillDTO.getContent());

        boolean saved = save(skill);
        if (!saved) {
            throw new BusinessException(ErrorCode.SKILL_ADD_FAILED);
        }
        log.info("添加技能成功: userId={}", userId);
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
        Skill skill = requireSkillAndCheckOwnership(id, userId, "修改");
        validateContent(skillDTO, UpdateGroup.class);
        skill.setContent(skillDTO.getContent());

        boolean updated = updateById(skill);
        if (!updated) {
            throw new BusinessException(ErrorCode.SKILL_UPDATE_FAILED);
        }
        log.info("更新技能成功: skillId={}", skill.getId());
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
        log.info("删除技能成功: skillId={}", skill.getId());
    }

    /**
     * 用 Bean Validation 校验技能 content（覆盖 Controller 与简历同步等内部调用）。
     */
    private void validateContent(SkillDTO skillDTO, Class<?> group) {
        Set<ConstraintViolation<SkillDTO>> violations = validator.validate(skillDTO, group);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), detail);
        }
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

}

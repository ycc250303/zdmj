package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.AwardDTO;
import com.zdmj.resumeService.entity.Award;
import com.zdmj.resumeService.enums.AwardTypeEnum;
import com.zdmj.resumeService.mapper.AwardMapper;
import com.zdmj.resumeService.mapper.AwardStructMapper;
import com.zdmj.resumeService.service.AwardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class AwardServiceImpl extends ServiceImpl<AwardMapper, Award> implements AwardService {

    private final AwardStructMapper awardPatchMapper;

    @Override
    public Award create(AwardDTO awardDTO) {
        Long userId = UserHolder.requireUserId();
        validateAwardType(awardDTO.getAwardType());

        Award award = new Award();
        award.setUserId(userId);
        award.setAwardType(awardDTO.getAwardType());
        award.setName(awardDTO.getName());
        award.setAwardDate(awardDTO.getAwardDate());
        award.setDescription(awardDTO.getDescription());

        boolean saved = save(award);
        if (!saved) {
            throw new BusinessException(ErrorCode.AWARD_ADD_FAILED);
        }
        log.info("添加获奖信息成功: {}", award.getName());
        return award;
    }

    @Override
    public Award getById(Long id) {
        return requireAward(id);
    }

    @Override
    public List<Award> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public Award update(AwardDTO awardDTO) {
        Long userId = UserHolder.requireUserId();
        Award award = requireAwardAndCheckOwnership(awardDTO.getId(), userId, "修改");
        if (awardDTO.getAwardType() != null) {
            validateAwardType(awardDTO.getAwardType());
        }

        awardPatchMapper.updateEntityFromDto(awardDTO, award);

        boolean updated = updateById(award);
        if (!updated) {
            throw new BusinessException(ErrorCode.AWARD_UPDATE_FAILED);
        }
        log.info("更新获奖信息成功: {}", award.getName());
        return award;
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        Award award = requireAwardAndCheckOwnership(id, userId, "删除");
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.AWARD_DELETE_FAILED);
        }
        log.info("删除获奖信息成功: {}", award.getName());
    }

    private void validateAwardType(Integer awardType) {
        if (AwardTypeEnum.fromCode(awardType) == null) {
            throw new BusinessException(ErrorCode.AWARD_TYPE_INVALID);
        }
    }

    private Award requireAward(Long id) {
        Award award = baseMapper.selectById(id);
        if (award == null) {
            throw new BusinessException(ErrorCode.AWARD_NOT_FOUND);
        }
        return award;
    }

    private Award requireAwardAndCheckOwnership(Long id, Long userId, String action) {
        Award award = requireAward(id);
        if (!award.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人获奖信息");
        }
        return award;
    }
}

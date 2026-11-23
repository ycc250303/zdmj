package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.EducationResponse;
import com.zdmj.resumeService.entity.Education;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.EducationStructMapper;
import com.zdmj.resumeService.service.EducationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class EducationServiceImpl extends ServiceImpl<EducationMapper, Education> implements EducationService {

    private final EducationStructMapper educationPatchMapper;

    @Override
    public EducationResponse create(CurrentActor actor, EducationRequest educationRequest) {
        Long userId = actor.userId();
        DateTimeUtil.rejectIfEndBeforeStart(
                educationRequest.startDate(),
                educationRequest.endDate(),
                ErrorCode.EDUCATION_GRADUATE_TIME_INVALID);

        Education education = new Education();
        education.setUserId(userId);
        education.setSchool(educationRequest.school());
        education.setMajor(educationRequest.major());
        education.setDegree(educationRequest.degree());
        education.setStartDate(educationRequest.startDate());
        education.setEndDate(educationRequest.endDate());
        education.setGpa(educationRequest.gpa());
        boolean saved = save(education);
        if (!saved) {
            throw new BusinessException(ErrorCode.EDUCATION_ADD_FAILED);
        }
        log.info("用户 {} 添加教育经历成功: id={}", userId, education.getId());
        return convertToResponse(education);
    }

    @Override
    public EducationResponse update(CurrentActor actor, EducationRequest educationRequest) {
        Long userId = actor.userId();
        Education existingEducation = requireEducationAndCheckOwnership(educationRequest.id(), userId, "修改");
        return updateOwned(existingEducation, educationRequest);
    }

    @Override
    public EducationResponse updateOwned(Education existing, EducationRequest educationRequest) {
        educationPatchMapper.updateEntityFromDto(educationRequest, existing);
        DateTimeUtil.rejectIfEndBeforeStart(
                existing.getStartDate(),
                existing.getEndDate(),
                ErrorCode.EDUCATION_GRADUATE_TIME_INVALID);
        if (!updateById(existing)) {
            throw new BusinessException(ErrorCode.EDUCATION_UPDATE_FAILED);
        }
        log.info("用户 {} 更新教育经历成功: id={}", existing.getUserId(), existing.getId());
        return convertToResponse(existing);
    }

    @Override
    public void delete(CurrentActor actor, Long id) {
        Long userId = actor.userId();
        requireEducationAndCheckOwnership(id, userId, "删除");
        deleteOwned(id);
    }

    @Override
    public void deleteOwned(Long id) {
        if (!removeById(id)) {
            throw new BusinessException(ErrorCode.EDUCATION_DELETE_FAILED);
        }
        log.info("删除教育经历成功: id={}", id);
    }

    private EducationResponse convertToResponse(Education education) {
        return new EducationResponse(education.getId(), education.getUserId(), education.getSchool(),
                education.getMajor(), education.getDegree(), education.getStartDate(), education.getEndDate(),
                education.getGpa());
    }

    private Education requireEducation(Long id) {
        Education education = baseMapper.selectById(id);
        if (education == null) {
            throw new BusinessException(ErrorCode.EDUCATION_NOT_FOUND);
        }
        return education;
    }

    private Education requireEducationAndCheckOwnership(Long id, Long userId, String action) {
        Education education = requireEducation(id);
        if (!education.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人教育经历");
        }
        return education;
    }
}

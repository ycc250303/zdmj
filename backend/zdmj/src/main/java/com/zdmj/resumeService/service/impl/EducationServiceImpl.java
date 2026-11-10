package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.EducationResponse;
import com.zdmj.resumeService.entity.Education;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.EducationStructMapper;
import com.zdmj.resumeService.service.EducationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
@Service
public class EducationServiceImpl extends ServiceImpl<EducationMapper, Education> implements EducationService {

    private final EducationStructMapper educationPatchMapper;

    @Override
    public EducationResponse create(EducationRequest educationRequest) {
        Long userId = UserHolder.requireUserId();
        rejectIfEndBeforeStart(educationRequest.getStartDate(), educationRequest.getEndDate());

        Education education = new Education();
        education.setUserId(userId);
        education.setSchool(educationRequest.getSchool());
        education.setMajor(educationRequest.getMajor());
        education.setDegree(educationRequest.getDegree());
        education.setStartDate(educationRequest.getStartDate());
        education.setEndDate(educationRequest.getEndDate());
        education.setGpa(educationRequest.getGpa());
        boolean saved = save(education);
        if (!saved) {
            throw new BusinessException(ErrorCode.EDUCATION_ADD_FAILED);
        }
        log.info("用户 {} 添加教育经历成功: id={}", userId, education.getId());
        return convertToResponse(education);
    }

    @Override
    public EducationResponse update(EducationRequest educationRequest) {
        Long userId = UserHolder.requireUserId();
        Long id = educationRequest.getId();
        Education existingEducation = requireEducationAndCheckOwnership(id, userId, "修改");

        educationPatchMapper.updateEntityFromDto(educationRequest, existingEducation);
        rejectIfEndBeforeStart(existingEducation.getStartDate(), existingEducation.getEndDate());

        boolean updated = updateById(existingEducation);
        if (!updated) {
            throw new BusinessException(ErrorCode.EDUCATION_UPDATE_FAILED);
        }

        log.info("用户 {} 更新教育经历成功: id={}", userId, id);
        return convertToResponse(existingEducation);
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        requireEducationAndCheckOwnership(id, userId, "删除");

        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.EDUCATION_DELETE_FAILED);
        }

        log.info("用户 {} 删除教育经历成功: id={}", userId, id);
    }

    /**
     * 毕业日期早于入学日期时拒绝写入。任一端为空时跳过，在读记录可以没有毕业日期。
     *
     * @param startDate 入学日期
     * @param endDate 毕业日期
     * @throws BusinessException 毕业日期早于入学日期时抛出 {@link ErrorCode#EDUCATION_GRADUATE_TIME_INVALID}
     */
    private void rejectIfEndBeforeStart(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.EDUCATION_GRADUATE_TIME_INVALID);
        }
    }

    private EducationResponse convertToResponse(Education education) {
        EducationResponse response = new EducationResponse();
        BeanUtils.copyProperties(education, response);
        return response;
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

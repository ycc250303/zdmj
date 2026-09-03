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

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class EducationServiceImpl extends ServiceImpl<EducationMapper, Education> implements EducationService {

    private final EducationStructMapper educationPatchMapper;

    @Override
    public EducationResponse create(EducationRequest educationRequest) {
        Long userId = UserHolder.requireUserId();

        if (educationRequest.getStartDate() != null && educationRequest.getEndDate() != null
                && educationRequest.getEndDate().isBefore(educationRequest.getStartDate())) {
            throw new BusinessException(ErrorCode.EDUCATION_GRADUATE_TIME_INVALID);
        }

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
        log.info("添加教育经历成功: {}", education.getSchool());
        return convertToResponse(education);
    }

    @Override
    public EducationResponse update(EducationRequest educationRequest) {
        Long userId = UserHolder.requireUserId();
        Long id = educationRequest.getId();
        Education existingEducation = requireEducationAndCheckOwnership(id, userId, "修改");

        educationPatchMapper.updateEntityFromDto(educationRequest, existingEducation);

        if (existingEducation.getStartDate() != null && existingEducation.getEndDate() != null) {
            if (existingEducation.getEndDate().isBefore(existingEducation.getStartDate())) {
                throw new BusinessException(ErrorCode.EDUCATION_GRADUATE_TIME_INVALID);
            }
        }

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

    @Override
    public EducationResponse getById(Long id) {
        return convertToResponse(requireEducation(id));
    }

    @Override
    public List<EducationResponse> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId).stream().map(this::convertToResponse).toList();
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

package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.CareerRequest;
import com.zdmj.resumeService.dto.CareerResponse;
import com.zdmj.resumeService.entity.Career;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.CareerStructMapper;
import com.zdmj.resumeService.service.CareerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class CareerServiceImpl extends ServiceImpl<CareerMapper, Career> implements CareerService {

    private final CareerStructMapper careerPatchMapper;

    @Override
    public CareerResponse create(CareerRequest careerRequest) {
        Long userId = UserHolder.requireUserId();
        Career career = new Career();
        career.setUserId(userId);
        career.setCompany(careerRequest.getCompany());
        career.setPosition(careerRequest.getPosition());
        career.setStartDate(careerRequest.getStartDate());
        career.setEndDate(careerRequest.getEndDate());
        career.setDetails(careerRequest.getDetails());
        boolean saved = save(career);
        if (!saved) {
            throw new BusinessException(ErrorCode.CAREER_ADD_FAILED);
        }
        log.info("添加工作经历成功: {}", career.getCompany());
        return convertToResponse(career);
    }

    @Override
    public CareerResponse getById(Long id) {
        return convertToResponse(requireCareer(id));
    }

    @Override
    public List<CareerResponse> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId).stream().map(this::convertToResponse).toList();
    }

    @Override
    public CareerResponse update(CareerRequest careerRequest) {
        Long userId = UserHolder.requireUserId();
        Long id = careerRequest.getId();
        Career career = requireCareerAndCheckOwnership(id, userId, "修改");

        careerPatchMapper.updateEntityFromDto(careerRequest, career);

        if (career.getStartDate() != null && career.getEndDate() != null) {
            if (career.getEndDate().isBefore(career.getStartDate())) {
                throw new BusinessException(ErrorCode.CAREER_LEAVE_TIME_INVALID);
            }
        }

        boolean updated = updateById(career);
        if (!updated) {
            throw new BusinessException(ErrorCode.CAREER_UPDATE_FAILED);
        }

        log.info("更新工作经历成功: {}", career.getCompany());
        return convertToResponse(career);
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        Career career = requireCareerAndCheckOwnership(id, userId, "删除");
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.CAREER_DELETE_FAILED);
        }

        log.info("删除工作经历成功: {}", career.getCompany());
    }

    private CareerResponse convertToResponse(Career career) {
        CareerResponse response = new CareerResponse();
        BeanUtils.copyProperties(career, response);
        return response;
    }

    private Career requireCareer(Long id) {
        Career career = baseMapper.selectById(id);
        if (career == null) {
            throw new BusinessException(ErrorCode.CAREER_NOT_FOUND);
        }
        return career;
    }

    private Career requireCareerAndCheckOwnership(Long id, Long userId, String action) {
        Career career = requireCareer(id);
        if (!career.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人工作经历");
        }
        return career;
    }
}

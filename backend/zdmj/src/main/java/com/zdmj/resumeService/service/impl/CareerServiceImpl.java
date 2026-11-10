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

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
@Service
public class CareerServiceImpl extends ServiceImpl<CareerMapper, Career> implements CareerService {

    private final CareerStructMapper careerPatchMapper;

    @Override
    public CareerResponse create(CareerRequest careerRequest) {
        Long userId = UserHolder.requireUserId();
        rejectIfEndBeforeStart(careerRequest.getStartDate(), careerRequest.getEndDate());

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
        log.info("用户 {} 添加工作经历成功: id={}", userId, career.getId());
        return convertToResponse(career);
    }

    @Override
    public CareerResponse update(CareerRequest careerRequest) {
        Long userId = UserHolder.requireUserId();
        Long id = careerRequest.getId();
        Career career = requireCareerAndCheckOwnership(id, userId, "修改");

        careerPatchMapper.updateEntityFromDto(careerRequest, career);
        rejectIfEndBeforeStart(career.getStartDate(), career.getEndDate());

        boolean updated = updateById(career);
        if (!updated) {
            throw new BusinessException(ErrorCode.CAREER_UPDATE_FAILED);
        }

        log.info("用户 {} 更新工作经历成功: id={}", userId, id);
        return convertToResponse(career);
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        requireCareerAndCheckOwnership(id, userId, "删除");
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.CAREER_DELETE_FAILED);
        }

        log.info("用户 {} 删除工作经历成功: id={}", userId, id);
    }

    /**
     * 结束日期早于开始日期时拒绝写入。任一端为空时跳过，在职记录可以没有离职日期。
     *
     * @param startDate 入职日期
     * @param endDate 离职日期
     * @throws BusinessException 离职日期早于入职日期时抛出 {@link ErrorCode#CAREER_LEAVE_TIME_INVALID}
     */
    private void rejectIfEndBeforeStart(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.CAREER_LEAVE_TIME_INVALID);
        }
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
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人工作经历");
        }
        return career;
    }
}

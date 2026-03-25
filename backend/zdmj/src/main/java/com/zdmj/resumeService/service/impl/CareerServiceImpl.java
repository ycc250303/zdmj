package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.resumeService.dto.CareerDTO;
import com.zdmj.resumeService.entity.Career;
import com.zdmj.resumeService.mapper.CareerStructMapper;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.service.CareerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class CareerServiceImpl extends ServiceImpl<CareerMapper, Career> implements CareerService {

    private final CareerStructMapper careerPatchMapper;

    @Override
    public Career create(CareerDTO careerDTO) {
        Long userId = UserHolder.requireUserId();
        Career career = new Career();
        career.setUserId(userId);
        career.setCompany(careerDTO.getCompany());
        career.setPosition(careerDTO.getPosition());
        career.setStartDate(careerDTO.getStartDate());
        career.setEndDate(careerDTO.getEndDate());
        career.setVisible(careerDTO.getVisible());
        career.setDetails(careerDTO.getDetails());
        boolean saved = save(career);
        if (!saved) {
            throw new BusinessException(ErrorCode.CAREER_ADD_FAILED);
        }
        log.info("添加工作经历成功: {}", career.getCompany());
        return career;
    }

    @Override
    public Career getById(Long id) {
        return requireCareer(id);
    }

    @Override
    public List<Career> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId, null);
    }

    @Override
    public Career update(CareerDTO careerDTO) {
        Long userId = UserHolder.requireUserId();
        Long id = careerDTO.getId();
        if (id == null) {
            throw new BusinessException(ErrorCode.CAREER_ID_EMPTY);
        }
        Career career = requireCareerAndCheckOwnership(id, userId, "修改");

        careerPatchMapper.updateEntityFromDto(careerDTO, career);

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
        return career;
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

    /**
     * 校验工作经历是否存在，返回工作经历实体
     *
     * @param id 工作经历ID
     * @return 工作经历实体
     * @throws BusinessException 如果工作经历不存在
     */
    private Career requireCareer(Long id) {
        Career career = baseMapper.selectById(id);
        if (career == null) {
            throw new BusinessException(ErrorCode.CAREER_NOT_FOUND);
        }
        return career;
    }

    /**
     * 校验工作经历是否存在且用户有权限操作，返回工作经历实体
     *
     * @param id     工作经历ID
     * @param userId 用户ID
     * @param action 操作类型（用于错误提示）
     * @return 工作经历实体
     * @throws BusinessException 如果工作经历不存在或用户无权限
     */
    private Career requireCareerAndCheckOwnership(Long id, Long userId, String action) {
        Career career = requireCareer(id);
        if (!career.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人工作经历");
        }
        return career;
    }
}

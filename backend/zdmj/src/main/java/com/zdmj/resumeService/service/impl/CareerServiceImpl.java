package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.exception.BusinessException;
import com.zdmj.resumeService.dto.CareerDTO;
import com.zdmj.resumeService.entity.Career;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.service.CareerService;
import com.zdmj.common.util.BeanUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CareerServiceImpl implements CareerService {

    private final CareerMapper careerMapper;

    public CareerServiceImpl(CareerMapper careerMapper) {
        this.careerMapper = careerMapper;
    }

    @Override
    public Career create(CareerDTO careerDTO) {
        Long userId = requireUserId();
        Career career = new Career();
        career.setUserId(userId);
        career.setCompany(careerDTO.getCompany());
        career.setPosition(careerDTO.getPosition());
        career.setStartDate(careerDTO.getStartDate());
        career.setEndDate(careerDTO.getEndDate());
        career.setVisible(careerDTO.getVisible());
        career.setDetails(careerDTO.getDetails());
        LocalDateTime now = DateTimeUtil.now();
        career.setCreatedAt(now);
        career.setUpdatedAt(now);
        int result = careerMapper.insert(career);
        if (result <= 0) {
            throw new BusinessException(500, "添加工作经历失败");
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
        Long userId = requireUserId();
        return careerMapper.selectByUserId(userId, null);
    }

    @Override
    public Career update(CareerDTO careerDTO) {
        Long userId = requireUserId();
        Long id = careerDTO.getId();
        if (id == null) {
            throw new BusinessException(400, "工作经历ID不能为空");
        }
        Career career = requireCareerAndCheckOwnership(id, userId, "修改");

        Long savedId = career.getId();
        BeanUtil.copyNonNullProperties(careerDTO, career);
        career.setId(savedId);

        if (career.getStartDate() != null && career.getEndDate() != null) {
            if (career.getEndDate().isBefore(career.getStartDate())) {
                throw new BusinessException(400, "离职时间不能早于入职时间");
            }
        }

        LocalDateTime now = DateTimeUtil.now();
        career.setUpdatedAt(now);

        int result = careerMapper.updateById(career);
        if (result <= 0) {
            throw new BusinessException(500, "更新工作经历失败");
        }

        log.info("更新工作经历成功: {}", career.getCompany());
        return career;
    }

    @Override
    public void delete(Long id) {
        Long userId = requireUserId();
        Career career = requireCareerAndCheckOwnership(id, userId, "删除");
        int result = careerMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(500, "删除工作经历失败");
        }

        log.info("删除工作经历成功: {}", career.getCompany());
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
            throw new BusinessException(401, "用户未登录");
        }
        return userId;
    }

    /**
     * 校验工作经历是否存在，返回工作经历实体
     *
     * @param id 工作经历ID
     * @return 工作经历实体
     * @throws BusinessException 如果工作经历不存在
     */
    private Career requireCareer(Long id) {
        Career career = careerMapper.selectById(id);
        if (career == null) {
            throw new BusinessException(404, "工作经历不存在");
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
            throw new BusinessException(403, "无权" + action + "他人工作经历");
        }
        return career;
    }
}

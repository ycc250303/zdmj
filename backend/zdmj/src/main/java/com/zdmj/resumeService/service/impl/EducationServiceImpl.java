package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.BeanUtil;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.exception.ErrorCode;
import com.zdmj.exception.BusinessException;
import com.zdmj.resumeService.dto.EducationDTO;
import com.zdmj.resumeService.entity.Education;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.service.EducationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教育经历服务实现类
 */
@Slf4j
@Service
public class EducationServiceImpl extends ServiceImpl<EducationMapper, Education> implements EducationService {

    /**
     * 添加教育经历
     *
     * @param educationDTO 教育经历DTO
     * @return 教育经历实体
     */
    @Override
    public Education create(EducationDTO educationDTO) {
        Long userId = UserHolder.requireUserId();
        Education education = new Education();
        education.setUserId(userId);
        education.setSchool(educationDTO.getSchool());
        education.setMajor(educationDTO.getMajor());
        education.setDegree(educationDTO.getDegree());
        education.setStartDate(educationDTO.getStartDate());
        education.setEndDate(educationDTO.getEndDate());
        education.setVisible(educationDTO.getVisible());
        education.setGpa(educationDTO.getGpa());
        // 使用统一的日期时间工具类，确保时区一致性
        LocalDateTime now = DateTimeUtil.now();
        education.setCreatedAt(now);
        education.setUpdatedAt(now);

        boolean saved = save(education);
        if (!saved) {
            throw new BusinessException(ErrorCode.EDUCATION_ADD_FAILED);
        }
        log.info("添加教育经历成功: {}", education.getSchool());
        return education;
    }

    /**
     * 更新教育经历
     *
     * @param educationDTO 教育经历DTO（包含ID和要更新的字段）
     * @return 更新后的教育经历实体
     */
    @Override
    public Education update(EducationDTO educationDTO) {
        Long userId = UserHolder.requireUserId();
        Long id = educationDTO.getId();
        if (id == null) {
            throw new BusinessException(ErrorCode.EDUCATION_ID_EMPTY);
        }

        Education existingEducation = requireEducationAndCheckOwnership(id, userId, "修改");

        // 只更新提供的字段（非空字段才更新）
        // 使用工具类简化代码，自动复制DTO中所有非null的属性到实体对象
        // 先保存原有的ID，防止被覆盖
        Long savedId = existingEducation.getId();
        BeanUtil.copyNonNullProperties(educationDTO, existingEducation);
        // 确保ID不被覆盖
        existingEducation.setId(savedId);

        // 验证日期逻辑：如果两个日期都提供了，需要验证结束时间不早于开始时间
        if (existingEducation.getStartDate() != null && existingEducation.getEndDate() != null) {
            if (existingEducation.getEndDate().isBefore(existingEducation.getStartDate())) {
                throw new BusinessException(ErrorCode.EDUCATION_GRADUATE_TIME_INVALID);
            }
        }

        // 更新更新时间
        existingEducation.setUpdatedAt(DateTimeUtil.now());

        // 使用 MyBatis-Plus 的 updateById 方法，根据ID更新（只更新非null字段）
        boolean updated = updateById(existingEducation);
        if (!updated) {
            throw new BusinessException(ErrorCode.EDUCATION_UPDATE_FAILED);
        }

        log.info("用户 {} 更新教育经历成功: id={}", userId, id);
        return existingEducation;
    }

    /**
     * 删除教育经历
     *
     * @param id 教育经历ID
     */
    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        requireEducationAndCheckOwnership(id, userId, "删除");

        // 删除教育经历
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.EDUCATION_DELETE_FAILED);
        }

        log.info("用户 {} 删除教育经历成功: id={}", userId, id);
    }

    /**
     * 根据ID查询教育经历
     *
     * @param id 教育经历ID
     * @return 教育经历实体
     */
    @Override
    public Education getById(Long id) {
        return requireEducation(id);
    }

    /**
     * 根据用户ID查询所有教育经历
     * 
     * @return 教育经历列表
     */
    @Override
    public List<Education> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId, null);
    }

    /**
     * 校验教育经历是否存在，返回教育经历实体
     *
     * @param id 教育经历ID
     * @return 教育经历实体
     * @throws BusinessException 如果教育经历不存在
     */
    private Education requireEducation(Long id) {
        Education education = baseMapper.selectById(id);
        if (education == null) {
            throw new BusinessException(ErrorCode.EDUCATION_NOT_FOUND);
        }
        return education;
    }

    /**
     * 校验教育经历是否存在且用户有权限操作，返回教育经历实体
     *
     * @param id     教育经历ID
     * @param userId 用户ID
     * @param action 操作类型（用于错误提示）
     * @return 教育经历实体
     * @throws BusinessException 如果教育经历不存在或用户无权限
     */
    private Education requireEducationAndCheckOwnership(Long id, Long userId, String action) {
        Education education = requireEducation(id);
        if (!education.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(), ErrorCode.NO_PERMISSION.getMessage() + action + "他人教育经历");
        }
        return education;
    }
}

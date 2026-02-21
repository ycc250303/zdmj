package com.zdmj.resumeService.controller;

import com.zdmj.common.Result;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import com.zdmj.resumeService.dto.EducationDTO;
import com.zdmj.resumeService.entity.Education;
import com.zdmj.resumeService.service.EducationService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教育经历控制器
 */
@Slf4j
@RestController
@RequestMapping("/educations")
public class EducationController {

    private final EducationService educationService;

    public EducationController(EducationService educationService) {
        this.educationService = educationService;
    }

    /**
     * 添加教育经历
     * 使用 CreateGroup 验证组，所有必填字段都需要验证
     */
    @PostMapping
    public Result<Education> addEducation(@Validated(CreateGroup.class) @RequestBody EducationDTO educationDTO) {
        return Result.success("添加教育经历成功", educationService.create(educationDTO));
    }

    /**
     * 更新教育经历
     * 使用 UpdateGroup 验证组，只验证提供的字段（非空字段才验证）
     * ID 包含在请求体中，不使用路径变量
     */
    @PutMapping
    public Result<Education> updateEducation(
            @Validated(UpdateGroup.class) @RequestBody EducationDTO educationDTO) {
        return Result.success("更新教育经历成功", educationService.update(educationDTO));
    }

    /**
     * 删除教育经历
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteEducation(@PathVariable Long id) {
        educationService.delete(id);
        return Result.success("删除教育经历成功", null);
    }

    /**
     * 根据ID查询教育经历
     */
    @GetMapping("/{id}")
    public Result<Education> getEducationById(@PathVariable Long id) {
        return Result.success("查询成功", educationService.getById(id));
    }

    /**
     * 根据用户ID查询所有教育经历
     */
    @GetMapping
    public Result<List<Education>> getEducations() {
        return Result.success("查询成功", educationService.getByUserId());
    }
}

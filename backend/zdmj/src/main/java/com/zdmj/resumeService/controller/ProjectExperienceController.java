package com.zdmj.resumeService.controller;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.ProjectExperienceDTO;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.service.ProjectExperienceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目经历控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/projects")
@Tag(name = "项目经历", description = "项目经历的增删改查")
public class ProjectExperienceController {
    private final ProjectExperienceService projectExperienceService;

    /**
     * 添加项目经历
     * 
     * @param projectExperienceDTO 项目经历DTO
     * @return 项目经历
     */
    @PostMapping
    public Result<ProjectExperience> addProjectExperience(
            @Validated(CreateGroup.class) @RequestBody ProjectExperienceDTO projectExperienceDTO) {
        return Result.success("添加项目经历成功", projectExperienceService.create(projectExperienceDTO));
    }

    /**
     * 更新项目经历
     * 
     * @param projectExperienceDTO 项目经历DTO
     * @return 项目经历
     */
    @PutMapping
    public Result<ProjectExperience> updateProjectExperience(
            @Validated(UpdateGroup.class) @RequestBody ProjectExperienceDTO projectExperienceDTO) {
        return Result.success("更新项目经历成功", projectExperienceService.update(projectExperienceDTO));
    }

    /**
     * 删除项目经历
     * 
     * @param id 项目经历ID
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProjectExperience(@PathVariable Long id) {
        projectExperienceService.delete(id);
        return Result.success("删除项目经历成功", null);
    }

    /**
     * 根据ID查询项目经历
     * 
     * @param id 项目经历ID
     * @return 项目经历
     */
    @GetMapping("/{id}")
    public Result<ProjectExperience> getProjectExperienceById(@PathVariable Long id) {
        return Result.success("查询项目经历成功", projectExperienceService.getById(id));
    }

    /**
     * 查询所有项目经历
     * 
     * @return 项目经历列表
     */
    @GetMapping
    public Result<List<ProjectExperience>> getProjectExperiences() {
        return Result.success("查询项目经历成功", projectExperienceService.getByUserId());
    }
}

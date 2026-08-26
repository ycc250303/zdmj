package com.zdmj.resumeService.controller;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.ProjectExperienceRequest;
import com.zdmj.resumeService.dto.ProjectExperienceResponse;
import com.zdmj.resumeService.service.ProjectExperienceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/projects")
@Tag(name = "项目经历", description = "项目经历的增删改查")
public class ProjectExperienceController {
    private final ProjectExperienceService projectExperienceService;

    @PostMapping
    public Result<ProjectExperienceResponse> addProjectExperience(
            @Validated(CreateGroup.class) @RequestBody ProjectExperienceRequest projectExperienceRequest) {
        return Result.success("添加项目经历成功", projectExperienceService.create(projectExperienceRequest));
    }

    @PutMapping
    public Result<ProjectExperienceResponse> updateProjectExperience(
            @Validated(UpdateGroup.class) @RequestBody ProjectExperienceRequest projectExperienceRequest) {
        return Result.success("更新项目经历成功", projectExperienceService.update(projectExperienceRequest));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProjectExperience(@PathVariable Long id) {
        projectExperienceService.delete(id);
        return Result.success("删除项目经历成功", null);
    }

    @GetMapping("/{id}")
    public Result<ProjectExperienceResponse> getProjectExperienceById(@PathVariable Long id) {
        return Result.success("查询项目经历成功", projectExperienceService.getById(id));
    }

    @GetMapping
    public Result<List<ProjectExperienceResponse>> getProjectExperiences() {
        return Result.success("查询项目经历成功", projectExperienceService.getByUserId());
    }
}

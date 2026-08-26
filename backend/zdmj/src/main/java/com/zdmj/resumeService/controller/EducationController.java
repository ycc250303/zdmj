package com.zdmj.resumeService.controller;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.EducationResponse;
import com.zdmj.resumeService.service.EducationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/educations")
@Tag(name = "教育经历", description = "教育经历的增删改查")
public class EducationController {

    private final EducationService educationService;

    @PostMapping
    public Result<EducationResponse> addEducation(
            @Validated(CreateGroup.class) @RequestBody EducationRequest educationRequest) {
        return Result.success("添加教育经历成功", educationService.create(educationRequest));
    }

    @PutMapping
    public Result<EducationResponse> updateEducation(
            @Validated(UpdateGroup.class) @RequestBody EducationRequest educationRequest) {
        return Result.success("更新教育经历成功", educationService.update(educationRequest));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteEducation(@PathVariable Long id) {
        educationService.delete(id);
        return Result.success("删除教育经历成功", null);
    }

    @GetMapping("/{id}")
    public Result<EducationResponse> getEducationById(@PathVariable Long id) {
        return Result.success("查询成功", educationService.getById(id));
    }

    @GetMapping
    public Result<List<EducationResponse>> getEducations() {
        return Result.success("查询成功", educationService.getByUserId());
    }
}

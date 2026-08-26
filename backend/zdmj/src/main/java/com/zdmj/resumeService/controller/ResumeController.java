package com.zdmj.resumeService.controller;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.ai.LlmRateLimits;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.dto.ResumeRequest;
import com.zdmj.resumeService.dto.ResumeResponse;
import com.zdmj.resumeService.service.ResumeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/resumes")
@Tag(name = "简历管理", description = "简历 CRUD 与内容查询")
public class ResumeController {
    private final ResumeService resumeService;

    @PostMapping
    public Result<ResumeResponse> createResume(
            @Validated(CreateGroup.class) @RequestBody ResumeRequest resumeRequest) {
        return Result.success("创建简历成功", resumeService.create(resumeRequest));
    }

    @GetMapping("/me/content")
    public Result<ResumeContentResponse> getMyResumeContent() {
        return Result.success("查询简历完整内容成功", resumeService.getMyResumeContent());
    }

    @PutMapping("/me/content")
    public Result<ResumeContentResponse> saveMyResumeContent(
            @Validated @RequestBody ResumeContentSaveRequest request) {
        return Result.success("保存简历成功", resumeService.saveMyResumeContent(request));
    }

    @GetMapping("/content")
    public Result<List<ResumeContentResponse>> getResumeContentList() {
        return Result.success("查询简历完整内容成功", resumeService.getResumeContentList());
    }

    @GetMapping
    public Result<List<ResumeResponse>> getResumes() {
        return Result.success("查询简历成功", resumeService.getByUserId());
    }

    @PutMapping
    public Result<ResumeResponse> updateResume(
            @Validated(UpdateGroup.class) @RequestBody ResumeRequest resumeRequest) {
        return Result.success("更新简历成功", resumeService.update(resumeRequest));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteResume(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.success("删除简历成功", null);
    }

    @RateLimit(dimension = RateLimit.Dimension.USER, count = LlmRateLimits.RESUME_IMPORT_PARSE_PER_MIN,
            interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/import/parse")
    public Result<ResumeImportParseResponse> parseResumeImport(
            @Validated @RequestBody ResumeImportParseRequest request) {
        ResumeImportParseResponse result = resumeService.parseImport(request);
        return Result.success("简历识别成功", result);
    }
}

package com.zdmj.resumeService.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.model.Result;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.service.ResumeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@RestController
@RequestMapping("/resumes")
@Tag(name = "简历管理", description = "当前用户整份简历的读取、保存与识别")
public class ResumeController {
    private final ResumeService resumeService;

    @GetMapping("/me/content")
    public Result<ResumeContentResponse> getMyResumeContent(@Parameter(hidden = true) CurrentActor actor) {
        return Result.success("查询简历完整内容成功", resumeService.getMyResumeContent(actor));
    }

    @PutMapping("/me/content")
    public Result<ResumeContentResponse> saveMyResumeContent(@Parameter(hidden = true) CurrentActor actor,
            @Validated @RequestBody ResumeContentSaveRequest request) {
        return Result.success("保存简历成功", resumeService.saveMyResumeContent(actor, request));
    }

    @GetMapping("/content")
    public Result<List<ResumeContentResponse>> getResumeContentList(@Parameter(hidden = true) CurrentActor actor) {
        return Result.success("查询简历完整内容成功", resumeService.getResumeContentList(actor));
    }

    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping(value = "/import/parse", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ResumeImportParseResponse> parseResumeImport(@Parameter(hidden = true) CurrentActor actor,
            @Validated @RequestBody ResumeImportParseRequest request) {
        return Result.success("简历识别成功", resumeService.parseImport(actor, request));
    }

    /**
     * 从上传文件提取文本并识别简历。文件只在本次请求中读取，不写入对象存储。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param file 简历文件
     * @return 识别结果
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping(value = "/import/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeImportParseResponse> parseResumeImportFile(@Parameter(hidden = true) CurrentActor actor,
            @RequestParam("file") MultipartFile file) {
        return Result.success("简历识别成功", resumeService.parseImport(actor, file));
    }
}

package com.zdmj.resumeService.controller;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.Result;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import com.zdmj.resumeService.dto.ResumeDTO;
import com.zdmj.resumeService.dto.ResumeFullContentDTO;
import com.zdmj.resumeService.entity.Resume;
import com.zdmj.resumeService.service.ResumeService;

/**
 * 简历控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * 创建简历
     * 
     * @param resumeDTO 简历DTO
     * @return 创建的简历
     */
    @PostMapping
    public Result<Resume> createResume(@Validated(CreateGroup.class) @RequestBody ResumeDTO resumeDTO) {
        return Result.success("创建简历成功", resumeService.create(resumeDTO));
    }

    /**
     * 根据ID查询简历
     * 
     * @param id 简历ID
     * @return 查询的简历
     */
    @GetMapping("/{id}")
    public Result<Resume> getResumeById(@PathVariable Long id) {
        return Result.success("查询简历成功", resumeService.getById(id));
    }

    /**
     * 根据ID查询简历完整内容
     * 
     * @param id 简历ID
     * @return 查询的简历
     */
    @GetMapping("/{id}/full-content")
    public Result<ResumeFullContentDTO> getResumeFullContentById(@PathVariable Long id) {
        return Result.success("查询简历完整内容成功", resumeService.getResumeFullContentById(id));
    }

    /**
     * 查询所有简历完整内容
     * 
     * @return 查询的简历完整内容列表
     */
    @GetMapping("/full-content")
    public Result<List<ResumeFullContentDTO>> getResumeFullContent() {
        return Result.success("查询简历完整内容成功", resumeService.getResumeFullContent());
    }

    /**
     * 查询所有简历
     * 
     * @return 查询的简历列表
     */
    @GetMapping
    public Result<List<Resume>> getResumes() {
        return Result.success("查询简历成功", resumeService.getByUserId());
    }

    /**
     * 更新简历
     * 
     * @param resumeDTO 简历DTO
     * @return 更新的简历
     */
    @PutMapping
    public Result<Resume> updateResume(@Validated(UpdateGroup.class) @RequestBody ResumeDTO resumeDTO) {
        return Result.success("更新简历成功", resumeService.update(resumeDTO));
    }

    /**
     * 删除简历
     * 
     * @param id 简历ID
     * @return 删除的简历
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteResume(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.success("删除简历成功", null);
    }
}
